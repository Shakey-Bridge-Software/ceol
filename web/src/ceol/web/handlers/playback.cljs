(ns ceol.web.handlers.playback
  "Melody playback orchestration: play!, stop!, and the guitar scheduling helper.
   play! is the user play/stop toggle (branches on the synchronous :playing? flag);
   start-playback! does the actual start, honouring count-in, set auto-advance, and
   loop. Self-referential callbacks (loop restart, set advance) call start-playback!
   directly so this namespace has no dependency on core.cljs."
  (:require [ceol.web.state :as state]
            [ceol.web.abc-bridge :as abc-bridge]
            [ceol.web.guitar :as guitar]
            [ceol.web.metronome :as metro]
            [ceol.beat-engine :as beat]
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

(defn anchor-metronome!
  "Persist the melody's beat phase (the shared reference the synced metronome
   keys off) and, when the metronome flag is on, (re)start it locked to this
   start-at. Called by every play/restart path right after the melody is
   scheduled, so the metronome re-anchors across tempo/section changes and
   loop repeats. start-at is AudioContext seconds of beat 0."
  [s tune start-at]
  (let [{:keys [ms-per-beat beats-per-bar]} (beat/beats-for-tune tune (:tempo-offset s))]
    (swap! state/app-state assoc
           :melody-start-at      start-at
           :melody-ms-per-beat   ms-per-beat
           :melody-beats-per-bar beats-per-bar)
    (when (:metronome? @state/app-state)
      (metro/start-synced! {:ms-per-beat ms-per-beat :beats-per-bar beats-per-bar}
                           start-at))))

(defn stop!
  "Stop playback unconditionally."
  []
  (abc-bridge/stop!)
  (guitar/stop!)
  (metro/stop!)
  (swap! state/app-state assoc :playing? false :playing-section nil
         :set-playing? false :set-tune-index 0 :current-beat nil
         :melody-start-at nil :melody-ms-per-beat nil :melody-beats-per-bar nil))

(declare play! start-playback!)

(defn restart-if-playing!
  "If currently playing, stop and start again so a state change affecting
   playback (section, tempo, count-in) takes effect immediately. Waits for
   the in-flight sheet music render before play! so prepare! reads the
   updated visual; without this, the first restart after a section change
   would reuse the previously-rendered section."
  []
  (when (:playing? @state/app-state)
    (stop!)
    (-> (render/wait-for-render!)
        (.then (fn [_] (start-playback!))))))

(defn- start-playback!
  "Internal: start playback for the selected tune. Not a toggle — always starts.
   Handles count-in, section, loop, and set context. Called by play! (user toggle),
   set-advance on-end, and loop on-end. Guards against in-flight prepare callbacks
   after the user has stopped (press space during prepare/prime phase)."
  []
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
                   ;; Stop the synced clock at every tune-end (mirrors guitar/stop!)
                   ;; so it never free-runs on a dead grid; replay paths (loop,
                   ;; set-advance) re-anchor it via anchor-metronome!.
                   (metro/stop!)
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
                               (js/setTimeout start-playback! 500))
                           ;; Set finished: terminal, so the metronome ends too.
                           (swap! state/app-state assoc :playing? false :playing-section nil
                                  :set-playing? false :set-tune-index 0 :current-beat nil
                                  :metronome? false :melody-start-at nil
                                  :melody-ms-per-beat nil :melody-beats-per-bar nil)))
                       (if (:loop? s)
                         (do (swap! state/app-state assoc :playing? false :playing-section nil
                                    :current-beat nil)
                             (start-playback!))
                         ;; Single tune finished: terminal, metronome ends too.
                         (swap! state/app-state assoc :playing? false :playing-section nil
                                :current-beat nil :metronome? false :melody-start-at nil
                                :melody-ms-per-beat nil :melody-beats-per-bar nil)))))]
      ;; Count-in path: prepare → count-in → start
      ;; No count-in: prepare → start immediately
      ;; start-at is captured AFTER abc-bridge/start! so it reflects the
      ;; melody's actual scheduling moment, not a few ms before it. The
      ;; metronome (if on) re-anchors to that start-at via anchor-metronome!,
      ;; so it stays locked to the melody's beat grid — with count-in the
      ;; start-at reflects the post-count-in downbeat.
      ;; Guard: re-check :playing? after prepare resolves — the user may have
      ;; pressed space during the async prepare/prime phase, and we must not
      ;; start the synth in that case.
      ;; Also guard against nil prepare (no visual, e.g. ABC not yet rendered):
      ;; if prepare! returns nil, just log a warning and bail out.
      (let [start-after-prepare (fn []
                                  (when (:playing? @state/app-state)
                                    (abc-bridge/start! {:on-end on-end})
                                    (let [start-at (abc-bridge/now)]
                                      (start-guitar! s tune abc-body (:section s)
                                                     (:ms-per-bar beat-params) start-at)
                                      (anchor-metronome! s tune start-at))))
            start-after-count-in (fn []
                                   (when (:playing? @state/app-state)
                                     (metro/count-in! beat-params
                                                      (fn []
                                                        (when (:playing? @state/app-state)
                                                          (abc-bridge/start! {:on-end on-end})
                                                          (let [start-at (abc-bridge/now)]
                                                            (start-guitar! s tune abc-body (:section s)
                                                                           (:ms-per-bar beat-params) start-at)
                                                            (anchor-metronome! s tune start-at)))))))]
        (if-let [p (abc-bridge/prepare!)]
          (if (and (:count-in? s) (not set-advancing?))
            (-> p (.then (fn [_] (start-after-count-in))))
            (-> p (.then (fn [_] (start-after-prepare)))))
          (js/console.warn "start-playback!: prepare! returned nil (no visual)"))))))

(defn play!
  "Toggle play/stop. Branches on the synchronous :playing? flag (not the async
   synth presence), so the toggle works correctly during the prepare/prime phase.
   If already playing, stops. Otherwise starts playback.
   Internal re-entry paths (set-advance on-end, loop on-end) call start-playback!
   directly, so this is purely a user toggle — no regression to set auto-advance."
  []
  (if (:playing? @state/app-state)
    (do (stop!)
        ;; Terminal user stop: the metronome ends with playback (button off),
        ;; rather than sitting lit-but-silent. Re-anchor paths (loop, tempo /
        ;; section change) go through stop!/restart-if-playing!, which preserve
        ;; :metronome?, so this only affects a deliberate stop.
        (swap! state/app-state assoc :metronome? false))
    (start-playback!)))
