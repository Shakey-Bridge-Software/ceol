(ns ceol.web.views
  "Pure Replicant hiccup components. All functions take state and return hiccup.
   No side effects, no state mutation. Actions are dispatched via Replicant's
   r/set-dispatch! — components emit action vectors, never call functions."
  (:require [ceol.web.state :as state]
            [ceol.abc :as abc]))

(def tune-type-labels
  {:all "All" :polka "Polka" :jig "Jig" :reel "Reel"
   :hornpipe "Hornpipe" :slip-jig "Slip Jig" :slide "Slide" :other "Other"})

(def tune-type-order [:all :polka :jig :reel :hornpipe :slip-jig :slide :other])
(def tune-types [:polka :jig :reel :hornpipe :slip-jig :slide :other])
(def time-sigs ["2/4" "4/4" "6/8" "9/8" "12/8" "3/4"])

(def key-mode-options
  [{:label "G Major"   :key "G" :mode-name "Ionian"}
   {:label "D Major"   :key "D" :mode-name "Ionian"}
   {:label "A Dorian"  :key "A" :mode-name "Dorian"}
   {:label "E Dorian"  :key "E" :mode-name "Dorian"}
   {:label "E Minor"   :key "E" :mode-name "Aeolian"}
   {:label "C Major"   :key "C" :mode-name "Ionian"}
   {:label "A Minor"   :key "A" :mode-name "Aeolian"}])

(defn- key-mode-label [key-name mode-name]
  (let [match (first (filter #(and (= (:key %) key-name) (= (:mode-name %) mode-name))
                             key-mode-options))]
    (or (:label match) (str key-name " " mode-name))))

(defn- next-in-cycle [coll current]
  (let [idx (.indexOf coll current)
        next-idx (mod (inc (if (neg? idx) -1 idx)) (count coll))]
    (nth coll next-idx)))

(defn filter-chip [current-filter type-key]
  [:button.filter-chip
   {:class (when (= current-filter type-key) "active")
    :on {:click [[:filter/set type-key]]}}
   (get tune-type-labels type-key)])

(defn tune-context-menu [tune-id custom?]
  [:div.tune-context-menu {:on {:click [[:event/stop]]}}
   [:button.cm-item {:on {:click [[:menu/close] [:learned/toggle tune-id]]}}
    [:span.cm-icon "○"] "Mark as Learned"]
   [:div.cm-divider]
   [:button.cm-item {:on {:click [[:menu/close] [:tune/select tune-id] [:tune/add-to-set tune-id]]}}
    [:span.cm-icon "≡"] "Add to Set…"]
   [:div.cm-divider]
   [:button.cm-item {:on {:click [[:menu/close] [:tune/select tune-id] [:playback/play]]}}
    [:span.cm-icon "▶"] "Play"]
   [:div.cm-divider]
   [:button.cm-item {:on {:click [[:menu/close] [:tune/select tune-id] [:editor/open]]}}
    [:span.cm-icon "✎"] "Edit"]
   (when custom?
     (list [:div.cm-divider]
           [:button.cm-item.cm-item-danger
            {:on {:click [[:menu/close] [:tune/delete tune-id]]}}
            [:span.cm-icon "✕"] "Delete"]))])

(defn tune-row [tune state]
  (let [active? (= (:id tune) (:selected-tune-id state))
        learned? (state/learned? state (:id tune))
        menu-open? (= (:context-menu-tune-id state) (:id tune))
        custom? (state/custom-tune? (:id tune))]
    [:div.tune-row-wrap
     [:div.tune-row {:class (str (when active? "active")
                                 (when learned? " learned"))
                     :on {:click [[:tune/select (:id tune)]]
                          :contextmenu [[:event/prevent] [:menu/open (:id tune)]]}}
      [:div.tune-info
       [:div.tune-name (:name tune)]
       [:div.tune-meta
        (str (name (:type tune)) " · " (:key tune) " " (:mode-name tune) " · " (:time-sig tune))]]
      [:button.tune-row-menu-btn
       {:on {:click [[:event/stop] [:menu/open (:id tune)]]}}
       "\u22ee"]]
     (when menu-open?
       (tune-context-menu (:id tune) custom?))]))

