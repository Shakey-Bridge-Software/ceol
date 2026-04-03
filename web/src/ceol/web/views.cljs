(ns ceol.web.views
  (:require [ceol.web.state :as state]
            [ceol.abc :as abc]))

(def tune-type-labels
  {:all "All" :polka "Polka" :jig "Jig" :reel "Reel"
   :hornpipe "Hornpipe" :slip-jig "Slip Jig" :slide "Slide" :other "Other"})

(def tune-type-order [:all :polka :jig :reel :hornpipe :slip-jig :slide :other])

(defn filter-chip [current-filter type-key]
  [:button.filter-chip
   {:class (when (= current-filter type-key) "active")
    :on {:click [[:filter/set type-key]]}}
   (get tune-type-labels type-key)])

(defn tune-row [tune selected-id]
  (let [active? (= (:id tune) selected-id)]
    [:div.tune-row {:class (when active? "active")
                    :on {:click [[:tune/select (:id tune)]]}}
     [:div.tune-dot {:class (when active? "active")}]
     [:div.tune-info
      [:div.tune-name (:name tune)]
      [:div.tune-meta
       (str (name (:type tune)) " · " (:key tune) " " (:mode-name tune) " · " (:time-sig tune))]]
     [:button.tune-add {:on {:click [[:tune/add-to-set (:id tune)]]}} "+"]]))

(defn sidebar [state]
  (let [current-filter (:filter state)
        selected-id (:selected-tune-id state)
        tunes (state/filtered-tunes state)]
    [:div.sidebar
     [:div.sidebar-header
      [:div.app-name "ceol"]
      [:div.app-tagline "IRISH TRADITIONAL MUSIC"]]
     [:div.tab-bar
      [:button.tab {:class (when (= :tunes (:tab state)) "active")
                    :on {:click [[:tab/set :tunes]]}} "Tunes"]
      [:button.tab {:class (when (= :sets (:tab state)) "active")
                    :on {:click [[:tab/set :sets]]}} "Sets"]]
     (when (= :tunes (:tab state))
       [:div.filters
        (map (fn [t] (filter-chip current-filter t)) tune-type-order)])
     [:div.tune-list
      (map (fn [t] (tune-row t selected-id)) tunes)]]))

(defn tune-header [tune state]
  (when tune
    (let [tempo-str (abc/tempo-for-type (:type tune) (:time-sig tune))
          bpm (second (re-find #"=(\d+)" tempo-str))
          section (:section state)
          editor-open? (:editor-open? state)]
      [:div.tune-header
       [:div.title-block
        [:div.tune-title (:name tune)]
        [:div.tune-title-meta
         (str (name (:type tune)) " · " (:key tune) " " (:mode-name tune)
              " · " (:time-sig tune) " · " bpm " BPM")]]
       [:div.section-controls
        [:button.section-btn {:class (when (= :a section) "active")
                              :on {:click [[:section/set :a]]}} "A"]
        [:button.section-btn {:class (when (= :b section) "active")
                              :on {:click [[:section/set :b]]}} "B"]
        [:button.section-btn {:class (when (nil? section) "active")
                              :on {:click [[:section/set nil]]}} "All"]
        [:button.edit-toggle {:class (when editor-open? "active")
                              :on {:click [[:editor/toggle]]}}
         "Edit"]]])))

(defn sheet-music [state]
  (let [tune (state/selected-tune state)
        abc-str (when tune (state/edited-abc-for-tune state (:id tune)))]
    [:div.sheet-area
     (if (and tune abc-str)
       [:div#sheet-music]
       [:div.sheet-empty
        (if tune
          "Loading notation..."
          "Select a tune to view sheet music")])]))

(defn abc-editor [state]
  (let [tune (state/selected-tune state)
        tune-id (:id tune)
        abc-str (when tune (state/edited-abc-for-tune state tune-id))]
    (when (and tune abc-str)
      [:div.editor-panel
       [:div.editor-header
        [:span.editor-label "ABC NOTATION"]
        [:div.editor-hints
         [:span.editor-hint "Chords: \"G\" \"Am\""]
         [:span.editor-hint "Notes: A-G a-g"]
         [:span.editor-hint.accent "Live preview"]]]
       [:textarea.editor-textarea
        {:value abc-str
         :spellcheck "false"
         :on {:input [[:editor/update tune-id :event/target.value]]}}]])))

(defn playback-status [state]
  (when (:playing? state)
    (let [section (:playing-section state)
          part (case section :a "A part" :b "B part" "All parts")
          loop? (:loop? state)]
      [:span.playback-status (str part " playing" (when loop? " on loop"))])))

(defn playback-bar [state]
  (let [tune (state/selected-tune state)
        tempo-str (when tune (abc/tempo-for-type (:type tune) (:time-sig tune)))
        bpm (when tempo-str (second (re-find #"=(\d+)" tempo-str)))]
    [:div.playback-bar
     [:div.left-controls
      [:button.play-btn {:class (when (:playing? state) "playing")
                         :on {:click [[:playback/play]]}}
       (if (:playing? state) "\u25A0 Stop" "\u25B6 Play")]
      [:button.control-btn {:class (when (:loop? state) "active")
                            :on {:click [[:loop/toggle]]}}
       (if (:loop? state) "\u21BB Loop" "Loop")]
      (playback-status state)]
     [:div.center-controls
      [:button.tempo-btn {:on {:click [[:tempo/down]]}} "\u2212"]
      [:span.tempo-label (str (or bpm "120") " BPM")]
      [:button.tempo-btn {:on {:click [[:tempo/up]]}} "+"]]
     [:div.right-controls
      [:button.guitar-btn {:class (when (:guitar? state) "active")
                           :on {:click [[:guitar/toggle]]}}
       (if (:guitar? state) "\uD83C\uDFB8 Guitar" "Guitar")]]]))

(defn main-area [state]
  (let [editor-open? (:editor-open? state)]
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
     (playback-bar state)]))

(defn app [state]
  [:div.app-layout
   (sidebar state)
   (main-area state)])
