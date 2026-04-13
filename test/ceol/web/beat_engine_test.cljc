(ns ceol.web.beat-engine-test
  (:require [clojure.test :refer [deftest testing is]]
            [ceol.web.beat-engine :as beat]))

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
