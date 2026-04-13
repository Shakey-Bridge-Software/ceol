(ns ceol.web.metronome
  (:require ["tone" :as Tone]
            [ceol.web.beat-engine :as beat]))

(defonce metro-state (atom {:synth nil :cancel nil}))

(defn- ensure-synth! []
  (when-not (:synth @metro-state)
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

(defn- click! [accent?]
  (when-let [^js synth (:synth @metro-state)]
    (.triggerAttackRelease synth
                           (if accent? "G5" "C5")
                           "16n"
                           js/undefined
                           (if accent? 0.7 0.3))))

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

(defn- schedule-count-in!
  "Schedule one bar of count-in clicks using the same self-correcting approach.
   Calls on-done one beat after the last click (on the downbeat)."
  [{:keys [ms-per-beat beats-per-bar]} on-done]
  (let [cancelled? (atom false)
        timeout-id (atom nil)
        start-time (js/performance.now)]
    (letfn [(tick [beat]
              (when-not @cancelled?
                (if (>= beat beats-per-bar)
                  ;; Count-in done — on-done fires on the next downbeat
                  (when on-done (on-done))
                  (let [accent? (zero? beat)]
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
  "Start the continuous metronome (self-correcting clock)."
  [beat-params & [opts]]
  (stop!)
  (ensure-synth!)
  (let [cancel (start-precise-clock! beat-params (or opts {}))]
    (swap! metro-state assoc :cancel cancel)
    cancel))

(defn count-in!
  "Play one bar of count-in clicks, then call on-done."
  [beat-params on-done]
  (ensure-synth!)
  (let [cancel (schedule-count-in! beat-params on-done)]
    (swap! metro-state assoc :cancel cancel)
    cancel))