;; --- Sets tab components ---

(defn set-tune-row [tune idx set-id]
  [:div.set-tune-row
   [:span.set-tune-num (str (inc idx))]
   [:span.set-tune-name {:on {:click [[:set/select-tune set-id (:id tune)]]}}
    (:name tune)]
   [:button.set-tune-remove {:on {:click [[:set/remove-tune set-id (:id tune)]]}} "\u00D7"]])

(defn typeahead-results [state]
  (let [query (:typeahead-query state)
        results (state/search-tunes state query 5)
        idx (:typeahead-index state)]
    (when (seq results)
      [:div.typeahead-dropdown
       (map-indexed
        (fn [i tune]
          [:div.typeahead-item {:class (when (= i idx) "highlighted")
                                :on {:click [[:set/pick-tune (:id tune)]]}}
           [:span.typeahead-name (:name tune)]
           [:span.typeahead-type (name (:type tune))]])
        results)])))

(defn set-card [set-data state]
  (let [active? (= (:id set-data) (:active-set-id state))
        tunes (state/set-tunes state set-data)]
    [:div.set-card {:class (when active? "active")}
     [:div.set-card-header {:on {:click [[:set/toggle (:id set-data)]]}}
      [:div.set-info
       [:div.set-name (:name set-data)]
       [:div.set-meta (str (count (:tune-ids set-data)) " tunes")]]
      [:span.set-expand (if active? "\u25B4" "\u25BE")]]
     (when active?
       (let [set-id (:id set-data)
             adding? (= (:adding-to-set state) set-id)]
         [:div.set-tunes
          (map-indexed (fn [i t] (when t (set-tune-row t i set-id))) tunes)
          (if adding?
            [:div.set-add-inline
             [:input.set-input
              {:type "text"
               :placeholder "Search tunes..."
               :value (:typeahead-query state)
               :auto-focus true
               :on {:input [[:set/typeahead :event/target.value]]
                    :keydown [[:set/add-tune-keydown set-id :event/key]]}}]
             (typeahead-results state)]
            [:div.set-actions
             [:button.set-add-btn {:on {:click [[:set/start-adding set-id]]}} "+"]
             [:button.delete-set {:on {:click [[:set/delete set-id]]}} "Delete"]])]))]))

(defn set-creation-form [state]
  (let [name-confirmed? (:creating-set-name state)
        added-tunes (:creating-set-tunes state)]
    [:div.set-creation
     (if-not name-confirmed?
       ;; Step 1: name
       [:div.set-name-input
        [:input.set-input
         {:type "text"
          :placeholder "Set name..."
          :auto-focus true
          :on {:keydown [[:set/name-keydown :event/key :event/target.value]]}}]]
       ;; Step 2: add tunes
       [:div.set-tune-picker
        [:div.set-creation-header (:creating-set-name state)]
        (when (seq added-tunes)
          [:div.set-creation-tunes
           (map-indexed
            (fn [i tune-id]
              (let [tune (state/tune-by-id state tune-id)]
                [:div.set-creation-tune-row
                 [:span.set-tune-num (str (inc i))]
                 [:span (:name tune)]
                 [:button.set-tune-remove
                  {:on {:click [[:set/uncreate-tune tune-id]]}} "\u00D7"]]))
            added-tunes)])
        [:input.set-input
         {:type "text"
          :placeholder "Search tunes..."
          :value (:typeahead-query state)
          :auto-focus true
          :on {:input [[:set/typeahead :event/target.value]]
               :keydown [[:set/tune-keydown :event/key]]}}]
        (typeahead-results state)
        [:div.set-creation-hints "Enter to add \u00B7 Enter on empty = done \u00B7 Esc to cancel"]])]))

