(ns ceol.web.persist
  "localStorage persistence and remote data loading.
   All save!/load! functions read/write directly to app-state and localStorage.
   update-tune-field! is here because it couples state mutation to save.
   No rendering logic, no audio logic.
   Loaded data is validated against the schemas in ceol.web.state and
   ceol.tunes; invalid data is logged and discarded rather than silently
   corrupting state."
  (:require [ceol.web.state :as state]
            [ceol.tunes :as tunes]
            [cljs.reader :as reader]
            [malli.core :as m]
            [malli.error :as me]))

(defn read-validated
  "Parse EDN from raw and validate against schema.
   On parse failure or schema mismatch, log a console.warn and return nil.
   Used at the localStorage boundary to prevent corrupt persisted state
   from silently propagating into the app."
  [storage-key raw schema]
  (let [parsed (try (reader/read-string raw)
                    (catch :default e
                      (js/console.warn
                       (str "persist: failed to parse EDN for " storage-key) e)
                      nil))]
    (cond
      (nil? parsed) nil
      (m/validate schema parsed) parsed
      :else
      (do (js/console.warn
           (str "persist: schema mismatch for " storage-key)
           (clj->js (me/humanize (m/explain schema parsed))))
          nil))))

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

(def AbcEdits [:map-of :int :string])

(defn load-saved-edits!
  "Load abc-edits from localStorage, merging defaults for any missing tune IDs."
  []
  (let [local-raw   (.getItem js/localStorage "ceol-abc-edits")
        local-edits (when local-raw
                      (read-validated "ceol-abc-edits" local-raw AbcEdits))]
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

(def LearnedTuneIds [:set :int])

(defn load-learned!
  "Load learned tune IDs from localStorage."
  []
  (when-let [raw (.getItem js/localStorage "ceol-learned-tunes")]
    (when-let [ids (read-validated "ceol-learned-tunes" raw LearnedTuneIds)]
      (swap! state/app-state assoc :learned-tune-ids ids))))

;; --- Sets ---

(defn save-sets!
  "Save sets to localStorage."
  []
  (.setItem js/localStorage "ceol-sets" (pr-str (:sets @state/app-state))))

(def SetsByID [:map-of :string state/Set])

(defn load-sets!
  "Load sets from localStorage, falling back to default-sets.edn."
  []
  (if-let [raw (.getItem js/localStorage "ceol-sets")]
    (when-let [sets (read-validated "ceol-sets" raw SetsByID)]
      (swap! state/app-state assoc :sets sets))
    (-> (js/fetch "/data/default-sets.edn")
        (.then #(.text %))
        (.then (fn [text]
                 (when-let [sets (read-validated "default-sets.edn" text SetsByID)]
                   (swap! state/app-state assoc :sets sets))))
        (.catch (fn [e] (js/console.error "Failed to load default sets:" e))))))

;; --- Custom tunes ---

(defn save-custom-tunes!
  "Save custom tunes to localStorage."
  []
  (let [custom (:custom-tunes @state/app-state)]
    (.setItem js/localStorage "ceol-custom-tunes" (pr-str custom))))

(def CustomTunes [:map-of :int tunes/Tune])

(defn load-custom-tunes!
  "Load custom tunes from localStorage and merge into state."
  []
  (when-let [raw (.getItem js/localStorage "ceol-custom-tunes")]
    (when-let [custom (read-validated "ceol-custom-tunes" raw CustomTunes)]
      (swap! state/app-state (fn [s]
                               (merge s {:custom-tunes custom}
                                      (state/merge-tunes state/base-tunes custom)))))))

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

(def AbcData [:map-of :int :string])

(defn load-abc-data!
  "Fetch local-abc.edn and merge into app state."
  []
  (-> (js/fetch "/data/local-abc.edn")
      (.then #(.text %))
      (.then (fn [text]
               (when-let [data (read-validated "local-abc.edn" text AbcData)]
                 (swap! state/app-state assoc :abc-data data))))
      (.catch (fn [e]
                (js/console.error "Failed to load ABC data:" e)))))
