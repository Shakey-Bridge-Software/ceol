(ns ceol.web.state
  "App state atom, query functions, and pure domain logic.
   The single source of truth for all UI state. Query functions are pure
   and take the state map as their first argument. Side-effectful mutations
   live in core.cljs via handle-action!."
  (:require [ceol.tunes :as tunes]
            [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; Web-only schemas
;;
;; Set and SessionQueueItem describe data persisted to localStorage, so they
;; are validated at the persist boundary on load (see persist.cljs). Tune
;; lives in ceol.tunes and is shared with the TUI.
;; ---------------------------------------------------------------------------

(def Set
  [:map {:closed true}
   [:id :string]
   [:name :string]
   [:tune-ids [:vector :int]]])

(def SessionQueueItem
  [:multi {:dispatch :type}
   [:tune [:map {:closed true}
           [:type [:= :tune]]
           [:tune-id :int]]]
   [:set  [:map {:closed true}
           [:type [:= :set]]
           [:set-id :string]
           [:name :string]
           [:tune-ids [:vector :int]]]]])

(def base-tunes
  (mapv #(select-keys % [:id :name :type :time-sig :key :mode-name :session-id])
        tunes/catalog))

(def ^:private base-tune-ids
  "Set of IDs from the base catalog. Used for O(1) custom-tune? checks."
  (set (map :id base-tunes)))

(defn merge-tunes
  "Merge base catalog with custom tunes (custom overrides by ID).
   Returns a partial state map {:tunes {id→tune} :tune-order [ids]}
   suitable for merging directly into app-state."
  [base custom]
  (let [updated-base (mapv (fn [t]
                             (if-let [overrides (get custom (:id t))]
                               (merge t overrides)
                               t))
                           base)
        new-tunes    (->> (vals custom)
                          (remove #(some (fn [bt] (= (:id bt) (:id %))) base))
                          vec)
        all-tunes    (into updated-base new-tunes)]
    {:tunes      (into {} (map (juxt :id identity)) all-tunes)
     :tune-order (mapv :id all-tunes)}))

;; ---------------------------------------------------------------------------
;; App state shape
;;
;; Tune data
;;   :tunes            {id → tune-map}   all tunes, base catalog merged with custom
;;   :tune-order       [id ...]          display order (catalog order + custom appended)
;;   :custom-tunes     {id → tune-map}   user-added/edited tunes, persisted to localStorage
;;   :abc-data         {id → abc-body}   raw ABC bodies loaded from local-abc.edn
;;   :abc-edits        {id → abc-body}   user-edited ABC (with chords), persisted to localStorage
;;
;; Selection & UI
;;   :selected-tune-id  id | nil
;;   :filter            :all | :polka | :jig | :reel | :hornpipe | :slip-jig | :slide | :other
;;   :tab               :tunes | :sets | :session
;;   :editor-open?      bool
;;   :editing-field     :name | nil       which header field is in inline-edit mode
;;
;; Playback
;;   :playing?          bool              melody is currently playing
;;   :playing-section   :a | :b | nil     section that was active when play started
;;   :section           :a | :b | nil     currently selected section (for next play)
;;   :loop?             bool
;;   :guitar?           bool              guitar track enabled (unmuted)
;;   :tempo-offset      int               BPM delta from type default, clamped [-40, +40]
;;   :metronome?        bool              standalone metronome running
;;   :count-in?         bool              count-in enabled for next play
;;   :current-beat      int | nil         current beat index for metronome UI highlight
;;
;; Set playback
;;   :set-playing?      bool              playing through a set (auto-advance between tunes)
;;   :set-tune-index    int               index within the active set's tune-ids
;;   :set-advancing?    bool              mid-set auto-advance in progress; suppresses
;;                                        count-in so only the first tune of a set gets one
;;
;; Sets
;;   :sets              {set-id → set-map}  set-map: {:id :name :tune-ids [...]}
;;   :active-set-id     set-id | nil       expanded/selected set
;;
;; Set creation wizard
;;   :creating-set?       bool
;;   :creating-set-name   str | nil        confirmed set name (after Enter on step 1)
;;   :creating-set-tunes  [tune-id ...]    tunes added so far in wizard
;;   :typeahead-query     str              current search input
;;   :typeahead-index     int              highlighted result index
;;   :adding-to-set       set-id | nil     set currently being added to (post-creation)
;;
;; Learned & Session
;;   :learned-tune-ids    #{id ...}        persisted to localStorage
;;   :session-mode?       bool             session active (read-only main panel)
;;   :session-queue       [{:type :tune/:set ...}]  shuffled play queue
;;   :session-index       int              current position in queue
;;   :session-set-index   int              current tune index within a set item
;;   :session-pausing?    bool             true during the 2s gap between queue items
;;   :session-within-set? bool             true while advancing through tunes inside a set;
;;                                         suppresses count-in for mid-set transitions
;;   :session-played      [queue-index ...] indices of completed queue items (for history)
;; ---------------------------------------------------------------------------

(defonce app-state
  (atom (merge
         {:custom-tunes    {}
          :abc-data        {}
          :abc-edits       {}
          :selected-tune-id nil
          :filter          :all
          :tab             :tunes
          :editor-open?    false
          :guitar?         false
          :editing-field   nil
          :playing?        false
          :playing-section nil
          :section         nil
          :loop?           false
          :tempo-offset    0
          ;; Sets
          :sets            {}
          :active-set-id   nil
          :set-playing?    false
          :set-tune-index  0
          ;; Set creation
          :creating-set?       false
          :creating-set-name   nil
          :creating-set-tunes  []
          :typeahead-query     ""
          :typeahead-index     0
          :adding-to-set       nil
          :metronome?          false
          :count-in?           false
          :current-beat        nil
          ;; Learned + Session
          :learned-tune-ids    #{}
          :session-mode?       false
          :session-queue       []
          :session-index       0
          :session-set-index   0
          :session-pausing?    false
          :session-within-set? false
          :session-played      []}
         (merge-tunes base-tunes {}))))

;; --- Tune queries ---

(defn tune-by-id [state id]
  (get (:tunes state) id))

(defn filtered-tunes [state]
  (let [tunes (:tunes state)
        order (:tune-order state)
        f     (:filter state)]
    (if (= f :all)
      (mapv tunes order)
      (filterv #(= f (:type %)) (mapv tunes order)))))

(defn selected-tune [state]
  (when-let [id (:selected-tune-id state)]
    (tune-by-id state id)))

(defn abc-for-tune [state tune-id]
  (get (:abc-data state) tune-id))

(defn edited-abc-for-tune
  "Get the edited ABC for a tune, falling back to the original."
  [state tune-id]
  (or (get (:abc-edits state) tune-id)
      (get (:abc-data state) tune-id)))

(defn custom-tune?
  "Is this tune ID a custom (user-added) tune, not from the base catalog?"
  [tune-id]
  (not (contains? base-tune-ids tune-id)))

(defn next-tune-id
  "Generate the next available tune ID."
  [state]
  (let [all-ids (keys (:tunes state))]
    (if (seq all-ids)
      (inc (apply max all-ids))
      1000)))

;; --- Set queries ---

(defn next-set-id
  "Generate the next set ID string."
  [state]
  (let [existing (keys (:sets state))
        nums     (->> existing
                      (map #(second (re-find #"set-(\d+)" %)))
                      (remove nil?)
                      (map #(js/parseInt % 10)))]
    (str "set-" (if (seq nums) (inc (apply max nums)) 1))))

(defn active-set
  "Get the currently active (expanded) set."
  [state]
  (when-let [id (:active-set-id state)]
    (get (:sets state) id)))

(defn set-tunes
  "Get the tune maps for a set's tune-ids."
  [state set-data]
  (mapv #(tune-by-id state %) (:tune-ids set-data)))

(defn search-tunes
  "Search all tunes by name (case-insensitive substring match). Returns max n results."
  [state query n]
  (if (str/blank? query)
    []
    (let [q (str/lower-case query)]
      (->> (vals (:tunes state))
           (filter #(str/includes? (str/lower-case (:name %)) q))
           (take n)
           vec))))

;; --- Set playback (pure logic) ---

(defn advance-set
  "Given current set state, compute the next state after a tune finishes.
   Returns {:action :play/:stop/:loop, :tune-id <id>, :index <n>} or nil."
  [sets active-set-id set-tune-index loop?]
  (when-let [s (get sets active-set-id)]
    (let [next-idx (inc set-tune-index)
          tune-ids (:tune-ids s)]
      (cond
        (< next-idx (count tune-ids))
        {:action :play :tune-id (nth tune-ids next-idx) :index next-idx}

        loop?
        {:action :loop :tune-id (first tune-ids) :index 0}

        :else
        {:action :stop}))))

;; --- Learned + Session (pure logic) ---

(defn learned? [state tune-id]
  (contains? (:learned-tune-ids state) tune-id))

(defn count-ready-sets
  "Count sets where all tunes are learned."
  [state]
  (count (filter (fn [[_ s]]
                   (every? #(learned? state %) (:tune-ids s)))
                 (:sets state))))

(defn build-session-queue
  "Build the session queue from learned tunes and sets.
   Returns unshuffled vector of {:type :tune/:set ...} items."
  [learned-ids sets]
  (let [complete-sets  (filter (fn [[_ s]]
                                 (and (seq (:tune-ids s))
                                      (every? learned-ids (:tune-ids s))))
                               sets)
        set-tune-ids   (into #{} (mapcat (fn [[_ s]] (:tune-ids s)) complete-sets))
        standalone-ids (remove set-tune-ids learned-ids)
        set-items      (mapv (fn [[id s]] {:type :set :set-id id :name (:name s) :tune-ids (:tune-ids s)})
                             complete-sets)
        tune-items     (mapv (fn [tid] {:type :tune :tune-id tid}) standalone-ids)]
    (into set-items tune-items)))

(defn shuffle-queue [queue]
  (into [] (shuffle queue)))

(defn advance-session
  "Given session state, compute next action after a tune finishes.
   Returns {:action :advance-in-set/:next-item/:done/:reshuffle, ...}"
  [queue session-index session-set-index loop?]
  (when (seq queue)
    (let [current (nth queue session-index nil)]
      (cond
        (and (= :set (:type current))
             (< (inc session-set-index) (count (:tune-ids current))))
        {:action          :advance-in-set
         :tune-id         (nth (:tune-ids current) (inc session-set-index))
         :session-set-index (inc session-set-index)}

        (< (inc session-index) (count queue))
        {:action         :next-item
         :session-index  (inc session-index)}

        loop?
        {:action :reshuffle}

        :else
        {:action :done}))))

(defn session-current-tune-id
  "Get the tune-id currently playing in the session."
  [state]
  (when-let [queue (seq (:session-queue state))]
    (let [item (nth queue (:session-index state) nil)]
      (case (:type item)
        :tune (:tune-id item)
        :set  (nth (:tune-ids item) (:session-set-index state) nil)
        nil))))
