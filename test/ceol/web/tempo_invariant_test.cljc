(ns ceol.web.tempo-invariant-test
  "The tempo source-of-truth invariant, made permanent and exhaustive.

   #13 (metronome sync) silently depends on the melody and the accompaniment
   agreeing on tempo AND meter. Before #53 that agreement was accidental — two
   separate tables (ceol.abc's melody Q: and ceol.beat-engine's scheduler BPM)
   that happened to line up for named types but diverged for :other/:mazourka/
   unknown types. #53 collapsed them onto one shared table; this test pins the
   agreement as an exhaustive, exercised invariant so it can never silently
   drift apart again.

   For every tune type × time-sig × tempo-offset:
     - the melody Q: BPM equals the scheduler's effective BPM, and
     - the melody meter (Q: beat-unit against the time-sig) equals the
       scheduler's derived beats-per-bar.
   Both sides are read here through their real public entry points
   (ceol.abc for the melody, ceol.beat-engine for the scheduler)."
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as str]
            [ceol.abc :as abc]
            [ceol.beat-engine :as be]))

(def ^:private types
  [:polka :jig :reel :hornpipe :slip-jig :slide :other :mazourka nil])

(def ^:private time-sigs
  ["2/4" "3/4" "4/4" "6/8" "9/8" "12/8"])

;; Offsets covering no-op, up, down, and the 40-BPM floor (exact hit + clamp).
(def ^:private offsets [nil 0 10 20 -20 -60 -80 -1000])

(defn- melody-q
  "The melody's final Q: field for a tune, exactly as the app derives it:
   tempo-for-type then adjust-abc-tempo."
  [tune-type time-sig offset]
  (-> (abc/tempo-for-type tune-type time-sig)
      (abc/adjust-abc-tempo offset)))

(defn- bpm-of [q]
  (be/parse-int (subs q (inc (str/index-of q "=")))))

(defn- beat-unit-of [q]
  (subs q 2 (str/index-of q "=")))

(deftest melody-and-scheduler-agree-test
  (testing "melody BPM == scheduler BPM, melody meter == scheduler beats-per-bar"
    (doseq [tune-type types
            time-sig  time-sigs
            offset    offsets]
      (let [q           (melody-q tune-type time-sig offset)
            melody-bpm  (bpm-of q)
            melody-meter (be/beats-per-bar time-sig (beat-unit-of q))
            sched       (be/beats-for-tune {:type tune-type :time-sig time-sig}
                                           offset)
            label       (str tune-type " " time-sig " offset=" offset)]
        (is (= melody-bpm (:bpm sched))
            (str "BPM disagreement for " label ": melody " melody-bpm
                 " vs scheduler " (:bpm sched)))
        (is (= melody-meter (:beats-per-bar sched))
            (str "meter disagreement for " label ": melody " melody-meter
                 " vs scheduler " (:beats-per-bar sched)))))))

(deftest bpm-never-below-floor-test
  (testing "no type/time-sig/offset drives either side below the 40 BPM floor"
    (doseq [tune-type types
            time-sig  time-sigs
            offset    offsets]
      (let [q     (melody-q tune-type time-sig offset)
            sched (be/beats-for-tune {:type tune-type :time-sig time-sig} offset)]
        (is (>= (bpm-of q) 40) (str "melody floor " tune-type " " time-sig " " offset))
        (is (>= (:bpm sched) 40) (str "scheduler floor " tune-type " " time-sig " " offset))))))
