(ns ceol.web.generative-test
  "Property-based tests using malli.generator. Sample valid musical inputs
   and assert pure functions in beat-engine and chords behave as expected
   on every sample. Catches edge cases that example-based tests miss."
  (:require [clojure.test :refer [deftest is testing]]
            [malli.core :as m]
            [malli.generator :as mg]
            [ceol.beat-engine :as beat]
            [ceol.web.chords :as chords]))

;; Tighter than ceol.tunes/Tune — restricts musical fields to values that
;; the algorithms actually support, so generated samples are always
;; musically sensible. Production schema stays open for custom user tunes.
(def MusicalTune
  [:map
   [:id :int]
   [:name :string]
   [:type [:enum :polka :jig :reel :hornpipe :slip-jig :slide :other]]
   [:time-sig [:enum "2/4" "3/4" "4/4" "6/8" "9/8" "12/8"]]
   [:key [:enum "C" "D" "E" "F" "G" "A" "B"]]
   [:mode-name [:enum "Ionian" "Dorian" "Aeolian"]]])

(def TempoOffset
  [:int {:min -40 :max 40}])

(def sample-count 100)

(deftest beats-for-tune-property
  (testing "beats-for-tune returns valid timing for every musical tune + tempo offset"
    (let [tunes   (mg/sample MusicalTune {:size 30 :seed 42})
          offsets (mg/sample TempoOffset {:size 30 :seed 7})]
      (doseq [tune tunes
              offset offsets]
        (let [r (beat/beats-for-tune tune offset)]
          (is (pos? (:bpm r)) (str "bpm should be positive for " tune " " offset))
          (is (pos? (:beats-per-bar r)))
          (is (pos? (:ms-per-beat r)))
          (is (= (:ms-per-bar r) (* (:ms-per-beat r) (:beats-per-bar r))))
          (is (>= (:bpm r) 40) "bpm clamped to >= 40"))))))

(deftest build-chord-set-property
  (testing "build-chord-set returns three named chords for every key + mode"
    (let [tunes (mg/sample MusicalTune {:size sample-count :seed 1})]
      (doseq [{:keys [key mode-name]} tunes]
        (let [chords-out (chords/build-chord-set key mode-name)]
          (is (= 3 (count chords-out))
              (str "expected 3 chords for " key " " mode-name))
          (is (every? #(string? (:name %)) chords-out))
          (is (every? #(set? (:tones %)) chords-out))
          (is (every? #(every? (fn [t] (and (>= t 0) (< t 12))) (:tones %))
                      chords-out)))))))

(deftest transpose-note-property
  (testing "transposing by 12 semitones returns the same pitch class"
    (let [notes (keys chords/note-to-semitone)]
      (doseq [note notes]
        (let [round-trip (chords/transpose-note note 12)]
          (is (= (get chords/note-to-semitone note)
                 (get chords/note-to-semitone round-trip))
              (str note " -> " round-trip " same pitch class")))))))

(deftest abc-note->pitch-class-property
  (testing "every generated ABC note token parses to a valid pitch class"
    (let [tokens ["A" "B" "c" "d" "^F" "_E" "=G" "A," "c'" "^^c"]]
      (doseq [tok tokens]
        (let [pc (chords/abc-note->pitch-class tok)]
          (is (and (>= pc 0) (< pc 12)) (str tok " -> " pc)))))))
