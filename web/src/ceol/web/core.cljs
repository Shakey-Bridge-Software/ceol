(ns ceol.web.core
  "Entry point, Replicant dispatch, action handlers, keyboard shortcuts, and init.
   Delegates localStorage I/O to persist.cljs and rendering to render.cljs.
   Complex playback and session orchestration live in handlers/playback.cljs
   and handlers/session.cljs respectively."
  (:require [replicant.dom :as r]
            [ceol.web.state :as state]
            [ceol.web.views :as views]
            [ceol.web.abc-bridge :as abc-bridge]
            [ceol.web.chords :as chords]
            [ceol.web.guitar :as guitar]
            [ceol.web.metronome :as metro]
            [ceol.web.beat-engine :as beat]
            [ceol.abc :as abc]
            [ceol.web.persist :as persist]
            [ceol.web.render :as render]
            [ceol.web.handlers.playback :as playback]
            [ceol.web.handlers.session :as session]
            [clojure.walk :as walk]))

(defn inject-chords-if-needed
  "If the ABC body doesn't already have chord annotations, suggest and inject them."
  [abc-body tune]
  (if (re-find #"\"[A-G]" abc-body)
    ;; Already has chords
    abc-body
    ;; Suggest and inject
    (let [chord-names (chords/suggest-chords abc-body (:key tune) (:mode-name tune))]
      (chords/inject-chords abc-body chord-names))))

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
;;
;; Learned & Session
;;   :learned/toggle      [tune-id]                  toggle learned flag
;;   :session/start       []                          build queue and start session
;;   :session/play-current []                         play current session item (internal)
;;   :session/stop        []                          end session
;; ---------------------------------------------------------------------------

(defn dispatch-action! [action args]
  (case action
    :filter/set
    (let [[filter-type] args]
      (swap! state/app-state assoc :filter filter-type))

    :tab/set
    (let [[tab] args]
      (swap! state/app-state assoc :tab tab))

    :tune/select
    (let [[tune-id] args]
      (let [s @state/app-state
            raw-abc (get (:abc-data s) tune-id)
            existing-edit (get (:abc-edits s) tune-id)]
        (when (and (string? raw-abc) (not existing-edit))
          (try
            (let [tune (state/tune-by-id s tune-id)
                  annotated (inject-chords-if-needed raw-abc tune)]
              (swap! state/app-state assoc-in [:abc-edits tune-id]
                     (if (string? annotated) annotated raw-abc)))
            (catch :default e
              (js/console.warn "Chord injection failed for tune" tune-id e)
              (swap! state/app-state assoc-in [:abc-edits tune-id] raw-abc)))))
      (swap! state/app-state assoc :selected-tune-id tune-id
             :set-playing? false :set-tune-index 0))

    :abc/render nil

    :editor/toggle
    (let [opening? (not (:editor-open? @state/app-state))]
      (swap! state/app-state assoc :editor-open? opening?)
      (when opening?
        (js/requestAnimationFrame
         (fn []
           (when-let [el (js/document.querySelector ".editor-textarea")]
             (.focus el))))))

    :editor/update
    (let [[tune-id new-val] args]
      (when (string? new-val)
        (swap! state/app-state assoc-in [:abc-edits tune-id] new-val)
        (persist/schedule-save!)))

    :editor/keydown
    (let [[key] args]
      (when (= key "Escape")
        (when-let [el (js/document.querySelector ".editor-textarea")]
          (.blur el))))

    :tune/add-to-set
    (let [[tune-id] args
          s    @state/app-state
          sets (:sets s)]
      (cond
        (empty? sets)
        (js/console.warn "No sets — create one first")

        (= 1 (count sets))
        (let [set-id (key (first sets))]
          (swap! state/app-state update-in [:sets set-id :tune-ids]
                 (fn [ids] (if (some #{tune-id} ids) ids (conj (or ids []) tune-id))))
          (persist/save-sets!))

        (:active-set-id s)
        (let [set-id (:active-set-id s)]
          (swap! state/app-state update-in [:sets set-id :tune-ids]
                 (fn [ids] (if (some #{tune-id} ids) ids (conj (or ids []) tune-id))))
          (persist/save-sets!))

        :else
        (js/console.warn "Multiple sets — select one first")))

    :tune/add
    (let [new-id (state/next-tune-id @state/app-state)
          new-tune {:id new-id :name "New Tune" :type :polka :time-sig "2/4"
                    :key "G" :mode-name "Ionian"}]
      (swap! state/app-state
             (fn [s]
               (let [custom (assoc (:custom-tunes s) new-id new-tune)
                     merged (state/merge-tunes state/base-tunes custom)]
                 (merge s {:custom-tunes custom} merged
                        {:selected-tune-id new-id :editing-field :name}))))
      (persist/save-custom-tunes!))

    :tune/update-field
    (let [[tune-id field value] args]
      (persist/update-tune-field! tune-id field value)
      (swap! state/app-state assoc :editing-field nil))

    :tune/update-key-mode
    (let [[tune-id key-name mode-name] args]
      (persist/update-tune-field! tune-id :key key-name)
      (persist/update-tune-field! tune-id :mode-name mode-name)
      (swap! state/app-state assoc :editing-field nil))

    :tune/delete
    (let [[tune-id] args]
      (when (state/custom-tune? tune-id)
        (swap! state/app-state
               (fn [s]
                 (let [custom (dissoc (:custom-tunes s) tune-id)
                       merged (state/merge-tunes state/base-tunes custom)]
                   (merge s {:custom-tunes custom} merged {:selected-tune-id nil}))))
        (persist/save-custom-tunes!)))

    :field/edit
    (let [[field] args]
      (swap! state/app-state assoc :editing-field field))

    :field/cancel
    (swap! state/app-state assoc :editing-field nil)

    :field/keydown
    (let [[key] args]
      (case key
        "Enter" (when-let [el (js/document.querySelector ".inline-edit-title")]
                  (.blur el))
        "Escape" (swap! state/app-state assoc :editing-field nil)
        nil))

    :playback/play
    (playback/play!)

    :playback/stop
    (playback/stop!)

    :guitar/toggle
    (let [new-val (not (:guitar? @state/app-state))]
      (swap! state/app-state assoc :guitar? new-val)
      (guitar/set-muted! (not new-val)))

    :section/set
    (let [[section] args]
      (swap! state/app-state assoc :section section))

    :loop/toggle
    (swap! state/app-state update :loop? not)

    :metronome/toggle
    (let [new-val (not (:metronome? @state/app-state))]
      (swap! state/app-state assoc :metronome? new-val :current-beat nil)
      (if new-val
        ;; Start standalone metronome
        (let [s @state/app-state
              tune (state/selected-tune s)
              params (beat/beats-for-tune tune (:tempo-offset s))]
          (metro/start-clicking! params))
        ;; Stop metronome
        (metro/stop!)))

    :count-in/toggle
    (swap! state/app-state update :count-in? not)

    :tempo/up
    (swap! state/app-state update :tempo-offset #(min 40 (+ (or % 0) 5)))

    :tempo/down
    (swap! state/app-state update :tempo-offset #(max -40 (- (or % 0) 5)))

    :tempo/reset
    (swap! state/app-state assoc :tempo-offset 0)

    ;; --- Set actions ---

    :set/start-create
    (swap! state/app-state assoc :creating-set? true :creating-set-name nil
           :creating-set-tunes [] :typeahead-query "" :typeahead-index 0)

    :set/name-keydown
    (let [[key value] args]
      (case key
        "Enter" (when (and (string? value) (seq (.trim value)))
                  (swap! state/app-state assoc :creating-set-name (.trim value)))
        "Escape" (swap! state/app-state assoc :creating-set? false)
        nil))

    :set/typeahead
    (let [[query] args]
      (swap! state/app-state assoc :typeahead-query (or query "") :typeahead-index 0))

    :set/tune-keydown
    (let [[key] args
          s @state/app-state
          query (:typeahead-query s)
          results (state/search-tunes s query 5)
          idx (:typeahead-index s)]
      (case key
        "Enter"
        (if (seq (.trim (or query "")))
          ;; Pick highlighted result
          (when-let [tune (get results idx)]
            (let [tune-id (:id tune)
                  existing (:creating-set-tunes s)]
              (when-not (some #{tune-id} existing)
                (swap! state/app-state update :creating-set-tunes conj tune-id))
              (swap! state/app-state assoc :typeahead-query "" :typeahead-index 0)))
          ;; Empty enter = done
          (let [name (:creating-set-name s)
                tune-ids (:creating-set-tunes s)]
            (when (and name (seq tune-ids))
              (let [set-id (state/next-set-id s)
                    new-set {:id set-id :name name :tune-ids tune-ids}]
                (swap! state/app-state
                       (fn [s]
                         (assoc s :sets (assoc (:sets s) set-id new-set)
                                :creating-set? false
                                :active-set-id set-id
                                :selected-tune-id (first tune-ids))))
                (persist/save-sets!)))))

        "Escape"
        (swap! state/app-state assoc :creating-set? false)

        "ArrowDown"
        (swap! state/app-state update :typeahead-index
               #(min (dec (count results)) (inc %)))

        "ArrowUp"
        (swap! state/app-state update :typeahead-index
               #(max 0 (dec %)))
        nil))

    :set/pick-tune
    (let [[tune-id] args
          s @state/app-state
          existing (:creating-set-tunes s)]
      (when-not (some #{tune-id} existing)
        (swap! state/app-state update :creating-set-tunes conj tune-id))
      (swap! state/app-state assoc :typeahead-query "" :typeahead-index 0))

    :set/uncreate-tune
    (let [[tune-id] args]
      (swap! state/app-state update :creating-set-tunes
             (fn [ids] (vec (remove #{tune-id} ids)))))

    :set/toggle
    (let [[set-id] args
          s @state/app-state]
      (if (= set-id (:active-set-id s))
        (swap! state/app-state assoc :active-set-id nil)
        (let [s-data (get (:sets s) set-id)
              first-tune-id (first (:tune-ids s-data))]
          (swap! state/app-state assoc :active-set-id set-id
                 :selected-tune-id first-tune-id))))

    :set/select-tune
    (let [[_set-id tune-id] args]
      (swap! state/app-state assoc :selected-tune-id tune-id))

    :set/add-tune
    (let [[set-id tune-id] args]
      (swap! state/app-state update-in [:sets set-id :tune-ids]
             (fn [ids]
               (if (some #{tune-id} ids) ids (conj (or ids []) tune-id))))
      (persist/save-sets!))

    :set/remove-tune
    (let [[set-id tune-id] args]
      (swap! state/app-state update-in [:sets set-id :tune-ids]
             (fn [ids] (vec (remove #{tune-id} ids))))
      (persist/save-sets!))

    :set/start-adding
    (let [[set-id] args]
      (swap! state/app-state assoc :adding-to-set set-id :typeahead-query "" :typeahead-index 0))

    :set/add-tune-keydown
    (let [[set-id key] args
          s @state/app-state
          query (:typeahead-query s)
          results (state/search-tunes s query 5)
          idx (:typeahead-index s)]
      (case key
        "Enter"
        (if (seq (.trim (or query "")))
          (when-let [tune (get results idx)]
            (swap! state/app-state update-in [:sets set-id :tune-ids]
                   (fn [ids]
                     (let [tid (:id tune)]
                       (if (some #{tid} ids) ids (conj (or ids []) tid)))))
            (swap! state/app-state assoc :typeahead-query "" :typeahead-index 0)
            (persist/save-sets!))
          ;; Empty enter = done adding
          (swap! state/app-state assoc :adding-to-set nil))

        "Escape"
        (swap! state/app-state assoc :adding-to-set nil)

        "ArrowDown"
        (swap! state/app-state update :typeahead-index #(min (dec (count results)) (inc %)))

        "ArrowUp"
        (swap! state/app-state update :typeahead-index #(max 0 (dec %)))

        nil))

    :set/delete
    (let [[set-id] args]
      (swap! state/app-state (fn [s]
                               (-> s
                                   (update :sets dissoc set-id)
                                   (cond-> (= set-id (:active-set-id s))
                                     (assoc :active-set-id nil)))))
      (persist/save-sets!))

    ;; --- Learned + Session ---

    :learned/toggle
    (let [[tune-id] args]
      (swap! state/app-state update :learned-tune-ids
             (fn [ids] (if (contains? ids tune-id) (disj ids tune-id) (conj ids tune-id))))
      (persist/save-learned!))

    :session/start
    (session/session-start!)

    :session/play-current
    (session/session-play-current!)

    :session/stop
    (do (playback/stop!)
        (swap! state/app-state assoc :session-mode? false :session-pausing? false))

    (js/console.warn "Unknown action:" action args)))

(defn execute! [dispatch-data actions]
  (let [actions (resolve-event-placeholders dispatch-data actions)]
    (doseq [[action & args] actions]
      (dispatch-action! action args))))

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

(defonce _keydown-listener
  (.addEventListener js/document "keydown" on-keydown))

(defn init! []
  (r/set-dispatch! execute!)
  (render/setup-render-watch!)
  (persist/load-custom-tunes!)
  (persist/load-sets!)
  (persist/load-learned!)
  (persist/load-abc-data!)
  (persist/load-saved-edits!)
  (r/render render/el (views/app @state/app-state)))
