(ns ceol.web.state
  (:require [ceol.tunes :as tunes]))

(def base-tunes
  (mapv #(select-keys % [:id :name :type :time-sig :key :mode-name :session-id])
        tunes/catalog))

(defn merge-tunes
  "Merge base catalog with custom tunes. Custom overrides by ID."
  [base custom]
  (let [custom-ids (set (keys custom))
        updated-base (mapv (fn [t]
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
         :loop? false}))

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
