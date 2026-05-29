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

(declare session-play-current!)

(defn- schedule-gap!
  "Schedule the post-tune gap callback `f` after `gap-ms`, remembering its
   timeout id in :session-gap-timer so skip / pause / stop can cancel a pending
   gap (Wave 1 C). The callback clears the stored id before running."
  [f gap-ms]
  (let [id (js/setTimeout
            (fn []
              (swap! state/app-state assoc :session-gap-timer nil)
              (f))
            gap-ms)]
    (swap! state/app-state assoc :session-gap-timer id)))

(defn cancel-gap-timer!
  "Cancel any pending inter-tune gap timeout (Wave 1 C). Without this, a skip,
   pause, or stop during the 2s/500ms gap would leave the orphaned timer to fire
   and auto-play the next tune."
  []
  (when-let [t (:session-gap-timer @state/app-state)]
    (js/clearTimeout t))
  (swap! state/app-state assoc :session-gap-timer nil))

(defn- queue-item-first-tid
  "The tune id to play first for a queue item: its own id for a :tune, the first
   tune of a :set."
  [item]
  (case (:type item)
    :tune (:tune-id item)
    :set  (first (:tune-ids item))))

(defn- apply-advance!
  "Apply a state/advance-session result to app-state and schedule the next play.

   immediate? = the user Skip path: advance the index AND :selected-tune-id
   together and play at once, no gap. Otherwise the natural end-of-tune path:
   keep the 500ms within-set / 2s between-item gaps, advancing :session-index at
   once but deferring :selected-tune-id (and clearing :session-pausing?) across
   the gap — that index-early / selected-late split is the inter-tune 'pausing'
   visual. `s` is the app-state snapshot taken before advancing.

   This is the single advance state machine shared by on-end (natural finish)
   and session-skip! (user skip)."
  [s result immediate?]
  (let [play! #(-> (render/wait-for-render!) (.then session-play-current!))]
    (case (:action result)
      :advance-in-set
      (do (swap! state/app-state assoc
                 :session-set-index   (:session-set-index result)
                 :session-within-set? true
                 :selected-tune-id    (:tune-id result))
          (if immediate? (play!) (schedule-gap! play! 500)))

      :next-item
      (let [next-idx (:session-index result)
            next-tid (queue-item-first-tid (nth (:session-queue s) next-idx))]
        (swap! state/app-state assoc
               :session-index       next-idx
               :session-set-index   0
               :session-within-set? false
               :session-pausing?    (not immediate?)
               :session-played      (conj (:session-played s) (:session-index s)))
        (if immediate?
          (do (swap! state/app-state assoc :selected-tune-id next-tid) (play!))
          (schedule-gap!
           (fn []
             (swap! state/app-state assoc :selected-tune-id next-tid :session-pausing? false)
             (play!))
           2000)))

      :reshuffle
      (let [new-queue (state/shuffle-queue
                       (state/build-session-queue (:learned-tune-ids s) (:sets s)))]
        (when (seq new-queue)
          (let [first-tid (queue-item-first-tid (first new-queue))]
            (swap! state/app-state assoc
                   :session-queue       new-queue
                   :session-index       0
                   :session-set-index   0
                   :session-played      []
                   :session-pausing?    (not immediate?)
                   :session-within-set? false)
            (if immediate?
              (do (swap! state/app-state assoc :selected-tune-id first-tid) (play!))
              (schedule-gap!
               (fn []
                 (swap! state/app-state assoc :selected-tune-id first-tid :session-pausing? false)
                 (play!))
               2000)))))

      :done
      (let [now (js/Date.now)]
        (swap! state/app-state assoc
               :playing?       false
               :session-mode?  false
               :session-played (conj (:session-played s) (:session-index s))
               ;; Item #5 — stow the completion summary. Elapsed is 0 if the
               ;; start was never stamped.
               :session-result (ss/result (:session-queue s)
                                          (- now (or (:session-started-at s) now))))
        (metro/stop!)))))

(defn session-play-current!
  "Play the current item in the session queue. Handles set-within advances
   (no count-in, 500ms gap) and queue-item advances (count-in, 2s gap).
   Calls itself recursively via async callbacks.

   Bails when the session is no longer live or has been paused — a defensive
   guard against a stale gap callback firing after stop/pause (Wave 1 C)."
  []
  (when (and (:session-mode? @state/app-state)
             (not (:session-paused? @state/app-state)))
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
                          (apply-advance! s result false)))]
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
                                                                  (:ms-per-bar beat-params) start-at)))))))))))

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
      (let [first-tune-id (queue-item-first-tid (first shuffled))]
        (swap! state/app-state assoc
               :session-mode?       true
               :session-queue       shuffled
               :session-index       0
               :session-set-index   0
               :session-played      []
               :session-pausing?    false
               :session-paused?     false
               :session-within-set? false
               :selected-tune-id    first-tune-id
               :tab                 :session
               ;; Item #5 — start the clock for the duration stat.
               :session-started-at  (js/Date.now))
        ;; Wait for render before playing — tune must be rendered before abc.js can prime
        (-> (render/wait-for-render!)
            (.then #(session-play-current!)))))))

(defn session-skip!
  "User-triggered Skip (Wave 1 C): stop the current tune and advance immediately
   to the next queue position with no inter-tune gap, then play it — or finish the
   session if that was the last item. Shares the advance state machine with the
   natural finish via apply-advance! (immediate? = true)."
  []
  (when (:session-mode? @state/app-state)
    (cancel-gap-timer!)              ; drop any pending natural-gap timer first
    (abc-bridge/stop!)
    (guitar/stop!)
    (let [s      @state/app-state
          result (state/advance-session (:session-queue s)
                                        (:session-index s)
                                        (:session-set-index s)
                                        (:loop? s))]
      (swap! state/app-state assoc :session-paused? false)
      (apply-advance! s result true))))

(defn session-pause!
  "Toggle playback pause during a live session (Wave 1 C). Pausing stops audio
   (abcjs/Tone can't resume mid-tune) and flips the now-playing button to Play;
   resuming replays the current tune from the top."
  []
  (let [s @state/app-state]
    (when (:session-mode? s)
      (if (:session-paused? s)
        (do (swap! state/app-state assoc :session-paused? false)
            (-> (render/wait-for-render!)
                (.then #(session-play-current!))))
        (do (cancel-gap-timer!)      ; a pause mid-gap must kill the pending play
            (abc-bridge/stop!)
            (guitar/stop!)
            (metro/stop!)
            (swap! state/app-state assoc :session-paused? true :playing? false))))))

(defn session-stop!
  "End the live session (Wave 1 C): cancel any pending gap, stop audio, clear the
   session flags. A manual stop shows no completion summary, so :session-result
   is cleared too."
  []
  (cancel-gap-timer!)
  (playback/stop!)
  (swap! state/app-state assoc
         :session-mode?    false
         :session-pausing? false
         :session-paused?  false
         :session-result   nil))