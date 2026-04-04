(ns ceol.web.sets-test
  (:require [clojure.test :refer [deftest testing is]]
            [ceol.web.state :as state]))

(deftest next-set-id-test
  (testing "first set gets set-1"
    (is (= "set-1" (state/next-set-id {:sets {}}))))

  (testing "increments from existing"
    (is (= "set-3" (state/next-set-id {:sets {"set-1" {} "set-2" {}}}))))

  (testing "handles gaps"
    (is (= "set-6" (state/next-set-id {:sets {"set-2" {} "set-5" {}}})))))

(deftest search-tunes-test
  (let [s {:tunes [{:id 1 :name "Maggie in the Woods" :type :polka}
                   {:id 2 :name "The Kerry Polka" :type :polka}
                   {:id 3 :name "The Ocean Jig" :type :jig}
                   {:id 4 :name "Crowley's Reel" :type :reel}]}]

    (testing "substring match"
      (is (= 1 (count (state/search-tunes s "maggie" 5))))
      (is (= "Maggie in the Woods" (:name (first (state/search-tunes s "maggie" 5))))))

    (testing "case insensitive"
      (is (= 1 (count (state/search-tunes s "KERRY" 5)))))

    (testing "partial match"
      (is (= 3 (count (state/search-tunes s "the" 5)))))  ;; Kerry, Ocean, Maggie in THE Woods

    (testing "max results"
      (is (= 1 (count (state/search-tunes s "the" 1)))))

    (testing "empty query returns nothing"
      (is (empty? (state/search-tunes s "" 5)))
      (is (empty? (state/search-tunes s "  " 5))))

    (testing "no match returns empty"
      (is (empty? (state/search-tunes s "xyz" 5))))))

(deftest advance-set-test
  (let [sets {"set-1" {:id "set-1" :name "Polka Set" :tune-ids [1 2 3]}}]

    (testing "advances to next tune"
      (let [result (state/advance-set sets "set-1" 0 false)]
        (is (= :play (:action result)))
        (is (= 2 (:tune-id result)))
        (is (= 1 (:index result)))))

    (testing "advances to last tune"
      (let [result (state/advance-set sets "set-1" 1 false)]
        (is (= :play (:action result)))
        (is (= 3 (:tune-id result)))
        (is (= 2 (:index result)))))

    (testing "stops at end without loop"
      (let [result (state/advance-set sets "set-1" 2 false)]
        (is (= :stop (:action result)))))

    (testing "loops back to start"
      (let [result (state/advance-set sets "set-1" 2 true)]
        (is (= :loop (:action result)))
        (is (= 1 (:tune-id result)))
        (is (= 0 (:index result)))))

    (testing "nil for unknown set"
      (is (nil? (state/advance-set sets "set-99" 0 false))))))

(deftest set-tunes-test
  (let [s {:tunes [{:id 1 :name "A"} {:id 2 :name "B"} {:id 3 :name "C"}]
           :sets {"set-1" {:tune-ids [3 1]}}}]

    (testing "resolves tune-ids to tune maps in order"
      (let [result (state/set-tunes s (get-in s [:sets "set-1"]))]
        (is (= 2 (count result)))
        (is (= "C" (:name (first result))))
        (is (= "A" (:name (second result))))))))
