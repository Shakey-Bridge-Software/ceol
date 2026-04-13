(ns ceol.web.metronome
  (:require ["tone" :as Tone]
            [ceol.web.beat-engine :as beat]))

;; --- Metronome state ---

(defonce metro-state (atom {:synth nil :started? false :cancel nil}))

(defn- ensure-started! []
  (when-not (:started? @metro-state)
    (-> (.start Tone)
        (.then (fn [] (swap! metro-state assoc :started? true))))))

(defn- init-synth! []
  (when-not (:synth @metro-state)
    (let [synth (-> (Tone/Synth.
                     (clj->js {:oscillator {:type "triangle"}
                               :envelope {:attack 0.001
                                          :decay 0.08
                                          :sustain 0
                                          :release 0.05}}))
                    (.toDestination))]
      (swap! metro-state assoc :synth synth)
      synth)))

(defn- click! [accent?]
  (when-let [^js synth (:synth @metro-state)]
    (try
      (.triggerAttackRelease synth
                             (if accent? "G5" "C5")
                             "32n"
                             nil
                             (if accent? 0.8 0.4))
      (catch :default _))))

;; --- Scheduling ---

(defn- schedule-beats!
  "Schedule beats using setTimeout. Returns a cancel fn."
  [{:keys [ms-per-beat beats-per-bar]} {:keys [on-beat on-done bars]}]
  (let [total-beats (if bars (* bars beats-per-bar) js/Infinity)
        timeouts (atom [])
        cancelled? (atom false)]
    (loop [beat 0]
      (when (and (< beat total-beats) (not @cancelled?))
        (let [ms (* beat ms-per-beat)
              accent? (zero? (mod beat beats-per-bar))
              t (js/setTimeout
                 (fn []
                   (when-not @cancelled?
                     (click! accent?)
                     (when on-beat
                       (on-beat {:beat beat :accent? accent?}))))
                 ms)]
          (swap! timeouts conj t)
          (recur (inc beat)))))
    (when (and bars on-done)
      (let [total-ms (* total-beats ms-per-beat)
            t (js/setTimeout
               (fn []
                 (when-not @cancelled?
                   (on-done)))
               total-ms)]
        (swap! timeouts conj t)))
    (fn []
      (reset! cancelled? true)
      (doseq [t @timeouts]
        (js/clearTimeout t))
      (reset! timeouts []))))

;; --- Public API ---

(defn stop!
  "Stop the metronome."
  []
  (when-let [cancel (:cancel @metro-state)]
    (cancel))
  (swap! metro-state assoc :cancel nil))

(defn running? []
  (boolean (:cancel @metro-state)))

(defn start-clicking!
  "Start the metronome clicking. Returns a cancel fn."
  [beat-params & [opts]]
  (stop!)
  (ensure-started!)
  (init-synth!)
  (let [cancel (schedule-beats! beat-params
                                (merge opts {:bars nil}))]
    (swap! metro-state assoc :cancel cancel)
    cancel))

(defn count-in!
  "Play one bar of count-in clicks, then call on-done. Returns a cancel fn."
  [beat-params on-done]
  (ensure-started!)
  (init-synth!)
  (schedule-beats! beat-params {:bars 1 :on-done on-done}))
