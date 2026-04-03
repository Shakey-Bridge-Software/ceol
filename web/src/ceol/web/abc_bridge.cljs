(ns ceol.web.abc-bridge
  (:require ["abcjs" :as ABCJS]))

(defn render-abc!
  "Render ABC string into a DOM element as SVG.
   Returns the visual object (needed for synth/cursor later)."
  [element abc-str & [opts]]
  (when (and element abc-str)
    (set! (.-innerHTML element) "")
    (js/requestAnimationFrame
     (fn []
       (let [container (js/document.querySelector ".sheet-area")
             available (if container (- (.-clientWidth container) 80) 800)
             staff-w (min 950 (max 400 available))]
         (ABCJS/renderAbc element abc-str
                          (clj->js (merge {:staffwidth staff-w
                                           :scale 1.1
                                           :add_classes true}
                                          opts))))))))
