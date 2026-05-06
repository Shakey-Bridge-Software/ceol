(ns ceol.state
  "TUI state management: initialisation, update-state dispatch, navigation,
   spinner, staff display, and audio pipeline (play/stop/prepare/advance).
   All state transitions return [new-state cmd-or-nil] per the charm.clj
   Elm architecture. Side-effectful audio operations are in audio.clj."
  (:require [charm.core :as charm]
            [charm.components.spinner :as spinner]
            [ceol.tunes :as tunes]
            [ceol.data :as data]
            [ceol.audio :as audio]
            [ceol.abc :as abc]
            [ceol.notation :as notation]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [babashka.process :as proc]))

(def tune-types [:all :polka :jig :reel :hornpipe :slip-jig :slide :other])

(defn- next-filter [current]
  (let [idx (.indexOf tune-types current)
        next-idx (mod (inc idx) (count tune-types))]
    (nth tune-types next-idx)))

(defn flash [state msg]
  (assoc state :flash {:msg msg :type :info}))

(defn flash-error [state msg]
  (assoc state :flash {:msg msg :type :error}))

(defn clear-flash [state]
  (assoc state :flash nil))

(defn- check-dep [cmd]
  (try
    (let [result @(proc/process {:cmd ["which" cmd] :out :string :err :string})]
      (zero? (:exit result)))
    (catch Exception _ false)))

(defn- safe-parse-abc
  "Parse ABC, returning nil on any parse error. Used for cached/edited ABC
   that may be malformed — staff display tolerates absence."
  [abc-str]
  (try (notation/parse-abc abc-str)
       (catch Exception _ nil)))

;; ---------------------------------------------------------------------------
;; TUI state shape
;;
;; Navigation
;;   :cursor           int               index into (visible-tunes state)
;;   :mode             :browse | :help
;;   :filter           :all | :polka | :jig | :reel | :hornpipe | :slip-jig | :slide | :other
;;   :width :height    int               terminal dimensions from :window-size msgs
;;   :flash            {:msg str :type :info | :error} | nil  status bar message
;;
;; Tune data
;;   :tunes            [tune-map ...]    full catalog, hydrated from cache
;;   :setlists         {slug → setlist}  loaded from ~/.ceol/setlists/
;;   :active-setlist   slug | nil        currently active setlist filter
;;
;; Playback
;;   :playing          tune-id | nil     currently playing tune
;;   :play-proc        Process | nil     live fluidsynth process
;;   :loading          tune-id | nil     tune being fetched/converted (shows spinner)
;;   :spinner          spinner-state     charm spinner; nil when not loading
;;   :loop             bool
;;   :tempo-offset     int               BPM delta, clamped [-40, +40]
;;   :section          :a | :b | nil
;;
;; Count-in
;;   :count-in         bool              count-in enabled
;;   :counting-in      bool              count-in click currently playing
;;   :countin-proc     Process | nil     live fluidsynth process for count-in click
;;   :pending-midi-path str | nil        MIDI path to play after count-in finishes
;;
;; Set queue
;;   :set-queue        {:tune-ids [...] :index int :set-name str} | nil
;;                                       active set playback queue; nil when not in set mode
;;
;; Staff notation
;;   :show-staff       bool              terminal staff panel visible
;;   :notation         [event ...]       parsed timeline from notation/parse-abc
;;   :notation-tune-id tune-id | nil     tune whose notation is currently rendered
;;   :play-start-ms    long | nil        System/currentTimeMillis when playback started
;;   :current-note-idx int | nil         index of currently highlighted note in staff
;; ---------------------------------------------------------------------------
;;
;; File sections
;;   init-state                          lines ~80-120
;;   Helpers (flash, spinner, cursor)    lines ~120-200
;;   Audio pipeline                      lines ~200-390
;;     start-playback, start-tune-pipeline, prepare-tune,
;;     play-or-stop, stop-playback!, reconvert-current,
;;     play-tune-by-id, advance-set-queue
;;   Staff helpers                       lines ~390-410
;;   update-state dispatch               lines ~410-end
;;
;; Note: pipeline helpers (start-spinner, update-tune, flash, apply-local-abc,
;; clear-countin-state) are shared between the audio pipeline and update-state,
;; which prevents clean extraction of the pipeline into a separate namespace
;; without introducing a third shared-helpers namespace.
;; ---------------------------------------------------------------------------

