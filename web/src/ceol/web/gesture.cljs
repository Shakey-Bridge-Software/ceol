(ns ceol.web.gesture
  "Touch gesture handling for mobile. Hand-rolled swipe on tune rows.
   The gesture works directly on DOM elements (no per-frame app-state churn)
   and only commits to app-state on touchend with a final outcome.
   Left swipe past delete-threshold → delete-confirm modal. Right swipe past
   learn-threshold → toggle learned. The intermediate peek (Edit/Delete
   reveal) was removed (G8 decision) — per-tune actions live in the bottom
   action sheet now."
  (:require [ceol.web.state :as state]
            [ceol.web.persist :as persist]))

(def ^:private delete-threshold 140)
(def ^:private learn-threshold 120)

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
    (when-let [wrap (.-parentElement row-el)]
      (let [wcl (.-classList wrap)]
        (.remove wcl "swipe-left")
        (.remove wcl "swipe-right")))
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
        (when-let [wrap (.-parentElement row)]
          (let [wcl (.-classList wrap)]
            (if (pos? dx)
              (do (.add wcl "swipe-right") (.remove wcl "swipe-left"))
              (do (.add wcl "swipe-left")  (.remove wcl "swipe-right")))))
        (set! (.. row -style -transform)
              (str "translateX(" dx "px)"))))))

(defn- on-touch-end [_e]
  (when-let [{:keys [row tune-id dx locked]} @g]
    (when (= :horizontal locked)
      (let [d (or dx 0)
            left (- d)]
        (cond
          ;; Left swipe past delete threshold → delete-confirm modal
          (>= left delete-threshold)
          (do (reset-transform! row)
              (swap! state/app-state assoc :delete-confirm-tune-id tune-id))

          ;; Right swipe past learn threshold → toggle learned and snap back
          (>= d learn-threshold)
          (do (reset-transform! row)
              (swap! state/app-state update :learned-tune-ids
                     (fn [ids] (if (contains? ids tune-id)
                                 (disj ids tune-id)
                                 (conj ids tune-id))))
              (persist/save-learned!))

          :else
          (reset-transform! row))))
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
