(ns ceol.web.beat-engine
  "Pure beat math shared between metronome, count-in, and guitar scheduling.
   No I/O, no state. beats-for-tune is the single source of truth for BPM
   and bar timing — all timing consumers should derive from it.")

;; --- Pure beat math (testable, shared .cljc) ---

(def ^:private type-params
  {:polka    {:bpm 120 :beats-per-bar 2}
   :jig      {:bpm 100 :beats-per-bar 2}
   :reel     {:bpm 100 :beats-per-bar 4}
   :hornpipe {:bpm 100 :beats-per-bar 4}
   :slip-jig {:bpm 100 :beats-per-bar 3}
   :slide    {:bpm 100 :beats-per-bar 4}})

(def default-params {:bpm 120 :beats-per-bar 4})

(defn beats-for-tune
  "Calculate effective BPM and beats-per-bar for a tune with tempo offset.
   Returns {:bpm <n> :beats-per-bar <n> :ms-per-beat <n>}."
  [tune tempo-offset]
  (let [base (get type-params (when tune (:type tune)) default-params)
        effective-bpm (max 40 (+ (:bpm base) (or tempo-offset 0)))
        ms-per-beat (/ 60000.0 effective-bpm)]
    {:bpm effective-bpm
     :beats-per-bar (:beats-per-bar base)
     :ms-per-beat ms-per-beat
     :ms-per-bar (* ms-per-beat (:beats-per-bar base))}))

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
