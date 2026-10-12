(ns ceol.web.abc-test
  (:require [clojure.test :refer [deftest testing is are]]
            [ceol.abc :as abc]))

(deftest tempo-for-type-test
  (testing "returns correct Q: field per type"
    (is (= "Q:1/4=120" (abc/tempo-for-type :polka "2/4")))
    (is (= "Q:3/8=100" (abc/tempo-for-type :jig "6/8")))
    (is (= "Q:1/4=100" (abc/tempo-for-type :reel "4/4")))
    (is (= "Q:1/4=100" (abc/tempo-for-type :hornpipe "4/4")))
    (is (= "Q:3/8=100" (abc/tempo-for-type :slip-jig "9/8")))
    (is (= "Q:3/8=100" (abc/tempo-for-type :slide "12/8"))))

  (testing "fallback based on time sig"
    (is (= "Q:3/8=100" (abc/tempo-for-type :other "6/8")))
    (is (= "Q:1/4=100" (abc/tempo-for-type :other "4/4")))))

(deftest build-abc-string-test
  (testing "assembles correct ABC header"
    (let [tune {:name "Test Tune" :type :polka :time-sig "2/4"
                :key "G" :mode-name "Ionian"}
          result (abc/build-abc-string tune "GGAB|d2Bd" nil)]
      (is (clojure.string/includes? result "X:1"))
      (is (clojure.string/includes? result "T:Test Tune"))
      (is (clojure.string/includes? result "M:2/4"))
      (is (clojure.string/includes? result "L:1/8"))
      (is (clojure.string/includes? result "Q:1/4=120"))
      (is (clojure.string/includes? result "K:G"))
      (is (clojure.string/includes? result "GGAB|d2Bd"))))

  (testing "Dorian mode abbreviation"
    (let [tune {:name "Test" :type :jig :time-sig "6/8"
                :key "A" :mode-name "Dorian"}
          result (abc/build-abc-string tune "body" nil)]
      (is (clojure.string/includes? result "K:Ador"))))

  (testing "Aeolian mode abbreviation"
    (let [tune {:name "Test" :type :reel :time-sig "4/4"
                :key "E" :mode-name "Aeolian"}
          result (abc/build-abc-string tune "body" nil)]
      (is (clojure.string/includes? result "K:Em"))))

  (testing "uses abc-key when provided"
    (let [tune {:name "Test" :type :polka :time-sig "2/4"
                :key "G" :mode-name "Ionian"}
          result (abc/build-abc-string tune "body" "Gmix")]
      (is (clojure.string/includes? result "K:Gmix")))))

;; #70: the M: field is the normalised :time-sig, not the tune's raw value —
;; this is the melody half of the meter agreement with ceol.beat-engine. The
;; value itself is pinned in beat_engine_test; this pins the wiring.
(deftest build-abc-string-normalises-meter-test
  (testing "an incoherent sig is replaced by the beat unit's canonical meter"
    (are [tune-type ts expected] (clojure.string/includes?
                                  (abc/build-abc-string
                                   {:name "T" :type tune-type :time-sig ts
                                    :key "G" :mode-name "Ionian"}
                                   "body" nil)
                                  (str "M:" expected "\n"))
      :reel "1/8" "4/4"
      :reel "4/0" "4/4"
      :jig  "1/8" "6/8"
      :jig  "2/4" "6/8"))

  (testing "a coherent sig passes through untouched"
    (are [ts] (clojure.string/includes?
               (abc/build-abc-string
                {:name "T" :type :reel :time-sig ts :key "G" :mode-name "Ionian"}
                "body" nil)
               (str "M:" ts "\n"))
      "4/4" "2/4" "12/8"))

  (testing "an absent sig yields a parseable M: rather than an empty one"
    (let [result (abc/build-abc-string
                  {:name "T" :type :reel :key "G" :mode-name "Ionian"}
                  "body" nil)]
      (is (clojure.string/includes? result "M:4/4")))))

