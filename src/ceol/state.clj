(ns ceol.state
  (:require [charm.core :as charm]
            [charm.components.spinner :as spinner]
            [ceol.tunes :as tunes]
            [ceol.data :as data]
            [ceol.audio :as audio]
            [ceol.notation :as notation]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [babashka.process :as proc]))

(defn flash [state msg]
  (assoc state :flash msg))

(defn clear-flash [state]
  (assoc state :flash nil))

(defn- check-dep [cmd]
  (try
    (let [result @(proc/process {:cmd ["which" cmd] :out :string :err :string})]
      (zero? (:exit result)))
    (catch Exception _ false)))

(defn init-state []
  (data/ensure-dirs!)
  (let [hydrated (data/hydrate-tunes tunes/catalog)
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
               :current-note-idx nil}
        missing (filterv (complement check-dep) ["abc2midi" "fluidsynth"])
        state (if (seq missing)
                (flash state (str "missing: " (str/join ", " missing)))
                state)
        state (if (nil? (data/soundfont-path))
                (flash state "no soundfont found — playback won't work")
                state)]
    state))

;; --- Tune helpers ---

(defn visible-tunes [state]
  (tunes/tunes-by-type (:tunes state) (:filter state)))

(defn selected-tune [state]
  (get (visible-tunes state) (:cursor state)))

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

;; --- Navigation ---

(defn cursor-up [state]
  (update state :cursor #(max 0 (dec %))))

(defn cursor-down [state]
  (let [max-idx (max 0 (dec (count (visible-tunes state))))]
    (update state :cursor #(min max-idx (inc %)))))

;; --- Spinner management ---

(defn start-spinner [state]
  (let [[s cmd] (spinner/spinner-init (spinner/spinner :dots :id :ceol-spinner))]
    [(assoc state :spinner s) cmd]))

(defn stop-spinner [state]
  (assoc state :spinner nil))

;; --- Audio pipeline ---

(defn prepare-tune
  "Start the fetch-convert pipeline for a tune."
  [state]
  (if-let [tune (selected-tune state)]
    (let [tune-id (:id tune)
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
    (let [tune-id (:id tune)
          tempo-offset (:tempo-offset state)
          section (:section state)
          loop? (:loop state)]
      (cond
        ;; Playing same tune -> stop
        (= (:playing state) tune-id)
        (do
          (audio/stop-playback! (:play-proc state))
          [(assoc state :playing nil :play-proc nil
                  :notation-tune-id nil
                  :play-start-ms nil :current-note-idx nil) nil])

        ;; Playing different tune -> stop old, start new
        (:playing state)
        (do
          (audio/stop-playback! (:play-proc state))
          (let [state' (assoc state :playing nil :play-proc nil)]
            (play-or-stop state')))

        ;; MIDI ready -> check if correct variant exists
        (= :ready (:midi-status tune))
        (let [target-path (data/midi-file-path-for tune-id tempo-offset section :loop? loop?)]
          (if (.exists (io/file target-path))
            [(assoc state :playing tune-id)
             (audio/play-cmd target-path)]
            ;; Need to convert for this tempo/section variant
            (if (:abc tune)
              (let [[s sc] (start-spinner (assoc state :loading tune-id))
                    s' (update-tune s tune-id assoc :midi-status :converting)]
                [s' (charm/batch sc (audio/convert-midi-cmd tune (:abc tune) tempo-offset section :loop-count (if (:loop state) 50 1)))])
              [(flash state "no ABC available") nil])))

        ;; ABC ready, no MIDI -> convert then play
        (= :ready (:abc-status tune))
        (let [[state' spinner-cmd] (start-spinner (assoc state :loading tune-id))
              state'' (update-tune state' tune-id assoc :midi-status :converting)]
          [state'' (charm/batch spinner-cmd (audio/convert-midi-cmd tune (:abc tune) tempo-offset section :loop-count (if (:loop state) 50 1)))])

        ;; Nothing -> fetch -> convert -> play
        :else
        (let [[state' spinner-cmd] (start-spinner (assoc state :loading tune-id))
              state'' (update-tune state' tune-id assoc :abc-status :fetching)]
          [state'' (charm/batch spinner-cmd (audio/fetch-abc-cmd tune))])))
    [state nil]))

(defn stop-playback! [state]
  (audio/stop-playback! (:play-proc state))
  [(assoc state :playing nil :play-proc nil :loading nil
          :notation-tune-id nil
          :play-start-ms nil :current-note-idx nil) nil])

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
        (if (.exists (io/file midi-path))
          ;; Already have this variant cached — play directly
          [(assoc state :play-proc nil :playing tune-id
                  :play-start-ms nil :current-note-idx nil)
           (audio/play-cmd midi-path)]
          ;; Need to convert — clear :playing, set :loading for auto-play
          (let [state' (assoc state :playing nil :play-proc nil :loading tune-id
                              :play-start-ms nil :current-note-idx nil)
                [s sc] (start-spinner state')
                s' (update-tune s tune-id assoc :midi-status :converting)]
            [s' (charm/batch sc (audio/convert-midi-cmd tune (:abc tune) tempo-offset section :loop-count (if (:loop state) 50 1)))])))
      [state nil])))

;; --- Staff helpers ---

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
                       (try (notation/parse-abc (:abc tune)) (catch Exception _ nil)))]
          (if parsed
            (assoc state :notation (:timeline parsed) :current-note-idx nil
                   :notation-tune-id tune-id)
            (assoc state :notation nil :current-note-idx nil
                   :notation-tune-id nil)))))
    state))

;; --- Main update ---

(defn update-state [state msg]
  (cond
    ;; Quit
    (or (charm/quit? msg)
        (and (= :browse (:mode state))
             (charm/key-match? msg "q")))
    (do
      (audio/stop-playback! (:play-proc state))
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
        [(assoc state' :playing tune-id)
         (audio/play-cmd midi-path)]
        [(flash state' "prepared") nil]))

    (= :midi-failed (:type msg))
    (let [tune-id (:tune-id msg)]
      [(-> state
           (update-tune tune-id assoc :midi-status :failed)
           (assoc :loading nil)
           (stop-spinner)
           (flash (str "MIDI convert failed: " (:error msg))))
       nil])

    (= :playback-started (:type msg))
    (let [proc (:proc msg)
          tune-id (:playing state)
          tune (when tune-id (tunes/tune-by-id (:tunes state) tune-id))
          ;; Parse notation for staff display if we have ABC
          parsed (when (and (:show-staff state) tune (:abc tune))
                   (try (notation/parse-abc (:abc tune)) (catch Exception _ nil)))
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
      ;; Current playback finished — two-layer looping:
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
                :play-start-ms nil :current-note-idx nil) nil])
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
        [(-> state
             (update :filter tunes/next-filter)
             (assoc :cursor 0)
             (clamp-cursor))
         nil]

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
                       (try (notation/parse-abc (:abc tune)) (catch Exception _ nil)))
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

        (charm/key-match? msg "?")
        [(assoc state :mode :help) nil]

        :else
        [state nil]))))
