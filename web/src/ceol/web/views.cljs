(ns ceol.web.views
  "Pure Replicant hiccup components. All functions take state and return hiccup.
   No side effects, no state mutation. Actions are dispatched via Replicant's
   r/set-dispatch! — components emit action vectors, never call functions."
  (:require [ceol.web.state :as state]
            [ceol.web.handlers.set-editor :as se]
            [ceol.web.handlers.session-summary :as ss]
            [ceol.web.handlers.session-nav :as nav]
            [ceol.abc :as abc]
            [clojure.string :as str]))

(def tune-type-labels
  {:all "All" :polka "Polka" :jig "Jig" :reel "Reel"
   :hornpipe "Hornpipe" :slip-jig "Slip Jig" :slide "Slide"
   :mazourka "Mazourka" :other "Other"})

(def tune-type-order [:all :polka :jig :reel :hornpipe :slip-jig :slide :mazourka :other])
(def tune-types [:polka :jig :reel :hornpipe :slip-jig :slide :mazourka :other])
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

(defn- tune-bpm [tune]
  (second (re-find #"=(\d+)" (abc/tempo-for-type (:type tune) (:time-sig tune)))))

(defn- tune-meta-line
  "Mono meta string for a tune: 'Reel · 4/4 · E Dorian', plus ' · 130 BPM' when
   bpm? is set. Used by the session-live now-playing and next-up cards."
  [tune bpm?]
  (str (get tune-type-labels (:type tune)) " · " (:time-sig tune)
       " · " (key-mode-label (:key tune) (:mode-name tune))
       (when bpm? (str " · " (tune-bpm tune) " BPM"))))

(defn- type-plural
  "Lowercase plural for the filter empty-state heading. Special-case slip-jig
  (two words) and other (don't pluralise 'others'; use 'tunes')."
  [type-key]
  (case type-key
    :slip-jig "slip jigs"
    :other "tunes"
    :all "tunes"
    (str (str/lower-case (get tune-type-labels type-key)) "s")))

(defn- next-in-cycle [coll current]
  (let [idx (.indexOf coll current)
        next-idx (mod (inc (if (neg? idx) -1 idx)) (count coll))]
    (nth coll next-idx)))

(defn filter-chip [current-filter type-key]
  [:button.filter-chip
   {:class (when (= current-filter type-key) "active")
    :on {:click [[:filter/set type-key]]}}
   (get tune-type-labels type-key)])

(defn tune-context-menu [tune-id]
  (let [catalog-tune? (state/catalog-tune? tune-id)]
    [:div.tune-context-menu {:on {:click [[:event/stop]]}}
     [:button.cm-item {:on {:click [[:menu/close] [:learned/toggle tune-id]]}}
      [:span.cm-icon "○"] "Mark as Learned"]
     [:div.cm-divider]
     [:button.cm-item {:on {:click [[:menu/close] [:tune/select tune-id] [:tune/add-to-set tune-id]]}}
      [:span.cm-icon "≡"] "Add to Set…"]
     [:div.cm-divider]
     [:button.cm-item {:on {:click [[:menu/close] [:tune/select tune-id] [:playback/play]]}}
      [:span.cm-icon "▶"] "Play"]
     (when-not catalog-tune?
       (list [:div.cm-divider]
             [:button.cm-item {:on {:click [[:menu/close] [:tune/select tune-id] [:editor/open]]}}
              [:span.cm-icon "✎"] "Edit"]))
     (list [:div.cm-divider]
           [:button.cm-item.cm-item-danger
            {:on {:click [[:menu/close] [:tune/delete tune-id]]}}
            [:span.cm-icon "✕"] "Delete"])]))

(defn tune-row [tune state]
  (let [active? (= (:id tune) (:selected-tune-id state))
        learned? (state/learned? state (:id tune))
        menu-open? (= (:context-menu-tune-id state) (:id tune))]
    [:div.tune-row-wrap
     [:div.tune-row-learn-hint
      [:span.tune-row-learn-check "✓"]
      [:span.tune-row-learn-label (if learned? "Unlearn" "Learned")]]
     [:div.tune-row {:class (str (when active? "active")
                                 (when learned? " learned"))
                     :data-tune-id (:id tune)
                     :on {:click [[:tune/select (:id tune)]]
                          :contextmenu [[:event/prevent] [:menu/open (:id tune)]]}}
      [:div.tune-info
       [:div.tune-name (:name tune)]
       [:div.tune-meta
        (str (get tune-type-labels (:type tune)) " · " (:time-sig tune) " · " (key-mode-label (:key tune) (:mode-name tune)))]]
      [:button.tune-row-menu-btn
       {:on {:click [[:event/stop] [:menu/open (:id tune)]]}}
       "⋮"]]
     ;; Desktop dropdown still renders inline (CSS hides on mobile);
     ;; mobile users get the bottom action sheet rendered from `app`.
     (when menu-open?
       (tune-context-menu (:id tune)))]))

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
             [:button.delete-set
              {:on {:click [[:confirm/open
                             {:title "Delete set?"
                              :body (str "“" (:name set-data) "” will be removed. "
                                         "Its tunes stay in your library.")
                              :destructive-label "Delete"
                              :on-confirm [[:set/delete set-id]]}]]}}
              "Delete"]])]))]))

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

(defn- sets-empty-state []
  [:div.sets-empty
   [:div.empty-icon
    [:svg {:width 24 :height 24 :viewBox "0 0 24 24" :fill "none"
           :stroke "currentColor" :stroke-width 2
           :stroke-linecap "round" :stroke-linejoin "round"}
     [:polygon {:points "12 2 2 7 12 12 22 7 12 2"}]
     [:polyline {:points "2 17 12 22 22 17"}]
     [:polyline {:points "2 12 12 17 22 12"}]]]
   [:div.empty-title "No sets yet"]
   [:div.empty-subtitle "Tap New Set to get started"]])

