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

;; type-params: the one per-tune-type tempo + meter table. Each value is
;;   {:bpm <int>            base beats-per-minute before any tempo offset
;;    :beat-unit <string>   the ABC Q: numerator, e.g. "1/4" or "3/8" — the
;;                          note value that counts as one beat. Modelled as a
;;                          string (not a Clojure ratio) so the same literal
;;                          works in both Clojure and ClojureScript.
;;    :meter <string>}      the meter to fall back on when the tune's own
;;                          :time-sig cannot be expressed in this beat unit
;;                          (see normalise-time-sig). It lives here so a new
;;                          type's tempo and meter are declared in one place,
;;                          and it is keyed by type rather than beat unit
;;                          because two types can share a beat unit and still
;;                          want different meters — a polka is 2/4, a reel
;;                          4/4, and both are "1/4".
;; beats-per-bar is NOT stored here — it is derived per tune as
;; time-sig ÷ beat-unit (see beats-per-bar), so a type's meter follows its
;; actual time signature rather than a second hard-coded assumption.
(def ^:private type-params
  {:polka    {:bpm 120 :beat-unit "1/4" :meter "2/4"}
   :jig      {:bpm 100 :beat-unit "3/8" :meter "6/8"}
   :reel     {:bpm 100 :beat-unit "1/4" :meter "4/4"}
   :hornpipe {:bpm 100 :beat-unit "1/4" :meter "4/4"}
   :slip-jig {:bpm 100 :beat-unit "3/8" :meter "9/8"}
   :slide    {:bpm 100 :beat-unit "3/8" :meter "12/8"}})

(defn- fallback-params
  "Tempo and fallback meter for tune types not in `type-params` (:other,
   :mazourka, unknown, nil). Melody-authoritative: mirrors the melody's
   historical time-sig-aware tempo so accompaniment matches the banjo exactly
   rather than drifting off it. The `:meter` is consulted only when the tune's
   own :time-sig is not expressible in the chosen beat unit — see
   [[normalise-time-sig]]."
  [time-sig]
  (case time-sig
    ("6/8" "9/8") {:bpm 100 :beat-unit "3/8" :meter "6/8"}
    "3/4"         {:bpm 120 :beat-unit "1/4" :meter "4/4"}
    {:bpm 100 :beat-unit "1/4" :meter "4/4"}))

(defn tempo-params
  "Canonical {:bpm :beat-unit :meter} for a tune type + time-sig. Named types
   come from `type-params` (time-sig ignored); everything else falls back to the
   melody-authoritative time-sig tempo. The base for both the melody Q: field
   (via ceol.abc) and the accompaniment scheduler (via beats-for-tune); the
   `:meter` is the fallback used by [[normalise-time-sig]]."
  [tune-type time-sig]
  (or (get type-params tune-type)
      (fallback-params time-sig)))

(defn clamp-bpm
  "Clamp an effective BPM to the minimum playable floor of 40. The single
   clamp shared by beats-for-tune and ceol.abc/adjust-abc-tempo."
  [bpm]
  (max 40 bpm))

