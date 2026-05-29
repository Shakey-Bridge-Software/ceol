(ns ceol.web.session-summary-test
  (:require [clojure.test :refer [deftest testing is]]
            [ceol.web.handlers.session-summary :as ss]))

(deftest queue-tune-count-test
  (testing "counts 1 per tune item and (count tune-ids) per set item"
    (is (= 4 (ss/queue-tune-count
              [{:type :tune :tune-id 1}
               {:type :set :tune-ids [3 7]}
               {:type :tune :tune-id 9}]))))
  (testing "empty queue is zero"
    (is (= 0 (ss/queue-tune-count []))))
  (testing "a set with one tune counts as one"
    (is (= 1 (ss/queue-tune-count [{:type :set :tune-ids [5]}])))))

(deftest result-test
  (testing "bundles tune-count + clamped duration"
    (is (= {:tune-count 2 :duration-ms 90000}
           (ss/result [{:type :tune :tune-id 1} {:type :tune :tune-id 2}] 90000))))
  (testing "negative elapsed clamps to zero"
    (is (= 0 (:duration-ms (ss/result [] -50))))))

(deftest format-duration-test
  (testing "under a minute"
    (is (= "less than a minute" (ss/format-duration 0)))
    (is (= "less than a minute" (ss/format-duration 59999))))
  (testing "singular minute"
    (is (= "1 minute" (ss/format-duration 60000)))
    (is (= "1 minute" (ss/format-duration 119000))))
  (testing "plural minutes"
    (is (= "12 minutes" (ss/format-duration (* 12 60000))))))

(deftest summary-line-test
  (testing "plural tunes + minutes"
    (is (= "4 tunes · 12 minutes"
           (ss/summary-line {:tune-count 4 :duration-ms (* 12 60000)}))))
  (testing "singular tune"
    (is (= "1 tune · 1 minute"
           (ss/summary-line {:tune-count 1 :duration-ms 90000}))))
  (testing "zero tunes still reads plural"
    (is (= "0 tunes · less than a minute"
           (ss/summary-line {:tune-count 0 :duration-ms 0})))))
