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
