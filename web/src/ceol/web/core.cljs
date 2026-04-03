(ns ceol.web.core
  (:require [replicant.dom :as r]
            [ceol.web.state :as state]
            [ceol.web.views :as views]
            [ceol.web.abc-bridge :as abc-bridge]
            [ceol.abc :as abc]
            [cljs.reader :as reader]))

(defonce el (js/document.getElementById "app"))

(defn build-full-abc
  "Build a complete ABC string from a tune and its raw ABC body."
  [tune abc-body]
  (abc/build-abc-string tune abc-body nil))

(defn execute! [_dispatch-data actions]
  (doseq [[action & args] actions]
    (case action
      :filter/set
      (let [[filter-type] args]
        (swap! state/app-state assoc :filter filter-type))

      :tab/set
      (let [[tab] args]
        (swap! state/app-state assoc :tab tab))

      :tune/select
      (let [[tune-id] args]
        (swap! state/app-state assoc :selected-tune-id tune-id))

      :abc/render
      (let [[abc-body tune] args]
        (when-let [el (js/document.getElementById "sheet-music")]
          (let [full-abc (build-full-abc tune abc-body)]
            (abc-bridge/render-abc! el full-abc))))

      :tune/add-to-set nil ;; TODO

      :playback/play nil ;; TODO
      :tempo/up nil ;; TODO
      :tempo/down nil ;; TODO

      (js/console.warn "Unknown action:" action args))))

(add-watch state/app-state ::render
           (fn [_ _ _ s]
             (r/render el (views/app s))))

(defn load-abc-data!
  "Fetch local-abc.edn and merge into app state."
  []
  (-> (js/fetch "/data/local-abc.edn")
      (.then #(.text %))
      (.then (fn [text]
               (let [data (reader/read-string text)]
                 (swap! state/app-state assoc :abc-data data))))))

(defn init! []
  (r/set-dispatch! execute!)
  (load-abc-data!)
  (r/render el (views/app @state/app-state)))
