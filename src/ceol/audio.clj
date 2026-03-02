(ns ceol.audio
  (:require [babashka.http-client :as http]
            [babashka.process :as proc]
            [ceol.data :as data]
            [charm.core :as charm]
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
        (str/replace #"dorian$" "dor"))))

(defn- search-session [tune-name]
  (let [url (str "https://thesession.org/tunes/search?q="
                 (java.net.URLEncoder/encode tune-name "UTF-8")
                 "&format=json")
        resp (http/get url {:headers {"Accept" "application/json"}})]
    (when (= 200 (:status resp))
      (parse-json (:body resp)))))

(defn- get-session-tune [session-id]
  (let [url (str "https://thesession.org/tunes/" session-id "?format=json")
        resp (http/get url {:headers {"Accept" "application/json"}})]
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
  (let [key-match (first (filter #(str/includes? (or (:key %) "") tune-key) settings))]
    (or key-match (first settings))))

(defn- tempo-for-type
  "Return ABC Q: field appropriate for tune type.
   Tempos are slightly relaxed for learning."
  [tune-type time-sig]
  (case tune-type
    :polka    "Q:1/4=110"
    :jig      "Q:3/8=110"
    :reel     "Q:1/4=100"
    :hornpipe "Q:1/4=76"
    :slip-jig "Q:3/8=110"
    ;; fallback based on time sig
    (case time-sig
      "6/8" "Q:3/8=100"
      "9/8" "Q:3/8=100"
      "Q:1/4=100")))

(defn- build-abc-string
  "Construct a full ABC file from tune metadata and ABC notation body."
  [tune abc-body abc-key]
  (let [mode-abbrev (case (:mode-name tune)
                      "Ionian"  ""
                      "Dorian"  "dor"
                      "Aeolian" "m"
                      "")
        k-field (str (:key tune) mode-abbrev)
        tempo (tempo-for-type (:type tune) (:time-sig tune))]
    (str "X:1\n"
         "T:" (:name tune) "\n"
         "M:" (:time-sig tune) "\n"
         tempo "\n"
         "K:" (or abc-key k-field) "\n"
         "%%MIDI program 25\n"
         abc-body "\n")))

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

(defn split-abc-parts
  "Split ABC into parts A and B by finding the :|...|: boundary.
   Returns {:a \"full-abc-for-A\" :b \"full-abc-for-B\"} or nil."
  [abc-str]
  (let [lines (str/split-lines abc-str)
        header-lines (vec (take-while header-line? lines))
        header (str/join "\n" header-lines)
        body (str/join "\n" (drop (count header-lines) lines))
        parts (str/split body #":\|\s*\|:")]
    (when (>= (count parts) 2)
      (let [a (str/trim (first parts))
            b (str/trim (second parts))
            a (str (if (str/starts-with? a "|:") a (str "|:" a)) ":|")
            b (str "|:" (if (str/ends-with? b ":|") b (str b ":|")))]
        {:a (str header "\n" a "\n")
         :b (str header "\n" b "\n")}))))

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

(defn convert-midi-cmd
  "Command to convert ABC string to MIDI via abc2midi.
   Applies section extraction and tempo adjustment before converting."
  [tune abc-str tempo-offset section]
  (charm/cmd
   (fn []
     (try
       (let [tune-id (:id tune)
             ;; Extract section if needed
             section-abc (if section
                           (or (get (split-abc-parts abc-str) section) abc-str)
                           abc-str)
             ;; Apply tempo offset
             final-abc (adjust-abc-tempo section-abc (or tempo-offset 0))
             abc-path (data/abc-file-path tune-id)
             midi-path (data/midi-file-path-for tune-id tempo-offset section)]
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
