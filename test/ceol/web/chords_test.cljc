(ns ceol.web.chords-test
  (:require [clojure.test :refer [deftest testing is are]]
            [ceol.web.chords :as chords]))

;; --- Music theory ---

(deftest note-to-semitone-test
  (testing "standard note mapping"
    (is (= 0 (get chords/note-to-semitone "C")))
    (is (= 7 (get chords/note-to-semitone "G")))
    (is (= 2 (get chords/note-to-semitone "D")))
    (is (= 9 (get chords/note-to-semitone "A")))))

(deftest transpose-note-test
  (testing "basic transposition"
    (is (= "D" (chords/transpose-note "C" 2)))
    (is (= "G" (chords/transpose-note "C" 7)))
    (is (= "C" (chords/transpose-note "G" 5)))
    (is (= "C" (chords/transpose-note "C" 12)))))

(deftest build-chord-set-test
  (testing "G Ionian gives G, C, D"
    (let [chords (chords/build-chord-set "G" "Ionian")
          names (set (map :name chords))]
      (is (= #{"G" "C" "D"} names))))

  (testing "D Ionian gives D, G, A"
    (let [chords (chords/build-chord-set "D" "Ionian")
          names (set (map :name chords))]
      (is (= #{"D" "G" "A"} names))))

  (testing "A Dorian gives Am, D, G"
    (let [chords (chords/build-chord-set "A" "Dorian")
          names (set (map :name chords))]
      (is (= #{"Am" "D" "G"} names))))

  (testing "E Aeolian gives Em, D, C"
    (let [chords (chords/build-chord-set "E" "Aeolian")
          names (set (map :name chords))]
      (is (= #{"Em" "D" "C"} names)))))

;; --- ABC parsing ---

(deftest abc-note->pitch-class-test
  (testing "basic notes"
    (is (= 7 (chords/abc-note->pitch-class "G")))
    (is (= 2 (chords/abc-note->pitch-class "D")))
    (is (= 7 (chords/abc-note->pitch-class "g"))))

  (testing "accidentals"
    (is (= 8 (chords/abc-note->pitch-class "^G")))   ;; G#
    (is (= 6 (chords/abc-note->pitch-class "_G")))   ;; Gb
    (is (= 7 (chords/abc-note->pitch-class "=G")))) ;; G natural

  (testing "nil for non-notes"
    (is (nil? (chords/abc-note->pitch-class "|")))
    (is (nil? (chords/abc-note->pitch-class ":")))))

(deftest split-bars-test
  (testing "simple bars"
    (is (= ["AB" "CD" "EF"] (chords/split-bars "AB|CD|EF"))))

  (testing "repeat markers — trailing colon may be included"
    (let [bars (chords/split-bars "|:AB|CD:|")]
      (is (= 2 (count bars)))
      (is (= "AB" (first bars)))))

  (testing "double barline"
    (is (= ["AB" "CD"] (chords/split-bars "AB||CD"))))

  (testing "section boundary"
    (let [bars (chords/split-bars "AB|CD:|||:EF|GH")]
      (is (>= (count bars) 4)))))

(deftest bar-pitch-classes-test
  (testing "weights first note 2x"
    (let [freqs (chords/bar-pitch-classes "GABd")]
      (is (= 2 (get freqs 7)))   ;; G on beat 1 = weight 2
      (is (= 1 (get freqs 9)))   ;; A = weight 1
      (is (= 1 (get freqs 11))))) ;; B = weight 1

  (testing "non-note characters don't corrupt accumulator"
    (let [freqs (chords/bar-pitch-classes "G|B")]
      (is (map? freqs))
      (is (= 2 (get freqs 7)))  ;; G still weight 2
      (is (= 1 (get freqs 11))))) ;; B still weight 1

  (testing "empty bar returns empty map"
    (is (empty? (chords/bar-pitch-classes "")))))

;; --- End-to-end ---

(deftest suggest-chords-test
  (testing "G Ionian tune gets G/C/D chords"
    (let [abc "GGAB|d2Bd|GGAB|d2Bd"
          result (chords/suggest-chords abc "G" "Ionian")]
      (is (= 4 (count result)))
      (is (every? #(contains? #{"G" "C" "D"} %) result))))

  (testing "A Dorian tune gets Am/D/G chords"
    (let [abc "AABA|d2Ad|AABA|d2Ad"
          result (chords/suggest-chords abc "A" "Dorian")]
      (is (= 4 (count result)))
      (is (every? #(contains? #{"Am" "D" "G"} %) result)))))

(deftest inject-chords-test
  (testing "injects chord before first note of each bar"
    (let [abc "GAB|dBd|GAB|dBd"
          chords ["G" "D" "G" "D"]
          result (chords/inject-chords abc chords)]
      (is (clojure.string/includes? result "\"G\""))
      (is (clojure.string/includes? result "\"D\""))
      ;; Original notes preserved
      (is (clojure.string/includes? result "GAB"))
      (is (clojure.string/includes? result "dBd"))))

  (testing "preserves repeat markers"
    (let [abc "|:GAB|dBd:|"
          chords ["G" "D"]
          result (chords/inject-chords abc chords)]
      (is (clojure.string/includes? result "|:"))
      (is (clojure.string/includes? result ":|")))))
