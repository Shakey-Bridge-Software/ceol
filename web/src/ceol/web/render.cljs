(ns ceol.web.render
  "ABC sheet music rendering pipeline and Replicant state watcher.
   Owns the shared AudioContext render promise, the sheet-music DOM update,
   and the add-watch that drives Replicant re-renders on state change.
   setup-render-watch! must be called once from init! to activate the watcher."
  (:require [replicant.dom :as r]
            [ceol.web.state :as state]
            [ceol.web.views :as views]
            [ceol.web.abc-bridge :as abc-bridge]
            [ceol.abc :as abc]))

(defonce el (js/document.getElementById "app"))

;; Tracks the most recent render inputs to avoid redundant sheet music renders.
(defonce ^:private prev-render-key (atom nil))

;; Promise that resolves when the current sheet music render is complete.
;; Used by session mode to wait for render before advancing to the next tune.
(defonce render-promise (atom (js/Promise.resolve nil)))

(defn wait-for-render!
  "Returns a promise that resolves when the current sheet music render is complete."
  []
  @render-promise)

(defn render-sheet-music!
  "Imperatively render ABC notation into the #sheet-music div.
   Creates and stores a promise that resolves when rendering is complete.
   Called from the state watcher when tune, ABC body, section, or tempo changes."
  [s]
  (when-let [tune (state/selected-tune s)]
    (when-let [abc-body (state/edited-abc-for-tune s (:id tune))]
      (when (string? abc-body)
        (let [section   (:section s)
              body      (if section
                          (let [parts (abc/split-abc-body abc-body)]
                            (if parts (get parts section abc-body) abc-body))
                          abc-body)
              raw-abc   (abc/build-abc-string tune (abc/add-line-breaks body 4) nil {:midi? false})
              final-abc (abc/adjust-abc-tempo raw-abc (or (:tempo-offset s) 0))
              p         (js/Promise.
                         (fn [resolve _]
                           (js/requestAnimationFrame
                            (fn []
                              (if-let [el (js/document.getElementById "sheet-music")]
                                (let [visual (abc-bridge/render-abc! el final-abc)]
                                  (resolve visual))
                                (resolve nil))))))]
          (reset! render-promise p))))))

(defn setup-render-watch!
  "Register the state watcher that drives Replicant renders and sheet music updates.
   Idempotent — removes any existing ::render watcher before adding."
  []
  (remove-watch state/app-state ::render)
  (add-watch state/app-state ::render
             (fn [_ _ _old-s s]
               (r/render el (views/app s))
               ;; Re-render sheet music when one of the following changed; 
               ;; selected tune
               ;; ABC
               ;; tempo
               ;; tune type (reel, polka etc)
               ;; meter (9/8, 6/8 etc)
               ;; mode (G Major, A Dorian etc)
               ;; section (A, B, ALL)
               (let [tune-id  (:selected-tune-id s)
                     abc      (state/edited-abc-for-tune s tune-id)
                     new-key  [tune-id
                               abc
                               (:section s)
                               (:tempo-offset s)
                               (state/selected-tune @state/app-state)]]
                 (when (not= new-key @prev-render-key)
                   (reset! prev-render-key new-key)
                   ;; Evict stale non-string edits (e.g. from earlier bug)
                   (when (and abc (not (string? abc)))
                     (swap! state/app-state update :abc-edits dissoc tune-id))
                   (render-sheet-music! s))))))
