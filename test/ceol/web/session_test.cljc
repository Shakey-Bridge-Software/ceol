(ns ceol.web.session-test
  (:require [clojure.test :refer [deftest testing is]]
            [ceol.web.state :as state]))

;; --- build-session-queue ---

(deftest build-session-queue-basic-test
  (testing "sets as units + standalone remainder"
    (let [learned #{1 2 3 7 8 9 10}
          sets {"s1" {:id "s1" :name "Polka Set" :tune-ids [1 2 3]}
                "s2" {:id "s2" :name "Jig Set" :tune-ids [7 8 9]}}
          queue (state/build-session-queue learned sets)
          set-items (filter #(= :set (:type %)) queue)
          tune-items (filter #(= :tune (:type %)) queue)]
      (is (= 2 (count set-items)))
      (is (= 1 (count tune-items)))
      (is (= 10 (:tune-id (first tune-items)))))))

(deftest build-session-queue-no-sets-test
  (testing "no sets — all standalone"
    (let [queue (state/build-session-queue #{1 2 3} {})]
      (is (= 3 (count queue)))
      (is (every? #(= :tune (:type %)) queue)))))

(deftest build-session-queue-no-learned-test
  (testing "no learned tunes — empty queue"
    (let [queue (state/build-session-queue #{} {"s1" {:tune-ids [1 2]}})]
      (is (empty? queue)))))

(deftest build-session-queue-all-in-one-set-test
  (testing "all tunes in one set, all learned"
    (let [queue (state/build-session-queue #{1 2 3}
                                           {"s1" {:id "s1" :name "Set" :tune-ids [1 2 3]}})]
      (is (= 1 (count queue)))
      (is (= :set (:type (first queue)))))))

(deftest build-session-queue-multiple-complete-sets-test
  (testing "tune in multiple complete sets — both included"
    (let [;; Tune 2 is in both sets
          learned #{1 2 3 4}
          sets {"s1" {:id "s1" :name "A" :tune-ids [1 2]}
                "s2" {:id "s2" :name "B" :tune-ids [2 3]}}
          queue (state/build-session-queue learned sets)
          set-items (filter #(= :set (:type %)) queue)
          tune-items (filter #(= :tune (:type %)) queue)]
      (is (= 2 (count set-items)))
      ;; Tune 4 is standalone (not in any complete set)
      (is (= 1 (count tune-items)))
      (is (= 4 (:tune-id (first tune-items)))))))

(deftest build-session-queue-incomplete-set-test
  (testing "tune in incomplete set only — standalone"
    (let [learned #{1 2}
          sets {"s1" {:id "s1" :name "Set" :tune-ids [1 2 3]}}  ;; 3 not learned
          queue (state/build-session-queue learned sets)]
      (is (= 2 (count queue)))
      (is (every? #(= :tune (:type %)) queue)))))

(deftest build-session-queue-complete-and-incomplete-test
  (testing "tune in complete + incomplete set — only in complete, not standalone"
    (let [learned #{1 2 3}
          sets {"s1" {:id "s1" :name "Complete" :tune-ids [1 2]}
                "s2" {:id "s2" :name "Incomplete" :tune-ids [2 3 4]}}  ;; 4 not learned
          queue (state/build-session-queue learned sets)
          set-items (filter #(= :set (:type %)) queue)
          tune-items (filter #(= :tune (:type %)) queue)]
      ;; s1 is complete, s2 is not
      (is (= 1 (count set-items)))
      (is (= "Complete" (:name (first set-items))))
      ;; Tune 3 is standalone (in incomplete set only)
      ;; Tunes 1,2 are in complete set — not standalone
      (is (= 1 (count tune-items)))
      (is (= 3 (:tune-id (first tune-items)))))))

(deftest build-session-queue-empty-set-test
  (testing "set with no tunes — excluded"
    (let [queue (state/build-session-queue #{1} {"s1" {:tune-ids []}})]
      (is (= 1 (count queue)))
      (is (= :tune (:type (first queue)))))))

;; --- advance-session ---

(deftest advance-session-standalone-test
  (testing "standalone tune → next item"
    (let [queue [{:type :tune :tune-id 1} {:type :tune :tune-id 2}]
          result (state/advance-session queue 0 0 false)]
      (is (= :next-item (:action result)))
      (is (= 1 (:session-index result))))))

(deftest advance-session-within-set-test
  (testing "set item → advance within set"
    (let [queue [{:type :set :tune-ids [1 2 3]}]
          result (state/advance-session queue 0 0 false)]
      (is (= :advance-in-set (:action result)))
      (is (= 2 (:tune-id result)))
      (is (= 1 (:session-set-index result))))))

(deftest advance-session-set-last-tune-test
  (testing "set item, last tune → next item"
    (let [queue [{:type :set :tune-ids [1 2]} {:type :tune :tune-id 3}]
          result (state/advance-session queue 0 1 false)]
      (is (= :next-item (:action result)))
      (is (= 1 (:session-index result))))))

(deftest advance-session-done-test
  (testing "last item, no loop → done"
    (let [queue [{:type :tune :tune-id 1}]
          result (state/advance-session queue 0 0 false)]
      (is (= :done (:action result))))))

(deftest advance-session-reshuffle-test
  (testing "last item, loop → reshuffle"
    (let [queue [{:type :tune :tune-id 1}]
          result (state/advance-session queue 0 0 true)]
      (is (= :reshuffle (:action result))))))

(deftest advance-session-empty-queue-test
  (testing "empty queue → nil"
    (is (nil? (state/advance-session [] 0 0 false)))))

;; --- Helpers ---

(deftest learned-test
  (let [s {:learned-tune-ids #{1 3 5}}]
    (testing "learned tune"
      (is (state/learned? s 1))
      (is (state/learned? s 5)))
    (testing "not learned"
      (is (not (state/learned? s 2)))
      (is (not (state/learned? s 99))))))

(deftest count-ready-sets-test
  (let [s {:learned-tune-ids #{1 2 3 7}
           :sets {"s1" {:tune-ids [1 2 3]}
                  "s2" {:tune-ids [7 8 9]}}}]
    (testing "counts only fully learned sets"
      (is (= 1 (state/count-ready-sets s))))))

(deftest session-current-tune-id-test
  (testing "standalone tune"
    (let [s {:session-queue [{:type :tune :tune-id 5}]
             :session-index 0
             :session-set-index 0}]
      (is (= 5 (state/session-current-tune-id s)))))

  (testing "set item, first tune"
    (let [s {:session-queue [{:type :set :tune-ids [10 11 12]}]
             :session-index 0
             :session-set-index 0}]
      (is (= 10 (state/session-current-tune-id s)))))

  (testing "set item, second tune"
    (let [s {:session-queue [{:type :set :tune-ids [10 11 12]}]
             :session-index 0
             :session-set-index 1}]
      (is (= 11 (state/session-current-tune-id s)))))

  (testing "empty queue"
    (let [s {:session-queue [] :session-index 0 :session-set-index 0}]
      (is (nil? (state/session-current-tune-id s))))))
