(ns ceol.web.beat-engine-test
  (:require [clojure.test :refer [deftest testing is]]
            [ceol.beat-engine :as beat]))

(deftest beats-for-tune-test
  (testing "polka: 120 BPM, 2 beats/bar"
    (let [r (beat/beats-for-tune {:type :polka} 0)]
      (is (= 120 (:bpm r)))
      (is (= 2 (:beats-per-bar r)))
      (is (= 500.0 (:ms-per-beat r)))
      (is (= 1000.0 (:ms-per-bar r)))))

  (testing "jig: 100 BPM, 2 beats/bar"
    (let [r (beat/beats-for-tune {:type :jig} 0)]
      (is (= 100 (:bpm r)))
      (is (= 2 (:beats-per-bar r)))
      (is (= 600.0 (:ms-per-beat r)))))

  (testing "reel: 100 BPM, 4 beats/bar"
    (let [r (beat/beats-for-tune {:type :reel} 0)]
      (is (= 100 (:bpm r)))
      (is (= 4 (:beats-per-bar r)))
      (is (= 2400.0 (:ms-per-bar r)))))

  (testing "slip-jig: 100 BPM, 3 beats/bar"
    (let [r (beat/beats-for-tune {:type :slip-jig} 0)]
      (is (= 3 (:beats-per-bar r)))))

  (testing "hornpipe: 100 BPM, 4 beats/bar"
    (let [r (beat/beats-for-tune {:type :hornpipe} 0)]
      (is (= 100 (:bpm r)))
      (is (= 4 (:beats-per-bar r))))))

(deftest beats-for-tune-with-offset-test
  (testing "positive offset increases BPM"
    (let [r (beat/beats-for-tune {:type :polka} 10)]
      (is (= 130 (:bpm r)))
      (is (< (:ms-per-beat r) 500.0))))

  (testing "negative offset decreases BPM"
    (let [r (beat/beats-for-tune {:type :reel} -20)]
      (is (= 80 (:bpm r)))
      (is (= 750.0 (:ms-per-beat r)))))

  (testing "clamps to minimum 40 BPM"
    (let [r (beat/beats-for-tune {:type :jig} -100)]
      (is (= 40 (:bpm r)))
      (is (= 1500.0 (:ms-per-beat r)))))

  (testing "nil offset treated as 0"
    (let [r (beat/beats-for-tune {:type :polka} nil)]
      (is (= 120 (:bpm r))))))

(deftest beats-for-tune-defaults-test
  (testing "nil tune gives defaults"
    (let [r (beat/beats-for-tune nil 0)]
      (is (= 120 (:bpm r)))
      (is (= 4 (:beats-per-bar r)))))

  (testing "unknown type gives defaults"
    (let [r (beat/beats-for-tune {:type :unknown} 0)]
      (is (= 120 (:bpm r)))
      (is (= 4 (:beats-per-bar r))))))

(deftest count-in-duration-test
  (testing "polka count-in: 2 beats at 500ms = 1000ms"
    (let [r (beat/beats-for-tune {:type :polka} 0)]
      (is (= 1000.0 (:ms-per-bar r)))))

  (testing "reel count-in: 4 beats at 600ms = 2400ms"
    (let [r (beat/beats-for-tune {:type :reel} 0)]
      (is (= 2400.0 (:ms-per-bar r)))))

  (testing "count-in with tempo offset"
    (let [r (beat/beats-for-tune {:type :polka} 20)]
      ;; 140 BPM, 2 beats, ms-per-beat = 60000/140 ≈ 428.57
      (is (< 850.0 (:ms-per-bar r) 860.0)))))

;; --- Beat-grid alignment (synced metronome) ---
;; ms-per-beat 600 = 100 BPM reel; beat 0 at start-at 10.0s.

(deftest next-beat-index-test
  (testing "now exactly on beat 0 boundary returns that beat (>= now)"
    (is (= 0 (beat/next-beat-index 10.0 600.0 10.0))))
  (testing "now just past a boundary rounds up to the next beat"
    (is (= 1 (beat/next-beat-index 10.0 600.0 10.1))))
  (testing "now exactly on a later boundary returns that beat, not the next"
    (is (= 1 (beat/next-beat-index 10.0 600.0 10.6)))
    (is (= 5 (beat/next-beat-index 10.0 600.0 13.0))))   ; beat 5 = 10.0 + 5*0.6
  (testing "mid-beat rounds up"
    (is (= 3 (beat/next-beat-index 10.0 600.0 11.5))))    ; 11.8 = beat 3
  (testing "now before the start clamps to beat 0, never negative"
    (is (= 0 (beat/next-beat-index 10.0 600.0 9.0)))
    (is (= 0 (beat/next-beat-index 10.0 600.0 0.0)))))

