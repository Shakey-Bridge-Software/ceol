(ns ceol.web.chords
  (:require [clojure.string :as str]))

;; --- Music theory ---

(def note-to-semitone
  {"C" 0 "D" 2 "E" 4 "F" 5 "G" 7 "A" 9 "B" 11})

(def semitone-to-note
  {0 "C" 1 "C#" 2 "D" 3 "Eb" 4 "E" 5 "F" 6 "F#" 7 "G" 8 "Ab" 9 "A" 10 "Bb" 11 "B"})

(defn transpose-note
  "Transpose a note name by semitones. Returns note name."
  [note semitones]
  (let [base (get note-to-semitone note 0)]
    (get semitone-to-note (mod (+ base semitones) 12))))

;; Scale degree intervals from root (in semitones)
;; Major (Ionian): 0 2 4 5 7 9 11
;; Dorian:         0 2 3 5 7 9 10
;; Aeolian:        0 2 3 5 7 8 10

(def mode-scale
  {"Ionian"  [0 2 4 5 7 9 11]
   "Dorian"  [0 2 3 5 7 9 10]
   "Aeolian" [0 2 3 5 7 8 10]})

;; Chord quality for each scale degree by mode
;; Each entry: [degree-index chord-quality]
;; We only include the commonly used chords in Irish trad
(def mode-chord-degrees
  {"Ionian"  [[0 :major] [3 :major] [4 :major]]     ;; I IV V
   "Dorian"  [[0 :minor] [3 :major] [6 :major]]     ;; i IV VII
   "Aeolian" [[0 :minor] [6 :major] [5 :major]]})   ;; i VII VI

(defn chord-tones
  "Return the set of pitch classes (0-11) for a chord given root semitone and quality."
  [root-semi quality]
  (let [intervals (case quality
                    :major [0 4 7]
                    :minor [0 3 7])]
    (set (map #(mod (+ root-semi %) 12) intervals))))

(defn build-chord-set
  "Build candidate chords for a key and mode.
   Returns [{:name \"G\" :tones #{7 11 2}} ...]"
  [key-name mode-name]
  (let [root-semi (get note-to-semitone key-name 0)
        scale (get mode-scale mode-name (get mode-scale "Ionian"))
        degrees (get mode-chord-degrees mode-name (get mode-chord-degrees "Ionian"))]
    (mapv (fn [[deg-idx quality]]
            (let [chord-root-semi (mod (+ root-semi (nth scale deg-idx)) 12)
                  chord-root-name (get semitone-to-note chord-root-semi)
                  suffix (if (= quality :minor) "m" "")]
              {:name (str chord-root-name suffix)
               :tones (chord-tones chord-root-semi quality)
               :root-semi chord-root-semi}))
          degrees)))

;; --- ABC bar parsing ---

(def abc-note-re #"[_^=]*[A-Ga-g][,']*[0-9/]*")

(defn abc-note->pitch-class
  "Extract pitch class (0-11) from an ABC note token like 'G', '^c', '_B' etc."
  [token]
  (let [s (str/replace token #"[_^=]" "")
        c (first s)]
    (when c
      (let [note-name (str/upper-case (str c))
            base (get note-to-semitone note-name)
            sharps (count (re-seq #"\^" token))
            flats (count (re-seq #"_" token))]
        (when base
          (mod (+ base sharps (- flats)) 12))))))

(defn split-bars
  "Split ABC body into bars (strings between | delimiters).
   Handles |: :| || etc."
  [abc-body]
  (->> (str/split abc-body #"\|+:?:?\|?")
       (map str/trim)
       (remove str/blank?)))

(defn bar-pitch-classes
  "Extract pitch classes from a bar string, with simple beat weighting.
   Returns a frequency map of {pitch-class weight}."
  [bar-str]
  (let [notes (re-seq abc-note-re bar-str)]
    (reduce
     (fn [acc [i note]]
       (when-let [pc (abc-note->pitch-class note)]
         (let [weight (if (zero? i) 2 1)]
           (update acc pc (fnil + 0) weight))))
     {}
     (map-indexed vector notes))))

(defn score-chord
  "Score a chord against a frequency map of pitch classes.
   Higher = better match."
  [chord pitch-freqs]
  (reduce-kv
   (fn [score pc weight]
     (if (contains? (:tones chord) pc)
       (+ score weight)
       score))
   0
   pitch-freqs))

(defn best-chord
  "Pick the best chord for a bar from candidates.
   prev-chord biases toward continuity on ties."
  [candidates pitch-freqs prev-chord-name]
  (let [scored (map (fn [c] (assoc c :score (score-chord c pitch-freqs))) candidates)
        max-score (apply max 0 (map :score scored))
        top (filter #(= max-score (:score %)) scored)]
    (or
     ;; Prefer previous chord on tie (continuity)
     (when prev-chord-name
       (first (filter #(= prev-chord-name (:name %)) top)))
     ;; Otherwise first (tonic bias — tonic is first in the list)
     (first top))))

;; --- Main API ---

(defn suggest-chords
  "Given an ABC body string (just the notes, no headers), a key name, and mode name,
   return a vector of chord names, one per bar.

   Example: (suggest-chords \"GGAB|d2Bd|...\" \"G\" \"Ionian\")
            => [\"G\" \"D\" ...]"
  [abc-body key-name mode-name]
  (let [candidates (build-chord-set key-name mode-name)
        bars (split-bars abc-body)]
    (loop [remaining bars
           prev nil
           result []]
      (if (empty? remaining)
        result
        (let [bar (first remaining)
              freqs (bar-pitch-classes bar)
              chord (best-chord candidates freqs prev)]
          (recur (rest remaining)
                 (:name chord)
                 (conj result (:name chord))))))))

(defn inject-chords
  "Inject chord symbols into ABC body string.
   Walks the string, finds bar boundaries (|), and inserts \"ChordName\"
   before the first note after each boundary. Preserves all ABC syntax."
  [abc-body chords]
  (let [;; Find positions of bar starts: beginning of string + after each | sequence
        chars (vec abc-body)
        len (count chars)]
    (loop [i 0
           chord-idx 0
           at-bar-start? true
           result []]
      (if (>= i len)
        (apply str result)
        (let [c (nth chars i)]
          (cond
            ;; Bar delimiter characters — mark next content as bar start
            (= c \|)
            (recur (inc i) chord-idx true (conj result c))

            ;; Colon as part of repeat — pass through
            (= c \:)
            (recur (inc i) chord-idx at-bar-start? (conj result c))

            ;; Whitespace — pass through, stay in same bar-start state
            (or (= c \space) (= c \newline) (= c \return) (= c \tab))
            (recur (inc i) chord-idx at-bar-start? (conj result c))

            ;; A note character at bar start — inject chord
            (and at-bar-start?
                 (or (re-matches #"[A-Ga-g]" (str c))
                     (contains? #{\^ \_ \= \(} c)))
            (let [chord-name (get chords chord-idx)]
              (if chord-name
                (recur i (inc chord-idx) false
                       (into result (vec (str "\"" chord-name "\""))))
                (recur i (inc chord-idx) false result)))

            ;; Any other character at bar start (digits, etc.) — skip, stay at bar start
            at-bar-start?
            (recur (inc i) chord-idx true (conj result c))

            ;; Normal content — pass through
            :else
            (recur (inc i) chord-idx false (conj result c))))))))
