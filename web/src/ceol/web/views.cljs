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

(defn tune-header [tune]
  (when tune
    (let [tempo-str (abc/tempo-for-type (:type tune) (:time-sig tune))
          bpm (second (re-find #"=(\d+)" tempo-str))]
      [:div.tune-header
       [:div.title-block
        [:div.tune-title (:name tune)]
        [:div.tune-title-meta
         (str (name (:type tune)) " · " (:key tune) " " (:mode-name tune)
              " · " (:time-sig tune) " · " bpm " BPM")]]
       [:div.section-controls
        [:button.section-btn.active "A"]
        [:button.section-btn "B"]
        [:button.section-btn "All"]]])))

(defn sheet-music [state]
  (let [tune (state/selected-tune state)
        abc-str (when tune (state/abc-for-tune state (:id tune)))]
    [:div.sheet-area
     (if (and tune abc-str)
       [:div#sheet-music {:replicant/on-render [[:abc/render abc-str tune]]}]
       [:div.sheet-empty
        (if tune
          "Loading notation..."
          "Select a tune to view sheet music")])]))

(defn playback-bar []
  [:div.playback-bar
   [:div.left-controls
    [:button.play-btn {:on {:click [[:playback/play]]}}
     "\u25B6 Play"]
    [:button.control-btn "Loop"]]
   [:div.center-controls
    [:button.tempo-btn {:on {:click [[:tempo/down]]}} "\u2212"]
    [:span.tempo-label "120 BPM"]
    [:button.tempo-btn {:on {:click [[:tempo/up]]}} "+"]]
   [:div.right-controls
    [:button.guitar-btn "Guitar"]]])

(defn main-area [state]
  [:div.main-area
   (tune-header (state/selected-tune state))
   [:div.divider]
   (sheet-music state)
   [:div.divider]
   (playback-bar)])

(defn app [state]
  [:div.app-layout
   (sidebar state)
   (main-area state)])