(defn- parse-frac
  "Parse a fraction string like \"6/8\" into [numerator denominator] ints, or
   nil when it is malformed (missing part, non-numeric, or zero denominator)."
  [s]
  (let [[n d] (str/split (str/trim (str s)) #"/")
        ni (when (and n (re-matches #"\d+" n)) (parse-int n))
        di (when (and d (re-matches #"\d+" d)) (parse-int d))]
    (when (and ni di (pos? di))
      [ni di])))

(defn- frac-or-4-4
  "[[parse-frac]], falling back to common time so a malformed sig fails soft
   instead of throwing (JVM: parse-long on nil) or silencing the scheduler
   (cljs: NaN propagates through ms-per-bar to zero scheduled beats). See
   issue #67."
  [s]
  (or (parse-frac s) [4 4]))

(defn beats-per-bar
  "Beats per bar = time-sig ÷ beat-unit, e.g. 6/8 ÷ 3/8 = 2, 4/4 ÷ 1/4 = 4.
   Both args are fraction strings; the quotient is exact for real meters.
   Clamped to a floor of 1: a schema-valid `time-sig` smaller than the
   `beat-unit` (e.g. a jig with :time-sig \"1/8\") would otherwise `quot` to 0
   and blow up the beats-in-window scheduler at `(mod n 0)` (JVM throw) or drop
   every bar accent (cljs NaN). Same corrupt-import surface as the parse-frac
   guard. #67. Callers passing a tune's :time-sig normalise it first (see
   [[normalise-time-sig]]), so they never reach the floor; it stays as a guard
   for direct callers."
  [time-sig beat-unit]
  (let [[tn td] (frac-or-4-4 time-sig)
        [bn bd] (frac-or-4-4 beat-unit)]
    (max 1 (quot (* tn bd) (* td bn)))))

(defn- parse-beats
  "Parse a tune's meter and beat unit into
   {:sig [numerator denominator], :beats-per-bar <exact int|nil>}, or nil when
   either fraction is malformed, zero-numeratored, or has a zero denominator.

   `:beats-per-bar` is the *exact* quotient time-sig ÷ beat-unit, and nil when
   that division is not exact (9/8 ÷ 1/4 = 4.5 beats) or not positive (0/4) —
   i.e. exactly when this beat unit cannot express this meter."
  [time-sig beat-unit]
  (let [[tn td] (parse-frac time-sig)
        [bn bd] (parse-frac beat-unit)]
    (when (and tn td bn bd (pos? tn) (pos? bn))
      (let [num (* tn bd)
            den (* td bn)
            q   (when (zero? (mod num den)) (quot num den))]
        {:sig           [tn td]
         :beats-per-bar (when (and q (pos? q)) q)}))))

(defn normalise-time-sig
  "The :time-sig that the melody's M: field, the accompaniment's beat maths, and
   the UI must all use, so the sig shown is the sig that sounds.

   Returns the tune's own signature re-rendered from its parsed
   [numerator denominator] — never the raw input string, so a malformed field
   like \"4/4/4\" cannot be echoed to abc.js — when its bar is a whole number of
   the type's beats, i.e. when time-sig ÷ beat-unit is an exact positive
   integer. Otherwise returns the type's canonical `:meter`.

   Without this the two surfaces diverge whenever the tune's :time-sig is not
   expressible in its beat unit, because abc.js parses M: literally while
   beats-per-bar derives its own quotient: M:1/8 under a quarter-note beat bars
   every eighth while beats-per-bar floors to one beat (a 2× disagreement), and
   M:4/0 leaves abc.js reporting a null bar length. Even the old 4/4 fallback
   for a non-numeric sig disagreed for a jig, since 4/4 ÷ 3/8 is 2.67 beats but
   abc.js reads a common-time bar. All of these pass the Tune schema
   ([:time-sig :string]) and the import boundary, so they are reachable from a
   hand-edited localStorage tune or an imported backup EDN. See issue #70."
  [tune-type time-sig]
  (let [{:keys [beat-unit meter]} (tempo-params tune-type time-sig)
        {:keys [sig beats-per-bar]} (parse-beats time-sig beat-unit)]
    (if beats-per-bar
      (str (first sig) "/" (second sig))
      ;; `meter` is always present on both tables; the default keeps this fn's
      ;; "always a usable meter string" contract even if a future table entry
      ;; omits it, rather than emitting a bare "M:" for abc.js to reject.
      (or meter "4/4"))))

(defn beats-for-tune
  "Effective BPM and bar timing for a tune with a tempo offset, derived from
   the shared tempo table. Returns
   {:bpm <n> :beats-per-bar <n> :ms-per-beat <n> :ms-per-bar <n>}.
   beats-per-bar comes from the tune's normalised :time-sig (see
   [[normalise-time-sig]]) ÷ the type's beat-unit."
  [tune tempo-offset]
  (let [{:keys [bpm beat-unit]} (tempo-params (:type tune) (:time-sig tune))
        effective-bpm (clamp-bpm (+ bpm (or tempo-offset 0)))
        meter         (normalise-time-sig (:type tune) (:time-sig tune))
        bpb           (beats-per-bar meter beat-unit)
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
