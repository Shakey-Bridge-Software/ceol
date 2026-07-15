(ns ceol.web.core
  "Entry point, Replicant dispatch, keyboard shortcuts, and init.
   dispatch-action! is a thin router: most actions delegate to handlers/*
   namespaces (tune, editor, set, playback, session). Inline cases are
   the trivial one-liners (filter, tab, tempo) that don't earn extraction.
   localStorage I/O lives in persist.cljs; rendering in render.cljs."
  (:require [replicant.dom :as r]
            [ceol.web.state :as state]
            [ceol.web.views :as views]
            [ceol.web.guitar :as guitar]
            [ceol.web.metronome :as metro]
            [ceol.beat-engine :as beat]
            [ceol.web.persist :as persist]
            [ceol.web.backup :as backup]
            [ceol.web.render :as render]
            [ceol.web.handlers.playback :as playback]
            [ceol.web.handlers.session :as session]
            [ceol.web.handlers.tune :as tune]
            [ceol.web.handlers.editor :as editor]
            [ceol.web.handlers.set :as set-h]
            [ceol.web.gesture :as gesture]
            [clojure.walk :as walk]))

(defn resolve-event-placeholders [dispatch-data actions]
  (let [js-event (:replicant/js-event dispatch-data)]
    (walk/postwalk
     (fn [x]
       (if (and (keyword? x) (= "event" (namespace x)))
         (case x
           :event/target.value (some-> js-event .-target .-value)
           :event/target.checked (some-> js-event .-target .-checked)
           :event/key (some-> js-event .-key)
           x)
         x))
     actions)))

;; ---------------------------------------------------------------------------
;; Action dispatch
;;
;; Actions are vectors dispatched via Replicant's :on handlers or on-keydown.
;; Each entry below: action keyword, expected args, one-line description.
;;
;; Tune
;;   :filter/set          [filter-type]              set sidebar filter
;;   :tab/set             [tab-key]                  switch sidebar tab
;;   :tune/select         [tune-id]                  select tune, inject chords if needed
;;   :tune/add            []                         create new custom tune
;;   :tune/update-field   [tune-id field value]      update one field on a tune
;;   :tune/update-key-mode [tune-id key mode-name]   update key + mode together
;;   :tune/delete         [tune-id]                  delete custom tune (no-op on catalog)
;;   :tune/duplicate      [tune-id]                  clone tune (+ABC) as fresh custom tune
;;   :tune/add-to-set     [tune-id]                  add tune to active/only set
;;   :abc/render          []                         no-op (render triggered by state watch)
;;
;; Editor
;;   :editor/toggle       []                         open/close ABC editor panel
;;   :editor/update       [tune-id new-abc]          live-update ABC body
;;   :editor/keydown      [key]                      handle Escape to blur editor
;;
;; Inline field editing
;;   :field/edit          [field-key]                enter inline edit mode
;;   :field/cancel        []                         exit inline edit without saving
;;   :field/keydown       [key]                      Enter to confirm, Escape to cancel
;;
;; Mobile tune-details editor (full-screen overlay)
;;   :tune-editor/open-new     []                    open editor in :new mode (blank draft)
;;   :tune-editor/open-edit    [tune-id]             open editor in :edit mode (draft cloned)
;;   :tune-editor/cancel       []                    discard draft + close
;;   :tune-editor/save         []                    commit draft + close
;;   :tune-editor/update-draft [field value]         write to :tune-editor draft
;;
;; Playback
;;   :playback/play       []                         toggle play/stop
;;   :playback/stop       []                         stop unconditionally
;;   :guitar/toggle       []                         toggle guitar track mute
;;   :section/set         [section]                  set active section (:a :b nil)
;;   :loop/toggle         []                         toggle loop
;;   :metronome/toggle    []                         toggle standalone metronome
;;   :count-in/toggle     []                         toggle count-in
;;   :tempo/up            []                         +5 BPM
;;   :tempo/down          []                         -5 BPM
;;   :tempo/reset         []                         reset BPM to type default
;;
;; Sets
;;   :set/start-create    []                         open set creation wizard
;;   :set/name-keydown    [key value]                wizard step 1: confirm name
;;   :set/typeahead       [query]                    update typeahead search query
;;   :set/tune-keydown    [key]                      wizard step 2: pick tune / finish
;;   :set/pick-tune       [tune-id]                  add tune to wizard list
;;   :set/uncreate-tune   [tune-id]                  remove tune from wizard list
;;   :set/toggle          [set-id]                   expand/collapse set in sidebar
;;   :set/select-tune     [set-id tune-id]           select tune within a set
;;   :set/add-tune        [set-id tune-id]           add tune to existing set
;;   :set/remove-tune     [set-id tune-id]           remove tune from set
;;   :set/start-adding    [set-id]                   open inline typeahead for set
;;   :set/add-tune-keydown [set-id key]              handle keydown in set's add-tune input
;;   :set/delete          [set-id]                   delete set
;;   :set/duplicate       [set-id]                   clone set with " (copy)" name
;;   :set-menu/open       [set-id]                   open the set action sheet (mobile)
;;   :set-menu/close      []                         close the set action sheet
;;
;; Mobile set editor (full-screen overlay)
;;   :set-editor/open-new     []                     open editor for a new set
;;   :set-editor/open-edit    [set-id]               open editor seeded from a set
;;   :set-editor/cancel       []                     discard draft, close
;;   :set-editor/save         []                     commit draft → :sets, close
;;   :set-editor/update-draft [field value]          edit a draft field (name)
;;   :set-editor/start-pick   []                     reveal the add-tune picker
;;   :set-editor/stop-pick    []                     hide the add-tune picker
;;   :set-editor/add-tune     [tune-id]              add a tune to the draft
;;   :set-editor/remove-tune  [tune-id]              remove a tune from the draft
;;   :set-editor/reorder      [from to]              move a draft tune by index
;;
;; Learned & Session
;;   :learned/toggle      [tune-id]                  toggle learned flag
;;   :session/start       []                          build queue and start session (also "Practice again")
;;   :session/play-current []                         play current session item (internal)
;;   :session/skip        []                          skip to the next queue item (Wave 1 C)
;;   :session/pause       []                          toggle pause/resume playback (Wave 1 C)
;;   :session/stop        []                          end session
;;   :session/dismiss-summary []                      clear the session-complete summary ("Done")
;;
;; Mobile UI
;;   :menu/open           [tune-id]                   open tune action menu / mobile action-sheet
;;   :menu/close          []                          close it
;;   :controls/toggle     []                          toggle "NOW PLAYING" controls sheet (mobile)
;;   :controls/close      []                          close controls sheet
;;   :settings/open       []                          show settings panel (main-view)
;;   :settings/close      []                          back to tune panel
;;   :mobile/back         []                          pop one nav level (detail→list, set/settings→tune)
;;   :delete/request      [tune-id]                   open delete-confirm modal for a tune
;;   :delete/cancel       []                          dismiss delete-confirm modal
;;   :delete/confirm      [tune-id]                   confirm + delete
;;   :confirm/open        [{:title :body :destructive-label :on-confirm}] open generic confirm
;;   :confirm/cancel      []                          dismiss generic confirm
;;   :onboarding/dismiss  []                          dismiss first-launch coachmark (persists)
;;   :data/clear-confirm  []                          confirm-then-wipe all localStorage
;;
;; Backup / restore
;;   :backup/export       []                          download EDN of all user data
;;   :backup/import       []                          file picker → validate → merge
;;
;; Notes
;;   :notes/toggle        []                          show/hide notes drawer
;;   :notes/update        [tune-id text]              edit notes for tune (debounced save)
;;   :notes/keydown       [key]                       Escape blurs textarea
;; ---------------------------------------------------------------------------

(defn dispatch-action! [action args]
  (case action
    :filter/set        (swap! state/app-state assoc :filter (first args))
    :tab/set           (swap! state/app-state assoc :tab (first args))
    :abc/render        nil

    ;; Tune
    :tune/select          (tune/select! args)
    :tune/add             (tune/add! args)
    :tune/update-field    (tune/update-field! args)
    :tune/update-key-mode (tune/update-key-mode! args)
    :tune/delete          (tune/delete! args)
    :tune/duplicate       (tune/duplicate! args)
    :tune/add-to-set      (tune/add-to-set! args)

    ;; Mobile tune-details editor (full-screen overlay)
    :tune-editor/open-new     (tune/editor-open-new! args)
    :tune-editor/open-edit    (tune/editor-open-edit! args)
    :tune-editor/cancel       (tune/editor-cancel! args)
    :tune-editor/save         (tune/editor-save! args)
    :tune-editor/update-draft (tune/editor-update-draft! args)

    ;; Editor + inline fields
    :editor/toggle  (editor/toggle! args)
    :editor/open    (editor/open! args)
    :menu/open      (swap! state/app-state assoc :context-menu-tune-id (first args))
    :menu/close     (swap! state/app-state assoc :context-menu-tune-id nil)
    :settings/open  (swap! state/app-state assoc :main-view :settings)
    :settings/close (swap! state/app-state assoc :main-view :tune)
    :controls/toggle (swap! state/app-state update :controls-sheet-open? not)
    :controls/close  (swap! state/app-state assoc :controls-sheet-open? false)
    :onboarding/dismiss
    (do (swap! state/app-state assoc :onboarded? true)
        (try (.setItem js/localStorage "ceol-onboarded" "1")
             (catch :default _)))

    :delete/request
    (swap! state/app-state assoc :delete-confirm-tune-id (first args))

    :delete/cancel
    (swap! state/app-state assoc :delete-confirm-tune-id nil)

    :delete/confirm
    (let [[tune-id] args]
      (swap! state/app-state assoc :delete-confirm-tune-id nil)
      (tune/delete! [tune-id]))

    ;; Generic confirm modal — opts is a map with :title :body
    ;; :destructive-label :on-confirm (a Replicant-style actions vector).
    ;; The confirm button's :on-confirm runs *and then* :confirm/cancel
    ;; clears the slot — wired in the view, not here.
    :confirm/open
    (swap! state/app-state assoc :confirm (first args))

    :confirm/cancel
    (swap! state/app-state assoc :confirm nil)

    :mobile/back
    (swap! state/app-state
           (fn [s]
             (cond
               (not= :tune (:main-view s))     (assoc s :main-view :tune)
               (= :detail (:mobile-view s))    (assoc s :mobile-view :list)
               :else s)))
    :data/clear-confirm
    (when (js/confirm "Clear all data? This removes every custom tune, set, edit, and learned mark. Cannot be undone.")
      (try
        (some-> js/window .-localStorage .clear)
        (.reload (.-location js/window))
        (catch :default e (js/console.warn "clear failed" e))))
    :editor/update  (editor/update! args)
    :editor/keydown (editor/keydown! args)
    :field/edit     (editor/field-edit! args)
    :field/cancel   (editor/field-cancel! args)
    :field/keydown  (editor/field-keydown! args)

    ;; Playback
    :playback/play  (playback/play!)
    ;; A user-initiated stop ends the metronome too (button off), consistent
    ;; with the play/stop toggle. The stop! fn itself preserves :metronome? so
    ;; restart-if-playing! (tempo/section change) still re-anchors.
    :playback/stop  (do (playback/stop!)
                        (swap! state/app-state assoc :metronome? false))
    :guitar/toggle  (let [new-val (not (:guitar? @state/app-state))]
                      (swap! state/app-state assoc :guitar? new-val)
                      (guitar/set-muted! (not new-val)))
    :section/set    (do (swap! state/app-state assoc :section (first args))
                        (playback/restart-if-playing!))
    :loop/toggle    (swap! state/app-state update :loop? not)
    :metronome/toggle
    (let [new-val (not (:metronome? @state/app-state))]
      (swap! state/app-state assoc :metronome? new-val :current-beat nil)
      (if new-val
        (let [s (deref state/app-state)]
          (if (and (:playing? s) (:melody-start-at s))
            ;; Playback active: lock to the melody's beat grid.
            (metro/start-synced! {:ms-per-beat   (:melody-ms-per-beat s)
                                  :beats-per-bar (:melody-beats-per-bar s)}
                                 (:melody-start-at s))
            ;; Standalone: self-correcting performance.now clock, click immediately.
            (let [tune   (state/selected-tune s)
                  params (beat/beats-for-tune tune (:tempo-offset s))]
              (metro/start-clicking! params))))
        (metro/stop!)))
    :count-in/toggle (do (swap! state/app-state update :count-in? not)
                         (playback/restart-if-playing!))
    :tempo/up        (do (swap! state/app-state update :tempo-offset #(min 40 (+ (or % 0) 5)))
                         (playback/restart-if-playing!))
    :tempo/down      (do (swap! state/app-state update :tempo-offset #(max -40 (- (or % 0) 5)))
                         (playback/restart-if-playing!))
    :tempo/reset     (do (swap! state/app-state assoc :tempo-offset 0)
                         (playback/restart-if-playing!))

    ;; Sets
    :set/start-create     (set-h/start-create! args)
    :set/name-keydown     (set-h/name-keydown! args)
    :set/typeahead        (set-h/typeahead! args)
    :set/tune-keydown     (set-h/tune-keydown! args)
    :set/pick-tune        (set-h/pick-tune! args)
    :set/uncreate-tune    (set-h/uncreate-tune! args)
    :set/toggle           (set-h/toggle! args)
    :set/select-tune      (set-h/select-tune! args)
    :set/add-tune         (set-h/add-tune! args)
    :set/remove-tune      (set-h/remove-tune! args)
    :set/start-adding     (set-h/start-adding! args)
    :set/add-tune-keydown (set-h/add-tune-keydown! args)
    :set/delete           (set-h/delete! args)
    :set/duplicate        (set-h/duplicate! args)

    ;; Mobile set action sheet (bottom sheet on the set-detail ⋮)
    :set-menu/open  (swap! state/app-state assoc :context-menu-set-id (first args))
    :set-menu/close (swap! state/app-state assoc :context-menu-set-id nil)

    ;; Mobile full-screen set editor (draft-based overlay)
    :set-editor/open-new     (set-h/editor-open-new! args)
    :set-editor/open-edit    (set-h/editor-open-edit! args)
    :set-editor/cancel       (set-h/editor-cancel! args)
    :set-editor/save         (set-h/editor-save! args)
    :set-editor/update-draft (set-h/editor-update-draft! args)
    :set-editor/start-pick   (set-h/editor-start-pick! args)
    :set-editor/stop-pick    (set-h/editor-stop-pick! args)
    :set-editor/add-tune     (set-h/editor-add-tune! args)
    :set-editor/remove-tune  (set-h/editor-remove-tune! args)
    :set-editor/reorder      (set-h/editor-reorder! args)

    ;; Learned + Session
    :learned/toggle
    (let [[tune-id] args]
      (swap! state/app-state update :learned-tune-ids
             (fn [ids] (if (contains? ids tune-id) (disj ids tune-id) (conj ids tune-id))))
      (persist/save-learned!))

    :session/start        (session/session-start!)
    :session/play-current (session/session-play-current!)
    :session/skip         (session/session-skip!)
    :session/pause        (session/session-pause!)
    :session/stop         (session/session-stop!)

    ;; Item #5 — dismiss the session-complete summary ("Done").
    :session/dismiss-summary
    (swap! state/app-state assoc :session-result nil)

    ;; Backup
    :backup/export (backup/export!)
    :backup/import (backup/import!)

    ;; Notes
    :notes/toggle (swap! state/app-state update :notes-open? not)
    :notes/update (let [[tune-id text] args]
                    (persist/update-tune-notes! tune-id text))
    :notes/keydown (let [[key] args]
                     (when (= key "Escape")
                       (some-> js/document .-activeElement .blur)))

    (js/console.warn "Unknown action:" action args)))

(defn execute! [dispatch-data actions]
  (let [js-event (:replicant/js-event dispatch-data)
        actions (resolve-event-placeholders dispatch-data actions)]
    (doseq [[action & args] actions]
      (case action
        :event/stop    (some-> js-event .stopPropagation)
        :event/prevent (some-> js-event .preventDefault)
        (dispatch-action! action args)))))

(defn- input-focused? []
  (let [tag (some-> js/document .-activeElement .-tagName str)]
    (contains? #{"INPUT" "TEXTAREA" "SELECT"} tag)))

(defn- on-keydown [e]
  (when-not (input-focused?)
    (let [key (.-key e)]
      (case key
        " "       (do (.preventDefault e) (playback/play!))
        "l"       (dispatch-action! :loop/toggle nil)
        "g"       (dispatch-action! :guitar/toggle nil)
        "e"       (dispatch-action! :editor/toggle nil)
        "m"       (dispatch-action! :metronome/toggle nil)
        "c"       (dispatch-action! :count-in/toggle nil)
        "n"       (dispatch-action! :notes/toggle nil)
        "k"       (when-let [id (:selected-tune-id @state/app-state)]
                    (dispatch-action! :learned/toggle [id]))
        "="       (dispatch-action! :tempo/up nil)
        "-"       (dispatch-action! :tempo/down nil)
        "0"       (dispatch-action! :tempo/reset nil)
        "1"       (dispatch-action! :section/set [:a])
        "2"       (dispatch-action! :section/set [:b])
        "3"       (dispatch-action! :section/set [nil])
        "ArrowUp" (do (.preventDefault e)
                      (let [s @state/app-state
                            tunes (state/filtered-tunes s)
                            idx (.indexOf (mapv :id tunes) (:selected-tune-id s))
                            new-idx (max 0 (dec idx))]
                        (when (seq tunes)
                          (dispatch-action! :tune/select [(:id (nth tunes new-idx))]))))
        "ArrowDown" (do (.preventDefault e)
                        (let [s @state/app-state
                              tunes (state/filtered-tunes s)
                              idx (.indexOf (mapv :id tunes) (:selected-tune-id s))
                              new-idx (min (dec (count tunes)) (inc idx))]
                          (when (seq tunes)
                            (dispatch-action! :tune/select [(:id (nth tunes new-idx))]))))
        nil))))

(defn- on-document-click [_e]
  ;; Menu-btn/menu-item clicks stopPropagation before reaching here, so any
  ;; click that fires this listener is outside the open context menu.
  (when (:context-menu-tune-id @state/app-state)
    (dispatch-action! :menu/close nil)))

(defonce _keydown-listener
  (.addEventListener js/document "keydown" on-keydown))

(defonce _document-click-listener
  (.addEventListener js/document "click" on-document-click))

(defn init! []
  (r/set-dispatch! execute!)
  (gesture/attach!)
  (when (.getItem js/localStorage "ceol-onboarded")
    (swap! state/app-state assoc :onboarded? true))
  (render/setup-render-watch!)
  (persist/load-tunes!)
  (persist/load-sets!)
  (persist/load-learned!)
  (persist/load-abc-data!)
  (persist/load-saved-edits!)
  (persist/load-notes!)
  (r/render render/el (views/app @state/app-state)))

