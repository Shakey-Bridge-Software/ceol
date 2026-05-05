(ns ceol.notation
  "ABC tokenizer, timeline builder, and note-finding utilities.
   Pure functions only. parse-abc is the main entry point: takes a full ABC
   string and returns {:tokens [...] :timeline [...] :tempo {...} :time-sig {...} :key str}.
   Timeline events have :start-ms/:end-ms offsets for real-time note tracking."
  (:require [clojure.string :as str]))

;; --- ABC Parser for Irish trad subset ---
;; Tokenizes ABC body into note events with pitch, duration, and timing info.

(def ^:private note-map
  ;; ABC standard: uppercase = octave 4, lowercase = octave 5
  {\C {:note "C" :octave 4} \D {:note "D" :octave 4} \E {:note "E" :octave 4}
   \F {:note "F" :octave 4} \G {:note "G" :octave 4} \A {:note "A" :octave 4}
   \B {:note "B" :octave 4}
   \c {:note "C" :octave 5} \d {:note "D" :octave 5} \e {:note "E" :octave 5}
   \f {:note "F" :octave 5} \g {:note "G" :octave 5} \a {:note "A" :octave 5}
   \b {:note "B" :octave 5}})

(defn- note-char? [c]
  (contains? note-map c))

(defn- digit? [c]
  (and c (<= (int \0) (int c) (int \9))))

(defn- parse-duration
  "Parse duration modifier from chars starting at idx.
   Returns [beats-multiplier new-idx].
   Handles: 2, 3, /2, /, 3/2, etc."
  [chars idx]
  (let [len (count chars)]
    (if (>= idx len)
      [1 idx]
      (let [c (nth chars idx)]
        (cond
          ;; /2 or / (halving)
          (= c \/)
          (if (and (< (inc idx) len) (digit? (nth chars (inc idx))))
            (let [denom (- (int (nth chars (inc idx))) (int \0))]
              [(/ 1 denom) (+ idx 2)])
            [1/2 (inc idx)])

          ;; digit, possibly followed by /digit
          (digit? c)
          (let [num (- (int c) (int \0))]
            (if (and (< (inc idx) len) (= \/ (nth chars (inc idx))))
              (if (and (< (+ idx 2) len) (digit? (nth chars (+ idx 2))))
                (let [denom (- (int (nth chars (+ idx 2))) (int \0))]
                  [(/ num denom) (+ idx 3)])
                [(/ num 2) (+ idx 2)])
              [num (inc idx)]))

          :else [1 idx])))))

(defn- parse-octave-modifiers
  "Parse , and ' after a note to adjust octave. Returns [octave-delta new-idx]."
  [chars idx]
  (loop [delta 0 i idx]
    (if (>= i (count chars))
      [delta i]
      (case (nth chars i)
        \, (recur (dec delta) (inc i))
        \' (recur (inc delta) (inc i))
        [delta i]))))

