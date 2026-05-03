(ns ceol.web.state
  (:require [ceol.tunes :as tunes]
            [clojure.string :as str]))

(def base-tunes
  (mapv #(select-keys % [:id :name :type :time-sig :key :mode-name :session-id])
        tunes/catalog))

(defn merge-tunes
  "Merge base catalog with custom tunes. Custom overrides by ID."
  [base custom]
  (let [updated-base (mapv (fn [t]
                             (if-let [overrides (get custom (:id t))]
                               (merge t overrides)
                               t))
                           base)
        new-tunes (->> (vals custom)
                       (remove #(some (fn [bt] (= (:id bt) (:id %))) base))
                       vec)]
    (into updated-base new-tunes)))

(defonce app-state
  (atom {:tunes base-tunes
         :custom-tunes {}
         :abc-data {}
         :abc-edits {}
         :selected-tune-id nil
         :filter :all
         :tab :tunes
         :editor-open? false
         :guitar? false
         :editing-field nil
         :playing? false
         :playing-section nil
         :section nil
         :loop? false
         :tempo-offset 0
         ;; Sets
         :sets {}
         :active-set-id nil
         :set-playing? false
         :set-tune-index 0
         ;; Set creation
         :creating-set? false
         :creating-set-name nil
         :creating-set-tunes []
         :typeahead-query ""
         :typeahead-index 0
         :adding-to-set nil
         :metronome? false
         :count-in? false
         :current-beat nil
         ;; Learned + Session
         :learned-tune-ids #{}
         :session-mode? false
         :session-queue []
         :session-index 0
         :session-set-index 0
         :session-pausing? false
         :session-within-set? false
         :session-played []}))

;; --- Tune queries ---

(defn tune-by-id [state id]
  (first (filter #(= id (:id %)) (:tunes state))))

(defn filtered-tunes [state]
  (let [f (:filter state)]
    (if (= f :all)
      (:tunes state)
      (filterv #(= f (:type %)) (:tunes state)))))

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
  (not (some #(= tune-id (:id %)) base-tunes)))

(defn next-tune-id
  "Generate the next available tune ID."
  [state]
  (let [all-ids (map :id (:tunes state))]
    (if (seq all-ids)
      (inc (apply max all-ids))
      1000)))

;; --- Set queries ---

(defn next-set-id
  "Generate the next set ID string."
  [state]
  (let [existing (keys (:sets state))
        nums (->> existing
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
      (->> (:tunes state)
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
  (let [;; Step 1: complete sets (all tunes learned)
        complete-sets (filter (fn [[_ s]]
                                (and (seq (:tune-ids s))
                                     (every? learned-ids (:tune-ids s))))
                              sets)
        ;; Step 2: tune-ids covered by complete sets
        set-tune-ids (into #{} (mapcat (fn [[_ s]] (:tune-ids s)) complete-sets))
        ;; Step 3: standalone learned tunes not in any complete set
        standalone-ids (remove set-tune-ids learned-ids)
        ;; Step 4: build queue
        set-items (mapv (fn [[id s]] {:type :set :set-id id :name (:name s) :tune-ids (:tune-ids s)})
                        complete-sets)
        tune-items (mapv (fn [tid] {:type :tune :tune-id tid}) standalone-ids)]
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
        ;; Currently in a set — check if more tunes
        (and (= :set (:type current))
             (< (inc session-set-index) (count (:tune-ids current))))
        {:action :advance-in-set
         :tune-id (nth (:tune-ids current) (inc session-set-index))
         :session-set-index (inc session-set-index)}

        ;; More items in queue
        (< (inc session-index) (count queue))
        {:action :next-item
         :session-index (inc session-index)}

        ;; End of queue, loop on
        loop?
        {:action :reshuffle}

        ;; End of queue, no loop
        :else
        {:action :done}))))

(defn session-current-tune-id
  "Get the tune-id currently playing in the session."
  [state]
  (when-let [queue (seq (:session-queue state))]
    (let [item (nth queue (:session-index state) nil)]
      (case (:type item)
        :tune (:tune-id item)
        :set (nth (:tune-ids item) (:session-set-index state) nil)
        nil))))