(defn sets-tab [state]
  [:div.sets-content
   (if (:creating-set? state)
     (set-creation-form state)
     ;; Desktop opens the inline wizard; mobile opens the full-screen editor
     ;; (Item #3). Visibility is CSS-gated per viewport — see set-editor.css.
     (list
      [:button.add-set-btn {:on {:click [[:set/start-create]]}} "+ New Set"]
      [:button.add-set-btn--mobile {:on {:click [[:set-editor/open-new]]}}
       "+ New Set"]))
   [:div.set-list
    (let [sets (vals (:sets state))]
      (if (seq sets)
        (map (fn [s] (set-card s state)) (sort-by :name sets))
        (sets-empty-state)))]])

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
     (if (pos? (count queue))
       (list
        ;; Wave 1 B — hero card (J8hkB): big learned count beside a two-line
        ;; sub-label, Start button inside.
        [:div.session-hero-card
         [:div.session-hero-label "READY TO PRACTICE"]
         [:div.session-hero-num
          [:div.session-hero-big (str learned-count)]
          [:div.session-hero-sub
           [:div (str "learned tune" (when (not= 1 learned-count) "s"))]
           [:div (str ready-sets " set" (when (not= 1 ready-sets) "s") " ready")]]]
         [:button.session-start {:on {:click [[:session/start]]}}
          [:svg.session-start-icon {:width 16 :height 16 :viewBox "0 0 24 24" :fill "none"
                                    :stroke "currentColor" :stroke-width 2
                                    :stroke-linecap "round" :stroke-linejoin "round"}
           [:polyline {:points "16 3 21 3 21 8"}]
           [:line {:x1 4 :y1 20 :x2 21 :y2 3}]
           [:polyline {:points "21 16 21 21 16 21"}]
           [:line {:x1 15 :y1 15 :x2 21 :y2 21}]
           [:line {:x1 4 :y1 4 :x2 9 :y2 9}]]
          [:span "Start session"]]]
        [:div.session-preview
         [:div.session-preview-label "SESSION QUEUE PREVIEW"]
         (map (fn [item] (session-preview-item item state)) queue)])
       [:div.session-empty
        [:div.empty-icon
         [:svg {:width 24 :height 24 :viewBox "0 0 24 24" :fill "none"
                :stroke "currentColor" :stroke-width 2
                :stroke-linecap "round" :stroke-linejoin "round"}
          [:polyline {:points "16 3 21 3 21 8"}]
          [:line {:x1 4 :y1 20 :x2 21 :y2 3}]
          [:polyline {:points "21 16 21 21 16 21"}]
          [:line {:x1 15 :y1 15 :x2 21 :y2 21}]
          [:line {:x1 4 :y1 4 :x2 9 :y2 9}]]]
        [:div.empty-title "No tunes ready yet"]
        [:div.empty-subtitle "Mark tunes as learned to start a session"]])]))

(defn session-tab-active [state]
  (let [queue     (:session-queue state)
        idx       (:session-index state)
        set-index (:session-set-index state)
        total     (count queue)
        progress  (if (pos? total) (/ (inc idx) total) 0)
        paused?   (:session-paused? state)
        cur       (nav/current-ref queue idx set-index)
        cur-tune  (when cur (state/tune-by-id state (:tune-id cur)))
        nxt       (nav/next-ref queue idx set-index)
        nxt-tune  (when nxt (state/tune-by-id state (:tune-id nxt)))]
    [:div.session-content
     [:div.session-active-header
      [:span.session-active-label "SESSION ACTIVE"]
      [:span.session-active-count (str (inc idx) " of " total)]]
     [:div.session-progress-bar
      [:div.session-progress-fill {:style {:width (str (* progress 100) "%")}}]]
     ;; Wave 1 C — now-playing card (XwIFG): meta line + Skip / Pause transport.
     [:div.session-now-playing
      [:div.session-now-label [:span.session-now-dot] "NOW PLAYING"]
      (when (:set-name cur) [:div.session-now-set (:set-name cur)])
      [:div.session-now-name (or (:name cur-tune) "Unknown")]
      (when cur-tune [:div.session-now-meta (tune-meta-line cur-tune true)])
      [:div.session-now-controls
       [:button.session-skip {:on {:click [[:session/skip]]}}
        [:svg.session-skip-icon {:width 12 :height 12 :viewBox "0 0 24 24" :fill "none"
                                 :stroke "currentColor" :stroke-width 2
                                 :stroke-linecap "round" :stroke-linejoin "round"}
         [:polygon {:points "5 4 15 12 5 20 5 4"}]
         [:line {:x1 19 :y1 5 :x2 19 :y2 19}]]
        [:span "Skip"]]
       [:button.session-pause {:on {:click [[:session/pause]]}
                               :aria-label (if paused? "Resume" "Pause")}
        (if paused?
          [:svg.session-pause-icon {:width 18 :height 18 :viewBox "0 0 24 24" :fill "currentColor"}
           [:polygon {:points "6 3 20 12 6 21 6 3"}]]
          [:svg.session-pause-icon {:width 18 :height 18 :viewBox "0 0 24 24" :fill "currentColor"}
           [:rect {:x 6 :y 4 :width 4 :height 16 :rx 1}]
           [:rect {:x 14 :y 4 :width 4 :height 16 :rx 1}]])]]]
     ;; Wave 1 C — resolved next-up card (was a "?" teaser).
     [:div.session-next
      [:div.session-next-label "NEXT UP"]
      [:div.session-next-name (or (:name nxt-tune) "—")]
      (when nxt-tune [:div.session-next-meta (tune-meta-line nxt-tune false)])]
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

(defn session-complete-summary
  "Centred end-of-session screen (Item #5, design LQ3CL). Shown when a session
   finishes naturally (handlers.session :done stows :session-result)."
  [state]
  [:div.session-content.session-complete
   [:div.session-complete-check
    [:svg {:width 30 :height 30 :viewBox "0 0 24 24" :fill "none"
           :stroke "currentColor" :stroke-width 2.5
           :stroke-linecap "round" :stroke-linejoin "round"}
     [:path {:d "M20 6 9 17l-5-5"}]]]
   [:div.session-complete-title "Practice complete"]
   [:div.session-complete-stats (ss/summary-line (:session-result state))]
   [:div.session-complete-actions
    [:button.session-complete-again {:on {:click [[:session/start]]}} "Practice again"]
    [:button.session-complete-done {:on {:click [[:session/dismiss-summary]]}} "Done"]]])

(defn session-tab [state]
  (cond
    (:session-mode? state)  (session-tab-active state)
    (:session-result state) (session-complete-summary state)
    :else                   (session-tab-pre state)))

(defn backup-status-banner
  "Transient export/import feedback (set/cleared by backup/set-status!). Shared
   by the desktop sidebar and the mobile toast wrapper (B3)."
  [state]
  (when-let [status (:backup-status state)]
    [:div.backup-status {:class (str "kind-" (name (:kind status)))}
     [:span.backup-status-icon (if (= :success (:kind status)) "✓" "!")]
     [:span.backup-status-msg (:message status)]]))

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
     (backup-status-banner state)
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
          session? (:session-mode? state)
          catalog-tune? (state/catalog-tune? (:id tune))]
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
          (when-not catalog-tune?
            [:button.edit-toggle {:class (when editor-open? "active")
                                  :on {:click [[:editor/toggle]]}}
             (if editor-open? "✓ Done" "✎ Edit")])
          (let [is-learned? (state/learned? state tune-id)]
            [:button.learned-toggle {:class (when is-learned? "active")
                                     :on {:click [[:learned/toggle tune-id]]}}
             (if is-learned? "\u2713 Learned" "Learned")])
          [:button.delete-tune {:on {:click [[:tune/delete tune-id]]}}
           "Delete"]])])))