(defn tokenize
  "Tokenize ABC body string into a sequence of tokens.
   Returns [{:type :note :pitch {:note 'D' :octave 5} :beats 1} ...]"
  [abc-body]
  (let [chars (vec abc-body)
        len (count chars)]
    ;; next-mult: when > or < is encountered, the next note gets this multiplier
    (loop [i 0
           tokens []
           accidental nil
           next-mult nil]
      (if (>= i len)
        tokens
        (let [c (nth chars i)]
          (cond
            ;; Skip whitespace
            (or (= c \space) (= c \tab) (= c \newline) (= c \return))
            (recur (inc i) tokens accidental next-mult)

            ;; Accidentals — store for next note
            (= c \^) (recur (inc i) tokens :sharp next-mult)
            (= c \_) (recur (inc i) tokens :flat next-mult)
            (= c \=) (recur (inc i) tokens :natural next-mult)

            ;; Grace notes / decorations — skip content in {} or ~
            (= c \{)
            (let [end (or (some #(when (= \} (nth chars %)) %) (range (inc i) len)) len)]
              (recur (inc end) tokens nil next-mult))

            (= c \~)
            (recur (inc i) tokens nil next-mult)

            ;; Decoration shorthand like !trill!
            (= c \!)
            (let [end (or (some #(when (= \! (nth chars %)) %) (range (inc i) len)) len)]
              (recur (inc end) tokens nil next-mult))

            ;; Notes
            (note-char? c)
            (let [base (get note-map c)
                  [oct-delta j] (parse-octave-modifiers chars (inc i))
                  [dur-mult k] (parse-duration chars j)
                  dur-mult (if next-mult (* dur-mult next-mult) dur-mult)
                  pitch (update base :octave + oct-delta)
                  pitch (if accidental (assoc pitch :accidental accidental) pitch)]
              (recur k (conj tokens {:type :note :pitch pitch :beats dur-mult}) nil nil))

            ;; Rest
            (= c \z)
            (let [[dur-mult j] (parse-duration chars (inc i))
                  dur-mult (if next-mult (* dur-mult next-mult) dur-mult)]
              (recur j (conj tokens {:type :rest :beats dur-mult}) nil nil))

            ;; Barlines
            (= c \|)
            (let [next-c (when (< (inc i) len) (nth chars (inc i)))]
              (cond
                (= next-c \:) (recur (+ i 2) (conj tokens {:type :barline :style :open-repeat}) nil nil)
                (= next-c \|) (recur (+ i 2) (conj tokens {:type :barline :style :double}) nil nil)
                (= next-c \]) (recur (+ i 2) (conj tokens {:type :barline :style :final}) nil nil)
                :else (recur (inc i) (conj tokens {:type :barline :style :single}) nil nil)))

            (and (= c \:) (< (inc i) len) (= \| (nth chars (inc i))))
            (recur (+ i 2) (conj tokens {:type :barline :style :close-repeat}) nil nil)

            ;; Tie — just skip
            (= c \-)
            (recur (inc i) tokens nil next-mult)

            ;; Dotted rhythm > : current note gets 1.5x, NEXT note gets 0.5x
            (= c \>)
            (let [tokens' (if (seq tokens)
                            (let [last-tok (peek tokens)
                                  rest-toks (pop tokens)]
                              (if (= :note (:type last-tok))
                                (conj rest-toks (update last-tok :beats * 3/2))
                                tokens))
                            tokens)]
              (recur (inc i) tokens' nil 1/2))

            ;; < (reverse dot): current note gets 0.5x, NEXT note gets 1.5x
            (= c \<)
            (let [tokens' (if (seq tokens)
                            (let [last-tok (peek tokens)
                                  rest-toks (pop tokens)]
                              (if (= :note (:type last-tok))
                                (conj rest-toks (update last-tok :beats * 1/2))
                                tokens))
                            tokens)]
              (recur (inc i) tokens' nil 3/2))

            ;; Numbers that appear between notes (like repeat markers [1 [2)
            (= c \[)
            (let [end (or (some #(when (= \] (nth chars %)) %) (range (inc i) len))
                          (inc i))]
              (recur (inc end) tokens nil next-mult))

            ;; Skip anything else (quotes, digits not after notes, etc.)
            :else
            (recur (inc i) tokens nil next-mult)))))))

(defn- extract-header-field
  "Extract value of a header field like Q:, M:, K: from ABC string."
  [abc-str field]
  (when-let [line (first (filter #(str/starts-with? % (str field ":"))
                                 (str/split-lines abc-str)))]
    (str/trim (subs line (inc (count field))))))

(defn parse-tempo
  "Parse Q: field to get BPM and beat unit.
   Returns {:bpm 120 :beat-num 1 :beat-den 4} or nil."
  [abc-str]
  (when-let [q (extract-header-field abc-str "Q")]
    (when-let [[_ num den bpm] (re-find #"(\d+)/(\d+)=(\d+)" q)]
      {:bpm (#?(:clj parse-long :cljs js/parseInt) bpm)
       :beat-num (#?(:clj parse-long :cljs js/parseInt) num)
       :beat-den (#?(:clj parse-long :cljs js/parseInt) den)})))

(defn parse-time-sig
  "Parse M: field. Returns {:num 6 :den 8} or nil."
  [abc-str]
  (when-let [m (extract-header-field abc-str "M")]
    (when-let [[_ num den] (re-find #"(\d+)/(\d+)" m)]
      {:num (#?(:clj parse-long :cljs js/parseInt) num) :den (#?(:clj parse-long :cljs js/parseInt) den)})))

(defn parse-key
  "Parse K: field. Returns key string like 'G' or 'Ador'."
  [abc-str]
  (extract-header-field abc-str "K"))

(defn extract-body
  "Extract the body (non-header lines) from an ABC string."
  [abc-str]
  (let [lines (str/split-lines abc-str)
        body-lines (drop-while #(or (re-matches #"^[A-Z]:.*" %)
                                    (str/starts-with? % "%%")
                                    (str/blank? %))
                               lines)]
    (str/join " " body-lines)))

(defn build-timeline
  "Given tokens and tempo info, compute millisecond offsets for each note.
   Returns [{:pitch {...} :start-ms 0 :end-ms 428 :beats 1} ...]"
  [tokens tempo]
  (let [{:keys [bpm beat-num beat-den]} (or tempo {:bpm 120 :beat-num 1 :beat-den 4})
        ;; Q:1/4=70 means 1 quarter note = 857ms. Default unit (1/8) = 428ms.
        ;; Q:3/8=70 means 3 eighth notes = 857ms. Default unit (1/8) = 285ms.
        ;; Formula: ms_per_unit = ms_per_beat * beat_den / (8 * beat_num)
        ;; where beat_unit = beat_num/beat_den and default L = 1/8
        ms-per-beat (/ 60000.0 bpm)
        ms-per-unit (/ (* ms-per-beat beat-den) (* 8 beat-num))]
    (loop [i 0
           ms 0.0
           timeline []]
      (if (>= i (count tokens))
        timeline
        (let [tok (nth tokens i)]
          (case (:type tok)
            :note
            (let [dur-ms (* (:beats tok) ms-per-unit)]
              (recur (inc i)
                     (+ ms dur-ms)
                     (conj timeline {:pitch (:pitch tok)
                                     :start-ms (long ms)
                                     :end-ms (long (+ ms dur-ms))
                                     :beats (:beats tok)
                                     :type :note
                                     :index (count timeline)})))
            :rest
            (let [dur-ms (* (:beats tok) ms-per-unit)]
              (recur (inc i)
                     (+ ms dur-ms)
                     (conj timeline {:type :rest
                                     :start-ms (long ms)
                                     :end-ms (long (+ ms dur-ms))
                                     :beats (:beats tok)
                                     :index (count timeline)})))

            :barline
            (recur (inc i) ms
                   (conj timeline {:type :barline
                                   :style (:style tok)
                                   :at-ms (long ms)
                                   :index (count timeline)}))

            ;; skip unknown
            (recur (inc i) ms timeline)))))))

(defn parse-abc
  "Full pipeline: ABC string → timeline of note events with ms offsets."
  [abc-str]
  (let [body (extract-body abc-str)
        tokens (tokenize body)
        tempo (parse-tempo abc-str)]
    {:tokens tokens
     :timeline (build-timeline tokens tempo)
     :tempo tempo
     :time-sig (parse-time-sig abc-str)
     :key (parse-key abc-str)}))

(defn find-current-note
  "Given a timeline and elapsed-ms, find the index of the currently playing note.
   Returns nil when there are no notes."
  [timeline elapsed-ms]
  (let [notes (filterv #(= :note (:type %)) timeline)]
    (when (seq notes)
      (loop [i 0]
        (cond
          (>= i (count notes)) (max 0 (dec (count notes)))
          (and (<= (:start-ms (nth notes i)) elapsed-ms)
               (< elapsed-ms (:end-ms (nth notes i)))) i
          (> (:start-ms (nth notes i)) elapsed-ms) (max 0 (dec i))
          :else (recur (inc i)))))))
