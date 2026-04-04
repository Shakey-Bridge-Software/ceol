(ns ceol.web.state-test
  (:require [clojure.test :refer [deftest testing is]]
            [ceol.web.state :as state]))

(deftest merge-tunes-test
  (let [base [{:id 1 :name "Tune A" :type :polka}
              {:id 2 :name "Tune B" :type :jig}]]

    (testing "no custom tunes returns base"
      (is (= base (state/merge-tunes base {}))))

    (testing "custom overrides existing tune"
      (let [custom {1 {:id 1 :name "Tune A Edited" :type :reel}}
            result (state/merge-tunes base custom)]
        (is (= "Tune A Edited" (:name (first result))))
        (is (= :reel (:type (first result))))
        (is (= 2 (count result)))))

    (testing "custom adds new tune"
      (let [custom {100 {:id 100 :name "New Tune" :type :polka}}
            result (state/merge-tunes base custom)]
        (is (= 3 (count result)))
        (is (= "New Tune" (:name (last result))))))

    (testing "custom overrides and adds"
      (let [custom {1 {:id 1 :name "Edited"} 100 {:id 100 :name "New"}}
            result (state/merge-tunes base custom)]
        (is (= 3 (count result)))
        (is (= "Edited" (:name (first result))))
        (is (= "New" (:name (last result))))))))

(deftest filtered-tunes-test
  (let [s {:tunes [{:id 1 :type :polka} {:id 2 :type :jig} {:id 3 :type :polka}]
           :filter :all}]

    (testing "all filter returns everything"
      (is (= 3 (count (state/filtered-tunes s)))))

    (testing "type filter"
      (is (= 2 (count (state/filtered-tunes (assoc s :filter :polka)))))
      (is (= 1 (count (state/filtered-tunes (assoc s :filter :jig))))))

    (testing "no matches returns empty"
      (is (= 0 (count (state/filtered-tunes (assoc s :filter :reel))))))))

(deftest tune-by-id-test
  (let [s {:tunes [{:id 1 :name "A"} {:id 2 :name "B"}]}]
    (testing "finds existing tune"
      (is (= "A" (:name (state/tune-by-id s 1)))))
    (testing "returns nil for missing"
      (is (nil? (state/tune-by-id s 99))))))

(deftest next-tune-id-test
  (testing "generates ID beyond max"
    (let [s {:tunes [{:id 1} {:id 55} {:id 10}]}]
      (is (= 56 (state/next-tune-id s)))))

  (testing "handles empty tunes"
    (let [s {:tunes []}]
      (is (= 1000 (state/next-tune-id s))))))

(deftest custom-tune-test
  (testing "catalog tune is not custom"
    (is (not (state/custom-tune? 1))))

  (testing "high ID is custom"
    (is (state/custom-tune? 9999))))