(deftest beat-time-test
  (testing "beat n lands at start-at + n*ms-per-beat/1000"
    (is (= 10.0 (beat/beat-time 10.0 600.0 0)))
    (is (= 10.6 (beat/beat-time 10.0 600.0 1)))
    (is (= 13.0 (beat/beat-time 10.0 600.0 5)))))

(deftest beats-in-window-test
  (testing "half-open window yields on-grid clicks with bar-downbeat accents"
    (is (= [{:beat 0 :time 10.0 :accent? true}
            {:beat 1 :time 10.6 :accent? false}
            {:beat 2 :time 11.2 :accent? false}]
           (beat/beats-in-window 10.0 600.0 4 10.0 11.3))))
  (testing "start of window is inclusive, end exclusive"
    ;; 11.2 (beat 2) excluded when until = 11.2; 10.6 included when from = 10.6
    (is (= [1] (map :beat (beat/beats-in-window 10.0 600.0 4 10.6 11.2)))))
  (testing "accent every beats-per-bar beats, aligned to the melody's bars"
    (is (= [true false false false true false false false]
           (map :accent? (beat/beats-in-window 10.0 600.0 4 10.0 14.8)))))
  (testing "empty when no beat falls in the window"
    (is (empty? (beat/beats-in-window 10.0 600.0 4 10.61 11.19)))))

(deftest synced-clicks-match-melody-beats-test
  ;; AC: with a tune playing, the metronome's scheduled click times equal the
  ;; melody beat times (start-at + n*ms-per-beat) within a small tolerance —
  ;; asserted numerically over a long tune, so there is no drift on the last beat.
  (testing "every scheduled click coincides with a melody beat, no drift"
    (let [start-at 5.0
          mspb     600.0
          bpb      4
          ;; simulate the scheduler sweeping 0.25s windows across a 4-minute tune
          clicks   (mapcat (fn [i]
                             (let [from  (+ 5.0 (* i 0.25))
                                   until (+ from 0.25)]
                               (beat/beats-in-window start-at mspb bpb from until)))
                           (range 1000))
          expected (fn [n] (+ start-at (* n (/ mspb 1000.0))))]
      (is (seq clicks))
      ;; contiguous beats, no gaps or dupes across window boundaries
      (is (= (range (count clicks)) (map :beat clicks)))
      (doseq [{:keys [beat time]} clicks]
        (is (< (abs (- time (expected beat))) 1e-9)))
      ;; last click of the long sweep is still exactly on its beat
      (let [{:keys [beat time]} (last clicks)]
        (is (< (abs (- time (expected beat))) 1e-9))))))

(deftest synced-scheduler-no-double-schedule-test
  ;; The synced clock ticks on OVERLAPPING lookahead windows ([now, now+0.25)
  ;; every ~0.1s) and de-dups with a monotonic beat-index guard. This simulates
  ;; that loop purely and asserts a beat is emitted exactly once — the guard that
  ;; makes the window-boundary epsilon in next-beat-index harmless.
  (testing "overlapping windows + index guard emit each beat exactly once, in order"
    (let [start 5.0 mspb 600.0 bpb 4 lookahead 0.25
          last-beat (atom -1)
          emitted   (atom [])
          tick (fn [now]
                 (doseq [{:keys [beat accent? time]}
                         (beat/beats-in-window start mspb bpb now (+ now lookahead))]
                   (when (> beat @last-beat)
                     (swap! emitted conj {:beat beat :time time :accent? accent?})
                     (reset! last-beat beat))))]
      ;; sweep ticks across ~10s of playback
      (doseq [i (range 0 101)] (tick (+ start (* i 0.1))))
      (let [beats (map :beat @emitted)]
        (is (seq beats))
        (is (= beats (range (count beats))) "contiguous, no gaps")
        (is (= (count beats) (count (distinct beats))) "no beat emitted twice")
        (is (every? true? (map (fn [{:keys [beat time]}]
                                 (< (abs (- time (+ start (* beat (/ mspb 1000.0))))) 1e-9))
                               @emitted))
            "each emitted time equals its beat time")))))
