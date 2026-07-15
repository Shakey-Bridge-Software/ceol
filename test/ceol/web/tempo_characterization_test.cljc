(ns ceol.web.tempo-characterization-test
  "Characterization golden for the melody tempo path ONLY:
   ceol.abc/tempo-for-type + adjust-abc-tempo. Freezes the current Q: output
   for a representative grid of tune type × time-sig × tempo-offset, plus the
   minimum-40 BPM floor and the fact that named types ignore time-sig.

   Safety net for the 'unify tempo source of truth' prefactor: the follow-up
   consolidates this melody Q: table with the guitar/metronome BPM table
   (ceol.beat-engine). Pinning the melody side here means that consolidation
   cannot silently change any tune's Q:.

   Scope note — this does NOT assert the melody table and the beat-engine
   table agree; they currently DIVERGE for unknown/:other types (melody
   Q:1/4=100 for 4/4 vs beat-engine's 120 BPM default-params). The
   beat-engine table is characterized separately by
   ceol.web.beat-engine-test; reconciling the two tables is the follow-up
   ticket's job, not this golden's.

   The `golden` rows below are FROZEN literals captured from the current
   implementation — not recomputed from it — so they detect drift. To
   regenerate after an intended change, eval `final-q` over the input grid
   and paste the results back."
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as str]
            [ceol.abc :as abc]))

(defn- final-q
  "Melody Q: field for a tune type/time-sig after applying a tempo offset,
   mirroring how the app derives it: tempo-for-type then adjust-abc-tempo."
  [tune-type time-sig offset]
  (-> (abc/tempo-for-type tune-type time-sig)
      (abc/adjust-abc-tempo offset)))

(defn- bpm-of
  "Parse the integer BPM out of a Q: field like \"Q:1/4=120\"."
  [q]
  (#?(:clj parse-long :cljs js/parseInt)
   (subs q (inc (str/index-of q "=")))))

