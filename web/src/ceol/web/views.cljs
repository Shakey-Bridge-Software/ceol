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

(defn tune-row [tune selected-id learned-ids]
  (let [active? (= (:id tune) selected-id)
        learned? (contains? (or learned-ids #{}) (:id tune))]
    [:div.tune-row {:class (when active? "active")
                    :on {:click [[:tune/select (:id tune)]]}}
     [:div.tune-dot {:class (when active? "active")}]
     [:div.tune-info
      [:div.tune-name (:name tune)]
      [:div.tune-meta
       (str (name (:type tune)) " · " (:key tune) " " (:mode-name tune) " · " (:time-sig tune))]]
     (when learned?
       [:span.learned-indicator "\u2713"])]))

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
      [:div.session-next-val
       (let [result (state/advance-session queue idx (:session-set-index state) false)
             next-item (when (= :next-item (:action result))
                         (nth queue (:session-index result) nil))]
         (if next-item
           (case (:type next-item)
             :set  (:name next-item)
             :tune (let [t (state/tune-by-id state (:tune-id next-item))]
                     (:name t))
             "?")
           "—"))]]
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
      [:div.app-name "ceol"]
      [:div.app-tagline "IRISH TRADITIONAL MUSIC"]]
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
          (map (fn [t] (filter-chip current-filter t)) tune-type-order)]
         [:button.add-tune-btn {:on {:click [[:tune/add]]}} "+"]]
        [:div.tune-list
         (map (fn [t] (tune-row t selected-id learned-ids)) tunes)]])]))

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
          [:div.tune-title {:on {:click [[:field/edit :name]]}}
           (:name tune)])
        ;; Editable metadata
        [:div.tune-title-meta
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
           "Edit"]
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
       "Metro"]
      [:button.guitar-btn {:class (when (:guitar? state) "active")
                           :on {:click [[:guitar/toggle]]}}
       (if (:guitar? state) "\uD83C\uDFB8 Guitar" "Guitar")]]]))

(defn main-area [state]
  (let [editor-open? (and (:editor-open? state) (not (:session-mode? state)))
        session? (:session-mode? state)]
    [:div.main-area
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
     [:div.divider]
     (when-not session?
       (playback-bar state))]))

(defn app [state]
  [:div.app-layout
   (sidebar state)
   (main-area state)])
