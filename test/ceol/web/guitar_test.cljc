(ns ceol.web.guitar-test
  (:require [clojure.test :refer [deftest testing is]]
            [ceol.web.guitar :as guitar]))

;; --- helpers ---

(defn- approx=
  "True if a and b differ by less than epsilon."
  [a b epsilon]
  (< (js/Math.abs (- a b)) epsilon))

;; --- build-events: pickup offset ---

(def ^:private attack-comp 0.020)

(deftest build-events-no-pickup-test
  (testing "without pickup offset, first event is at bar-0 downbeat"
    (let [events (guitar/build-events ["G" "C"] :reel 2000 10.0)
          first-t (:t (first events))]
      ;; bar-s = 2000/1000 = 2.0; first event bar 0 time 0.0 bass
      ;; expected: 10.0 + 0.0*2.0 + 0.0*2.0 - attack-comp = 9.98
      (is (pos? (count events))
          "should produce events for valid chords")
      (is (< first-t 10.0)
          "first event should be before start-at (attack compensation)")
      (is (> first-t 9.0)
          "first event should be close to start-at, not far in the past"))))

(deftest build-events-pickup-offset-test
  (testing "with pickup offset, first event is shifted by the offset"
    (let [events-no-offset (guitar/build-events ["G" "C"] :reel 2000 10.0)
          events-offset    (guitar/build-events ["G" "C"] :reel 2000 10.0 0.5)
          first-no-offset  (:t (first events-no-offset))
          first-offset     (:t (first events-offset))]
      (is (= (count events-no-offset) (count events-offset))
          "same number of events (chord list unchanged)")
      (is (number? first-offset))
      (is (pos? first-offset))
      (is (approx= (+ first-no-offset 0.5) first-offset 0.001)
          "first event time shifted by exactly the pickup offset"))))

(deftest build-events-no-events-during-pickup-test
  (testing "no events scheduled during the pickup interval"
    (let [start-at   10.0
          offset     0.25
          events     (guitar/build-events ["G" "C" "D"] :reel 2000 start-at offset)
          ;; The earliest any event can be: start-at + offset - attack-comp
          earliest   (- (+ start-at offset) attack-comp)]
      (is (pos? (count events)))
      (is (every? (fn [e] (>= (:t e) earliest)) events)
          (str "all events must be at or after start-at + offset - attack-comp ("
               earliest ")")))))

(deftest build-events-zero-offset-unchanged-test
  (testing "zero pickup-offset-s produces same events as no-offset call"
    (let [no-offset (guitar/build-events ["G" "C"] :reel 2000 10.0)
          zero-off  (guitar/build-events ["G" "C"] :reel 2000 10.0 0.0)]
      (is (= (map :t no-offset) (map :t zero-off))
          "zero offset should be identical to no offset argument"))))

(deftest build-events-nil-offset-unchanged-test
  (testing "nil pickup-offset-s (missing arg) produces same events as no-offset call"
    (let [no-offset (guitar/build-events ["G" "C"] :reel 2000 10.0)
          nil-off   (guitar/build-events ["G" "C"] :reel 2000 10.0 nil)]
      (is (= (map :t no-offset) (map :t nil-off))
          "nil offset should be identical to no offset argument"))))

