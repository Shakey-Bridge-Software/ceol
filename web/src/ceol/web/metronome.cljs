(ns ceol.web.metronome
  "Self-correcting click track using performance.now() to cancel setTimeout drift.
   Provides standalone metronome (start-clicking!), a playback-synced metronome
   (start-synced!), and count-in (count-in!).
   Uses the same Tone.js AudioContext as guitar/abc-bridge via Tone/PolySynth.
   Public API: start-clicking!, start-synced!, count-in!, stop!, running?"
  (:require ["tone" :as Tone]
            [ceol.web.abc-bridge :as abc-bridge]
            [ceol.beat-engine :as beat]))

(defonce metro-state (atom {:synth nil :cancel nil}))

(defn- ensure-shared-context!
  "Bind Tone to abc.js's AudioContext so absolute click times (start-synced!)
   land on the same timeline as the melody's scheduled beats. No-op if Tone is
   already wrapping that raw context (e.g. the guitar set it first)."
  []
  (let [raw (abc-bridge/get-audio-context)]
    (when-not (identical? raw (some-> ^js (Tone/getContext) .-rawContext))
      (Tone/setContext (Tone/Context. raw)))))

(defn- ensure-synth! []
  (when-not (:synth @metro-state)
    ;; Switch to the shared context BEFORE (.start Tone) so the resume lands on
    ;; abc.js's AudioContext (which may be suspended) — otherwise a standalone
    ;; metronome started before any melody plays would be silent.
    (ensure-shared-context!)
    (.start Tone)
    (let [synth (-> (Tone/PolySynth.
                     Tone/Synth
                     (clj->js {:oscillator {:type "triangle"}
                               :envelope {:attack 0.001
                                          :decay 0.1
                                          :sustain 0
                                          :release 0.05}}))
                    (.toDestination))]
      (swap! metro-state assoc :synth synth))))

(defn- click-at!
  "Trigger a click at absolute AudioContext time `when-s` (seconds), or
   immediately when `when-s` is js/undefined. Tone shares abc.js's
   AudioContext, so `when-s` is on the melody's beat timeline."
  [accent? when-s]
  (when-let [^js synth (:synth @metro-state)]
    (.triggerAttackRelease synth
                           (if accent? "G5" "C5")
                           "16n"
                           when-s
                           (if accent? 0.7 0.3))))

(defn- click! [accent?]
  (click-at! accent? js/undefined))

;; --- Self-correcting clock ---
;; Uses setTimeout but corrects drift by comparing against performance.now()

