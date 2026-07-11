(ns ceol.web.set-editor-test
  (:require [clojure.test :refer [deftest testing is]]
            [ceol.web.handlers.set-editor :as se]))

;; state/Set is the closed shape draft->set must always produce. Asserted here
;; structurally (keys + types) rather than via malli, so the file runs under the
;; malli-free bb classpath as well as the cljs runner. The malli boundary itself
;; is exercised by persist/load-sets! against state/SetsByID.
(defn- conforms-to-set? [s]
  (and (= #{:id :name :tune-ids} (set (keys s)))
       (string? (:id s))
       (string? (:name s))
       (vector? (:tune-ids s))
       (every? int? (:tune-ids s))))

(deftest blank-draft-test
  (testing "blank draft is an empty draft of the right shape"
    (is (= {:name "" :tune-ids []} se/blank-draft))
    (is (vector? (:tune-ids se/blank-draft)))))

(deftest set->draft-test
  (testing "clones name + tune-ids, coerces tune-ids to a vector"
    (let [d (se/set->draft {:id "set-1" :name "Polkas" :tune-ids '(1 3 7)})]
      (is (= "Polkas" (:name d)))
      (is (= [1 3 7] (:tune-ids d)))
      (is (vector? (:tune-ids d)))))
  (testing "nil name becomes empty string"
    (is (= "" (:name (se/set->draft {:id "set-2" :tune-ids [1]}))))))

(deftest draft->set-test
  (testing "produces a value conforming to the closed Set shape"
    (is (conforms-to-set? (se/draft->set "set-1" {:name "Polkas" :tune-ids [1 3]}))))
  (testing "trims the name"
    (is (= "Friday Night" (:name (se/draft->set "set-1" {:name "  Friday Night  " :tune-ids [1]})))))
  (testing "blank name falls back to a placeholder, still conformant"
    (let [s (se/draft->set "set-2" {:name "   " :tune-ids [7]})]
      (is (= "Untitled set" (:name s)))
      (is (conforms-to-set? s))))
  (testing "preserves the given id and coerces tune-ids to a vector"
    (let [s (se/draft->set "set-9" {:name "x" :tune-ids '(2 4)})]
      (is (= "set-9" (:id s)))
      (is (= [2 4] (:tune-ids s)))
      (is (vector? (:tune-ids s))))))

(deftest can-save?-test
  (testing "needs a non-blank name and at least one tune"
    (is (not (se/can-save? se/blank-draft)))
    (is (not (se/can-save? {:name "x" :tune-ids []})))
    (is (not (se/can-save? {:name "   " :tune-ids [1]})))
    (is (se/can-save? {:name "x" :tune-ids [1]}))))

(deftest add-tune-test
  (testing "appends a new tune-id"
    (is (= [1 3 7] (se/add-tune [1 3] 7))))
  (testing "is a no-op when already present (dedup)"
    (is (= [1 3] (se/add-tune [1 3] 3))))
  (testing "works from an empty/list seed and returns a vector"
    (is (= [5] (se/add-tune [] 5)))
    (is (vector? (se/add-tune '(1) 2)))))

(deftest remove-tune-test
  (testing "drops the tune-id"
    (is (= [1 7] (se/remove-tune [1 3 7] 3))))
  (testing "missing id is a no-op"
    (is (= [1 3] (se/remove-tune [1 3] 9))))
  (testing "returns a vector"
    (is (vector? (se/remove-tune [1 3] 3)))))

(deftest reorder-test
  (testing "moves an element to a later final position"
    (is (= [3 7 1] (se/reorder [1 3 7] 0 2))))
  (testing "moves an element to an earlier final position"
    (is (= [1 7 3] (se/reorder [1 3 7] 2 1))))
  (testing "moves to the very end"
    (is (= [3 7 1] (se/reorder [1 3 7] 0 2))))
  (testing "same index is a no-op"
    (is (= [1 3 7] (se/reorder [1 3 7] 1 1))))
  (testing "out-of-bounds index is a no-op"
    (is (= [1 3 7] (se/reorder [1 3 7] 0 9)))
    (is (= [1 3 7] (se/reorder [1 3 7] -1 1))))
  (testing "preserves all elements (a permutation)"
    (is (= #{1 3 7} (set (se/reorder [1 3 7] 0 2))))
    (is (= 3 (count (se/reorder [1 3 7] 0 2))))))

(deftest drop-target-index-test
  (testing "no movement stays put"
    (is (= 0 (se/drop-target-index 0 3 0 60))))
  (testing "dragging down one+ slot moves the index down"
    (is (= 1 (se/drop-target-index 0 3 50 60)))   ; round(0.83)=1
    (is (= 2 (se/drop-target-index 0 3 90 60))))  ; round(1.5)=2
  (testing "dragging up moves the index up"
    (is (= 1 (se/drop-target-index 2 3 -90 60))))
  (testing "clamps within bounds"
    (is (= 2 (se/drop-target-index 1 3 999 60)))
    (is (= 0 (se/drop-target-index 1 3 -999 60))))
  (testing "guards against an empty list / zero slot"
    (is (= 0 (se/drop-target-index 0 0 100 60)))
    (is (= 1 (se/drop-target-index 1 3 100 0)))))
