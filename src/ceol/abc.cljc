(ns ceol.abc
  "Pure ABC string utilities shared between TUI and web.
   No state, no I/O. Covers header assembly (build-abc-string), tempo
   adjustment (adjust-abc-tempo), line breaking (add-line-breaks),
   pickup offset computation (compute-pickup-offset-s), and
   A/B section splitting (split-abc-body, split-abc-parts). Tempo is owned by
   ceol.beat-engine; tempo-for-type just formats its Q: field."
  (:require [clojure.string :as str]
            [ceol.beat-engine :as be]))

(defn tempo-for-type
  "Return the ABC Q: field appropriate for a tune type + time-sig, formatted
   from ceol.beat-engine's shared {:bpm :beat-unit} tempo table. Signature is
   unchanged so existing callers (TUI, melody build, display) are untouched.
   Tempos are slightly relaxed for learning."
  [tune-type time-sig]
  (let [{:keys [bpm beat-unit]} (be/tempo-params tune-type time-sig)]
    (str "Q:" beat-unit "=" bpm)))

(defn build-abc-string
  "Construct a full ABC file from tune metadata and ABC notation body.
   Options: :midi? (default true) — include %%MIDI program 105 directive."
  [tune abc-body abc-key & [{:keys [midi?] :or {midi? true}}]]
  (let [mode-abbrev (case (:mode-name tune)
                      "Ionian"  ""
                      "Dorian"  "dor"
                      "Aeolian" "m"
                      "")
        k-field    (str (:key tune) mode-abbrev)
        tempo      (tempo-for-type (:type tune) (:time-sig tune))
        clean-body (str/replace abc-body "! " "\n")]
    (str "X:1\n"
         "T:" (:name tune) "\n"
         "M:" (:time-sig tune) "\n"
         "L:1/8\n"
         tempo "\n"
         "K:" (or abc-key k-field) "\n"
         (when midi? "%%MIDI program 105\n")
         clean-body "\n")))

(defn adjust-abc-tempo
  "Adjust the BPM in an ABC string's Q: field by offset. Clamps to min 40 via
   the shared ceol.beat-engine/clamp-bpm."
  [abc-str offset]
  (if (or (nil? offset) (zero? offset))
    abc-str
    (str/replace abc-str #"(Q:\d+/\d+=)(\d+)"
                 (fn [[_ prefix bpm-str]]
                   (str prefix (be/clamp-bpm (+ (be/parse-int bpm-str) offset)))))))

(defn- split-bar-contents
  "Split an ABC body string into bar-separated content strings.
  Skips repeat markers (|: :|) to find note barlines."
  [body]
  (let [;; Find all plain | barlines (not |:, :|, ||)
        plain-bars (loop [i 0 result []]
                     (if-let [idx (str/index-of body "|" i)]
                       (let [prev (when (pos? idx) (nth body (dec idx)))
                             next (when (< (inc idx) (count body)) (nth body (inc idx)))
                             plain? (and (not= prev \:)
                                         (not= next \:)
                                         (not= next \|))
                             bar-content (subs body i idx)]
                         (recur (inc idx)
                                (if plain? (conj result (str/trim bar-content)) result)))
                       result))]
    plain-bars))

(defn compute-pickup-offset-s
  "Pickup (anacrusis) duration in seconds for a section body, or 0.0 if the
   body starts on a full bar. Parses note durations in the first bar (before
   the first |) and compares their sum to the second bar's sum. If the first
   bar is shorter, returns its proportional duration.
   Works for any section body (A, B, or whole tune)."
  [abc-body ms-per-bar]
  (let [;; Note+rest char followed by optional duration: digits, /digit, digits/digit
        note-re #"([A-Ga-gz])(?:(\d+))?(?:/(\d+))?"
        sum-durations (fn [s]
                        (if (empty? s) 0.0
                            (reduce + 0.0
                                    (for [[_ _ num-str den-str] (re-seq note-re s)]
                                      (let [num (if num-str (be/parse-int num-str) 1)
                                            den (if den-str (be/parse-int den-str) 1)]
                                        (/ num den))))))
        bars (split-bar-contents abc-body)
        fc (sum-durations (first bars))
        sc (sum-durations (second bars))]
    (if (and (pos? fc) (pos? sc) (< fc sc))
      (* (/ fc sc) (/ ms-per-bar 1000.0))
      0.0)))

(defn add-line-breaks
  "Insert newlines after every bars-per-line simple barlines in an ABC body.
   Skips repeat markers (|: :| ||) so only plain | barlines trigger breaks."
  [abc-body bars-per-line]
  (let [chars (seq abc-body)]
    (loop [remaining chars
           bar-count 0
           result    []]
      (if (empty? remaining)
        (apply str result)
        (let [c (first remaining)]
          (if (= c \|)
            (let [next-c  (second remaining)
                  prev-c  (peek result)
                  simple? (and (not= next-c \|)
                               (not= next-c \:)
                               (not= prev-c \:))
                  new-count (if simple? (inc bar-count) bar-count)
                  break?    (and simple?
                                 (pos? bars-per-line)
                                 (zero? (mod new-count bars-per-line)))]
              (recur (rest remaining)
                     new-count
                     (if break?
                       (conj result c \newline)
                       (conj result c))))
            (recur (rest remaining) bar-count (conj result c))))))))

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

(defn split-abc-body
  "Split an ABC body string (no headers) at the A/B section boundary.
   Returns {:a \"...\" :b \"...\"} or nil if no clear split point found."
  [body]
  (or
   ;; :|||: — compact close-repeat double-bar open-repeat
   (when-let [idx (str/index-of body ":|||:")]
     (let [a (str/trim (subs body 0 (+ idx 2)))
           b (str/trim (subs body (+ idx 3)))]
       (when (and (seq a) (seq b)) {:a a :b b})))
   ;; || |: or ||\n|: — double barline followed by open repeat
   (when-let [[a b] (or (split-body-at body "|| |:")
                        (split-body-at body "||\n|:"))]
     {:a a :b (str "|:" b)})
   ;; ||\n — double barline only
   (when-let [[a b] (split-body-at body "||\n")]
     (when (and (seq a) (seq b)) {:a a :b b}))
   ;; :| — simple close repeat; A includes :| and B is the remainder
   (when-let [idx (str/index-of body ":|")]
     (let [after (+ idx 2)
           b     (str/trim (subs body after))]
       (when (seq b)
         {:a (str/trim (subs body 0 after)) :b b})))))

(defn split-abc-parts
  "Split a full ABC string into parts A and B. Tries common section boundaries.
   Returns {:a \"full-abc-for-A\" :b \"full-abc-for-B\"} or nil."
  [abc-str]
  (let [lines        (str/split-lines abc-str)
        header-lines (vec (take-while header-line? lines))
        header       (str/join "\n" header-lines)
        body         (str/join "\n" (drop (count header-lines) lines))]
    (when-let [{:keys [a b]} (split-abc-body body)]
      {:a (str header "\n" (ensure-repeats a) "\n")
       :b (str header "\n" (ensure-repeats b) "\n")})))