(defn init-state []
  (data/ensure-dirs!)
  (let [hydrated (data/hydrate-tunes tunes/catalog)
        setlists (data/load-setlists)
        state {:cursor           0
               :mode             :browse
               :filter           :all
               :width            80
               :height           24
               :flash            nil
               :tunes            hydrated
               :playing          nil
               :play-proc        nil
               :loading          nil
               :spinner          nil
               :loop             false
               :tempo-offset     0
               :section          nil
               :show-staff       false
               :notation         nil
               :notation-tune-id nil
               :play-start-ms    nil
               :current-note-idx nil
               :count-in         false
               :counting-in      false
               :countin-proc     nil
               :pending-midi-path nil
               :setlists         setlists
               :active-setlist   nil
               :set-queue        nil}
        missing (filterv (complement check-dep) ["abc2midi" "fluidsynth"])
        state (if (seq missing)
                (flash state (str "missing: " (str/join ", " missing)))
                state)
        state (if (nil? (data/soundfont-path))
                (flash state "no soundfont found — playback won't work")
                state)]
    state))

;; =============================================================================
;; Helpers: flash messages, cursor, spinner, tune mutation, local ABC
;; =============================================================================

(defn visible-tunes [state]
  (if-let [slug (:active-setlist state)]
    (let [setlist (get (:setlists state) slug)]
      (tunes/resolve-setlist setlist (:tunes state)))
    (tunes/tunes-by-type (:tunes state) (:filter state))))

(defn selected-tune [state]
  (get (visible-tunes state) (:cursor state)))

(defn- delete-midi-variants!
  "Delete all MIDI files for a tune (base + section/tempo variants)."
  [tune-id]
  (let [dir (io/file data/midi-dir)
        prefix (str tune-id)]
    (when (.exists dir)
      (doseq [f (.listFiles dir)]
        (let [n (.getName f)]
          (when (and (str/ends-with? n ".mid")
                     (or (= n (str prefix ".mid"))
                         (str/starts-with? n (str prefix "_"))))
            (.delete f)))))))

