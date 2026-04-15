(ns ceol.web.abc-bridge
  (:require ["abcjs" :as ABCJS]))

(defonce synth-state (atom {:synth nil :visual nil :generation 0 :audio-ctx nil :duration nil}))

(defn get-audio-context
  "Get or create a shared AudioContext."
  []
  (or (:audio-ctx @synth-state)
      (let [ctx (js/AudioContext.)]
        (swap! synth-state assoc :audio-ctx ctx)
        ctx)))

(defn render-abc!
  "Render ABC string into a DOM element as SVG. Synchronous.
   Stores and returns the visual object for synth use.
   Caller is responsible for ensuring DOM is laid out (e.g. via requestAnimationFrame)."
  [element abc-str & [opts]]
  (when (and element abc-str)
    (set! (.-innerHTML element) "")
    (let [container (js/document.querySelector ".sheet-area")
          available (if container (- (.-clientWidth container) 80) 800)
          staff-w (min 950 (max 400 available))
          visual (ABCJS/renderAbc element abc-str
                                  (clj->js (merge {:staffwidth staff-w
                                                   :scale 1.1
                                                   :add_classes true}
                                                  opts)))
          v (first visual)]
      (swap! synth-state assoc :visual v)
      v)))

(defn stop!
  "Stop any current playback."
  []
  (let [gen (:generation (swap! synth-state update :generation inc))]
    (when-let [synth (:synth @synth-state)]
      (try (.stop synth) (catch :default _)))
    (swap! synth-state assoc :synth nil :duration nil)
    gen))

(defn playing?
  "Is the synth currently playing?"
  []
  (boolean (:synth @synth-state)))

(defn prepare!
  "Init + prime the synth. Returns a promise that resolves when ready to start."
  []
  (stop!)
  (when-let [visual (:visual @synth-state)]
    (let [gen (:generation @synth-state)
          synth (ABCJS/synth.CreateSynth.)
          ctx (get-audio-context)]
      (-> (.init synth (clj->js {:visualObj visual
                                 :audioContext ctx
                                 :options {:program 105
                                           :chordsOff true}}))
          (.then (fn [] (.prime synth)))
          (.then (fn []
                   (let [dur (.-duration synth)]
                     (when (= gen (:generation @synth-state))
                       (swap! synth-state assoc :synth synth :duration dur))
                     {:synth synth :duration dur :generation gen})))
          (.catch (fn [e]
                    (js/console.error "Prepare failed:" e)))))))

(defn start!
  "Start a pre-primed synth. Near-zero latency."
  [& [{:keys [on-end]}]]
  (when-let [synth (:synth @synth-state)]
    (.start synth)
    (let [dur (:duration @synth-state)
          gen (:generation @synth-state)]
      (when (and on-end dur)
        (js/setTimeout
         (fn []
           (when (= gen (:generation @synth-state))
             (swap! synth-state assoc :synth nil :duration nil)
             (on-end)))
         (* (+ dur 0.5) 1000))))))

(defn play!
  "Convenience: prepare then start."
  [& [{:keys [on-end]}]]
  (when-let [p (prepare!)]
    (-> p
        (.then (fn [_] (start! {:on-end on-end})))
        (.catch (fn [e]
                  (js/console.error "Playback failed:" e))))))
