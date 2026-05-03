(ns ceol.audio
  (:require [babashka.http-client :as http]
            [babashka.process :as proc]
            [ceol.abc :as abc]
            [ceol.data :as data]
            [charm.core :as charm]
            [cheshire.core]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;; --- thesession.org API ---

(defn- parse-json [s]
  (cheshire.core/parse-string s true))

(defn- session-key->abc-key
  "Convert thesession.org key format to ABC key field.
   e.g. 'Gmajor' -> 'G', 'Eminor' -> 'Em', 'Adorian' -> 'Ador'"
  [session-key]
  (when session-key
    (-> session-key
        (str/replace #"major$" "")
        (str/replace #"minor$" "m")
        (str/replace #"mixolydian$" "mix")
        (str/replace #"dorian$" "dor")
        (str/replace #"lydian$" "lyd")
        (str/replace #"phrygian$" "phr")
        (str/replace #"locrian$" "loc"))))

(defn- search-session [tune-name]
  (let [url (str "https://thesession.org/tunes/search?q="
                 (java.net.URLEncoder/encode tune-name "UTF-8")
                 "&format=json")
        resp (http/get url {:headers {"Accept" "application/json"}
                            :timeout 10000})]
    (when (= 200 (:status resp))
      (parse-json (:body resp)))))

(defn- get-session-tune [session-id]
  (let [url (str "https://thesession.org/tunes/" session-id "?format=json&orderby=popular")
        resp (http/get url {:headers {"Accept" "application/json"}
                            :timeout 10000})]
    (when (= 200 (:status resp))
      (parse-json (:body resp)))))

(defn- best-match
  "Pick the best match from search results by comparing name and key.
   Returns nil if no reasonable match found — never picks a random tune."
  [results tune]
  (let [tunes (:tunes results)
        target-name (str/lower-case (:name tune))]
    (or
     ;; Exact name match
     (first (filter #(= target-name (str/lower-case (:name %))) tunes))
     ;; Partial name match
     (first (filter #(str/includes? (str/lower-case (:name %)) target-name) tunes)))))

(defn- pick-setting
  "Pick the best ABC setting, preferring matching key."
  [settings tune-key]
  (let [key-match (first (filter #(str/starts-with? (or (:key %) "") tune-key) settings))]
    (or key-match (first settings))))

;; Pure ABC functions live in ceol.abc — use them directly via the abc/ alias

;; --- Commands (async via charm/cmd) ---

(defn fetch-abc-cmd
  "Command to fetch ABC from thesession.org. Returns message with result."
  [tune]
  (charm/cmd
   (fn []
     (try
       (let [tune-id (:id tune)
             result (if-let [sid (:session-id tune)]
                      ;; Known session ID - fetch directly
                      (get-session-tune sid)
                      ;; Search by name
                      (let [results (search-session (:name tune))
                            match (best-match results tune)]
                        (when match
                          (get-session-tune (:id match)))))
             setting (when result
                       (pick-setting (:settings result) (:key tune)))
             abc-body (:abc setting)
             abc-key (session-key->abc-key (:key setting))
             session-id (:id result)]
         (if abc-body
           (let [abc-str (abc/build-abc-string tune abc-body abc-key)]
             ;; Cache the result
             (data/update-cache! tune-id {:session-id session-id :abc abc-str})
             {:type :abc-fetched
              :tune-id tune-id
              :abc abc-str
              :session-id session-id})
           {:type :abc-failed
            :tune-id tune-id
            :error "No ABC notation found"}))
       (catch Exception e
         {:type :abc-failed
          :tune-id (:id tune)
          :error (.getMessage e)})))))

(defn- repeat-abc-body
  "Duplicate the body of an ABC string n times for seamless looping."
  [abc-str n]
  (if (<= n 1)
    abc-str
    (let [lines (str/split-lines abc-str)
          header-lines (vec (take-while abc/header-line? lines))
          header (str/join "\n" header-lines)
          body (str/join "\n" (drop (count header-lines) lines))]
      (str header "\n" (str/join "\n" (repeat n body)) "\n"))))

(defn convert-midi-cmd
  "Command to convert ABC string to MIDI via abc2midi.
   Applies section extraction and tempo adjustment before converting."
  [tune abc-str tempo-offset section & {:keys [loop-count] :or {loop-count 1}}]
  (charm/cmd
   (fn []
     (try
       (let [tune-id (:id tune)
             ;; Extract section if needed
             section-abc (if section
                           (or (get (abc/split-abc-parts abc-str) section) abc-str)
                           abc-str)
             ;; Apply tempo offset
             tempo-abc (abc/adjust-abc-tempo section-abc (or tempo-offset 0))
             ;; Bake in repeats for looping
             final-abc (repeat-abc-body tempo-abc loop-count)
             abc-path (data/abc-file-path tune-id)
             loop? (> loop-count 1)
             midi-path (data/midi-file-path-for tune-id tempo-offset section :loop? loop?)]
         (data/ensure-dirs!)
         (spit abc-path final-abc)
         (let [result @(proc/process {:cmd ["abc2midi" abc-path "-o" midi-path]
                                      :out :string
                                      :err :string})]
           (if (and (zero? (:exit result))
                    (.exists (io/file midi-path)))
             (do
               ;; Only cache base (no tempo/section) conversions
               (when (and (nil? section) (or (nil? tempo-offset) (zero? tempo-offset)))
                 (data/update-cache! tune-id {:midi-path midi-path}))
               {:type :midi-ready
                :tune-id tune-id
                :midi-path midi-path})
             {:type :midi-failed
              :tune-id tune-id
              :error (or (not-empty (:err result)) "abc2midi failed")})))
       (catch Exception e
         {:type :midi-failed
          :tune-id (:id tune)
          :error (.getMessage e)})))))

(defn play-cmd
  "Command to start fluidsynth playback. Returns process handle."
  [midi-path]
  (charm/cmd
   (fn []
     (let [sf (data/soundfont-path)]
       (if sf
         (let [p (proc/process {:cmd ["fluidsynth" "-niq" sf midi-path]
                                :out :string
                                :err :string})]
           {:type :playback-started
            :proc (:proc p)})
         {:type :playback-failed
          :error "No soundfont found"})))))

(defn watch-playback-cmd
  "Command to wait for fluidsynth process to finish."
  [proc tune-id]
  (charm/cmd
   (fn []
     (.waitFor proc)
     {:type :playback-finished
      :tune-id tune-id
      :proc proc})))

(defn stop-playback!
  "Destroy the fluidsynth process."
  [proc]
  (when proc
    (try
      (.destroyForcibly proc)
      (catch Exception _ nil))))

;; --- Count-in click ---

(defn countin-body
  "ABC body for 1 bar of woodblock clicks matching the time signature."
  [time-sig]
  (case time-sig
    "6/8" "C C C C C C |"
    "9/8" "C C C C C C C C C |"
    "4/4" "C2 C2 C2 C2 |"
    "2/4" "C2 C2 |"
    ;; fallback: 4 quarter notes
    "C2 C2 C2 C2 |"))

(defn countin-abc-string
  "Full ABC string for a 1-bar count-in click using MIDI program 115 (woodblock)."
  [time-sig tempo-q-str]
  (str "X:1\n"
       "T:Count-in\n"
       "M:" time-sig "\n"
       tempo-q-str "\n"
       "L:1/8\n"
       "K:C\n"
       "%%MIDI program 115\n"
       (countin-body time-sig) "\n"))

(defn effective-tempo-str
  "Return Q: string for a tune after applying tempo offset."
  [tune tempo-offset]
  (let [base-q (abc/tempo-for-type (:type tune) (:time-sig tune))
        offset (or tempo-offset 0)]
    (if (zero? offset)
      base-q
      (str/replace base-q #"(Q:\d+/\d+=)(\d+)"
                   (fn [[_ prefix bpm-str]]
                     (str prefix (max 40 (+ (parse-long bpm-str) offset))))))))

(defn- extract-bpm
  "Extract integer BPM from a Q: string like \"Q:3/8=70\"."
  [q-str]
  (when-let [[_ bpm] (re-find #"=(\d+)" q-str)]
    (parse-long bpm)))

(defn- countin-midi-path
  "Cache path for count-in MIDI: ~/.ceol/midi/countin_{num}_{den}_{bpm}.mid"
  [time-sig tempo-q-str]
  (let [[num den] (str/split time-sig #"/")
        bpm (extract-bpm tempo-q-str)]
    (str data/midi-dir "/countin_" num "_" den "_" bpm ".mid")))

(defn countin-convert-and-play-cmd
  "Command to generate count-in MIDI (if not cached) and play it via fluidsynth.
   Returns :countin-started with the process, or :countin-failed."
  [time-sig tempo-q-str]
  (charm/cmd
   (fn []
     (try
       (let [midi-path (countin-midi-path time-sig tempo-q-str)]
         (when-not (.exists (io/file midi-path))
           (data/ensure-dirs!)
           (let [abc (countin-abc-string time-sig tempo-q-str)
                 abc-path (str data/abc-dir "/countin.abc")]
             (spit abc-path abc)
             (let [result @(proc/process {:cmd ["abc2midi" abc-path "-o" midi-path]
                                          :out :string :err :string})]
               (when-not (zero? (:exit result))
                 (throw (Exception. (str "abc2midi failed: " (:err result))))))))
         (let [sf (data/soundfont-path)]
           (if sf
             (let [p (proc/process {:cmd ["fluidsynth" "-niq" sf midi-path]
                                    :out :string :err :string})]
               {:type :countin-started
                :proc (:proc p)})
             {:type :countin-failed
              :error "No soundfont found"})))
       (catch Exception e
         {:type :countin-failed
          :error (.getMessage e)})))))

(defn watch-countin-cmd
  "Command to wait for count-in fluidsynth process to finish."
  [proc]
  (charm/cmd
   (fn []
     (.waitFor proc)
     {:type :countin-finished
      :proc proc})))

(defn playback-tick-cmd
  "Command that sleeps 150ms then sends a :playback-tick message.
   Self-restarting: the state handler spawns a new one if still playing."
  []
  (charm/cmd
   (fn []
     (Thread/sleep 150)
     {:type :playback-tick})))
