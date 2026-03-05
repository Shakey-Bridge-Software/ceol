(ns ceol.audio
  (:require [babashka.http-client :as http]
            [babashka.process :as proc]
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

(defn- tempo-for-type
  "Return ABC Q: field appropriate for tune type.
   Tempos are slightly relaxed for learning."
  [tune-type time-sig]
  (case tune-type
    :polka    "Q:1/4=70"
    :jig      "Q:3/8=70"
    :reel     "Q:1/4=60"
    :hornpipe "Q:1/4=40"
    :slip-jig "Q:3/8=70"
    ;; fallback based on time sig
    (case time-sig
      "6/8" "Q:3/8=60"
      "9/8" "Q:3/8=60"
      "Q:1/4=60")))

(defn- build-abc-string
  "Construct a full ABC file from tune metadata and ABC notation body."
  [tune abc-body abc-key]
  (let [mode-abbrev (case (:mode-name tune)
                      "Ionian"  ""
                      "Dorian"  "dor"
                      "Aeolian" "m"
                      "")
        k-field (str (:key tune) mode-abbrev)
        tempo (tempo-for-type (:type tune) (:time-sig tune))
        ;; Strip thesession.org line break markers (|! between bars)
        ;; abc2midi misinterprets them as decoration markers
        clean-body (str/replace abc-body "! " "\n")]
    (str "X:1\n"
         "T:" (:name tune) "\n"
         "M:" (:time-sig tune) "\n"
         tempo "\n"
         "K:" (or abc-key k-field) "\n"
         "%%MIDI program 105\n"
         clean-body "\n")))

;; --- Tempo + section helpers ---

(defn adjust-abc-tempo
  "Adjust the BPM in an ABC string's Q: field by offset. Clamps to min 40."
  [abc-str offset]
  (if (or (nil? offset) (zero? offset))
    abc-str
    (str/replace abc-str #"(Q:\d+/\d+=)(\d+)"
                 (fn [[_ prefix bpm-str]]
                   (str prefix (max 40 (+ (parse-long bpm-str) offset)))))))

(defn- header-line? [line]
  (or (re-matches #"^[A-Z]:.*" line)
      (str/starts-with? line "%%")))

(defn- split-body-at
  "Split body into [a-str b-str] at separator, returning nil if not found."
  [body sep]
  (when-let [idx (str/index-of body sep)]
    (let [a (str/trim (subs body 0 idx))
          b (str/trim (subs body (+ idx (count sep))))]
      (when (and (seq a) (seq b))
        [a b]))))

(defn- ensure-repeats
  "Ensure a part has |: and :| repeat markers."
  [part]
  (let [p (str/trim part)
        ;; Strip leading || or |:
        p (cond
            (str/starts-with? p "||") (str/trim (subs p 2))
            (str/starts-with? p "|:") (str/trim (subs p 2))
            :else p)
        ;; Strip trailing || after :|
        p (if (str/ends-with? p "||")
            (str/trim (subs p 0 (- (count p) 2)))
            p)]
    (str "|:" p (if (str/ends-with? p ":|") "" ":|"))))

(defn split-abc-parts
  "Split ABC into parts A and B. Tries || boundary first (tunes with
   1st/2nd endings), then falls back to first :| split.
   Returns {:a \"full-abc-for-A\" :b \"full-abc-for-B\"} or nil."
  [abc-str]
  (let [lines (str/split-lines abc-str)
        header-lines (vec (take-while header-line? lines))
        header (str/join "\n" header-lines)
        body (str/join "\n" (drop (count header-lines) lines))
        ;; Try splitting on || (part boundary with 1st/2nd endings)
        [a b] (or (split-body-at body "|| |:")
                  (split-body-at body "||\n|:")
                  (split-body-at body "||\n")
                   ;; Fallback: split on first :|
                  (when-let [idx (str/index-of body ":|")]
                    (let [a (str/trim (subs body 0 idx))
                          rest-body (str/trim (subs body (+ idx 2)))]
                      (when (and (seq rest-body) (str/index-of rest-body ":|"))
                        [a rest-body]))))]
    (when (and a b)
      {:a (str header "\n" (ensure-repeats a) "\n")
       :b (str header "\n" (ensure-repeats b) "\n")})))

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
           (let [abc-str (build-abc-string tune abc-body abc-key)]
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
          header-lines (vec (take-while header-line? lines))
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
                           (or (get (split-abc-parts abc-str) section) abc-str)
                           abc-str)
             ;; Apply tempo offset
             tempo-abc (adjust-abc-tempo section-abc (or tempo-offset 0))
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
