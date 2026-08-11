(ns ceol.web.abc-bridge
  "abc.js interop: SVG sheet music rendering and Web Audio synth playback.
   Owns the shared AudioContext (get-audio-context) used by both abc.js and
   the guitar/metronome Tone.js nodes. Public API: render-abc!, prepare!,
   start!, play!, stop!, playing?, pickup-offset-s, now."
  (:require ["abcjs" :as ABCJS]
            [ceol.abc :as abc]))

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
          cw       (if container (.-clientWidth container) 880)
          narrow?  (< cw 560)
          ;; .sheet-area padding: 16px each side on mobile, 40px on desktop
          pad      (if narrow? 24 80)
          staff-w  (min 950 (max 240 (- cw pad)))
          visual (ABCJS/renderAbc element abc-str
                                  (clj->js (merge {:staffwidth staff-w
                                                   :scale (if narrow? 0.85 1.1)
                                                   :responsive "resize"
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

(defn pickup-offset-s
  "Pickup (anacrusis) duration in AudioContext seconds, or 0 if the tune/section
   starts on a full bar. When abc-body is provided, delegates to the pure parser
   in ceol.abc (preferred — works for any section body, not just the tune's
   first bar). Otherwise falls back to the abcjs visual object stored after the
   last render-abc! call (whole-tune only). ms-per-bar comes from
   beat/beats-for-tune."
  [ms-per-bar & [abc-body]]
  (if abc-body
    (abc/compute-pickup-offset-s abc-body ms-per-bar)
    (if-let [visual (:visual @synth-state)]
      (let [pickup (.getPickupLength ^js visual)
            bar-len (.getBarLength ^js visual)]
        (if (and (pos? pickup) (pos? bar-len))
          (* (/ pickup bar-len) (/ ms-per-bar 1000.0))
          0.0))
      0.0)))

(defn now
  "Returns the current AudioContext time in seconds."
  []
  (.-currentTime (get-audio-context)))

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
