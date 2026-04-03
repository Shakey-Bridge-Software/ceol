(ns ceol.web.abc-bridge
  (:require ["abcjs" :as ABCJS]))

(defonce synth-state (atom {:synth nil :controller nil :visual nil}))

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
  "Stop any current playback."
  []
  (when-let [controller (:controller @synth-state)]
    (try (.pause controller) (catch :default _)))
  (when-let [synth (:synth @synth-state)]
    (try (.stop synth) (catch :default _)))
  (swap! synth-state assoc :synth nil :controller nil))

(defn play!
  "Start playback of the currently rendered ABC.
   Calls on-end when playback finishes."
  [& [{:keys [on-end]}]]
  (stop!)
  (when-let [visual (:visual @synth-state)]
    (let [synth (ABCJS/synth.CreateSynth.)]
      (-> (.init synth (clj->js {:visualObj visual}))
          (.then (fn []
                   (.prime synth)))
          (.then (fn []
                   (.start synth)
                   (swap! synth-state assoc :synth synth)
                   (when on-end
                     ;; Watch for playback end
                     (let [check-fn (fn check []
                                      (if (.-isRunning synth)
                                        (js/setTimeout check 200)
                                        (on-end)))]
                       (js/setTimeout check-fn 200)))))
          (.catch (fn [e]
                    (js/console.error "Playback failed:" e)))))))

(defn playing?
  "Is the synth currently playing?"
  []
  (when-let [synth (:synth @synth-state)]
    (.-isRunning synth)))
