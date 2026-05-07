(ns ceol.web.backup-test
  "Tests for backup/restore round-trip semantics. Browser I/O (download,
   file picker) is not exercised — only the pure state transitions and
   schema validation."
  (:require [cljs.test :refer [deftest testing is]]
            [ceol.web.backup :as backup]
            [ceol.web.state :as state]
            [malli.core :as m]
            [cljs.reader :as reader]))

(defn- base-state []
  {:abc-edits        {1 "GABcd"}
   :custom-tunes     {1 {:id 1 :name "T" :type :reel :time-sig "4/4"
                         :key "G" :mode-name "Ionian"}}
   :tunes            {1 {:id 1 :name "T" :type :reel :time-sig "4/4"
                         :key "G" :mode-name "Ionian"}}
   :tune-order       [1]
   :sets             {"set-1" {:id "set-1" :name "S" :tune-ids [1]}}
   :learned-tune-ids #{1}})

(deftest build-backup-shape
  (let [b (backup/build-backup (base-state))]
    (is (= 1 (:ceol/version b)))
    (is (string? (:exported-at b)))
    (is (= #{:abc-edits :custom-tunes :sets :learned-tune-ids}
           (set (keys (:data b)))))
    (is (m/validate backup/Backup b))))

(deftest round-trip-via-edn
  (let [b      (backup/build-backup (base-state))
        text   (pr-str b)
        parsed (reader/read-string text)]
    (is (m/validate backup/Backup parsed))
    (is (= (:data b) (:data parsed)))))

(deftest apply-merges-edits-replaces-sets
  (let [s0 (assoc (base-state)
                  :abc-edits {1 "old" 2 "keep"}
                  :sets {"set-old" {:id "set-old" :name "old" :tune-ids []}}
                  :learned-tune-ids #{99})
        s1 (backup/apply-to-state
            s0
            {:abc-edits {1 "new"}
             :sets {"set-1" {:id "set-1" :name "S" :tune-ids [1]}}
             :learned-tune-ids #{1 2}})]
    (testing "abc-edits merge: incoming wins, untouched keys kept"
      (is (= "new"  (get-in s1 [:abc-edits 1])))
      (is (= "keep" (get-in s1 [:abc-edits 2]))))
    (testing "sets replace wholesale"
      (is (= #{"set-1"} (set (keys (:sets s1))))))
    (testing "learned-tune-ids replace wholesale"
      (is (= #{1 2} (:learned-tune-ids s1))))))

(deftest apply-skips-missing-sections
  (let [s0 (base-state)
        s1 (backup/apply-to-state s0 {:abc-edits {2 "x"}})]
    (testing "absent sections don't clobber existing state"
      (is (= (:sets s0) (:sets s1)))
      (is (= (:learned-tune-ids s0) (:learned-tune-ids s1)))
      (is (= "x" (get-in s1 [:abc-edits 2]))))))

(deftest schema-rejects-bad-data
  (is (not (m/validate backup/Backup {:ceol/version 2 :exported-at "x" :data {}})))
  (is (not (m/validate backup/Backup
                       {:ceol/version 1 :exported-at "x"
                        :data {:abc-edits {"not-an-int" "abc"}}}))))
