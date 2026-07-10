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
  Used at the localStorage/import boundary to prevent corrupt persisted
  state from silently propagating into the app. An optional migrate-fn is
  applied to the parsed value before validation, so older-shaped data can
  be normalized to the current schema instead of failing closed."
  ([storage-key raw schema] (read-validated storage-key raw schema identity))
  ([storage-key raw schema migrate-fn]
   (let [parsed (try (-> (reader/read-string raw) migrate-fn)
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
           nil)))))

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

(defn save-tunes!
  "Save tunes to localStorage. Also marks tune storage as migrated — any full
  snapshot written here is authoritative, so a later load-tunes! must never
  re-merge the base catalog underneath it (that would resurrect a tune the
  user deleted)."
  []
  (let [tunes (:tunes @state/app-state)]
    (.setItem js/localStorage "ceol-custom-tunes" (pr-str tunes))
    (.setItem js/localStorage "ceol-tunes-migrated" "true")))

(def PersistedTunes [:map-of :int tunes/Tune])

(defn load-tunes!
  "Load tunes from localStorage and merge into state.
  Three cases: (1) no localStorage entry — first run, seed the base catalog.
  (2) an entry exists but tune storage was never migrated — this is a
  pre-existing user's sparse, custom-only blob from before catalog tunes
  became deletable; merge the base catalog underneath it once and re-save
  the full snapshot so future loads stop merging. (3) already migrated — the
  stored map is itself a full, authoritative snapshot (save-tunes! always
  writes one), load it as-is so a deleted catalog tune stays deleted."
  []
  (let [migrated? (some? (.getItem js/localStorage "ceol-tunes-migrated"))
        custom (some-> (.getItem js/localStorage "ceol-custom-tunes")
                       (#(read-validated "ceol-custom-tunes" % PersistedTunes)))]
    (cond
      (nil? custom)
      (swap! state/app-state merge (state/prepare-tunes tunes/catalog))

      (not migrated?)
      (do (swap! state/app-state merge (state/prepare-tunes (merge tunes/catalog custom)))
          (save-tunes!))

      :else
      (swap! state/app-state merge (state/prepare-tunes custom)))))

(defn update-tune-field!
  "Update a field on a tune and persist."
  [tune-id field value]
  (swap! state/app-state
         (fn [s]
           (let [updated (update (:tunes s) tune-id
                                 #(merge %
                                         {:id   tune-id
                                          field value}))]
             (merge s (state/prepare-tunes updated)))))
  (save-tunes!))

;; --- Tune notes ---

(defonce notes-save-timer (atom nil))

(def TuneNotes [:map-of :int :string])

(defn schedule-save-notes!
  "Save tune-notes to localStorage after 500 ms debounce."
  []
  (when-let [t @notes-save-timer]
    (js/clearTimeout t))
  (reset! notes-save-timer
          (js/setTimeout
            (fn []
              (let [notes (:tune-notes @state/app-state)]
                (.setItem js/localStorage "ceol-tune-notes" (pr-str notes))))
            500)))

(defn load-notes!
  "Load tune notes from localStorage."
  []
  (when-let [raw (.getItem js/localStorage "ceol-tune-notes")]
    (when-let [notes (read-validated "ceol-tune-notes" raw TuneNotes)]
      (swap! state/app-state assoc :tune-notes notes))))

(defn update-tune-notes!
  "Update notes for a tune and schedule a debounced save. Empty strings
  are stored as-is so the user can clear notes without the row vanishing
  between keystrokes; load-notes! treats empty strings the same as absent."
  [tune-id text]
  (swap! state/app-state assoc-in [:tune-notes tune-id] text)
  (schedule-save-notes!))

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
