(ns ceol.web.actions-test
  "Tests for pure state transitions that back the action handlers in core.cljs.
   Tests here use direct state map construction rather than app-state atom
   to stay browser-free and runnable under both Babashka and shadow-cljs."
  (:require [clojure.test :refer [deftest testing is]]
            [ceol.web.state :as state]))

;; ---------------------------------------------------------------------------
;; Helpers shared across tests
;; ---------------------------------------------------------------------------

(defn- base-state
  "Minimal valid app state for testing."
  []
  (let [tunes [{:id 1 :name "Maggie in the Woods" :type :polka :time-sig "2/4" :key "G" :mode-name "Ionian"}
               {:id 2 :name "Out on the Ocean"    :type :jig   :time-sig "6/8" :key "G" :mode-name "Ionian"}
               {:id 3 :name "Crowley's Reel"      :type :reel  :time-sig "4/4" :key "D" :mode-name "Ionian"}]]
    {:tunes          (into {} (map (juxt :id identity)) tunes)
     :tune-order     (mapv :id tunes)
     :abc-data       {}
     :abc-edits      {}
     :selected-tune-id nil
     :filter         :all
     :section        nil
     :loop?          false
     :tempo-offset   0
     :count-in?      false
     :sets           {}
     :active-set-id  nil
     :learned-tune-ids #{}}))

;; ---------------------------------------------------------------------------
;; Tempo offset — clamping and arithmetic (mirrors :tempo/up/down/reset logic)
;; ---------------------------------------------------------------------------

(defn- apply-tempo-up   [s] (update s :tempo-offset #(min 40 (+ (or % 0) 5))))
(defn- apply-tempo-down [s] (update s :tempo-offset #(max -40 (- (or % 0) 5))))
(defn- apply-tempo-reset [s] (assoc s :tempo-offset 0))

(deftest tempo-offset-test
  (testing "up increases by 5"
    (is (= 5  (:tempo-offset (apply-tempo-up (base-state)))))
    (is (= 10 (:tempo-offset (apply-tempo-up (apply-tempo-up (base-state)))))))

  (testing "down decreases by 5"
    (is (= -5 (:tempo-offset (apply-tempo-down (base-state))))))

  (testing "up clamps at 40"
    (let [s (assoc (base-state) :tempo-offset 38)]
      (is (= 40 (:tempo-offset (apply-tempo-up s))))))

  (testing "down clamps at -40"
    (let [s (assoc (base-state) :tempo-offset -38)]
      (is (= -40 (:tempo-offset (apply-tempo-down s))))))

  (testing "reset returns to 0"
    (let [s (assoc (base-state) :tempo-offset 25)]
      (is (= 0 (:tempo-offset (apply-tempo-reset s)))))))

;; ---------------------------------------------------------------------------
;; Section, loop, count-in — simple toggles (mirrors :section/set etc.)
;; ---------------------------------------------------------------------------

(deftest section-test
  (testing "sets section to :a"
    (is (= :a (:section (assoc (base-state) :section :a)))))
  (testing "clears section to nil"
    (is (nil? (:section (assoc (base-state) :section nil))))))

(deftest loop-toggle-test
  (testing "toggles false → true"
    (is (true? (:loop? (update (base-state) :loop? not)))))
  (testing "toggles true → false"
    (let [s (assoc (base-state) :loop? true)]
      (is (false? (:loop? (update s :loop? not)))))))

;; ---------------------------------------------------------------------------
;; Filter and tune selection
;; ---------------------------------------------------------------------------

(deftest filtered-tunes-test
  (testing "all filter returns all tunes in order"
    (let [s (base-state)]
      (is (= [1 2 3] (mapv :id (state/filtered-tunes s))))))

  (testing "type filter returns matching tunes"
    (let [s (assoc (base-state) :filter :polka)]
      (is (= [1] (mapv :id (state/filtered-tunes s))))))

  (testing "filter with no match returns empty"
    (let [s (assoc (base-state) :filter :hornpipe)]
      (is (empty? (state/filtered-tunes s))))))

;; ---------------------------------------------------------------------------
;; Set toggle logic (mirrors :set/toggle)
;; ---------------------------------------------------------------------------

(defn- apply-set-toggle [s set-id]
  (if (= set-id (:active-set-id s))
    (assoc s :active-set-id nil)
    (let [set-data  (get (:sets s) set-id)
          first-tid (first (:tune-ids set-data))]
      (assoc s :active-set-id set-id :selected-tune-id first-tid))))

(deftest set-toggle-test
  (let [s (assoc (base-state)
                 :sets {"set-1" {:id "set-1" :name "Polkas" :tune-ids [1 3]}})]

    (testing "opening a set sets active-set-id and selects first tune"
      (let [result (apply-set-toggle s "set-1")]
        (is (= "set-1" (:active-set-id result)))
        (is (= 1 (:selected-tune-id result)))))

    (testing "toggling the active set collapses it"
      (let [s (assoc s :active-set-id "set-1")
            result (apply-set-toggle s "set-1")]
        (is (nil? (:active-set-id result)))))

    (testing "opening a different set switches active"
      (let [s (assoc s
                     :active-set-id "set-1"
                     :sets (assoc (:sets s) "set-2" {:id "set-2" :name "Reels" :tune-ids [3]}))
            result (apply-set-toggle s "set-2")]
        (is (= "set-2" (:active-set-id result)))
        (is (= 3 (:selected-tune-id result)))))))

;; ---------------------------------------------------------------------------
;; Set CRUD — add/remove tune, delete set
;; ---------------------------------------------------------------------------

(deftest set-add-remove-tune-test
  (let [s (assoc (base-state)
                 :sets {"set-1" {:id "set-1" :name "Polkas" :tune-ids [1]}})]

    (testing "add-tune appends tune-id"
      (let [result (update-in s [:sets "set-1" :tune-ids]
                              (fn [ids] (if (some #{3} ids) ids (conj (or ids []) 3))))]
        (is (= [1 3] (get-in result [:sets "set-1" :tune-ids])))))

    (testing "add-tune is idempotent"
      (let [result (update-in s [:sets "set-1" :tune-ids]
                              (fn [ids] (if (some #{1} ids) ids (conj (or ids []) 1))))]
        (is (= [1] (get-in result [:sets "set-1" :tune-ids])))))

    (testing "remove-tune removes by id"
      (let [s (assoc-in s [:sets "set-1" :tune-ids] [1 2 3])
            result (update-in s [:sets "set-1" :tune-ids]
                               (fn [ids] (vec (remove #{2} ids))))]
        (is (= [1 3] (get-in result [:sets "set-1" :tune-ids])))))

    (testing "delete set removes from sets map"
      (let [result (update s :sets dissoc "set-1")]
        (is (nil? (get-in result [:sets "set-1"])))))))

;; ---------------------------------------------------------------------------
;; Learned toggle
;; ---------------------------------------------------------------------------

(deftest learned-toggle-test
  (testing "marks tune as learned"
    (let [s      (base-state)
          result (update s :learned-tune-ids
                         (fn [ids] (if (contains? ids 1) (disj ids 1) (conj ids 1))))]
      (is (contains? (:learned-tune-ids result) 1))))

  (testing "unmarks learned tune"
    (let [s      (assoc (base-state) :learned-tune-ids #{1 2})
          result (update s :learned-tune-ids
                         (fn [ids] (if (contains? ids 1) (disj ids 1) (conj ids 1))))]
      (is (not (contains? (:learned-tune-ids result) 1)))
      (is (contains? (:learned-tune-ids result) 2)))))

;; ---------------------------------------------------------------------------
;; edited-abc-for-tune — fallback logic
;; ---------------------------------------------------------------------------

(deftest edited-abc-for-tune-test
  (let [s (assoc (base-state)
                 :abc-data  {1 "original body"}
                 :abc-edits {2 "edited body"})]

    (testing "returns edit when present"
      (is (= "edited body" (state/edited-abc-for-tune s 2))))

    (testing "falls back to abc-data when no edit"
      (is (= "original body" (state/edited-abc-for-tune s 1))))

    (testing "returns nil when neither present"
      (is (nil? (state/edited-abc-for-tune s 3))))))