(defn sets-tab [state]
  [:div.sets-content
   (if (:creating-set? state)
     (set-creation-form state)
     [:button.add-set-btn {:on {:click [[:set/start-create]]}} "+ New Set"])
   [:div.set-list
    (let [sets (vals (:sets state))]
      (if (seq sets)
        (map (fn [s] (set-card s state)) (sort-by :name sets))
        [:div.sets-empty "No sets yet"]))]])

;; --- Session tab ---

(defn session-preview-item [item state]
  (case (:type item)
    :set [:div.session-item.session-set
          [:span.session-item-icon "\u25A4"]
          [:div.session-item-info
           [:div.session-item-name (:name item)]
           [:div.session-item-meta (str (count (:tune-ids item)) " tunes")]]]
    :tune (let [tune (state/tune-by-id state (:tune-id item))]
            [:div.session-item
             [:span.session-item-icon "\u266A"]
             [:div.session-item-name (or (:name tune) "Unknown")]])
    nil))

(defn session-tab-pre [state]
  (let [learned-count (count (:learned-tune-ids state))
        ready-sets (state/count-ready-sets state)
        queue (state/build-session-queue (:learned-tune-ids state) (:sets state))]
    [:div.session-content
     [:div.session-summary
      [:span.session-stats (str learned-count " learned tune" (when (not= 1 learned-count) "s")
                                " \u00b7 " ready-sets " set" (when (not= 1 ready-sets) "s") " ready")]]
     (if (pos? (count queue))
       [:div.session-actions
        [:button.session-start {:on {:click [[:session/start]]}} "Start Session"]
        [:div.session-preview
         [:div.session-preview-label "SESSION QUEUE PREVIEW"]
         (map (fn [item] (session-preview-item item state)) queue)]]
       [:div.session-empty "Mark tunes as learned to start a session"])]))

(defn session-tab-active [state]
  (let [queue (:session-queue state)
        idx (:session-index state)
        total (count queue)
        current (nth queue idx nil)
        progress (if (pos? total) (/ (inc idx) total) 0)]
    [:div.session-content
     [:div.session-active-header
      [:span.session-active-label "SESSION ACTIVE"]
      [:span.session-active-count (str (inc idx) " of " total)]]
     [:div.session-progress-bar
      [:div.session-progress-fill {:style {:width (str (* progress 100) "%")}}]]
     [:div.session-now-playing
      [:div.session-now-label "NOW PLAYING"]
      (case (:type current)
        :set [:div.session-now-info
              [:div.session-now-name (:name current)]
              [:div.session-now-meta (str (inc (:session-set-index state)) "/"
                                          (count (:tune-ids current)))]]
        :tune (let [tune (state/tune-by-id state (:tune-id current))]
                [:div.session-now-info
                 [:div.session-now-name (:name tune)]])
        nil)]
     [:div.session-next
      [:div.session-next-label "NEXT"]
      [:div.session-next-val.session-next-teaser
       (let [last? (= (inc idx) total)]
         (if last? "—" "?"))]]
     (when (seq (:session-played state))
       [:div.session-history
        [:div.session-history-label "PLAYED"]
        (map (fn [played-idx]
               (let [item (nth queue played-idx nil)]
                 [:div.session-history-item
                  [:span.session-history-check "\u2713"]
                  [:span (case (:type item)
                           :set (:name item)
                           :tune (let [t (state/tune-by-id state (:tune-id item))]
                                   (:name t))
                           "?")]]))
             (:session-played state))])
     [:button.session-end {:on {:click [[:session/stop]]}} "End Session"]]))

(defn session-tab [state]
  (if (:session-mode? state)
    (session-tab-active state)
    (session-tab-pre state)))

