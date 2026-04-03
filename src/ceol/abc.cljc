(ns ceol.abc
  (:require [clojure.string :as str]))

(defn tempo-for-type
  "Return ABC Q: field appropriate for tune type.
   Tempos are slightly relaxed for learning."
  [tune-type time-sig]
  (case tune-type
    :polka    "Q:1/4=120"
    :jig      "Q:3/8=100"
    :reel     "Q:1/4=100"
    :hornpipe "Q:1/4=100"
    :slip-jig "Q:3/8=100"
    :slide    "Q:3/8=100"
    (case time-sig
      "6/8" "Q:3/8=100"
      "9/8" "Q:3/8=100"
      "3/4" "Q:1/4=120"
      "Q:1/4=100")))

(defn build-abc-string
  "Construct a full ABC file from tune metadata and ABC notation body."
  [tune abc-body abc-key]
  (let [mode-abbrev (case (:mode-name tune)
                      "Ionian"  ""
                      "Dorian"  "dor"
                      "Aeolian" "m"
                      "")
        k-field (str (:key tune) mode-abbrev)
        tempo (tempo-for-type (:type tune) (:time-sig tune))
        clean-body (str/replace abc-body "! " "\n")]
    (str "X:1\n"
         "T:" (:name tune) "\n"
         "M:" (:time-sig tune) "\n"
         "L:1/8\n"
         tempo "\n"
         "K:" (or abc-key k-field) "\n"
         "%%MIDI program 105\n"
         clean-body "\n")))

(defn adjust-abc-tempo
  "Adjust the BPM in an ABC string's Q: field by offset. Clamps to min 40."
  [abc-str offset]
  (if (or (nil? offset) (zero? offset))
    abc-str
    (str/replace abc-str #"(Q:\d+/\d+=)(\d+)"
                 (fn [[_ prefix bpm-str]]
                   (str prefix (max 40 (+ (#?(:clj parse-long :cljs js/parseInt) bpm-str) offset)))))))

(defn header-line? [line]
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
        p (cond
            (str/starts-with? p "||") (str/trim (subs p 2))
            (str/starts-with? p "|:") (str/trim (subs p 2))
            :else p)
        p (if (str/ends-with? p "||")
            (str/trim (subs p 0 (- (count p) 2)))
            p)]
    (str "|:" p (if (str/ends-with? p ":|") "" ":|"))))

(defn split-abc-parts
  "Split ABC into parts A and B. Tries || boundary first, then falls back to first :|.
   Returns {:a \"full-abc-for-A\" :b \"full-abc-for-B\"} or nil."
  [abc-str]
  (let [lines (str/split-lines abc-str)
        header-lines (vec (take-while header-line? lines))
        header (str/join "\n" header-lines)
        body (str/join "\n" (drop (count header-lines) lines))
        [a b] (or (split-body-at body "|| |:")
                  (split-body-at body "||\n|:")
                  (split-body-at body "||\n")
                  (when-let [idx (str/index-of body ":|")]
                    (let [a (str/trim (subs body 0 idx))
                          rest-body (str/trim (subs body (+ idx 2)))]
                      (when (seq rest-body)
                        [a rest-body]))))]
    (when (and a b)
      {:a (str header "\n" (ensure-repeats a) "\n")
       :b (str header "\n" (ensure-repeats b) "\n")})))
