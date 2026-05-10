(ns ceol.web.handlers.set
  "Set CRUD and creation-wizard action handlers. Covers the multi-step
   set creation typeahead, set toggle/select/delete, and inline tune
   addition into existing sets via typeahead. All persistence routes
   through ceol.web.persist."
  (:require [ceol.web.state :as state]
            [ceol.web.persist :as persist]))

;; --- Creation wizard ---

(defn start-create! [_args]
  (swap! state/app-state assoc :creating-set? true :creating-set-name nil
         :creating-set-tunes [] :typeahead-query "" :typeahead-index 0))

(defn name-keydown! [[key value]]
  (case key
    "Enter" (when (and (string? value) (seq (.trim value)))
              (swap! state/app-state assoc :creating-set-name (.trim value)))
    "Escape" (swap! state/app-state assoc :creating-set? false)
    nil))

(defn typeahead! [[query]]
  (swap! state/app-state assoc :typeahead-query (or query "") :typeahead-index 0))

(defn- finish-creation!
  "Build the set from the wizard's accumulated state and persist."
  [s]
  (let [name (:creating-set-name s)
        tune-ids (:creating-set-tunes s)]
    (when (and name (seq tune-ids))
      (let [set-id (state/next-set-id s)
            new-set {:id set-id :name name :tune-ids tune-ids}]
        (swap! state/app-state
               (fn [s']
                 (assoc s' :sets (assoc (:sets s') set-id new-set)
                        :creating-set? false
                        :active-set-id set-id
                        :selected-tune-id (first tune-ids))))
        (persist/save-sets!)))))

(defn- pick-from-typeahead!
  "Add the highlighted typeahead result to the wizard's tune list."
  [s results idx]
  (when-let [tune (get results idx)]
    (let [tune-id (:id tune)
          existing (:creating-set-tunes s)]
      (when-not (some #{tune-id} existing)
        (swap! state/app-state update :creating-set-tunes conj tune-id))
      (swap! state/app-state assoc :typeahead-query "" :typeahead-index 0))))

(defn tune-keydown! [[key]]
  (let [s @state/app-state
        query (:typeahead-query s)
        results (state/search-tunes s query 5)
        idx (:typeahead-index s)]
    (case key
      "Enter"
      (if (seq (.trim (or query "")))
        (pick-from-typeahead! s results idx)
        (finish-creation! s))

      "Escape"
      (swap! state/app-state assoc :creating-set? false)

      "ArrowDown"
      (swap! state/app-state update :typeahead-index
             #(min (dec (count results)) (inc %)))

      "ArrowUp"
      (swap! state/app-state update :typeahead-index #(max 0 (dec %)))

      nil)))

(defn pick-tune! [[tune-id]]
  (let [existing (:creating-set-tunes @state/app-state)]
    (when-not (some #{tune-id} existing)
      (swap! state/app-state update :creating-set-tunes conj tune-id))
    (swap! state/app-state assoc :typeahead-query "" :typeahead-index 0)))

(defn uncreate-tune! [[tune-id]]
  (swap! state/app-state update :creating-set-tunes
         (fn [ids] (vec (remove #{tune-id} ids)))))

;; --- Set CRUD on existing sets ---

(defn toggle! [[set-id]]
  (let [s @state/app-state]
    (if (= set-id (:active-set-id s))
      (swap! state/app-state assoc :active-set-id nil :main-view :tune)
      (let [s-data (get (:sets s) set-id)
            first-tune-id (first (:tune-ids s-data))]
        (swap! state/app-state assoc :active-set-id set-id
               :selected-tune-id first-tune-id
               :main-view :set)))))

(defn select-tune! [[_set-id tune-id]]
  (swap! state/app-state assoc :selected-tune-id tune-id :main-view :tune))

(defn add-tune! [[set-id tune-id]]
  (swap! state/app-state update-in [:sets set-id :tune-ids]
         (fn [ids] (if (some #{tune-id} ids) ids (conj (or ids []) tune-id))))
  (persist/save-sets!))

(defn remove-tune! [[set-id tune-id]]
  (swap! state/app-state update-in [:sets set-id :tune-ids]
         (fn [ids] (vec (remove #{tune-id} ids))))
  (persist/save-sets!))

(defn start-adding! [[set-id]]
  (swap! state/app-state assoc :adding-to-set set-id
         :typeahead-query "" :typeahead-index 0))

(defn- add-from-typeahead!
  "Append the highlighted typeahead result to set-id's tune-ids and persist."
  [set-id results idx]
  (when-let [tune (get results idx)]
    (swap! state/app-state update-in [:sets set-id :tune-ids]
           (fn [ids]
             (let [tid (:id tune)]
               (if (some #{tid} ids) ids (conj (or ids []) tid)))))
    (swap! state/app-state assoc :typeahead-query "" :typeahead-index 0)
    (persist/save-sets!)))

(defn add-tune-keydown! [[set-id key]]
  (let [s @state/app-state
        query (:typeahead-query s)
        results (state/search-tunes s query 5)
        idx (:typeahead-index s)]
    (case key
      "Enter"
      (if (seq (.trim (or query "")))
        (add-from-typeahead! set-id results idx)
        (swap! state/app-state assoc :adding-to-set nil))

      "Escape"
      (swap! state/app-state assoc :adding-to-set nil)

      "ArrowDown"
      (swap! state/app-state update :typeahead-index
             #(min (dec (count results)) (inc %)))

      "ArrowUp"
      (swap! state/app-state update :typeahead-index #(max 0 (dec %)))

      nil)))

(defn delete! [[set-id]]
  (swap! state/app-state (fn [s]
                           (-> s
                               (update :sets dissoc set-id)
                               (cond-> (= set-id (:active-set-id s))
                                 (assoc :active-set-id nil)))))
  (persist/save-sets!))
