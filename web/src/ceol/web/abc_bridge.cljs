(ns ceol.web.abc-bridge
  (:require ["abcjs" :as ABCJS]))

(defonce synth-state (atom {:synth nil :visual nil :generation 0}))

(defn render-abc!
  "Render ABC string into a DOM element as SVG.
   Stores the visual object for synth use."
  [element abc-str & [opts]]
  (when (and element abc-str)
    (set! (.-innerHTML element) "")
    (js/requestAnimationFrame
     (fn []
       (let [container (js/document.querySelector ".sheet-area")
             available (if container (- (.-clientWidth container) 80) 800)
             staff-w (min 950 (max 400 available))
             visual (ABCJS/renderAbc element abc-str
                                     (clj->js (merge {:staffwidth staff-w
                                                      :scale 1.1
                                                      :add_classes true}
                                                     opts)))]
         (swap! synth-state assoc :visual (first visual)))))))

(defn stop!
  "Stop any current playback. Returns the generation that was stopped."
  []
  (let [gen (:generation (swap! synth-state update :generation inc))]
    (when-let [synth (:synth @synth-state)]
      (try (.stop synth) (catch :default _)))
    (swap! synth-state assoc :synth nil)
    gen))

(defn playing?
  "Is the synth currently playing?"
  []
  (boolean (:synth @synth-state)))

(defn play!
  "Start playback of the currently rendered ABC.
   Calls on-end when playback finishes naturally (not when stopped)."
  [& [{:keys [on-end]}]]
  (stop!)
  (when-let [visual (:visual @synth-state)]
    (let [gen (:generation @synth-state)
          synth (ABCJS/synth.CreateSynth.)
          ctx (js/AudioContext.)]
      (-> (.init synth (clj->js {:visualObj visual
                                 :audioContext ctx}))
          (.then (fn [] (.prime synth)))
          (.then (fn []
                   (.start synth)
                   (swap! synth-state assoc :synth synth)
                   ;; Use AudioContext state + duration to detect end
                   (when on-end
                     (when-let [duration (.-duration synth)]
                       (let [check-ms (* (+ duration 0.5) 1000)]
                         (js/setTimeout
                          (fn []
                            ;; Only fire if same generation (not stopped by user)
                            (when (= gen (:generation @synth-state))
                              (swap! synth-state assoc :synth nil)
                              (on-end)))
                          check-ms))))))
          (.catch (fn [e]
                    (js/console.error "Playback failed:" e)))))))