(defn sheet-music [state]
  (let [tune (state/selected-tune state)
        abc-str (when tune (state/edited-abc-for-tune state (:id tune)))]
    [:div.sheet-area
     ;; abc.js imperatively injects an <svg> + inline style into #sheet-music.
     ;; Distinct :replicant/keys stop Replicant from reusing that managed node
     ;; for the empty-state branch — otherwise the foreign SVG lingers inside
     ;; the morphed div (see verify/newtune-render).
     (if (and tune abc-str)
       [:div#sheet-music {:replicant/key :sheet-music}]
       [:div.sheet-empty {:replicant/key :sheet-empty}
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
  ;; Always rendered when a tune is selected so the CSS transition runs in
  ;; both directions. .open class toggles slide up / slide down.
  (let [tune (state/selected-tune state)]
    (when tune
      (let [tune-id (:id tune)
            notes (get-in state [:tune-notes tune-id] "")
            open? (:notes-open? state)]
        [:div.notes-panel {:class (when open? "open")}
         [:div.notes-header
          [:span.notes-label "PRACTICE NOTES"]
          [:button.notes-close {:on {:click [[:notes/toggle]]}} "×"]]
         [:div.notes-title-block
          [:div.notes-tune-title (:name tune)]
          [:div.notes-tune-meta
           (str (get tune-type-labels (:type tune)) " · "
                (:time-sig tune) " · "
                (key-mode-label (:key tune) (:mode-name tune)))]]
         [:textarea.notes-textarea
          {:value (or notes "")
           :placeholder "Practice notes — BPM, ornaments, progress..."
           :spellcheck "false"
           :on {:input [[:notes/update tune-id :event/target.value]]
                :keydown [[:notes/keydown :event/key]]}}]
         [:div.notes-footer
          [:span.notes-saved "Saved"]
          [:span.notes-count
           (let [n (count notes)]
             (str n " character" (when (not= 1 n) "s")))]]]))))

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

;; --- Mobile playback: slim bottom bar + "NOW PLAYING" controls sheet ---

(defn- bpm-for [state tune]
  (let [tempo-str (when tune (abc/tempo-for-type (:type tune) (:time-sig tune)))
        base (when tempo-str (js/parseInt (second (re-find #"=(\d+)" tempo-str)) 10))
        offset (or (:tempo-offset state) 0)]
    {:base base :bpm (when base (max 40 (+ base offset))) :offset offset}))

(defn mobile-playback-bar [state]
  (let [tune (state/selected-tune state)
        {:keys [bpm offset]} (bpm-for state tune)
        playing? (:playing? state)]
    [:div.mobile-playback-bar
     [:div.mpb-tempo
      [:button.mpb-tempo-btn {:on {:click [[:tempo/down]]}} "−"]
      [:span.mpb-bpm {:class (when (not= 0 offset) "modified")
                      :on {:dblclick [[:tempo/reset]]}}
       (str (or bpm "120")) [:span.mpb-bpm-unit "BPM"]]
      [:button.mpb-tempo-btn {:on {:click [[:tempo/up]]}} "+"]]
     [:button.mpb-play {:class (when playing? "playing")
                        :on {:click [[:playback/play]]}}
      [:span.mpb-play-icon (if playing? "■" "▶")]]
     [:div.mpb-actions
      [:button.mpb-icon {:class (when (:notes-open? state) "active")
                         :on {:click [[:notes/toggle]]}}
       [:svg {:width 20 :height 20 :viewBox "0 0 24 24" :fill "none"
              :stroke "currentColor" :stroke-width 2 :stroke-linecap "round" :stroke-linejoin "round"}
        [:path {:d "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"}]
        [:polyline {:points "14 2 14 8 20 8"}]
        [:line {:x1 8 :y1 13 :x2 16 :y2 13}]
        [:line {:x1 8 :y1 17 :x2 16 :y2 17}]
        [:line {:x1 8 :y1 9 :x2 10 :y2 9}]]]
      [:button.mpb-icon {:class (when (:controls-sheet-open? state) "active")
                         :on {:click [[:controls/toggle]]}}
       [:svg {:width 20 :height 20 :viewBox "0 0 24 24" :fill "none"
              :stroke "currentColor" :stroke-width 2 :stroke-linecap "round" :stroke-linejoin "round"}
        [:line {:x1 21 :y1 4 :x2 14 :y2 4}]
        [:line {:x1 10 :y1 4 :x2 3 :y2 4}]
        [:line {:x1 21 :y1 12 :x2 12 :y2 12}]
        [:line {:x1 8 :y1 12 :x2 3 :y2 12}]
        [:line {:x1 21 :y1 20 :x2 16 :y2 20}]
        [:line {:x1 12 :y1 20 :x2 3 :y2 20}]
        [:line {:x1 14 :y1 2 :x2 14 :y2 6}]
        [:line {:x1 8 :y1 10 :x2 8 :y2 14}]
        [:line {:x1 16 :y1 18 :x2 16 :y2 22}]]]]]))

(defn controls-sheet [state]
  ;; Always rendered so the CSS transition runs in both directions.
  ;; .open on the backdrop fades it in and slides the sheet up.
  (let [tune (state/selected-tune state)
        section (:section state)
        {:keys [base bpm offset]} (bpm-for state tune)
        playing? (:playing? state)
        open? (:controls-sheet-open? state)]
    [:div.controls-sheet-backdrop {:class (when open? "open")
                                   :on {:click [[:controls/close]]}}
     [:div.controls-sheet {:on {:click [[:event/stop]]}}
        [:div.mobile-drawer-handle]
        [:div.controls-sheet-head
         [:span.controls-sheet-label "NOW PLAYING"]
         [:button.controls-sheet-close {:on {:click [[:controls/close]]}} "×"]]
        (when tune
          (let [tune-id (:id tune)
                learned? (state/learned? state tune-id)]
            [:div.controls-sheet-title-row
             [:div.controls-sheet-title-block
              [:div.controls-sheet-title (:name tune)]
              [:div.controls-sheet-meta
               (str (get tune-type-labels (:type tune)) " · " (:time-sig tune) " · "
                    (key-mode-label (:key tune) (:mode-name tune)))]]
             [:button.cs-learned-icon
              {:class (when learned? "active")
               :title (if learned? "Mark as not learned" "Mark as learned")
               :on {:click [[:learned/toggle tune-id]]}}
              [:svg {:width 22 :height 22 :viewBox "0 0 24 24" :fill "none"
                     :stroke "currentColor" :stroke-width 2 :stroke-linecap "round" :stroke-linejoin "round"}
               [:path {:d "M22 11.08V12a10 10 0 1 1-5.93-9.14"}]
               [:polyline {:points "22 4 12 14.01 9 11.01"}]]]]))
        [:div.controls-sheet-parts
         [:button.cs-part {:class (when (= :a section) "active") :on {:click [[:section/set :a]]}} "A part"]
         [:button.cs-part {:class (when (= :b section) "active") :on {:click [[:section/set :b]]}} "B part"]
         [:button.cs-part {:class (when (nil? section) "active") :on {:click [[:section/set nil]]}} "All"]]
        [:div.controls-sheet-tempo
         [:span.cs-tempo-label "TEMPO"]
         [:div.cs-tempo-row
          [:button.cs-tempo-btn {:on {:click [[:tempo/down]]}} "−"]
          [:div.cs-tempo-val (str (or bpm "120")) [:span.cs-tempo-unit "BPM"]]
          [:button.cs-tempo-btn {:on {:click [[:tempo/up]]}} "+"]]
         [:button.cs-tempo-reset {:class (when (zero? offset) "hidden")
                                  :on {:click [[:tempo/reset]]}}
          (str "Reset to default (" (or base "—") ")")]]
        [:div.controls-sheet-grid
         [:button.cs-toggle {:class (when (:loop? state) "active") :on {:click [[:loop/toggle]]}}
          [:span.cs-toggle-icon
           [:svg {:width 22 :height 22 :viewBox "0 0 24 24" :fill "none"
                  :stroke "currentColor" :stroke-width 2 :stroke-linecap "round" :stroke-linejoin "round"}
            [:polyline {:points "17 1 21 5 17 9"}]
            [:path {:d "M3 11V9a4 4 0 0 1 4-4h14"}]
            [:polyline {:points "7 23 3 19 7 15"}]
            [:path {:d "M21 13v2a4 4 0 0 1-4 4H3"}]]]
          [:span "Loop"]]
         [:button.cs-toggle {:class (when (:guitar? state) "active") :on {:click [[:guitar/toggle]]}}
          [:span.cs-toggle-icon
           [:svg {:width 22 :height 22 :viewBox "0 0 24 24" :fill "none"
                  :stroke "currentColor" :stroke-width 2 :stroke-linecap "round" :stroke-linejoin "round"}
            [:path {:d "m11.9 12.1 4.514-4.514"}]
            [:path {:d "M20.1 2.3a1 1 0 0 0-1.4 0l-1.114 1.114A2 2 0 0 0 17 4.828v1.344a2 2 0 0 1-.586 1.414A2 2 0 0 1 17.828 7h1.344a2 2 0 0 0 1.414-.586L21.7 5.3a1 1 0 0 0 0-1.4z"}]
            [:path {:d "m6 16 2 2"}]
            [:path {:d "M8.2 9.9C8.7 8.8 9.8 8 11 8c2.8 0 5 2.2 5 5 0 1.2-.8 2.3-1.9 2.8l-.9.4A2 2 0 0 0 12 18a4 4 0 0 1-4 4c-3.3 0-6-2.7-6-6a4 4 0 0 1 4-4 2 2 0 0 0 1.8-1.2z"}]]]
          [:span "Guitar"]]
         [:button.cs-toggle {:class (when (:count-in? state) "active") :on {:click [[:count-in/toggle]]}}
          [:span.cs-toggle-icon "⏱"] [:span "Count-in"]]
         [:button.cs-toggle {:class (when (:metronome? state) "active") :on {:click [[:metronome/toggle]]}}
          [:span.cs-toggle-icon "♪"] [:span "Metronome"]]]
        (when tune
          (let [tune-id (:id tune)
                catalog-tune? (state/catalog-tune? tune-id)]
            [:div.controls-sheet-grid
             (when-not catalog-tune?
               [:button.cs-toggle {:on {:click [[:controls/close] [:editor/open]]}}
                [:span.cs-toggle-icon
                  [:svg {:width  22 :height 22 :viewBox "0 0 24 24" :fill "none"
                        :stroke "currentColor" :stroke-width 2 :stroke-linecap "round" :stroke-linejoin "round"}
                  [:path {:d "M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"}]]]
                [:span "Edit"]])
             [:button.cs-toggle {:on {:click [[:controls/close] [:tune/add-to-set tune-id]]}}
              [:span.cs-toggle-icon
               [:svg {:width 22 :height 22 :viewBox "0 0 24 24" :fill "none"
                      :stroke "currentColor" :stroke-width 2 :stroke-linecap "round" :stroke-linejoin "round"}
                [:line {:x1 8 :y1 6 :x2 21 :y2 6}]
                [:line {:x1 8 :y1 12 :x2 21 :y2 12}]
                [:line {:x1 8 :y1 18 :x2 15 :y2 18}]
                [:polyline {:points "18 16 18 22 12 22"}]
                [:line {:x1 3 :y1 6 :x2 3.01 :y2 6}]
                [:line {:x1 3 :y1 12 :x2 3.01 :y2 12}]
                [:line {:x1 3 :y1 18 :x2 3.01 :y2 18}]]]
              [:span "Add to Set"]]]))
     [:button.controls-sheet-play {:class (when playing? "playing")
                                   :on {:click [[:playback/play]]}}
      [:span.cs-play-icon (if playing? "■" "▶")]
      (if playing? "STOP" "PLAY")]]]))

(defn set-detail-tune-row [state set-id idx tune]
  (let [learned? (state/learned? state (:id tune))]
    [:div.set-detail-tune-row
     [:span.set-detail-grip "\u283f"]
     [:span.set-detail-num (str (inc idx))]
     [:div.set-detail-tune-info
      {:on {:click [[:set/select-tune set-id (:id tune)]]}}
      [:div.set-detail-tune-name (:name tune)]
      [:div.set-detail-tune-meta
       (str (get tune-type-labels (:type tune)) " \u00b7 " (:time-sig tune) " \u00b7 " (key-mode-label (:key tune) (:mode-name tune)))]]
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
         [:button.set-detail-more
          {:on {:click [[:set-menu/open set-id]]}} "\u22ee"]]]
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
     ;; Wave 1 E — Export/Import as tappable icon + text + chevron list rows
     ;; (design ddeLd); same handlers as the old label+button rows.
     [:button.settings-list-row {:on {:click [[:backup/export]]}}
      [:span.settings-list-icon
       [:svg {:width 18 :height 18 :viewBox "0 0 24 24" :fill "none"
              :stroke "currentColor" :stroke-width 2
              :stroke-linecap "round" :stroke-linejoin "round"}
        [:path {:d "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"}]
        [:polyline {:points "7 10 12 15 17 10"}]
        [:line {:x1 12 :y1 15 :x2 12 :y2 3}]]]
      [:div.settings-list-body
       [:div.settings-list-title "Export backup"]
       [:div.settings-list-sub "Save all data as a .edn file"]]
      [:span.settings-list-chevron "›"]]
     [:div.settings-list-divider]
     [:button.settings-list-row
      {:on {:click [[:confirm/open
                     {:title "Replace all data?"
                      :body (str "Importing a backup overwrites every custom "
                                 "tune, set, edit, and learned mark on this "
                                 "device. Export your current data first if "
                                 "you want to keep it.")
                      :destructive-label "Choose file"
                      :on-confirm [[:backup/import]]}]]}}
      [:span.settings-list-icon
       [:svg {:width 18 :height 18 :viewBox "0 0 24 24" :fill "none"
              :stroke "currentColor" :stroke-width 2
              :stroke-linecap "round" :stroke-linejoin "round"}
        [:path {:d "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"}]
        [:polyline {:points "17 8 12 3 7 8"}]
        [:line {:x1 12 :y1 3 :x2 12 :y2 15}]]]
      [:div.settings-list-body
       [:div.settings-list-title "Import backup"]
       [:div.settings-list-sub "Restore from a .edn file"]]
      [:span.settings-list-chevron "›"]]]
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
  (let [selected-tune (state/selected-tune state)
        catalog-tune? (state/catalog-tune? (:id selected-tune))
        editor-open? (and (:editor-open? state)
                          (not (:session-mode? state))
                          (not catalog-tune?))
        session? (:session-mode? state)]
    [:div.tune-main
     (when editor-open? (editing-strip))
     (tune-header selected-tune state)
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
       (list
        ;; Desktop bar — hidden ≤720px (see style.css)
        (playback-bar state)
        ;; Mobile slim bar — hidden >720px
        (mobile-playback-bar state)))]))

(defn- mobile-set-card
  "Always-expanded set card for the mobile sets list (design 7VNKz): numbered
   tune list, a learned-progress footer (one dot — green when all learned, amber
   otherwise), and Play set. Tapping the card drills into the set detail (where
   the ⋮ opens the action sheet); the Play button stops that bubble."
  [set-data state]
  (let [set-id    (:id set-data)
        tune-ids  (:tune-ids set-data)
        tunes     (state/set-tunes state set-data)
        total     (count tune-ids)
        learned-n (count (filter #(state/learned? state %) tune-ids))
        all?      (and (pos? total) (= learned-n total))]
    [:div.mset-card {:on {:click [[:set/toggle set-id]]}}
     [:div.mset-header
      [:div.mset-name (:name set-data)]
      [:div.mset-count (str total " tune" (when (not= 1 total) "s"))]]
     [:ol.mset-tunes
      (map-indexed
       (fn [i t]
         (when t
           [:li.mset-tune
            [:span.mset-num (str (inc i) ".")]
            [:span.mset-tune-name (:name t)]]))
       tunes)]
     [:div.mset-footer
      [:span.mset-progress {:class (if all? "all" "partial")}
       [:span.mset-dot]
       (if all? "All learned" (str learned-n " of " total " learned"))]
      (when (pos? total)
        [:button.mset-play
         {:on {:click [[:event/stop] [:set/select-tune set-id (first tune-ids)] [:playback/play]]}}
         [:span.mset-play-icon "▶"] " Play set"])]]))

(defn- mobile-sets-tab
  "Mobile sets list (Wave 1 D): always-expanded cards, no accordion. New-set opens
   the full-screen editor (Item #3); desktop's inline wizard stays in sets-tab."
  [state]
  [:div.sets-content
   [:button.add-set-btn--mobile {:on {:click [[:set-editor/open-new]]}} "+ New Set"]
   (let [sets (vals (:sets state))]
     (if (seq sets)
       [:div.mset-list (map (fn [s] (mobile-set-card s state)) (sort-by :name sets))]
       (sets-empty-state)))])

(defn mobile-list-view [state]
  (let [tunes (state/filtered-tunes state)
        current-filter (:filter state)
        current-tab (:tab state)]
    [:div.mobile-list-view
     [:div.mobile-list-header
      [:div.mobile-list-title-row
       [:div.mobile-list-logo-block
        [:span.mobile-list-logo "ceol"]
        [:span.mobile-list-version "v0.3.0"]]
       [:button.mobile-list-menu {:on {:click [[:settings/open]]}} "⚙"]]
      [:div.mobile-list-tagline "PRACTICE COMPANION"]]
     [:div.mobile-list-tabs
      [:div.mobile-tabs-inner
       [:button.mobile-tab {:class (when (= :tunes current-tab) "active")
                            :on {:click [[:tab/set :tunes]]}} "Tunes"]
       [:button.mobile-tab {:class (when (= :sets current-tab) "active")
                            :on {:click [[:tab/set :sets]]}} "Sets"]
       [:button.mobile-tab {:class (when (= :session current-tab) "active")
                            :on {:click [[:tab/set :session]]}} "Session"]]]
     (case current-tab
       :sets (mobile-sets-tab state)
       :session (session-tab state)
       (list
        [:div.mobile-filters
         (map (fn [t] (filter-chip current-filter t)) tune-type-order)]
        (if (empty? tunes)
          [:div.mobile-empty-state
           [:div.empty-icon
            [:svg {:width 24 :height 24 :viewBox "0 0 24 24" :fill "none"
                   :stroke "currentColor" :stroke-width 2
                   :stroke-linecap "round" :stroke-linejoin "round"}
             [:polygon {:points "22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3"}]]]
           [:div.empty-title (str "No " (type-plural current-filter) " yet")]
           [:div.empty-subtitle "Try another filter or add a tune"]]
          [:div.mobile-tune-list
           (map (fn [t] (tune-row t state)) tunes)])))
     [:button.mobile-fab {:on {:click [[:tune-editor/open-new]]}}
      [:span.mobile-fab-icon "+"]]]))

(defn main-area [state]
  [:div.main-area
   (case (:main-view state)
     :settings (settings-view state)
     :set      (set-detail-view state)
     ;; :tune (default) — render both layouts; CSS shows tune-main on desktop
     ;; (and on mobile detail), mobile-list when the phone is in list mode
     ;; (.app-layout.show-mobile-list, see app + style.css).
     (list (tune-main-view state)
           (mobile-list-view state)))])

(defn mobile-top-bar [state]
  (let [main-view (:main-view state)
        mobile-view (:mobile-view state)
        tune (state/selected-tune state)
        section (:section state)
        in-detail? (and (= :tune main-view) (= :detail mobile-view))
        title (case main-view
                :set      (or (:name (state/active-set state)) "Set")
                :settings "Settings"
                (if in-detail? (:name tune) "ceol"))
        show-back? (or in-detail? (not= :tune main-view))]
    [:div.mobile-top-bar {:class (when (and in-detail? tune) "with-detail")}
     (if show-back?
       [:button.mobile-back {:on {:click [[:mobile/back]]}} "‹"]
       [:span.mobile-top-bar-spacer])
     (if (and in-detail? tune)
       [:div.mobile-title-block
        [:div.mobile-title title]
        [:div.mobile-title-meta
         (str (get tune-type-labels (:type tune)) " · "
              (:time-sig tune) " · "
              (key-mode-label (:key tune) (:mode-name tune)))]]
       [:div.mobile-title title])
     (if (and in-detail? tune (not (:session-mode? state)))
       [:div.mobile-top-section-btns
        [:button.mts-btn {:class (when (= :a section) "active")
                          :on {:click [[:section/set :a]]}} "A"]
        [:button.mts-btn {:class (when (= :b section) "active")
                          :on {:click [[:section/set :b]]}} "B"]
        [:button.mts-btn {:class (when (nil? section) "active")
                          :on {:click [[:section/set nil]]}} "All"]]
       [:span.mobile-top-bar-spacer])]))

(def ^:private editor-types
  ;; Types shown as chip row in the mobile editor. Matches the design (5 types
  ;; fit cleanly on a 390-wide screen); the app's :slide :mazourka :other are
  ;; still usable via desktop, but rare for hand-entered tunes.
  [:polka :jig :reel :hornpipe :slip-jig])

(def ^:private editor-keys ["C" "D" "E" "F" "G" "A" "B"])

(def ^:private editor-modes
  ;; Friendly labels for the mode dropdown; :value maps to :mode-name.
  [{:value "Ionian"     :label "major"}
   {:value "Dorian"     :label "dorian"}
   {:value "Mixolydian" :label "mixolydian"}
   {:value "Aeolian"    :label "minor"}])

(defn- te-chip [{:keys [active? label on-click]}]
  [:button.te-chip {:class (when active? "active")
                    :on {:click on-click}} label])

(def ^:private action-sheet-rows
  ;; Order matches design qgF2n: Edit details / Edit notation / Add to set /
  ;; Duplicate / Delete. Each row builds its dispatch from the tune-id.
  [{:icon "✎" :label "Edit details"
    :build (fn [id] [[:menu/close] [:tune-editor/open-edit id]])}
   {:icon "♪" :label "Edit notation"
    :build (fn [id] [[:menu/close] [:tune/select id] [:editor/open]])}
   {:icon "≡" :label "Add to set"
    :build (fn [id] [[:menu/close] [:tune/select id] [:tune/add-to-set id]])}
   {:icon "⧉" :label "Duplicate"
    :build (fn [id] [[:menu/close] [:tune/duplicate id]])}
   {:icon "✕" :label "Delete" :danger? true
    :build (fn [id] [[:menu/close] [:delete/request id]])}])

(defn mobile-bottom-sheet
  "Shared mobile bottom-sheet shell (design qgF2n / kihYP). `rows` is a seq of
   {:icon :label :danger? :on-click}; `on-close` is the actions vector run by
   both the backdrop and the Cancel button. Used by the tune + set action
   sheets — the only thing that differs between them is the card + rows."
  [{:keys [title subtitle rows on-close]}]
  [:div.as-overlay {:on {:click on-close}}
   [:div.as-sheet {:on {:click [[:event/stop]]}}
    [:div.as-handle [:div.as-handle-bar]]
    [:div.as-card
     [:div.as-title title]
     [:div.as-meta subtitle]]
    [:div.as-divider]
    [:div.as-actions
     (map (fn [{:keys [icon label danger? on-click]}]
            [:button.as-row {:class (when danger? "as-row-danger")
                             :on {:click on-click}}
             [:span.as-row-icon icon]
             [:span.as-row-label label]])
          rows)]
    [:div.as-cancel-wrap
     [:button.as-cancel {:on {:click on-close}} "Cancel"]]]])

(defn- sheet-rows
  "Resolve a row-spec table ({:icon :label :danger? :build}) against `arg` into
   the {:icon :label :danger? :on-click} rows mobile-bottom-sheet renders."
  [row-specs arg]
  (map (fn [{:keys [icon label danger? build]}]
         {:icon icon :label label :danger? danger? :on-click (build arg)})
       row-specs))

(defn mobile-tune-action-sheet [state]
  (when-let [tune-id (:context-menu-tune-id state)]
    (when-let [tune (state/tune-by-id state tune-id)]
      (mobile-bottom-sheet
       {:title    (:name tune)
        :subtitle (str (get tune-type-labels (:type tune)) " · "
                       (:time-sig tune) " · "
                       (key-mode-label (:key tune) (:mode-name tune)))
        :on-close [[:menu/close]]
        :rows     (sheet-rows action-sheet-rows tune-id)}))))

(def ^:private set-action-sheet-rows
  ;; Order matches design kihYP: Play set / Edit set / Duplicate / Delete.
  ;; Each row builds its dispatch from the set-data map.
  [{:icon "▶" :label "Play set"
    :build (fn [{:keys [id tune-ids]}]
             [[:set-menu/close] [:set/select-tune id (first tune-ids)] [:playback/play]])}
   {:icon "✎" :label "Edit set"
    :build (fn [{:keys [id]}] [[:set-menu/close] [:set-editor/open-edit id]])}
   {:icon "⧉" :label "Duplicate"
    :build (fn [{:keys [id]}] [[:set-menu/close] [:set/duplicate id]])}
   {:icon "✕" :label "Delete" :danger? true
    :build (fn [{:keys [id name]}]
             [[:set-menu/close]
              [:confirm/open {:title "Delete set?"
                              :body (str "“" name "” will be removed. "
                                         "Its tunes stay in your library.")
                              :destructive-label "Delete"
                              :on-confirm [[:set/delete id]]}]])}])

(defn mobile-set-action-sheet [state]
  (when-let [set-id (:context-menu-set-id state)]
    (when-let [set-data (get (:sets state) set-id)]
      (let [n (count (:tune-ids set-data))
            all-learned? (and (pos? n)
                              (every? #(state/learned? state %) (:tune-ids set-data)))]
        (mobile-bottom-sheet
         {:title    (:name set-data)
          :subtitle (str n " tune" (when (not= 1 n) "s")
                         (when all-learned? " · all learned"))
          :on-close [[:set-menu/close]]
          :rows     (sheet-rows set-action-sheet-rows set-data)})))))

(defn mobile-tune-editor [state]
  (when-let [{:keys [mode draft]} (:tune-editor state)]
    (let [title (if (= mode :new) "New tune" "Edit tune")]
      [:div.te-overlay
       [:div.te-top
        [:button.te-cancel {:on {:click [[:tune-editor/cancel]]}} "Cancel"]
        [:div.te-title title]
        [:button.te-save {:on {:click [[:tune-editor/save]]}} "Save"]]
       [:div.te-body
        ;; NAME
        [:div.te-section
         [:div.te-label "NAME"]
         [:input.te-input
          {:type "text"
           :value (:name draft)
           :placeholder "Tune name"
           :auto-focus (= mode :new)
           :on {:input [[:tune-editor/update-draft :name :event/target.value]]}}]]
        ;; TYPE
        [:div.te-section
         [:div.te-label "TYPE"]
         [:div.te-chip-row
          (map (fn [t]
                 (te-chip {:active?  (= t (:type draft))
                           :label    (get tune-type-labels t)
                           :on-click [[:tune-editor/update-draft :type t]]}))
               editor-types)]]
        ;; KEY + MODE row — :selected on the matching option (HTML doesn't honour
        ;; `value` on <select> directly; Replicant doesn't normalise this for us).
        [:div.te-row
         [:div.te-section
          [:div.te-label "KEY"]
          [:div.te-select-wrap
           [:select.te-select
            {:on {:change [[:tune-editor/update-draft :key :event/target.value]]}}
            (map (fn [k]
                   [:option (cond-> {:value k}
                              (= k (:key draft)) (assoc :selected true))
                    k])
                 editor-keys)]
           [:span.te-select-caret "›"]]]
         [:div.te-section
          [:div.te-label "MODE"]
          [:div.te-select-wrap
           [:select.te-select
            {:on {:change [[:tune-editor/update-draft :mode-name :event/target.value]]}}
            (map (fn [{:keys [value label]}]
                   [:option (cond-> {:value value}
                              (= value (:mode-name draft)) (assoc :selected true))
                    label])
                 editor-modes)]
           [:span.te-select-caret "›"]]]]
        ;; TIME SIGNATURE
        [:div.te-section
         [:div.te-label "TIME SIGNATURE"]
         [:div.te-chip-row
          (map (fn [ts]
                 (te-chip {:active?  (= ts (:time-sig draft))
                           :label    ts
                           :on-click [[:tune-editor/update-draft :time-sig ts]]}))
               time-sigs)]]
        ;; SESSION ID
        [:div.te-section
         [:div.te-label "THESESSION.ORG ID  (optional)"]
         [:input.te-input
          {:type "text"
           :inputmode "numeric"
           :value (:session-id draft)
           :placeholder "e.g. 1834"
           :on {:input [[:tune-editor/update-draft :session-id :event/target.value]]}}]]]])))

(defn delete-confirm-modal [state]
  (when-let [tune-id (:delete-confirm-tune-id state)]
    (let [tune (state/tune-by-id state tune-id)]
      [:div.modal-backdrop {:on {:click [[:delete/cancel]]}}
       [:div.modal {:on {:click [[:event/stop]]}}
        [:div.modal-title "Delete tune?"]
        [:div.modal-body
         (str "“" (:name tune) "” will be removed permanently. Cannot be undone.")]
        [:div.modal-actions
         [:button.modal-cancel {:on {:click [[:delete/cancel]]}} "Cancel"]
         [:button.modal-destructive
          {:on {:click [[:delete/confirm tune-id]]}} "Delete"]]]])))


(defn confirm-modal
  "Generic confirm modal. State slot :confirm holds the payload:
     {:title \"...\" :body \"...\" :destructive-label \"...\"
      :on-confirm [[:action arg] ...]}
   The destructive button fires the :on-confirm actions and then closes
   the slot via [:confirm/cancel]."
  [state]
  (when-let [{:keys [title body destructive-label on-confirm]} (:confirm state)]
    [:div.modal-backdrop {:on {:click [[:confirm/cancel]]}}
     [:div.modal {:on {:click [[:event/stop]]}}
      [:div.modal-title title]
      [:div.modal-body body]
      [:div.modal-actions
       [:button.modal-cancel {:on {:click [[:confirm/cancel]]}} "Cancel"]
       [:button.modal-destructive
        {:on {:click (vec (concat on-confirm [[:confirm/cancel]]))}}
        (or destructive-label "OK")]]]]))

(defn onboarding-coachmark [state]
  (when (and (not (:onboarded? state))
             (seq (state/filtered-tunes state)))
    [:div.coachmark-overlay
     {:on {:click [[:onboarding/dismiss]]}}
     [:div.coachmark-app-header
      [:div.coachmark-logo "ceol"]
      [:div.coachmark-tagline "PRACTICE COMPANION"]]
     [:div.coachmark-hint-area
      [:div.coachmark-peek-row
       [:div.coachmark-learned-action
        [:span.coachmark-learned-icon "✓"]
        "Learned"]
       [:div.coachmark-tune-cell
        [:div.coachmark-tune-name "The Kerry Polka"]
        [:div.coachmark-tune-meta "Polka · 2/4 · G major"]]]]
     [:div.coachmark-cap1 "SWIPE RIGHT TO MARK AS LEARNED"]
     [:div.coachmark-cap2 "swipe left for Edit / Delete"]
     [:button.coachmark-ok {:on {:click [[:onboarding/dismiss]]}} "Got it"]]))

;; --- Mobile full-screen set editor (Item #3, design he1dM) ------------------
;; Reuses the .te-* overlay shell (Cancel / Title / Save + scroll body) shared
;; with the tune editor — that shell is already mobile-only via @media. Set
;; rows, grip, remove and the add-tune picker carry .se-* classes. The grip
;; (data-idx / data-tune-id) is the drag-reorder handle wired in gesture.cljs;
;; the reorder itself is the pure :set-editor/reorder action.

(defn- se-tune-row [idx tune]
  [:div.se-tune-row {:data-idx idx}
   [:span.se-grip "⠿"]
   [:div.se-tune-info
    [:div.se-tune-name (:name tune)]
    [:div.se-tune-meta
     (str (get tune-type-labels (:type tune)) " · " (:time-sig tune) " · "
          (key-mode-label (:key tune) (:mode-name tune)))]]
   [:button.se-tune-remove
    {:on {:click [[:set-editor/remove-tune (:id tune)]]}} "×"]])

(defn- se-picker
  "Tune search inside the editor — results exclude tunes already in the draft."
  [state draft-tune-ids]
  (let [in-draft? (set draft-tune-ids)
        query     (:typeahead-query state)
        ;; Pull a generous candidate set, exclude already-added tunes, THEN
        ;; cap — capping first could hide matches when the top hits are all
        ;; already in the draft.
        results   (->> (state/search-tunes state query 50)
                       (remove #(in-draft? (:id %)))
                       (take 6))]
    [:div.se-picker
     [:div.se-picker-bar
      [:input.se-picker-input
       {:type "text" :placeholder "Search tunes..." :value query :auto-focus true
        :on {:input [[:set/typeahead :event/target.value]]}}]
      [:button.se-picker-done {:on {:click [[:set-editor/stop-pick]]}} "Done"]]
     (when (seq results)
       [:div.se-picker-results
        (map (fn [tune]
               [:div.se-picker-item {:on {:click [[:set-editor/add-tune (:id tune)]]}}
                [:span.se-picker-name (:name tune)]
                [:span.se-picker-type (get tune-type-labels (:type tune))]])
             results)])]))

(defn set-editor [state]
  (when-let [{:keys [mode draft picking?]} (:set-editor state)]
    (let [tune-ids (:tune-ids draft)
          title    (if (= mode :new) "New set" "Edit set")
          saveable? (se/can-save? draft)]
      [:div.te-overlay.se-overlay
       [:div.te-top
        [:button.te-cancel {:on {:click [[:set-editor/cancel]]}} "Cancel"]
        [:div.te-title title]
        [:button.te-save {:class (when-not saveable? "disabled")
                          :on {:click [[:set-editor/save]]}} "Save"]]
       [:div.te-body
        [:div.te-section
         [:div.te-label "NAME"]
         [:input.te-input
          {:type "text" :value (:name draft) :placeholder "Set name..."
           :on {:input [[:set-editor/update-draft :name :event/target.value]]}}]]
        [:div.te-section
         [:div.se-tunes-header
          [:div.te-label "TUNES"]
          [:div.se-tunes-count (str (count tune-ids))]]
         [:div.se-tune-list
          (map-indexed (fn [i tid]
                         (when-let [t (state/tune-by-id state tid)]
                           (se-tune-row i t)))
                       tune-ids)]
         (if picking?
           (se-picker state tune-ids)
           [:button.se-add-tune {:on {:click [[:set-editor/start-pick]]}}
            [:span.se-add-tune-icon "+"]
            [:span "Add tune"]])]]])))

(defn mobile-backup-status
  "Mobile toast wrapper for the backup-status banner (B3). The desktop banner
   lives in the sidebar, which is display:none on mobile — so mobile users got
   no export/import feedback. Fixed-position, mobile-only via CSS."
  [state]
  (when (:backup-status state)
    [:div.mobile-backup-status (backup-status-banner state)]))

(defn app [state]
  [:div.app-layout
   {:class (cond-> []
             (and (= :tune (:main-view state)) (= :list (:mobile-view state)))
             (conj "show-mobile-list"))}
   (sidebar state)
   (mobile-top-bar state)
   (main-area state)
   (controls-sheet state)
   (delete-confirm-modal state)
   (confirm-modal state)
   (mobile-tune-action-sheet state)
   (mobile-set-action-sheet state)
   (mobile-tune-editor state)
   (set-editor state)
   (mobile-backup-status state)
   (onboarding-coachmark state)])
