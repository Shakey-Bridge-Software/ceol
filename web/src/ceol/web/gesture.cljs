(ns ceol.web.gesture
  "Touch gesture handling for mobile. Hand-rolled left-swipe on tune rows.
   The gesture works directly on DOM elements (no per-frame app-state churn)
   and only dispatches into Replicant on touchend with a final outcome
   (:peek, :delete, or :clear)."
  (:require [ceol.web.state :as state]))

(def ^:private peek-threshold 60)
(def ^:private delete-threshold 140)

;; Mutable closure shared across listeners. Only touched here.
(defonce ^:private g (atom nil))

(defn- find-row [el]
  (loop [n el]
    (cond
      (nil? n) nil
      (and (.-classList n) (.contains (.-classList n) "tune-row")) n
      :else (recur (.-parentElement n)))))

(defn- tune-id-of [row-el]
  (some-> row-el (.getAttribute "data-tune-id") (js/parseInt 10)))

(defn- reset-transform! [row-el]
  (when row-el
    (set! (.. row-el -style -transform) "")
    (set! (.. row-el -style -transition) "transform 0.2s")
    (.remove (.-classList row-el) "swiping")
    (js/setTimeout
     (fn [] (when row-el (set! (.. row-el -style -transition) ""))) 220)))

(defn- on-touch-start [e]
  (when-let [row (find-row (.-target e))]
    (let [t (aget (.-touches e) 0)]
      (reset! g {:row row
                 :tune-id (tune-id-of row)
                 :start-x (.-clientX t)
                 :start-y (.-clientY t)
                 :locked nil}))))

(defn- on-touch-move [e]
  (when-let [{:keys [row start-x start-y locked]} @g]
    (let [t  (aget (.-touches e) 0)
          dx (- (.-clientX t) start-x)
          dy (- (.-clientY t) start-y)
          locked* (or locked
                      (cond
                        (and (> (Math/abs dx) 8) (> (Math/abs dx) (Math/abs dy))) :horizontal
                        (> (Math/abs dy) 8) :vertical
                        :else nil))]
      (swap! g assoc :locked locked* :dx dx)
      (when (= :horizontal locked*)
        (.preventDefault e)
        (.add (.-classList row) "swiping")
        (set! (.. row -style -transform)
              (str "translateX(" (min 0 dx) "px)"))))))

(defn- on-touch-end [_e]
  (when-let [{:keys [row tune-id dx locked]} @g]
    (when (= :horizontal locked)
      (let [moved (- (or dx 0))]
        (cond
          (>= moved delete-threshold)
          (do (reset-transform! row)
              (swap! state/app-state assoc :delete-confirm-tune-id tune-id))

          (>= moved peek-threshold)
          (do (set! (.. row -style -transform) "translateX(-120px)")
              (swap! state/app-state assoc :swipe-peek-tune-id tune-id))

          :else
          (do (reset-transform! row)
              (swap! state/app-state assoc :swipe-peek-tune-id nil)))))
    (reset! g nil)))

(defn- on-touch-cancel [_e]
  (when-let [{:keys [row]} @g]
    (reset-transform! row))
  (reset! g nil))

(defn attach!
  "Wire document-level touch listeners. Idempotent."
  []
  (let [doc js/document]
    (.addEventListener doc "touchstart" on-touch-start #js {:passive true})
    (.addEventListener doc "touchmove"  on-touch-move  #js {:passive false})
    (.addEventListener doc "touchend"   on-touch-end   #js {:passive true})
    (.addEventListener doc "touchcancel" on-touch-cancel #js {:passive true})))