;; [type time-sig offset => expected-Q]. Rows cover every known type (both
;; Q-numerator forms 1/4 and 3/8), every fallback time-sig branch via :other
;; and nil, and offsets that exercise up/down/no-op plus the 40-floor
;; (exact hit and clamp-from-below).
(def golden
  [[:polka "2/4" nil "Q:1/4=120"]
   [:polka "2/4" 0 "Q:1/4=120"]
   [:polka "2/4" 10 "Q:1/4=130"]
   [:polka "2/4" 20 "Q:1/4=140"]
   [:polka "2/4" -20 "Q:1/4=100"]
   [:polka "2/4" -60 "Q:1/4=60"]
   [:polka "2/4" -80 "Q:1/4=40"]
   [:polka "2/4" -1000 "Q:1/4=40"]
   [:jig "6/8" nil "Q:3/8=100"]
   [:jig "6/8" 0 "Q:3/8=100"]
   [:jig "6/8" 10 "Q:3/8=110"]
   [:jig "6/8" 20 "Q:3/8=120"]
   [:jig "6/8" -20 "Q:3/8=80"]
   [:jig "6/8" -60 "Q:3/8=40"]
   [:jig "6/8" -80 "Q:3/8=40"]
   [:jig "6/8" -1000 "Q:3/8=40"]
   [:reel "4/4" nil "Q:1/4=100"]
   [:reel "4/4" 0 "Q:1/4=100"]
   [:reel "4/4" 10 "Q:1/4=110"]
   [:reel "4/4" 20 "Q:1/4=120"]
   [:reel "4/4" -20 "Q:1/4=80"]
   [:reel "4/4" -60 "Q:1/4=40"]
   [:reel "4/4" -80 "Q:1/4=40"]
   [:reel "4/4" -1000 "Q:1/4=40"]
   [:hornpipe "4/4" nil "Q:1/4=100"]
   [:hornpipe "4/4" 0 "Q:1/4=100"]
   [:hornpipe "4/4" 10 "Q:1/4=110"]
   [:hornpipe "4/4" 20 "Q:1/4=120"]
   [:hornpipe "4/4" -20 "Q:1/4=80"]
   [:hornpipe "4/4" -60 "Q:1/4=40"]
   [:hornpipe "4/4" -80 "Q:1/4=40"]
   [:hornpipe "4/4" -1000 "Q:1/4=40"]
   [:slip-jig "9/8" nil "Q:3/8=100"]
   [:slip-jig "9/8" 0 "Q:3/8=100"]
   [:slip-jig "9/8" 10 "Q:3/8=110"]
   [:slip-jig "9/8" 20 "Q:3/8=120"]
   [:slip-jig "9/8" -20 "Q:3/8=80"]
   [:slip-jig "9/8" -60 "Q:3/8=40"]
   [:slip-jig "9/8" -80 "Q:3/8=40"]
   [:slip-jig "9/8" -1000 "Q:3/8=40"]
   [:slide "12/8" nil "Q:3/8=100"]
   [:slide "12/8" 0 "Q:3/8=100"]
   [:slide "12/8" 10 "Q:3/8=110"]
   [:slide "12/8" 20 "Q:3/8=120"]
   [:slide "12/8" -20 "Q:3/8=80"]
   [:slide "12/8" -60 "Q:3/8=40"]
   [:slide "12/8" -80 "Q:3/8=40"]
   [:slide "12/8" -1000 "Q:3/8=40"]
   [:other "6/8" nil "Q:3/8=100"]
   [:other "6/8" 0 "Q:3/8=100"]
   [:other "6/8" 10 "Q:3/8=110"]
   [:other "6/8" 20 "Q:3/8=120"]
   [:other "6/8" -20 "Q:3/8=80"]
   [:other "6/8" -60 "Q:3/8=40"]
   [:other "6/8" -80 "Q:3/8=40"]
   [:other "6/8" -1000 "Q:3/8=40"]
   [:other "9/8" nil "Q:3/8=100"]
   [:other "9/8" 0 "Q:3/8=100"]
   [:other "9/8" 10 "Q:3/8=110"]
   [:other "9/8" 20 "Q:3/8=120"]
   [:other "9/8" -20 "Q:3/8=80"]
   [:other "9/8" -60 "Q:3/8=40"]
   [:other "9/8" -80 "Q:3/8=40"]
   [:other "9/8" -1000 "Q:3/8=40"]
   [:other "3/4" nil "Q:1/4=120"]
   [:other "3/4" 0 "Q:1/4=120"]
   [:other "3/4" 10 "Q:1/4=130"]
   [:other "3/4" 20 "Q:1/4=140"]
   [:other "3/4" -20 "Q:1/4=100"]
   [:other "3/4" -60 "Q:1/4=60"]
   [:other "3/4" -80 "Q:1/4=40"]
   [:other "3/4" -1000 "Q:1/4=40"]
   [:other "4/4" nil "Q:1/4=100"]
   [:other "4/4" 0 "Q:1/4=100"]
   [:other "4/4" 10 "Q:1/4=110"]
   [:other "4/4" 20 "Q:1/4=120"]
   [:other "4/4" -20 "Q:1/4=80"]
   [:other "4/4" -60 "Q:1/4=40"]
   [:other "4/4" -80 "Q:1/4=40"]
   [:other "4/4" -1000 "Q:1/4=40"]
   [:other "2/4" nil "Q:1/4=100"]
   [:other "2/4" 0 "Q:1/4=100"]
   [:other "2/4" 10 "Q:1/4=110"]
   [:other "2/4" 20 "Q:1/4=120"]
   [:other "2/4" -20 "Q:1/4=80"]
   [:other "2/4" -60 "Q:1/4=40"]
   [:other "2/4" -80 "Q:1/4=40"]
   [:other "2/4" -1000 "Q:1/4=40"]
   [nil nil nil "Q:1/4=100"]
   [nil nil 0 "Q:1/4=100"]
   [nil nil 10 "Q:1/4=110"]
   [nil nil 20 "Q:1/4=120"]
   [nil nil -20 "Q:1/4=80"]
   [nil nil -60 "Q:1/4=40"]
   [nil nil -80 "Q:1/4=40"]
   [nil nil -1000 "Q:1/4=40"]])

(deftest melody-tempo-golden-test
  (testing "melody Q: output is frozen for every grid row"
    (doseq [[tune-type time-sig offset expected] golden]
      (is (= expected (final-q tune-type time-sig offset))
          (str tune-type " " time-sig " offset=" offset)))))

;; Named types derive Q: from type alone — the nested time-sig case in
;; tempo-for-type only fires for unknown types. Freeze that independence
;; so a future 'unify tempo' refactor can't quietly make a named type's
;; BPM depend on its time-sig without breaking this test.
(def ^:private all-time-sigs ["2/4" "3/4" "4/4" "6/8" "9/8" "12/8"])

(def named-type-q
  {:polka    "Q:1/4=120"
   :jig      "Q:3/8=100"
   :reel     "Q:1/4=100"
   :hornpipe "Q:1/4=100"
   :slip-jig "Q:3/8=100"
   :slide    "Q:3/8=100"})

(deftest named-type-time-sig-independent-test
  (testing "each named type freezes one Q: regardless of time-sig"
    (doseq [[tune-type expected] named-type-q
            time-sig all-time-sigs]
      (is (= expected (abc/tempo-for-type tune-type time-sig))
          (str tune-type " " time-sig)))))

(deftest minimum-40-bpm-floor-test
  (testing "no offset drives the melody tempo below the 40 BPM floor"
    (doseq [[tune-type time-sig offset] golden]
      (let [q (final-q tune-type time-sig offset)]
        (is (>= (bpm-of q) 40)
            (str tune-type " " time-sig " offset=" offset " -> " q)))))

  (testing "a large negative offset clamps to exactly 40 for every type"
    (doseq [[tune-type time-sig] (map (juxt first second) golden)]
      (is (= 40 (bpm-of (final-q tune-type time-sig -1000)))
          (str tune-type " " time-sig)))))
