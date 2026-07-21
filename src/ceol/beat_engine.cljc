(ns ceol.beat-engine
  "Pure beat math and the single source of truth for tempo: one per-type
   {:bpm :beat-unit} table from which beats-per-bar, ms timing, the BPM clamp,
   and the ABC Q: field (formatted downstream by ceol.abc) all derive."
  (:require [clojure.string :as str]))

(defn parse-int
  "Parse an integer string, cross-platform (Clojure + ClojureScript). The one
   int parser shared by the tempo path (here and ceol.abc)."
  [s]
  #?(:clj (parse-long s) :cljs (js/parseInt s 10)))

;; --- Tempo source of truth (testable, shared .cljc) ---

;; type-params: the one per-tune-type tempo table. Each value is
;;   {:bpm <int>            base beats-per-minute before any tempo offset
;;    :beat-unit <string>}  the ABC Q: numerator, e.g. "1/4" or "3/8" — the
;;                          note value that counts as one beat. Modelled as a
;;                          string (not a Clojure ratio) so the same literal
;;                          works in both Clojure and ClojureScript.
;; beats-per-bar is NOT stored here — it is derived per tune as
;; time-sig ÷ beat-unit (see beats-per-bar), so a type's meter follows its
;; actual time signature rather than a second hard-coded assumption.
(def ^:private type-params
  {:polka    {:bpm 120 :beat-unit "1/4"}
   :jig      {:bpm 100 :beat-unit "3/8"}
   :reel     {:bpm 100 :beat-unit "1/4"}
   :hornpipe {:bpm 100 :beat-unit "1/4"}
   :slip-jig {:bpm 100 :beat-unit "3/8"}
   :slide    {:bpm 100 :beat-unit "3/8"}})

(defn- fallback-params
  "Tempo for tune types not in type-params (:other, :mazourka, unknown, nil).
   Melody-authoritative: mirrors the melody's historical time-sig-aware tempo
   so accompaniment matches the banjo exactly rather than drifting off it."
  [time-sig]
  (case time-sig
    ("6/8" "9/8") {:bpm 100 :beat-unit "3/8"}
    "3/4"         {:bpm 120 :beat-unit "1/4"}
    {:bpm 100 :beat-unit "1/4"}))

(defn tempo-params
  "Canonical {:bpm :beat-unit} for a tune type + time-sig. Named types come
   from type-params (time-sig ignored); everything else falls back to the
   melody-authoritative time-sig tempo. The base for both the melody Q: field
   (via ceol.abc) and the accompaniment scheduler (via beats-for-tune)."
  [tune-type time-sig]
  (or (get type-params tune-type)
      (fallback-params time-sig)))

(defn clamp-bpm
  "Clamp an effective BPM to the minimum playable floor of 40. The single
   clamp shared by beats-for-tune and ceol.abc/adjust-abc-tempo."
  [bpm]
  (max 40 bpm))

(defn- parse-frac
  "Parse a fraction string like \"6/8\" into [numerator denominator] ints.
   Falls back to 4/4 for a malformed sig (missing, non-numeric, or zero
   denominator) so a corrupt or imported :time-sig fails soft instead of
   throwing (JVM: parse-long on nil) or silencing the scheduler (cljs:
   NaN propagates through ms-per-bar to zero scheduled beats). See issue #67."
  [s]
  (let [[n d] (str/split (str s) #"/")
        ni (when (and n (re-matches #"\d+" n)) (parse-int n))
        di (when (and d (re-matches #"\d+" d)) (parse-int d))]
    (if (and ni di (pos? di))
      [ni di]
      [4 4])))

(defn beats-per-bar
  "Beats per bar = time-sig ÷ beat-unit, e.g. 6/8 ÷ 3/8 = 2, 4/4 ÷ 1/4 = 4.
   Both args are fraction strings; the quotient is exact for real meters."
  [time-sig beat-unit]
  (let [[tn td] (parse-frac time-sig)
        [bn bd] (parse-frac beat-unit)]
    (quot (* tn bd) (* td bn))))

(defn beats-for-tune
  "Effective BPM and bar timing for a tune with a tempo offset, derived from
   the shared tempo table. Returns
   {:bpm <n> :beats-per-bar <n> :ms-per-beat <n> :ms-per-bar <n>}.
   beats-per-bar comes from the tune's :time-sig ÷ the type's beat-unit
   (defaulting to 4/4 when :time-sig is absent)."
  [tune tempo-offset]
  (let [{:keys [bpm beat-unit]} (tempo-params (:type tune) (:time-sig tune))
        effective-bpm (clamp-bpm (+ bpm (or tempo-offset 0)))
        bpb           (beats-per-bar (or (:time-sig tune) "4/4") beat-unit)
        ms-per-beat   (/ 60000.0 effective-bpm)]
    {:bpm effective-bpm
     :beats-per-bar bpb
     :ms-per-beat ms-per-beat
     :ms-per-bar (* ms-per-beat bpb)}))

;; --- Beat-grid alignment (playback-synced metronome) ---
;; Beat 0 sits at `start-at-s` (AudioContext seconds); beat n at
;; start-at-s + n*ms-per-beat/1000. These pure helpers let the metronome lock
;; its clicks to the melody's AudioContext beat grid — the machine-checkable
;; core behind the "clicks equal melody beat times" acceptance test.

(defn next-beat-index
  "Index of the first beat at or after `now-s`, given beat 0 at `start-at-s`.
   Clamps to 0 when `now-s` precedes the start. A `now-s` sitting exactly on a
   beat boundary returns that beat (a tiny epsilon guards against float drift
   bumping it to the next one)."
  [start-at-s ms-per-beat now-s]
  (let [beat-s (/ ms-per-beat 1000.0)
        raw    (/ (- now-s start-at-s) beat-s)]
    (max 0 (long (Math/ceil (- raw 1e-9))))))

(defn beat-time
  "Absolute AudioContext seconds of beat `n`, given beat 0 at `start-at-s`."
  [start-at-s ms-per-beat n]
  (+ start-at-s (* n (/ ms-per-beat 1000.0))))

(defn beats-in-window
  "Beats whose times fall in the half-open window [from-s, until-s).
   Returns a seq of {:beat n :time <s> :accent? <bool>}, accent on bar
   downbeats (beat index divisible by beats-per-bar). Drives the lookahead
   scheduler: each tick asks for the beats in the next lookahead slice."
  [start-at-s ms-per-beat beats-per-bar from-s until-s]
  (let [first-n (next-beat-index start-at-s ms-per-beat from-s)]
    (->> (iterate inc first-n)
         (map (fn [n] {:beat n
                       :time (beat-time start-at-s ms-per-beat n)
                       :accent? (zero? (mod n beats-per-bar))}))
         (take-while (fn [{:keys [time]}] (< time until-s))))))
