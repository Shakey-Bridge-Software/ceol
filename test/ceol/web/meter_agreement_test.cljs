(ns ceol.web.meter-agreement-test
  "Cross-checks the rendered melody's bar length against the accompaniment's
   ms-per-bar, for every tune type × time-sig.

   This is the value-bearing half of the #70 guard. The melody's bar length
   comes from abc.js — an independent parser that reads the M: field the app
   really emits — so the test pins the *value* of the agreement rather than
   restating ceol.beat-engine's own inputs (the failure mode #68 identified in
   the pure invariant test). A numeric-but-incoherent :time-sig reaches abc.js
   unnormalised on main, where it bars the melody differently from the
   guitar/metronome; every pair below must agree instead."
  (:require ["abcjs" :as ABCJS]
            [clojure.test :refer [deftest testing is]]
            [ceol.abc :as abc]
            [ceol.beat-engine :as be]))

(def ^:private types
  [:polka :jig :reel :hornpipe :slip-jig :slide :other :mazourka nil])

;; Catalog sigs, plus the corrupt-import surface from #70/#67: numeric sigs
;; abc.js parses literally, and non-numeric ones it falls back on.
(def ^:private time-sigs
  ["2/4" "3/4" "4/4" "6/8" "9/8" "12/8"
   "1/8" "1/4" "6/9" "0/4" "4/0"
   "" "C" "abc" "banana/4" "3/4 " nil])

(defn- rendered-abc
  "The full ABC string the app hands abc.js for this tune-type + sig."
  [tune-type ts]
  (abc/build-abc-string {:name "T" :type tune-type :time-sig ts
                         :key "G" :mode-name "Ionian"}
                        "|:GABc defg:|" "G"))

(defn- tune-method
  "Call a method on an abc.js tune object by name. Reflect rather than
   `tune/name` so the build stays free of undeclared-var warnings — abc.js
   ships no externs."
  [tune method]
  (let [f (js/Reflect.get tune method)]
    (when (fn? f) (.call f tune))))

(defn- melody-bar-seconds
  "Seconds per bar of the rendered melody, from abc.js's own parse. abc.js
   reports the bar length in whole notes; `Q:<bn>/<bd>=<n>` means one whole
   note is (1/(bn/bd)) = bd/bn beats, each 60/n seconds. Nil when abc.js
   reports no bar length at all (see the \"4/0\" case)."
  [abc-str]
  (let [tune       (first (ABCJS/parseOnly abc-str))
        bar-length (tune-method tune "getBarLength")
        [_ bn bd n] (re-find #"Q:(\d+)/(\d+)=(\d+)" abc-str)]
    (when (and bar-length (pos? bar-length))
      (* bar-length (/ (* 60.0 (be/parse-int bd))
                       (* (be/parse-int n) (be/parse-int bn)))))))

(deftest melody-and-accompaniment-agree-on-bar-length-test
  (testing "abc.js's melody bar == the scheduler's ms-per-bar, for every pair"
    (doseq [tune-type types
            ts        time-sigs]
      (let [abc-str (rendered-abc tune-type ts)
            melody  (melody-bar-seconds abc-str)
            sched-s (/ (:ms-per-bar (be/beats-for-tune {:type tune-type
                                                        :time-sig ts}
                                                       0))
                       1000.0)
            label   (str tune-type " " (pr-str ts))]
        (is (some? melody)
            (str "abc.js reported no bar length for " label))
        (is (< (js/Math.abs (- melody sched-s)) 1e-9)
            (str "bar-length disagreement for " label
                 ": abc.js " melody "s vs scheduler " sched-s "s"))))))

(deftest rendered-meter-is-well-formed-test
  (testing "abc.js parses the emitted M: without warnings"
    (doseq [tune-type types
            ts        time-sigs]
      (let [abc-str  (rendered-abc tune-type ts)
            warnings (vec (js/Reflect.get (first (ABCJS/parseOnly abc-str))
                                          "warnings"))]
        (is (empty? warnings)
            (str "abc.js warnings for " tune-type " " (pr-str ts)
                 ": " warnings))))))
