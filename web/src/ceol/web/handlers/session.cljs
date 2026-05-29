(ns ceol.web.handlers.session
  "Session mode orchestration: session-start! and session-play-current!.
   Session mode plays through a shuffled queue of learned tunes and sets,
   with count-in between items and 500ms/2s gaps for transitions.
   Self-referential callbacks call session-play-current! directly so this
   namespace has no dependency on core.cljs."
  (:require [ceol.web.state :as state]
            [ceol.web.abc-bridge :as abc-bridge]
            [ceol.web.guitar :as guitar]
            [ceol.web.metronome :as metro]
            [ceol.web.beat-engine :as beat]
            [ceol.web.render :as render]
            [ceol.web.handlers.playback :as playback]
            [ceol.web.handlers.session-summary :as ss]))

(defn session-play-current!
  "Play the current item in the session queue. Handles set-within advances
   (no count-in, 500ms gap) and queue-item advances (count-in, 2s gap).
   Calls itself recursively via async callbacks."
  []
  (let [s           @state/app-state
        tune-id     (state/session-current-tune-id s)
        tune        (state/tune-by-id s tune-id)
        abc-body    (state/edited-abc-for-tune s tune-id)
        beat-params (beat/beats-for-tune tune (:tempo-offset s))
        within-set? (:session-within-set? s)
        on-end      (fn []
                      (guitar/stop!)
                      (let [s      @state/app-state
                            result (state/advance-session (:session-queue s)
                                                          (:session-index s)
                                                          (:session-set-index s)
                                                          (:loop? s))]
                        (case (:action result)
                          :advance-in-set
                          (do (swap! state/app-state assoc
                                     :session-set-index   (:session-set-index result)
                                     :session-within-set? true
                                     :selected-tune-id    (:tune-id result))
                              ;; 500ms gap between tunes within a set
                              (js/setTimeout
                               (fn []
                                 (-> (render/wait-for-render!)
                                     (.then #(session-play-current!))))
                               500))

                          :next-item
                          (let [next-idx  (:session-index result)
                                queue     (:session-queue s)
                                next-item (nth queue next-idx)
                                next-tid  (case (:type next-item)
                                            :tune (:tune-id next-item)
                                            :set  (first (:tune-ids next-item)))]
                            (swap! state/app-state assoc
                                   :session-index       next-idx
                                   :session-set-index   0
                                   :session-within-set? false
                                   :session-pausing?    true
                                   :session-played      (conj (:session-played s) (:session-index s)))
                            ;; 2s pause between queue items
                            (js/setTimeout
                             (fn []
                               (swap! state/app-state assoc
                                      :selected-tune-id next-tid
                                      :session-pausing? false)
                               (-> (render/wait-for-render!)
                                   (.then #(session-play-current!))))
                             2000))

                          :reshuffle
                          (let [new-queue (state/shuffle-queue
                                           (state/build-session-queue (:learned-tune-ids s) (:sets s)))]
                            (when (seq new-queue)
                              (let [first-item (first new-queue)
                                    first-tid  (case (:type first-item)
                                                 :tune (:tune-id first-item)
                                                 :set  (first (:tune-ids first-item)))]
                                (swap! state/app-state assoc
                                       :session-queue       new-queue
                                       :session-index       0
                                       :session-set-index   0
                                       :session-played      []
                                       :session-pausing?    true
                                       :session-within-set? false)
                                (js/setTimeout
                                 (fn []
                                   (swap! state/app-state assoc
                                          :selected-tune-id first-tid
                                          :session-pausing? false)
                                   (-> (render/wait-for-render!)
                                       (.then #(session-play-current!))))
                                 2000))))

                          :done
                          (let [now (js/Date.now)]
                            (swap! state/app-state assoc
                                   :playing?       false
                                   :session-mode?  false
                                   :session-played (conj (:session-played s) (:session-index s))
                                   ;; Item #5 — stow the completion summary.
                                   ;; Elapsed is 0 if the start was never stamped.
                                   :session-result (ss/result
                                                    (:session-queue s)
                                                    (- now (or (:session-started-at s) now))))
                            (metro/stop!)))))]
    ;; Set playing state and stop metronome if running
    (swap! state/app-state assoc :playing? true :selected-tune-id tune-id)
    (when (metro/running?)
      (metro/stop!)
      (swap! state/app-state assoc :metronome? false :current-beat nil))
    ;; Within-set: no count-in; new item: count-in
    (if within-set?
      (-> (abc-bridge/prepare!)
          (.then (fn [_]
                   (let [start-at (abc-bridge/now)]
                     (abc-bridge/start! {:on-end on-end})
                     (playback/start-guitar! s tune abc-body nil
                                             (:ms-per-bar beat-params) start-at)))))
      (-> (abc-bridge/prepare!)
          (.then (fn [_]
                   (metro/count-in! beat-params
                                    (fn []
                                      (let [start-at (abc-bridge/now)]
                                        (abc-bridge/start! {:on-end on-end})
                                        (playback/start-guitar! s tune abc-body nil
                                                                (:ms-per-bar beat-params) start-at))))))))))

(defn session-start!
  "Build the session queue, shuffle it, set session state, and auto-play the first item."
  []
  (let [s        @state/app-state
        queue    (state/build-session-queue (:learned-tune-ids s) (:sets s))
        shuffled (state/shuffle-queue queue)]
    ;; Item #5 — clear a prior summary on every restart attempt, even when the
    ;; queue is now empty (e.g. all tunes unlearned). Otherwise "Practice again"
    ;; would no-op while the stale summary kept showing — a dead-end.
    (swap! state/app-state assoc :session-result nil)
    (when (seq shuffled)
      (let [first-item   (first shuffled)
            first-tune-id (case (:type first-item)
                            :tune (:tune-id first-item)
                            :set  (first (:tune-ids first-item)))]
        (swap! state/app-state assoc
               :session-mode?       true
               :session-queue       shuffled
               :session-index       0
               :session-set-index   0
               :session-played      []
               :session-pausing?    false
               :session-within-set? false
               :selected-tune-id    first-tune-id
               :tab                 :session
               ;; Item #5 — start the clock for the duration stat.
               :session-started-at  (js/Date.now))
        ;; Wait for render before playing — tune must be rendered before abc.js can prime
        (-> (render/wait-for-render!)
            (.then #(session-play-current!)))))))