(deftest adjust-abc-tempo-test
  (testing "increases BPM"
    (let [abc "X:1\nQ:1/4=100\nK:G\nGGAB"
          result (abc/adjust-abc-tempo abc 20)]
      (is (clojure.string/includes? result "Q:1/4=120"))))

  (testing "decreases BPM"
    (let [abc "X:1\nQ:3/8=100\nK:G\nGGAB"
          result (abc/adjust-abc-tempo abc -30)]
      (is (clojure.string/includes? result "Q:3/8=70"))))

  (testing "clamps to minimum 40"
    (let [abc "X:1\nQ:1/4=50\nK:G\nGGAB"
          result (abc/adjust-abc-tempo abc -20)]
      (is (clojure.string/includes? result "Q:1/4=40"))))

  (testing "no change with zero offset"
    (let [abc "X:1\nQ:1/4=100\nK:G\nGGAB"]
      (is (= abc (abc/adjust-abc-tempo abc 0)))))

  (testing "no change with nil offset"
    (let [abc "X:1\nQ:1/4=100\nK:G\nGGAB"]
      (is (= abc (abc/adjust-abc-tempo abc nil))))))

(deftest header-line-test
  (testing "identifies header lines"
    (is (abc/header-line? "X:1"))
    (is (abc/header-line? "T:My Tune"))
    (is (abc/header-line? "K:G"))
    (is (abc/header-line? "%%MIDI program 105")))

  (testing "rejects body lines"
    (is (not (abc/header-line? "GGAB|d2Bd")))
    (is (not (abc/header-line? "|:GABd|")))))

(deftest split-abc-parts-test
  (testing "splits on :|||: boundary"
    (let [abc "X:1\nT:Test\nM:2/4\nL:1/8\nQ:1/4=120\nK:G\n|:GGAB|d2Bd:|||:ggab|D2BD:||"
          result (abc/split-abc-parts abc)]
      (is (some? result))
      (is (contains? result :a))
      (is (contains? result :b))
      (is (clojure.string/includes? (:a result) "GGAB"))
      (is (clojure.string/includes? (:b result) "ggab"))))

  (testing "midi? false omits MIDI directive"
    (let [tune {:name "Test" :type :polka :time-sig "2/4" :key "G" :mode-name "Ionian"}
          result (abc/build-abc-string tune "body" nil {:midi? false})]
      (is (not (clojure.string/includes? result "%%MIDI")))
      (is (clojure.string/includes? result "K:G")))))

(deftest add-line-breaks-test
  (testing "inserts newline after every n bars"
    (let [body "AB|CD|EF|GH|IJ|KL|MN|OP"
          result (abc/add-line-breaks body 4)]
      (is (clojure.string/includes? result "\n"))
      (is (= 2 (count (clojure.string/split-lines result))))))

  (testing "does not break on repeat markers"
    (let [body "|:AB|CD:||:EF|GH:|"
          result (abc/add-line-breaks body 2)]
      ;; repeat markers should not count as simple barlines
      (is (string? result))))

  (testing "zero bars-per-line returns unchanged"
    (let [body "AB|CD|EF"]
      (is (= body (abc/add-line-breaks body 0))))))

(deftest split-abc-body-test
  (testing "splits on :|||: boundary"
    (let [result (abc/split-abc-body "GGAB|d2Bd:|||:|:ggab|D2BD")]
      (is (some? result))
      (is (clojure.string/includes? (:a result) "GGAB"))
      (is (clojure.string/includes? (:b result) "ggab"))))

  (testing "splits on :| boundary"
    (let [result (abc/split-abc-body "GGAB|d2Bd:|ggab|D2BD")]
      (is (some? result))
      (is (clojure.string/includes? (:a result) "GGAB"))
      (is (clojure.string/includes? (:b result) "ggab"))))

  (testing "returns nil when no boundary found"
    (is (nil? (abc/split-abc-body "GGAB|d2Bd"))))

  (testing "returns nil for empty string"
    (is (nil? (abc/split-abc-body "")))))