(defn sidebar [state]
  (let [current-filter (:filter state)
        selected-id (:selected-tune-id state)
        learned-ids (:learned-tune-ids state)
        tunes (state/filtered-tunes state)]
    [:div.sidebar
     [:div.sidebar-header
      [:div.app-title-row
       [:div.app-name "ceol"]
       [:div.app-version "v0.3.0"]]
      [:div.app-tagline "PRACTICE COMPANION"]]
     [:div.tab-bar
      [:button.tab {:class (when (= :tunes (:tab state)) "active")
                    :on {:click [[:tab/set :tunes]]}} "Tunes"]
      [:button.tab {:class (when (= :sets (:tab state)) "active")
                    :on {:click [[:tab/set :sets]]}} "Sets"]
      [:button.tab {:class (str (when (= :session (:tab state)) "active")
                                (when (:session-mode? state) " session-live"))
                    :on {:click [[:tab/set :session]]}} "Session"]]
     (case (:tab state)
       :sets (sets-tab state)
       :session (session-tab state)
       ;; default: tunes
       [:div.tunes-content
        [:div.filters-row
         [:div.filters
          (map (fn [t] (filter-chip current-filter t)) tune-type-order)]]
        [:button.new-tune-btn {:on {:click [[:tune/add]]}}
         [:span.new-tune-icon "+"] "New tune"]
        [:div.tune-list
         (map (fn [t] (tune-row t state)) tunes)]])
     (when-let [status (:backup-status state)]
       [:div.backup-status {:class (str "kind-" (name (:kind status)))}
        [:span.backup-status-icon (if (= :success (:kind status)) "✓" "!")]
        [:span.backup-status-msg (:message status)]])
     [:div.sidebar-footer
      [:button.sidebar-footer-btn.sidebar-settings-btn
       {:class (when (= :settings (:main-view state)) "active")
        :on {:click [[:settings/open]]}}
       [:span.settings-gear "⚙"] "Settings"]]]))

