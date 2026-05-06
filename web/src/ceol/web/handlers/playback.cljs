(ns ceol.web.handlers.playback
  "Melody playback orchestration: play!, stop!, and the guitar scheduling helper.
   play! handles the full play/stop toggle, count-in, set auto-advance, and loop.
   Self-referential callbacks (loop restart, set advance) call play! directly
   so this namespace has no dependency on core.cljs."
  (:require [ceol.web.state :as state]
            [ceol.web.abc-bridge :as abc-bridge]
            [ceol.web.guitar :as guitar]
            [ceol.web.metronome :as metro]
            [ceol.web.beat-engine :as beat]
            [ceol.web.render :as render]
            [ceol.abc :as abc]))

(defn start-guitar!
  "Schedule guitar accompaniment from start-at (AudioContext seconds).
   section: :a, :b, or nil for the whole tune. s: app state snapshot.
   ms-per-bar: from beat/beats-for-tune, so tempo offset is respected.
   Called by session.cljs as well as internally."
  [s tune abc-body section ms-per-bar start-at]
  (when (and tune abc-body (string? abc-body))
    (guitar/set-muted! (not (:guitar? s)))
    (let [parts      (abc/split-abc-body abc-body)
          tonic      (:key tune)
          bar-chords (if section
                       (let [part-body (if parts (get parts section abc-body) abc-body)
                             chords    (guitar/extract-bar-chords part-body)]
                         (into chords chords))
                       (if parts
                         (let [a-chords (guitar/extract-bar-chords (:a parts))
                               b-chords (guitar/extract-bar-chords (:b parts))]
                           (vec (concat a-chords a-chords b-chords b-chords)))
                         (let [chords (guitar/extract-bar-chords abc-body)]
                           (into chords chords))))
          filled     (reduce (fn [acc c] (conj acc (or c (peek acc) tonic)))
                             [] bar-chords)]
      (guitar/play! filled (:type tune) ms-per-bar start-at))))

(defn stop!
  "Stop playback unconditionally."
  []
  (abc-bridge/stop!)
  (guitar/stop!)
  (metro/stop!)
  (swap! state/app-state assoc :playing? false :playing-section nil
         :set-playing? false :set-tune-index 0 :current-beat nil))

(defn play!
  "Toggle play/stop. If already playing, stops. Otherwise starts playback
   for the selected tune, honouring count-in, section, loop, and set context."
  []
  (if (abc-bridge/playing?)
    (do (abc-bridge/stop!)
        (guitar/stop!)
        (metro/stop!)
        (swap! state/app-state assoc :playing? false :current-beat nil))
    (let [s            @state/app-state
          tune         (state/selected-tune s)
          abc-body     (state/edited-abc-for-tune s (:id tune))
          in-set?      (and (:active-set-id s) (= :sets (:tab s)))
          set-advancing? (:set-advancing? s)
          beat-params  (beat/beats-for-tune tune (:tempo-offset s))]
      (swap! state/app-state assoc :playing? true
             :playing-section (when-not in-set? (:section s))
             :set-playing?    (boolean in-set?)
             :set-tune-index  (if in-set? (or (:set-tune-index s) 0) 0)
             :set-advancing?  false)
      (let [on-end (fn []
                     (guitar/stop!)
                     (let [s @state/app-state]
                       (if (:set-playing? s)
                         (let [result (state/advance-set (:sets s) (:active-set-id s)
                                                         (:set-tune-index s) (:loop? s))]
                           (case (:action result)
                             (:play :loop)
                             (do (swap! state/app-state assoc
                                        :set-tune-index  (:index result)
                                        :selected-tune-id (:tune-id result)
                                        :set-advancing?  true)
                                 (js/setTimeout play! 500))
                             (do (metro/stop!)
                                 (swap! state/app-state assoc :playing? false :playing-section nil
                                        :set-playing? false :set-tune-index 0 :current-beat nil))))
                         (do (swap! state/app-state assoc :playing? false :playing-section nil
                                    :current-beat nil)
                             (when (:loop? s)
                               (play!))))))]
        ;; Stop standalone metronome if running
        (when (metro/running?)
          (metro/stop!)
          (swap! state/app-state assoc :metronome? false :current-beat nil))
        ;; Count-in path: prepare → count-in → start
        ;; No count-in: prepare → start immediately
        ;; start-at is captured AFTER abc-bridge/start! so it reflects the
        ;; melody's actual scheduling moment, not a few ms before it.
        (if (and (:count-in? s) (not set-advancing?))
          (-> (abc-bridge/prepare!)
              (.then (fn [_]
                       (metro/count-in! beat-params
                                        (fn []
                                          (abc-bridge/start! {:on-end on-end})
                                          (let [start-at (abc-bridge/now)]
                                            (start-guitar! s tune abc-body (:section s)
                                                           (:ms-per-bar beat-params) start-at)))))))
          (-> (abc-bridge/prepare!)
              (.then (fn [_]
                       (abc-bridge/start! {:on-end on-end})
                       (let [start-at (abc-bridge/now)]
                         (start-guitar! s tune abc-body (:section s)
                                        (:ms-per-bar beat-params) start-at))))))))))
