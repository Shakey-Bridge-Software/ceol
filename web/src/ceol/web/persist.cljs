(ns ceol.web.persist
  "localStorage persistence and remote data loading.
   All save!/load! functions read/write directly to app-state and localStorage.
   update-tune-field! is here because it couples state mutation to save.
   No rendering logic, no audio logic."
  (:require [ceol.web.state :as state]
            [cljs.reader :as reader]))

;; --- ABC edits ---

(defonce save-timer (atom nil))

(defn schedule-save!
  "Save abc-edits to localStorage after 1s debounce."
  []
  (when-let [t @save-timer]
    (js/clearTimeout t))
  (reset! save-timer
          (js/setTimeout
           (fn []
             (let [edits (:abc-edits @state/app-state)]
               (when (seq edits)
                 (.setItem js/localStorage "ceol-abc-edits"
                           (pr-str edits)))))
           1000)))

(defn load-saved-edits!
  "Load abc-edits from localStorage, merging defaults for any missing tune IDs."
  []
  (let [local-raw    (.getItem js/localStorage "ceol-abc-edits")
        local-edits  (when local-raw
                       (try (reader/read-string local-raw)
                            (catch :default _ nil)))]
    (-> (js/fetch "/data/default-abc-edits.edn")
        (.then #(.text %))
        (.then (fn [text]
                 (let [defaults (reader/read-string text)
                       merged   (merge defaults local-edits)]
                   (swap! state/app-state assoc :abc-edits merged))))
        (.catch (fn [e]
                  (js/console.error "Failed to load default ABC edits:" e)
                  (when local-edits
                    (swap! state/app-state assoc :abc-edits local-edits)))))))

;; --- Learned tunes ---

(defn save-learned!
  "Save learned tune IDs to localStorage."
  []
  (.setItem js/localStorage "ceol-learned-tunes"
            (pr-str (:learned-tune-ids @state/app-state))))

(defn load-learned!
  "Load learned tune IDs from localStorage."
  []
  (when-let [raw (.getItem js/localStorage "ceol-learned-tunes")]
    (try
      (let [ids (reader/read-string raw)]
        (swap! state/app-state assoc :learned-tune-ids (set ids)))
      (catch :default _ nil))))

;; --- Sets ---

(defn save-sets!
  "Save sets to localStorage."
  []
  (.setItem js/localStorage "ceol-sets" (pr-str (:sets @state/app-state))))

(defn load-sets!
  "Load sets from localStorage, falling back to default-sets.edn."
  []
  (if-let [raw (.getItem js/localStorage "ceol-sets")]
    (try
      (let [sets (reader/read-string raw)]
        (swap! state/app-state assoc :sets sets))
      (catch :default _ nil))
    (-> (js/fetch "/data/default-sets.edn")
        (.then #(.text %))
        (.then (fn [text]
                 (let [sets (reader/read-string text)]
                   (swap! state/app-state assoc :sets sets))))
        (.catch (fn [e] (js/console.error "Failed to load default sets:" e))))))

;; --- Custom tunes ---

(defn save-custom-tunes!
  "Save custom tunes to localStorage."
  []
  (let [custom (:custom-tunes @state/app-state)]
    (.setItem js/localStorage "ceol-custom-tunes" (pr-str custom))))

(defn load-custom-tunes!
  "Load custom tunes from localStorage and merge into state."
  []
  (when-let [raw (.getItem js/localStorage "ceol-custom-tunes")]
    (try
      (let [custom (reader/read-string raw)]
        (swap! state/app-state (fn [s]
                                 (merge s {:custom-tunes custom}
                                        (state/merge-tunes state/base-tunes custom)))))
      (catch :default _ nil))))

(defn- tune-by-id-from-base [tune-id]
  (first (filter #(= tune-id (:id %)) state/base-tunes)))

(defn update-tune-field!
  "Update a field on a tune and persist."
  [tune-id field value]
  (swap! state/app-state
         (fn [s]
           (let [custom (update (:custom-tunes s) tune-id
                                (fn [existing]
                                  (merge (or existing (tune-by-id-from-base tune-id))
                                         {:id tune-id field value})))
                 merged (state/merge-tunes state/base-tunes custom)]
             (merge s {:custom-tunes custom} merged))))
  (save-custom-tunes!))

;; --- ABC data ---

(defn load-abc-data!
  "Fetch local-abc.edn and merge into app state."
  []
  (-> (js/fetch "/data/local-abc.edn")
      (.then #(.text %))
      (.then (fn [text]
               (let [data (reader/read-string text)]
                 (swap! state/app-state assoc :abc-data data))))
      (.catch (fn [e]
                (js/console.error "Failed to load ABC data:" e)))))