(defn- start-precise-clock!
  "Start a self-correcting click clock. Each tick calculates the exact
   delay for the next tick based on elapsed time vs expected time.
   Returns a cancel fn."
  [{:keys [ms-per-beat beats-per-bar]} {:keys [on-beat]}]
  (let [cancelled? (atom false)
        timeout-id (atom nil)
        start-time (js/performance.now)]
    (letfn [(tick [beat]
              (when-not @cancelled?
                (let [accent? (zero? (mod beat beats-per-bar))]
                  (click! accent?)
                  (when on-beat
                    (on-beat {:beat beat :accent? accent?}))
                  ;; Calculate next tick time based on start, not current time
                  (let [next-beat (inc beat)
                        expected-time (+ start-time (* next-beat ms-per-beat))
                        now (js/performance.now)
                        delay (max 1 (- expected-time now))]
                    (reset! timeout-id (js/setTimeout #(tick next-beat) delay))))))]
      ;; Fire first beat immediately
      (tick 0)
      ;; Return cancel fn
      (fn []
        (reset! cancelled? true)
        (when-let [t @timeout-id]
          (js/clearTimeout t))))))

;; --- Playback-synced clock ---
;; Locks clicks to the melody's AudioContext beat grid via a lookahead
;; scheduler (mirrors ceol.web.guitar): each tick pre-schedules the beats in
;; the next lookahead window at their exact audio-clock times, so clicks
;; coincide with melody beats and never drift.

(def ^:private lookahead-s
  "How far ahead to pre-schedule clicks into Web Audio."
  0.25)

(def ^:private tick-ms
  "Scheduler wake interval. Must be < lookahead-s so no beat slips past the
   window between ticks."
  100)

(defn- start-synced-clock!
  "Lookahead scheduler locking clicks to the melody's beat grid. Beat 0 sits at
   `start-at-s` (AudioContext seconds). Each tick schedules the beats in the
   next lookahead window; a monotonic beat-index guard (last-beat) means a beat
   near a window edge is emitted exactly once even though consecutive windows
   overlap. Returns a cancel fn that halts future scheduling; up to lookahead-s
   of already-queued clicks may still sound."
  [{:keys [ms-per-beat beats-per-bar]} start-at-s]
  (let [ctx        (abc-bridge/get-audio-context)
        cancelled? (atom false)
        timer-id   (atom nil)
        last-beat  (atom -1)
        tick (fn tick []
               (when-not @cancelled?
                 (let [now     (.-currentTime ctx)
                       clicks  (beat/beats-in-window start-at-s ms-per-beat
                                                     beats-per-bar now
                                                     (+ now lookahead-s))]
                   (doseq [{:keys [beat accent? time]} clicks]
                     (when (> beat @last-beat)
                       (click-at! accent? time)
                       (reset! last-beat beat))))))]
    ;; First slice synchronously so the initial beat isn't gated on setInterval.
    (tick)
    (reset! timer-id (js/setInterval tick tick-ms))
    (fn []
      (reset! cancelled? true)
      (when-let [id @timer-id] (js/clearInterval id)))))

(defn- count-in-taps
  "How many taps for the count-in. Short bars (2 beats) get 2 bars, others get 1 bar."
  [beats-per-bar]
  (if (< beats-per-bar 3)
    (* beats-per-bar 2)
    beats-per-bar))

(defn- schedule-count-in!
  "Schedule count-in taps at the tune's tempo. Accent on bar downbeats.
   Calls on-done after the last click."
  [{:keys [ms-per-beat beats-per-bar]} on-done]
  (let [total (count-in-taps beats-per-bar)
        cancelled? (atom false)
        timeout-id (atom nil)
        start-time (js/performance.now)]
    (letfn [(tick [beat]
              (when-not @cancelled?
                (if (>= beat total)
                  (when on-done (on-done))
                  (let [accent? (zero? (mod beat beats-per-bar))]
                    (click! accent?)
                    (let [next-beat (inc beat)
                          expected-time (+ start-time (* next-beat ms-per-beat))
                          now (js/performance.now)
                          delay (max 1 (- expected-time now))]
                      (reset! timeout-id (js/setTimeout #(tick next-beat) delay)))))))]
      (tick 0)
      (fn []
        (reset! cancelled? true)
        (when-let [t @timeout-id]
          (js/clearTimeout t))))))

;; --- Public API ---

(defn stop! []
  (when-let [cancel (:cancel @metro-state)]
    (cancel))
  (swap! metro-state assoc :cancel nil))

(defn running? []
  (boolean (:cancel @metro-state)))

(defn start-clicking!
  "Start the continuous standalone metronome (self-correcting performance.now
   clock, first click immediate). Use when no tune is playing."
  [beat-params & [opts]]
  (stop!)
  (ensure-synth!)
  (let [cancel (start-precise-clock! beat-params (or opts {}))]
    (swap! metro-state assoc :cancel cancel)
    cancel))

(defn start-synced!
  "Start the metronome locked to the melody's beat grid. `start-at-s` is the
   melody's beat-0 time (AudioContext seconds); the first click lands on the
   next beat boundary at or after now. Replaces any running clock."
  [beat-params start-at-s]
  (stop!)
  (ensure-synth!)
  (let [cancel (start-synced-clock! beat-params start-at-s)]
    (swap! metro-state assoc :cancel cancel)
    cancel))

(defn count-in!
  "Play one bar of count-in clicks, then call on-done. Cancels any running
   clock first — without this, starting a count-in while a standalone/synced
   clock is running would overwrite its cancel handle and orphan its timers."
  [beat-params on-done]
  (stop!)
  (ensure-synth!)
  (let [cancel (schedule-count-in! beat-params on-done)]
    (swap! metro-state assoc :cancel cancel)
    cancel))