(defn apply-local-abc
  "If tune has a local ABC entry, build the full ABC string and mark ready.
   Resets MIDI status when ABC has changed so stale MIDI is reconverted.
   Also deletes stale MIDI files from disk to prevent cache.edn hydration issues."
  [state tune]
  (let [local-abc (data/load-local-abc)
        body (get local-abc (:id tune))]
    (if body
      (let [abc (abc/build-abc-string tune body nil)
            changed? (not= abc (:abc tune))
            _ (when changed? (delete-midi-variants! (:id tune)))
            updates (cond-> {:abc abc :abc-status :ready :local-abc? true}
                      changed? (merge {:midi-status :none :midi-path nil}))
            tune' (merge tune updates)
            state' (update state :tunes
                           (fn [tunes]
                             (mapv #(if (= (:id tune) (:id %)) tune' %) tunes)))]
        [state' tune'])
      [state tune])))

(defn clamp-cursor [state]
  (let [items (visible-tunes state)
        max-idx (max 0 (dec (count items)))]
    (update state :cursor #(min (max 0 %) max-idx))))

(defn update-tune
  "Update a tune in the tunes vec by id."
  [state tune-id f & args]
  (update state :tunes
          (fn [tunes]
            (mapv (fn [t]
                    (if (= tune-id (:id t))
                      (apply f t args)
                      t))
                  tunes))))

;; =============================================================================
;; Navigation
;; =============================================================================

(defn cursor-up [state]
  (update state :cursor #(max 0 (dec %))))

(defn cursor-down [state]
  (let [max-idx (max 0 (dec (count (visible-tunes state))))]
    (update state :cursor #(min max-idx (inc %)))))

;; =============================================================================
;; Spinner
;; =============================================================================

(defn start-spinner [state]
  (let [[s cmd] (spinner/spinner-init (spinner/spinner :dots :id :ceol-spinner))]
    [(assoc state :spinner s) cmd]))

(defn stop-spinner [state]
  (assoc state :spinner nil))

;; =============================================================================
;; Audio pipeline
;;
;; Fetch → convert → play flow. start-tune-pipeline is the shared cond that
;; drives prepare-tune, play-or-stop, and play-tune-by-id.
;; =============================================================================

(defn- clear-countin-state [state]
  (assoc state :counting-in false :countin-proc nil :pending-midi-path nil))

(defn- start-playback
  "Play a tune MIDI, optionally preceded by a count-in click.
   If count-in is enabled, plays the count-in first and defers the tune."
  [state tune-id midi-path]
  (if (:count-in state)
    (let [tune    (tunes/tune-by-id (:tunes state) tune-id)
          tempo-q (-> (abc/tempo-for-type (:type tune) (:time-sig tune))
                      (abc/adjust-abc-tempo (or (:tempo-offset state) 0)))]
      [(assoc state :playing tune-id :counting-in true :pending-midi-path midi-path)
       (audio/countin-convert-and-play-cmd (:time-sig tune) tempo-q)])
    [(assoc state :playing tune-id)
     (audio/play-cmd midi-path)]))

(defn- start-tune-pipeline
  "Given a tune (with local-abc already applied), start the pipeline needed
   to get it playing: MIDI variant exists → play; ABC ready → convert;
   nothing → fetch. Returns [state cmd]."
  [state tune]
  (let [tune-id      (:id tune)
        tempo-offset (:tempo-offset state)
        section      (:section state)
        loop?        (:loop state)
        loop-count   (if loop? 50 1)]
    (cond
      ;; MIDI variant on disk → play immediately
      (= :ready (:midi-status tune))
      (let [target-path (data/midi-file-path-for tune-id tempo-offset section :loop? loop?)]
        (if (.exists (io/file target-path))
          (start-playback state tune-id target-path)
          (if (:abc tune)
            (let [[s sc] (start-spinner (assoc state :loading tune-id))
                  s'     (update-tune s tune-id assoc :midi-status :converting)]
              [s' (charm/batch sc (audio/convert-midi-cmd tune (:abc tune) tempo-offset section :loop-count loop-count))])
            [(flash-error state "no ABC available") nil])))

      ;; ABC ready, no MIDI → convert then play
      (= :ready (:abc-status tune))
      (let [[s sc] (start-spinner (assoc state :loading tune-id))
            s'     (update-tune s tune-id assoc :midi-status :converting)]
        [s' (charm/batch sc (audio/convert-midi-cmd tune (:abc tune) tempo-offset section :loop-count loop-count))])

      ;; Nothing → fetch → convert → play
      :else
      (let [[s sc] (start-spinner (assoc state :loading tune-id))
            s'     (update-tune s tune-id assoc :abc-status :fetching)]
        [s' (charm/batch sc (audio/fetch-abc-cmd tune))]))))

(defn prepare-tune
  "Start the fetch-convert pipeline for a tune."
  [state]
  (if-let [tune (selected-tune state)]
    (let [[state tune] (apply-local-abc state tune)
          tune-id (:id tune)
          tempo-offset (:tempo-offset state)
          section (:section state)
          loop? (:loop state)
          target-path (data/midi-file-path-for tune-id tempo-offset section :loop? loop?)]
      (cond
        ;; Already ready for current settings
        (and (= :ready (:midi-status tune))
             (.exists (io/file target-path)))
        [(flash state "already prepared") nil]

        ;; Already loading
        (= (:loading state) tune-id)
        [(flash state "already loading") nil]

        ;; ABC ready, just need MIDI
        (= :ready (:abc-status tune))
        (let [[state' spinner-cmd] (start-spinner (assoc state :loading tune-id))
              state'' (update-tune state' tune-id assoc :midi-status :converting)]
          [state'' (charm/batch spinner-cmd (audio/convert-midi-cmd tune (:abc tune) tempo-offset section :loop-count (if (:loop state) 50 1)))])

        ;; Need to fetch ABC first
        :else
        (let [[state' spinner-cmd] (start-spinner (assoc state :loading tune-id))
              state'' (update-tune state' tune-id assoc :abc-status :fetching)]
          [state'' (charm/batch spinner-cmd (audio/fetch-abc-cmd tune))])))
    [state nil]))

(defn play-or-stop
  "Play selected tune, or stop if already playing."
  [state]
  (if-let [tune (selected-tune state)]
    (let [[state tune] (apply-local-abc state tune)
          tune-id (:id tune)
          tempo-offset (:tempo-offset state)
          section (:section state)
          loop? (:loop state)]
      (cond
        ;; Playing same tune -> stop
        (= (:playing state) tune-id)
        (do
          (audio/stop-playback! (:play-proc state))
          (audio/stop-playback! (:countin-proc state))
          [(-> state
               (assoc :playing nil :play-proc nil
                      :notation-tune-id nil
                      :play-start-ms nil :current-note-idx nil
                      :set-queue nil)
               (clear-countin-state)) nil])

        ;; Playing different tune -> stop old, start new
        (:playing state)
        (do
          (audio/stop-playback! (:play-proc state))
          (audio/stop-playback! (:countin-proc state))
          (let [state' (-> state
                           (assoc :playing nil :play-proc nil :set-queue nil)
                           (clear-countin-state))]
            (play-or-stop state')))

        ;; MIDI ready, ABC ready, or nothing → delegate to pipeline
        :else
        (start-tune-pipeline state tune)))
    [state nil]))

(defn stop-playback! [state]
  (audio/stop-playback! (:play-proc state))
  (audio/stop-playback! (:countin-proc state))
  [(-> state
       (assoc :playing nil :play-proc nil :loading nil
              :notation-tune-id nil
              :play-start-ms nil :current-note-idx nil
              :set-queue nil)
       (clear-countin-state)) nil])

(defn reconvert-current
  "Stop playback, reconvert with current tempo/section, auto-play."
  [state]
  (let [tune-id (:playing state)
        tune (when tune-id (tunes/tune-by-id (:tunes state) tune-id))
        tempo-offset (:tempo-offset state)
        section (:section state)
        loop? (:loop state)]
    (if (and tune (:abc tune))
      (let [midi-path (data/midi-file-path-for tune-id tempo-offset section :loop? loop?)]
        (audio/stop-playback! (:play-proc state))
        (audio/stop-playback! (:countin-proc state))
        (let [state (clear-countin-state state)]
          (if (.exists (io/file midi-path))
            ;; Already have this variant cached — play directly (no count-in on reconvert)
            [(assoc state :play-proc nil :playing tune-id
                    :play-start-ms nil :current-note-idx nil)
             (audio/play-cmd midi-path)]
            ;; Need to convert — clear :playing, set :loading for auto-play
            (let [state' (assoc state :playing nil :play-proc nil :loading tune-id
                                :play-start-ms nil :current-note-idx nil)
                  [s sc] (start-spinner state')
                  s' (update-tune s tune-id assoc :midi-status :converting)]
              [s' (charm/batch sc (audio/convert-midi-cmd tune (:abc tune) tempo-offset section :loop-count (if (:loop state) 50 1)))]))))
      [state nil])))

;; =============================================================================
;; Set queue helpers
;; =============================================================================

(defn play-tune-by-id
  "Look up a tune by id and start the play pipeline."
  [state tune-id]
  (let [tune        (tunes/tune-by-id (:tunes state) tune-id)
        [state tune] (apply-local-abc state tune)]
    (if (nil? tune)
      [(flash-error state "tune not found") nil]
      (start-tune-pipeline state tune))))

(defn- advance-set-queue
  "Advance to the next tune in set-queue. Returns [state cmd] or nil if no queue/past end."
  [state]
  (when-let [sq (:set-queue state)]
    (let [next-idx (inc (:index sq))
          tune-ids (:tune-ids sq)]
      (cond
        (< next-idx (count tune-ids))
        (let [next-id (nth tune-ids next-idx)
              state' (assoc state :set-queue (assoc sq :index next-idx)
                            :playing next-id)]
          (play-tune-by-id state' next-id))

        (:loop state)
        (let [first-id (first tune-ids)
              state' (assoc state :set-queue (assoc sq :index 0)
                            :playing first-id)]
          (play-tune-by-id state' first-id))

        :else
        [(assoc state :set-queue nil :playing nil :play-proc nil
                :notation nil :notation-tune-id nil
                :play-start-ms nil :current-note-idx nil) nil]))))

;; =============================================================================
;; Staff helpers
;; =============================================================================

(defn- update-staff-for-selected
  "When staff is visible and not playing, update notation for the selected tune.
   Skips re-parsing when the selected tune hasn't changed."
  [state]
  (if (and (:show-staff state) (not (:playing state)))
    (let [tune (selected-tune state)
          tune-id (:id tune)]
      (if (= tune-id (:notation-tune-id state))
        state
        (let [parsed (when (and tune (:abc tune))
                       (safe-parse-abc (:abc tune)))]
          (if parsed
            (assoc state :notation (:timeline parsed) :current-note-idx nil
                   :notation-tune-id tune-id)
            (assoc state :notation nil :current-note-idx nil
                   :notation-tune-id nil)))))
    state))

;; =============================================================================
;; Main update-state dispatch
;;
;; Handles all message types from charm/run. Pure: takes [state msg], returns
;; [new-state cmd-or-nil]. Audio side-effects dispatched via audio.clj cmds.
;; =============================================================================

(defn update-state [state msg]
  (cond
    ;; Quit
    (or (charm/quit? msg)
        (and (= :browse (:mode state))
             (charm/key-match? msg "q")))
    (do
      (audio/stop-playback! (:play-proc state))
      (audio/stop-playback! (:countin-proc state))
      [state charm/quit-cmd])

    ;; Window size
    (= :window-size (:type msg))
    [(assoc state :width (:width msg) :height (:height msg)) nil]

    ;; Spinner ticks
    (and (:spinner state) (spinner/tick-msg? msg))
    (let [[new-spinner cmd] (spinner/spinner-update (:spinner state) msg)]
      [(assoc state :spinner new-spinner) cmd])

    ;; --- Audio messages ---

    (= :abc-fetched (:type msg))
    (let [tune-id (:tune-id msg)
          abc (:abc msg)
          session-id (:session-id msg)
          tempo-offset (:tempo-offset state)
          section (:section state)
          state' (-> state
                     (update-tune tune-id assoc
                                  :abc abc
                                  :abc-status :ready
                                  :session-id session-id))
          ;; Auto-chain: convert to MIDI
          updated-tune (tunes/tune-by-id (:tunes state') tune-id)]
      [(update-tune state' tune-id assoc :midi-status :converting)
       (audio/convert-midi-cmd updated-tune abc tempo-offset section :loop-count (if (:loop state) 50 1))])

    (= :abc-failed (:type msg))
    (let [tune-id (:tune-id msg)]
      [(-> state
           (update-tune tune-id assoc :abc-status :failed)
           (assoc :loading nil)
           (stop-spinner)
           (flash (str "fetch failed: " (:error msg))))
       nil])

    (= :midi-ready (:type msg))
    (let [tune-id (:tune-id msg)
          midi-path (:midi-path msg)
          was-loading? (= (:loading state) tune-id)
          wants-play? (or (= (:playing state) tune-id) was-loading?)
          state' (-> state
                     (update-tune tune-id assoc
                                  :midi-path midi-path
                                  :midi-status :ready)
                     (assoc :loading nil)
                     (stop-spinner))]
      ;; If we were triggered by play-or-stop, auto-play now
      (if (and wants-play? (not (:playing state')))
        (start-playback state' tune-id midi-path)
        [(flash state' "prepared") nil]))

    (= :midi-failed (:type msg))
    (let [tune-id (:tune-id msg)]
      [(-> state
           (update-tune tune-id assoc :midi-status :failed)
           (assoc :loading nil)
           (stop-spinner)
           (flash (str "MIDI convert failed: " (:error msg))))
       nil])

    ;; --- Count-in messages ---

    (= :countin-started (:type msg))
    (let [proc (:proc msg)]
      [(assoc state :countin-proc proc)
       (audio/watch-countin-cmd proc)])

    (= :countin-finished (:type msg))
    (if (= (:proc msg) (:countin-proc state))
      ;; Count-in done — play the pending tune
      (let [midi-path (:pending-midi-path state)
            state' (clear-countin-state state)]
        (if midi-path
          [state' (audio/play-cmd midi-path)]
          [state' nil]))
      ;; Stale count-in finished — ignore
      [state nil])

    (= :countin-failed (:type msg))
    ;; Fallback: play tune directly without count-in
    (let [midi-path (:pending-midi-path state)
          state' (clear-countin-state state)]
      (if midi-path
        [state' (audio/play-cmd midi-path)]
        [(flash state' (str "count-in failed: " (:error msg))) nil]))

    (= :playback-started (:type msg))
    (let [proc (:proc msg)
          tune-id (:playing state)
          tune (when tune-id (tunes/tune-by-id (:tunes state) tune-id))
          ;; Parse notation for staff display if we have ABC
          parsed (when (and (:show-staff state) tune (:abc tune))
                   (safe-parse-abc (:abc tune)))
          state' (cond-> (assoc state :play-proc proc)
                   parsed (assoc :notation (:timeline parsed)
                                 :play-start-ms (System/currentTimeMillis)
                                 :current-note-idx 0))
          ;; Start ticker if staff is showing
          tick-cmd (when (and (:show-staff state) parsed)
                     (audio/playback-tick-cmd))]
      [state' (charm/batch (when tune-id (audio/watch-playback-cmd proc tune-id))
                           tick-cmd)])

    (= :playback-tick (:type msg))
    (if (and (:playing state) (:show-staff state) (:notation state) (:play-start-ms state))
      (let [elapsed (- (System/currentTimeMillis) (:play-start-ms state))
            idx (notation/find-current-note (:notation state) elapsed)]
        [(assoc state :current-note-idx idx) (audio/playback-tick-cmd)])
      ;; Not playing or staff hidden — stop ticking
      [state nil])

    (= :playback-finished (:type msg))
    (if (= (:proc msg) (:play-proc state))
      ;; Current playback finished
      (if-let [sq-result (and (:set-queue state) (advance-set-queue (assoc state :play-proc nil)))]
        ;; Set queue active — advance to next tune
        sq-result
        ;; No set queue — two-layer looping:
        ;; 1) 50x baked-in ABC body repeats for gapless looping within a run
        ;; 2) This restart-on-finish as fallback when the 50x body ends
        (if (:loop state)
          (let [tune-id (:playing state)
                tune (when tune-id (tunes/tune-by-id (:tunes state) tune-id))]
            (if (and tune (:midi-path tune))
              [(assoc state :play-proc nil :play-start-ms (System/currentTimeMillis))
               (charm/batch (audio/play-cmd (:midi-path tune)) (audio/playback-tick-cmd))]
              [(assoc state :playing nil :play-proc nil
                      :notation nil :notation-tune-id nil
                      :play-start-ms nil :current-note-idx nil) nil]))
          [(assoc state :playing nil :play-proc nil
                  :notation nil :notation-tune-id nil
                  :play-start-ms nil :current-note-idx nil :set-queue nil) nil]))
      ;; Stale playback finished (old process) — ignore
      [state nil])

    (= :playback-failed (:type msg))
    [(-> state
         (assoc :playing nil :play-proc nil)
         (flash (str "playback failed: " (:error msg))))
     nil]

    ;; --- Help mode ---

    (= :help (:mode state))
    (cond
      (or (charm/key-match? msg "?")
          (charm/key-match? msg "escape"))
      [(assoc state :mode :browse) nil]

      :else [state nil])

    ;; --- Browse mode keys ---

    :else
    (let [state (clear-flash state)]
      (cond
        (or (charm/key-match? msg "j")
            (charm/key-match? msg :down))
        [(update-staff-for-selected (cursor-down state)) nil]

        (or (charm/key-match? msg "k")
            (charm/key-match? msg :up))
        [(update-staff-for-selected (cursor-up state)) nil]

        (or (charm/key-match? msg "enter")
            (charm/key-match? msg " "))
        (play-or-stop state)

        (charm/key-match? msg "s")
        (stop-playback! state)

        (charm/key-match? msg "p")
        (prepare-tune state)

        (charm/key-match? msg "f")
        (if (:active-setlist state)
          [(flash state "filter disabled in setlist mode") nil]
          [(-> state
               (update :filter next-filter)
               (assoc :cursor 0)
               (clamp-cursor))
           nil])

        ;; Loop toggle
        (charm/key-match? msg "l")
        (let [new-loop (not (:loop state))
              state' (flash (assoc state :loop new-loop)
                            (if new-loop "loop on" "loop off"))]
          (if (:playing state)
            (reconvert-current state')
            [state' nil]))

        ;; Tempo +5
        (charm/key-match? msg "=")
        (let [new-offset (min 40 (+ (:tempo-offset state) 5))
              state' (flash (assoc state :tempo-offset new-offset)
                            (if (zero? new-offset)
                              "tempo default"
                              (str (when (pos? new-offset) "+") new-offset " BPM")))]
          (if (:playing state)
            (reconvert-current state')
            [state' nil]))

        ;; Tempo -5
        (charm/key-match? msg "-")
        (let [new-offset (max -40 (- (:tempo-offset state) 5))
              state' (flash (assoc state :tempo-offset new-offset)
                            (if (zero? new-offset)
                              "tempo default"
                              (str (when (pos? new-offset) "+") new-offset " BPM")))]
          (if (:playing state)
            (reconvert-current state')
            [state' nil]))

        ;; Tempo reset
        (charm/key-match? msg "0")
        (let [state' (flash (assoc state :tempo-offset 0) "tempo reset")]
          (if (:playing state)
            (reconvert-current state')
            [state' nil]))

        ;; Section A toggle
        (charm/key-match? msg "1")
        (let [new-section (if (= :a (:section state)) nil :a)
              label (if new-section "section A" "whole tune")
              state' (flash (assoc state :section new-section) label)]
          (if (:playing state)
            (reconvert-current state')
            [state' nil]))

        ;; Section B toggle
        (charm/key-match? msg "2")
        (let [new-section (if (= :b (:section state)) nil :b)
              label (if new-section "section B" "whole tune")
              state' (flash (assoc state :section new-section) label)]
          (if (:playing state)
            (reconvert-current state')
            [state' nil]))

        ;; Staff notation toggle
        (charm/key-match? msg "m")
        (let [new-show (not (:show-staff state))
              tune (if (:playing state)
                     (tunes/tune-by-id (:tunes state) (:playing state))
                     (selected-tune state))
              parsed (when (and new-show tune (:abc tune))
                       (safe-parse-abc (:abc tune)))
              state' (cond-> (assoc state :show-staff new-show)
                       ;; Toggling ON while playing — start tracking
                       (and new-show parsed (:playing state))
                       (assoc :notation (:timeline parsed)
                              :play-start-ms (System/currentTimeMillis)
                              :current-note-idx 0)
                       ;; Toggling ON while not playing — static display
                       (and new-show parsed (not (:playing state)))
                       (assoc :notation (:timeline parsed)
                              :current-note-idx nil)
                       ;; Toggling OFF
                       (not new-show)
                       (assoc :notation nil :notation-tune-id nil
                              :current-note-idx nil :play-start-ms nil))
              cmd (when (and new-show parsed (:playing state))
                    (audio/playback-tick-cmd))]
          [(flash state' (if new-show "staff on" "staff off")) cmd])

        ;; Count-in toggle
        (charm/key-match? msg "c")
        (let [new-ci (not (:count-in state))]
          [(flash (assoc state :count-in new-ci)
                  (if new-ci "count-in on" "count-in off")) nil])

        ;; Cycle setlist
        (charm/key-match? msg "S")
        (let [slugs (vec (sort (keys (:setlists state))))
              current (:active-setlist state)
              next-slug (cond
                          (empty? slugs) nil
                          (nil? current) (first slugs)
                          :else (let [idx (.indexOf slugs current)
                                      next-idx (inc idx)]
                                  (when (< next-idx (count slugs))
                                    (nth slugs next-idx))))
              label (if next-slug
                      (:name (get (:setlists state) next-slug) next-slug)
                      "all tunes")]
          [(-> state
               (assoc :active-setlist next-slug :cursor 0 :filter :all)
               (flash label))
           nil])

        ;; Play full set from cursor
        (charm/key-match? msg "g")
        (if-let [slug (:active-setlist state)]
          (let [tune (selected-tune state)
                setlist (get (:setlists state) slug)]
            (if-let [s (tunes/set-for-tune setlist (:id tune))]
              (let [tune-ids (:tune-ids s)
                    pos (or (:set-position tune)
                            (.indexOf (vec tune-ids) (:id tune)))
                    ;; Stop current playback if any
                    _ (when (:play-proc state) (audio/stop-playback! (:play-proc state)))
                    _ (when (:countin-proc state) (audio/stop-playback! (:countin-proc state)))
                    state' (-> state
                               (assoc :set-queue {:tune-ids tune-ids :index pos :set-name (:name s)}
                                      :playing nil :play-proc nil)
                               (clear-countin-state))
                    first-id (nth tune-ids pos)]
                (play-tune-by-id state' first-id))
              [(flash state "not in a set") nil]))
          [(flash state "no setlist active") nil])

        ;; Skip to next tune in set queue
        (charm/key-match? msg "n")
        (if (:set-queue state)
          (do
            (audio/stop-playback! (:play-proc state))
            (audio/stop-playback! (:countin-proc state))
            (let [state' (-> state
                             (assoc :play-proc nil)
                             (clear-countin-state))]
              (or (advance-set-queue state')
                  [(-> state'
                       (assoc :set-queue nil :playing nil
                              :notation nil :notation-tune-id nil
                              :play-start-ms nil :current-note-idx nil))
                   nil])))
          [state nil])

        (charm/key-match? msg "?")
        [(assoc state :mode :help) nil]

        :else
        [state nil]))))
