(ns ceol.web.metronome
  (:require ["tone" :as Tone]
            [ceol.web.beat-engine :as beat]))

(defonce metro-state (atom {:synth nil :cancel nil}))

(defn- ensure-synth!
  "Ensure Tone.js is started and synth is created. Synchronous — Tone.start()
   is async but the synth works once created (audio just needs user gesture first)."
  []
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

;; --- Scheduling ---

(defn- schedule-beats!
  "Schedule beats one at a time. Returns a cancel fn."
  [{:keys [ms-per-beat beats-per-bar]} {:keys [on-beat on-done bars]}]
  (let [total-beats (when bars (* bars beats-per-bar))
        cancelled? (atom false)
        current-timeout (atom nil)]
    (letfn [(schedule-next [beat]
              (when-not @cancelled?
                (if (and total-beats (>= beat total-beats))
                  ;; Wait one more beat so on-done fires on the next downbeat
                  (when on-done
                    (let [t (js/setTimeout on-done ms-per-beat)]
                      (reset! current-timeout t)))
                  (let [accent? (zero? (mod beat beats-per-bar))
                        t (js/setTimeout
                           (fn []
                             (when-not @cancelled?
                               (click! accent?)
                               (when on-beat
                                 (on-beat {:beat beat :accent? accent?}))
                               (schedule-next (inc beat))))
                           (if (zero? beat) 0 ms-per-beat))]
                    (reset! current-timeout t)))))]
      (schedule-next 0)
      (fn []
        (reset! cancelled? true)
        (when-let [t @current-timeout]
          (js/clearTimeout t))))))

;; --- Public API ---

(defn stop! []
  (when-let [cancel (:cancel @metro-state)]
    (cancel))
  (swap! metro-state assoc :cancel nil))

(defn running? []
  (boolean (:cancel @metro-state)))

(defn start-clicking!
  "Start the metronome clicking."
  [beat-params & [opts]]
  (stop!)
  (ensure-synth!)
  (let [cancel (schedule-beats! beat-params (merge opts {:bars nil}))]
    (swap! metro-state assoc :cancel cancel)
    cancel))

(defn count-in!
  "Play one bar of count-in clicks, then call on-done."
  [beat-params on-done]
  (ensure-synth!)
  (schedule-beats! beat-params {:bars 1 :on-done on-done}))
