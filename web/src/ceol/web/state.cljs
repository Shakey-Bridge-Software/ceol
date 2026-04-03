(ns ceol.web.state
  (:require [ceol.tunes :as tunes]))

(defonce app-state
  (atom {:tunes (mapv #(select-keys % [:id :name :type :time-sig :key :mode-name :session-id])
                      tunes/catalog)
         :abc-data {}
         :selected-tune-id nil
         :filter :all
         :tab :tunes}))

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
