(ns ceol.web.state-test
  (:require [clojure.test :refer [deftest testing is]]
            [ceol.web.state :as state]))

(deftest merge-tunes-test
  (let [base [{:id 1 :name "Tune A" :type :polka}
              {:id 2 :name "Tune B" :type :jig}]]

    (testing "no custom tunes returns all base tunes in order"
      (let [result (state/merge-tunes base {})]
        (is (= [1 2] (:tune-order result)))
        (is (= "Tune A" (get-in result [:tunes 1 :name])))
        (is (= "Tune B" (get-in result [:tunes 2 :name])))))

    (testing "custom overrides existing tune"
      (let [custom {1 {:id 1 :name "Tune A Edited" :type :reel}}
            result (state/merge-tunes base custom)]
        (is (= "Tune A Edited" (get-in result [:tunes 1 :name])))
        (is (= :reel (get-in result [:tunes 1 :type])))
        (is (= 2 (count (:tune-order result))))))

    (testing "custom adds new tune"
      (let [custom {100 {:id 100 :name "New Tune" :type :polka}}
            result (state/merge-tunes base custom)]
        (is (= 3 (count (:tune-order result))))
        (is (= "New Tune" (get-in result [:tunes 100 :name])))))

    (testing "custom overrides and adds"
      (let [custom {1 {:id 1 :name "Edited"} 100 {:id 100 :name "New"}}
            result (state/merge-tunes base custom)]
        (is (= 3 (count (:tune-order result))))
        (is (= "Edited" (get-in result [:tunes 1 :name])))
        (is (= "New" (get-in result [:tunes 100 :name])))))))

(defn- test-state [tunes]
  "Build a minimal state map from a seq of tune maps."
  (let [tunes-map (into {} (map (juxt :id identity)) tunes)]
    {:tunes      tunes-map
     :tune-order (mapv :id tunes)
     :filter     :all}))

(deftest filtered-tunes-test
  (let [tunes [{:id 1 :type :polka} {:id 2 :type :jig} {:id 3 :type :polka}]
        s     (test-state tunes)]

    (testing "all filter returns everything"
      (is (= 3 (count (state/filtered-tunes s)))))

    (testing "type filter"
      (is (= 2 (count (state/filtered-tunes (assoc s :filter :polka)))))
      (is (= 1 (count (state/filtered-tunes (assoc s :filter :jig))))))

    (testing "no matches returns empty"
      (is (= 0 (count (state/filtered-tunes (assoc s :filter :reel))))))

    (testing "preserves catalog order"
      (let [result (state/filtered-tunes (assoc s :filter :polka))]
        (is (= [1 3] (mapv :id result)))))))

(deftest tune-by-id-test
  (let [s (test-state [{:id 1 :name "A"} {:id 2 :name "B"}])]
    (testing "finds existing tune"
      (is (= "A" (:name (state/tune-by-id s 1)))))
    (testing "returns nil for missing"
      (is (nil? (state/tune-by-id s 99))))))

(deftest next-tune-id-test
  (testing "generates ID beyond max"
    (let [s (test-state [{:id 1} {:id 55} {:id 10}])]
      (is (= 56 (state/next-tune-id s)))))

  (testing "handles empty tunes"
    (let [s {:tunes {} :tune-order []}]
      (is (= 1000 (state/next-tune-id s))))))

(deftest custom-tune-test
  (testing "catalog tune is not custom"
    (is (not (state/custom-tune? 1))))

  (testing "high ID is custom"
    (is (state/custom-tune? 9999))))
