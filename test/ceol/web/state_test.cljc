(ns ceol.web.state-test
  (:require [clojure.test :refer [deftest testing is]]
            [ceol.web.state :as state]
            [ceol.tunes :as tunes]))

(deftest catalog-tune-test
  (testing "an id present in the static catalog is a catalog tune"
    (is (state/catalog-tune? (first (keys tunes/catalog)))))

  (testing "an id outside the catalog is not"
    (is (not (state/catalog-tune? 999999)))))

(deftest prepare-tunes-test
  (testing "builds :tunes and :tune-order from the given map"
    (let [tunes {1 {:id 1 :name "Tune A" :type :polka}
                 2 {:id 2 :name "Tune B" :type :jig}}
          result (state/prepare-tunes tunes)]
      (is (= [1 2] (sort (:tune-order result))))
      (is (= "Tune A" (get-in result [:tunes 1 :name])))
      (is (= "Tune B" (get-in result [:tunes 2 :name])))))

  (testing "does not preserve entries absent from the given map"
    ;; prepare-tunes has no base catalog to fall back on — it's the caller's
    ;; job (e.g. persist/load-tunes!) to merge in whatever base is wanted
    ;; before calling this.
    (let [result (state/prepare-tunes {2 {:id 2 :name "Tune B"}})]
      (is (= [2] (:tune-order result)))
      (is (nil? (get-in result [:tunes 1]))))))

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
  (testing "handles empty tunes"
    (let [s {:tunes {} :tune-order []}]
      (is (= 1000 (state/next-tune-id s)))))

  (testing "known ids within the catalog range still seed a high id"
    ;; Below/at the catalog's own max id — could be an untouched catalog
    ;; subset, or every added tune having since been deleted. Either way,
    ;; the next low id isn't safe to assume free (a deleted catalog tune's
    ;; id is never reused).
    (let [s (test-state [{:id 1} {:id 55} {:id 10}])]
      (is (= 1000 (state/next-tune-id s)))))

  (testing "known id above the catalog max allocates one past it"
    (let [s (test-state [{:id 1} {:id 1000}])]
      (is (= 1001 (state/next-tune-id s)))))

  (testing "guards against orphaned :abc-edits, :tune-notes, :learned-tune-ids"
    ;; A deleted tune can leave data behind in these keyspaces even when
    ;; :tunes itself no longer has it — next-tune-id must still clear them,
    ;; or a re-used id inherits a stranger's ABC edit, note, or learned flag.
    (is (= 2001 (state/next-tune-id (assoc (test-state [{:id 1}])
                                           :abc-edits {2000 "X:1\n"}))))
    (is (= 2001 (state/next-tune-id (assoc (test-state [{:id 1}])
                                           :tune-notes {2000 "orphaned note"}))))
    (is (= 2001 (state/next-tune-id (assoc (test-state [{:id 1}])
                                           :learned-tune-ids #{2000}))))))