(defn tune-header [tune state]
  (when tune
    (let [tempo-str (abc/tempo-for-type (:type tune) (:time-sig tune))
          bpm (second (re-find #"=(\d+)" tempo-str))
          section (:section state)
          editor-open? (:editor-open? state)
          editing (:editing-field state)
          tune-id (:id tune)
          session? (:session-mode? state)]
      [:div.tune-header
       [:div.title-block
        ;; Editable title
        (if (= editing :name)
          [:input.inline-edit-title
           {:type "text"
            :value (:name tune)
            :auto-focus true
            :on {:blur [[:tune/update-field tune-id :name :event/target.value]]
                 :keydown [[:field/keydown :event/key]]}}]
          [:div.tune-title {:class (when editor-open? "edit-mode")
                            :on {:click [[:field/edit :name]]}}
           (:name tune)])
        ;; Editable metadata
        [:div.tune-title-meta {:class (when editor-open? "edit-mode")}
         ;; Type — click to cycle
         [:span.meta-field.clickable
          {:on {:click [[:tune/update-field tune-id :type
                         (next-in-cycle tune-types (:type tune))]]}}
          (name (:type tune))]
         [:span.meta-sep " · "]
         ;; Key/mode — click to cycle
         (let [current-label (key-mode-label (:key tune) (:mode-name tune))
               current-opt (first (filter #(= (:label %) current-label) key-mode-options))
               next-opt (next-in-cycle key-mode-options (or current-opt (first key-mode-options)))]
           [:span.meta-field.clickable
            {:on {:click [[:tune/update-key-mode tune-id (:key next-opt) (:mode-name next-opt)]]}}
            current-label])
         [:span.meta-sep " · "]
         ;; Time sig — click to cycle
         [:span.meta-field.clickable
          {:on {:click [[:tune/update-field tune-id :time-sig
                         (next-in-cycle time-sigs (:time-sig tune))]]}}
          (:time-sig tune)]
         [:span.meta-sep " · "]
         [:span bpm " BPM"]]]
       (when-not session?
         [:div.section-controls
          (when-not (:set-playing? state)
            [:div.section-btns
             [:button.section-btn {:class (when (= :a section) "active")
                                   :on {:click [[:section/set :a]]}} "A"]
             [:button.section-btn {:class (when (= :b section) "active")
                                   :on {:click [[:section/set :b]]}} "B"]
             [:button.section-btn {:class (when (nil? section) "active")
                                   :on {:click [[:section/set nil]]}} "All"]])
          [:button.edit-toggle {:class (when editor-open? "active")
                                :on {:click [[:editor/toggle]]}}
           (if editor-open? "✓ Done" "✎ Edit")]
          (let [is-learned? (state/learned? state tune-id)]
            [:button.learned-toggle {:class (when is-learned? "active")
                                     :on {:click [[:learned/toggle tune-id]]}}
             (if is-learned? "\u2713 Learned" "Learned")])
          (when (state/custom-tune? tune-id)
            [:button.delete-tune {:on {:click [[:tune/delete tune-id]]}}
             "Delete"])])])))

(defn sheet-music [state]
  (let [tune (state/selected-tune state)
        abc-str (when tune (state/edited-abc-for-tune state (:id tune)))]
    [:div.sheet-area
     (if (and tune abc-str)
       [:div#sheet-music]
       [:div.sheet-empty
        (if tune
          "Select tune and open editor to add ABC notation"
          "Select a tune to view sheet music")])]))

(defn abc-editor [state]
  (let [tune (state/selected-tune state)
        tune-id (:id tune)
        abc-str (when tune (state/edited-abc-for-tune state tune-id))]
    (when tune
      [:div.editor-panel
       [:div.editor-header
        [:span.editor-label "ABC NOTATION"]
        [:div.editor-hints
         [:span.editor-hint "Chords: \"G\" \"Am\""]
         [:span.editor-hint "Notes: A-G a-g"]
         [:span.editor-hint.accent "Live preview"]]]
       [:textarea.editor-textarea
        {:value (or abc-str "")
         :placeholder "Type ABC notation here..."
         :spellcheck "false"
         :on {:input [[:editor/update tune-id :event/target.value]]
              :keydown [[:editor/keydown :event/key]]}}]])))

(defn notes-panel [state]
  (let [tune (state/selected-tune state)
        tune-id (:id tune)
        notes (when tune (get-in state [:tune-notes tune-id] ""))]
    (when (and tune (:notes-open? state))
      [:div.notes-panel
       [:div.notes-header
        [:span.notes-label "NOTES"]
        [:button.notes-close {:on {:click [[:notes/toggle]]}} "×"]]
       [:textarea.notes-textarea
        {:value (or notes "")
         :placeholder "Practice notes — BPM, ornaments, progress..."
         :spellcheck "false"
         :on {:input [[:notes/update tune-id :event/target.value]]
              :keydown [[:notes/keydown :event/key]]}}]])))

(defn playback-status [state]
  (when (:playing? state)
    (cond
      (:session-mode? state)
      (let [idx (:session-index state)
            total (count (:session-queue state))]
        [:span.playback-status (str "Session \u2014 " (inc idx) "/" total)])

      (:set-playing? state)
      (let [s (state/active-set state)
            idx (:set-tune-index state)
            total (count (:tune-ids s))
            loop? (:loop? state)]
        [:span.playback-status
         (str "Set: " (:name s) " \u2014 " (inc idx) "/" total
              (when loop? " on loop"))])

      :else
      (let [section (:playing-section state)
            part (case section :a "A part" :b "B part" "All parts")
            loop? (:loop? state)]
        [:span.playback-status (str part " playing" (when loop? " on loop"))]))))

(defn playback-bar [state]
  (let [tune (state/selected-tune state)
        tempo-str (when tune (abc/tempo-for-type (:type tune) (:time-sig tune)))
        base-bpm (when tempo-str (js/parseInt (second (re-find #"=(\d+)" tempo-str)) 10))
        offset (or (:tempo-offset state) 0)
        bpm (when base-bpm (max 40 (+ base-bpm offset)))]
    [:div.playback-bar
     [:div.left-controls
      (let [set-context? (and (:active-set-id state) (= :sets (:tab state)))]
        [:button.play-btn {:class (when (:playing? state) "playing")
                           :on {:click [[:playback/play]]}}
         (cond
           (:set-playing? state) "\u25A0 Stop Set"
           (:playing? state) "\u25A0 Stop"
           set-context? "\u25B6 Play Set"
           :else "\u25B6 Play")])
      [:button.control-btn {:class (when (:loop? state) "active")
                            :on {:click [[:loop/toggle]]}}
       (if (:loop? state) "\u21BB Loop" "Loop")]
      (playback-status state)]
     [:div.center-controls
      [:button.tempo-btn {:on {:click [[:tempo/down]]}} "\u2212"]
      [:span.tempo-label {:class (when (not= 0 offset) "tempo-modified")
                          :on {:dblclick [[:tempo/reset]]}}
       (str (or bpm "120") " BPM")]
      [:button.tempo-btn {:on {:click [[:tempo/up]]}} "+"]]
     [:div.right-controls
      [:button.control-btn {:class (when (:count-in? state) "active")
                            :on {:click [[:count-in/toggle]]}}
       "Count-in"]
      [:button.control-btn {:class (when (:metronome? state) "active")
                            :on {:click [[:metronome/toggle]]}}
       "Metronome"]
      [:button.control-btn {:class (when (:notes-open? state) "active")
                            :on {:click [[:notes/toggle]]}}
       "Notes"]
      [:button.guitar-btn {:class (when (:guitar? state) "active")
                           :on {:click [[:guitar/toggle]]}}
       (if (:guitar? state) "\uD83C\uDFB8 Guitar" "Guitar")]]]))

(defn editing-strip []
  [:div.editing-strip
   [:span.editing-strip-icon "✎"]
   [:span.editing-strip-label "EDITING TUNE"]
   [:span.editing-strip-spacer]
   [:span.editing-strip-help "Edits to ABC update sheet live · Done to save"]])

(defn set-detail-tune-row [state set-id idx tune]
  (let [learned? (state/learned? state (:id tune))]
    [:div.set-detail-tune-row
     [:span.set-detail-grip "\u283f"]
     [:span.set-detail-num (str (inc idx))]
     [:div.set-detail-tune-info
      {:on {:click [[:set/select-tune set-id (:id tune)]]}}
      [:div.set-detail-tune-name (:name tune)]
      [:div.set-detail-tune-meta
       (str (name (:type tune)) " \u00b7 " (:key tune) " \u00b7 " (:time-sig tune))]]
     (when learned?
       [:span.set-detail-check "\u2713"])
     [:button.set-detail-remove
      {:on {:click [[:set/remove-tune set-id (:id tune)]]}}
      "\u00d7"]]))

(defn set-detail-view [state]
  (when-let [s (state/active-set state)]
    (let [tunes (state/set-tunes state s)
          set-id (:id s)
          n (count (:tune-ids s))
          all-learned? (and (pos? n) (every? #(state/learned? state %) (:tune-ids s)))]
      [:div.set-detail
       [:div.set-detail-header
        [:div.set-detail-title-block
         [:div.set-detail-title (:name s)]
         [:div.set-detail-meta
          (str n " tune" (when (not= 1 n) "s")
               (when all-learned? " \u00b7 all learned"))]]
        [:div.set-detail-actions
         [:button.set-detail-play
          {:on {:click [[:set/select-tune set-id (first (:tune-ids s))]
                        [:playback/play]]}}
          "\u25b6 Play set"]
         [:button.set-detail-more "\u22ee"]]]
       [:div.divider]
       [:div.set-detail-list-label "TUNES"]
       [:div.set-detail-list
        (map-indexed (fn [i t]
                       (when t (set-detail-tune-row state set-id i t)))
                     tunes)]])))

(defn settings-view [_state]
  [:div.settings-view
   [:div.settings-header
    [:div.settings-title "Settings"]
    [:button.settings-back
     {:on {:click [[:settings/close]]}}
     "‹ Back to tunes"]]
   [:div.settings-body
    [:div.settings-card
     [:div.settings-card-label "BACKUP"]
     [:div.settings-row
      [:div.settings-row-text "Export backup as .edn"]
      [:button.settings-action
       {:on {:click [[:backup/export]]}}
       "Export"]]
     [:div.settings-row
      [:div.settings-row-text "Import backup from .edn"]
      [:button.settings-action.settings-action-secondary
       {:on {:click [[:backup/import]]}}
       "Choose file"]]]
    [:div.settings-card
     [:div.settings-card-label "ABOUT"]
     [:div.settings-row
      [:div.settings-row-text "Version"]
      [:div.settings-row-value "v0.3.0"]]
     [:div.settings-row
      [:div.settings-row-text "Source code"]
      [:a.settings-row-link
       {:href "https://github.com/anthropics/ceol" :target "_blank"}
       "github ↗"]]
     [:div.settings-row
      [:div.settings-row-text "Report an issue"]
      [:a.settings-row-link
       {:href "https://github.com/anthropics/ceol/issues" :target "_blank"}
       "open ↗"]]]
    [:div.settings-card
     [:div.settings-card-label "DATA"]
     [:div.settings-row
      [:div.settings-row-text "Clear all data (tunes, sets, edits, learned)"]
      [:button.settings-action.settings-action-danger
       {:on {:click [[:data/clear-confirm]]}}
       "Clear all data"]]]]])

(defn tune-main-view [state]
  (let [editor-open? (and (:editor-open? state) (not (:session-mode? state)))
        session? (:session-mode? state)]
    [:div.tune-main
     (when editor-open? (editing-strip))
     (tune-header (state/selected-tune state) state)
     [:div.divider]
     (sheet-music state)
     (when editor-open?
       [:div.editor-split
        [:div.split-divider
         [:div.split-line]
         [:div.split-grip "\u2261"]
         [:div.split-line]]
        (abc-editor state)])
     (notes-panel state)
     [:div.divider]
     (when-not session?
       (playback-bar state))]))

(defn main-area [state]
  [:div.main-area
   (case (:main-view state)
     :set (set-detail-view state)
     :settings (settings-view state)
     (tune-main-view state))])

(defn mobile-top-bar [state]
  (let [view (:main-view state)
        tune (state/selected-tune state)
        title (case view
                :set      (or (:name (state/active-set state)) "Set")
                :settings "Settings"
                (if tune (:name tune) "ceol"))]
    [:div.mobile-top-bar
     (if (not= :tune view)
       [:button.mobile-back
        {:on {:click [[:settings/close]]}}
        "‹"]
       [:button.mobile-menu
        {:on {:click [[:drawer/toggle]]}}
        "☰"])
     [:div.mobile-title title]
     [:span.mobile-top-bar-spacer]]))

(defn mobile-drawer [state]
  (when (:drawer-open? state)
    [:div.mobile-drawer-backdrop
     {:on {:click [[:drawer/close]]}}
     [:div.mobile-drawer
      {:on {:click [[:event/stop]]}}
      [:div.mobile-drawer-handle]
      [:div.mobile-drawer-tabs
       [:button.mobile-drawer-tab
        {:class (when (= :tunes (:tab state)) "active")
         :on {:click [[:drawer/close] [:tab/set :tunes]]}} "Tunes"]
       [:button.mobile-drawer-tab
        {:class (when (= :sets (:tab state)) "active")
         :on {:click [[:drawer/close] [:tab/set :sets]]}} "Sets"]
       [:button.mobile-drawer-tab
        {:class (when (= :session (:tab state)) "active")
         :on {:click [[:drawer/close] [:tab/set :session]]}} "Session"]]
      [:div.mobile-drawer-divider]
      [:button.mobile-drawer-item
       {:on {:click [[:drawer/close] [:settings/open]]}}
       [:span.cm-icon "⚙"] "Settings"]]]))

(defn app [state]
  [:div.app-layout {:class (when (:drawer-open? state) "drawer-open")}
   (sidebar state)
   (mobile-top-bar state)
   (main-area state)
   (mobile-drawer state)])
