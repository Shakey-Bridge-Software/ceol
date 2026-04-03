(ns ceol.web.core
  (:require [replicant.dom :as r]))

(defonce store (atom {}))
(defonce el (js/document.getElementById "app"))

(defn render [state]
  [:div#app
   [:h1 "ceol"]
   [:p (str "Started: " (:app/started-at state))]])

(defn execute! [_dispatch-data actions]
  (doseq [[action & args] actions]
    (case action
      :store/assoc-in (apply swap! store assoc-in args)
      (js/console.warn "Unknown action:" action args))))

(add-watch store ::render
           (fn [_ _ _ state]
             (r/render el (render state))))

(defn init! []
  (r/set-dispatch! execute!)
  (swap! store assoc :app/started-at (js/Date.)))
