(ns ceol.web.handlers.set
  "Set CRUD and creation-wizard action handlers. Covers the multi-step
   set creation typeahead, set toggle/select/delete, inline tune addition
   into existing sets via typeahead, and the mobile full-screen set editor.
   Pure helpers behind the editor live in ceol.web.handlers.set-editor.
   All persistence routes through ceol.web.persist."
  (:require [ceol.web.state :as state]
            [ceol.web.persist :as persist]
            [ceol.web.handlers.set-editor :as se]))

(defn- reset-typeahead
  "Clear the shared tune-search box. The query + index always move together —
   they encode one concept (an empty search). Used across the set editor."
  [s]
  (assoc s :typeahead-query "" :typeahead-index 0))

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

;; --- Mobile full-screen set editor ------------------------------------------
;; Draft-based editor (Cancel discards, Save commits) mirroring the tune
;; editor. Pure helpers live in ceol.web.handlers.set-editor (`se/`); side
;; effects stay here. The desktop inline creation wizard above is untouched —
;; this overlay is mobile-only (see CSS @media gate). The tune picker reuses
;; the shared :typeahead-query state and the :set/typeahead handler.

(defn editor-open-new! [_args]
  (swap! state/app-state
         (fn [s]
           (-> s
               (assoc :set-editor {:mode :new :set-id nil
                                   :draft se/blank-draft :picking? false})
               reset-typeahead))))

(defn editor-open-edit! [[set-id]]
  (let [s @state/app-state]
    (when-let [set-data (get (:sets s) set-id)]
      ;; Scrub any tune-ids pointing at a since-deleted tune (tune/delete! does
      ;; not scrub sets). This keeps the draft, the rendered rows and the drag
      ;; reorder all in one index space — and drops the dangling ref on save.
      (let [draft   (se/set->draft set-data)
            present (filterv #(state/tune-by-id s %) (:tune-ids draft))]
        (swap! state/app-state
               (fn [s']
                 (-> s'
                     (assoc :set-editor {:mode :edit :set-id set-id
                                         :draft (assoc draft :tune-ids present)
                                         :picking? false})
                     reset-typeahead)))))))

(defn editor-cancel! [_args]
  (swap! state/app-state (fn [s] (-> s (assoc :set-editor nil) reset-typeahead))))

(defn editor-update-draft! [[field value]]
  (swap! state/app-state assoc-in [:set-editor :draft field] value))

(defn editor-start-pick! [_args]
  (swap! state/app-state
         (fn [s] (-> s (assoc-in [:set-editor :picking?] true) reset-typeahead))))

(defn editor-stop-pick! [_args]
  (swap! state/app-state
         (fn [s] (-> s (assoc-in [:set-editor :picking?] false) reset-typeahead))))

(defn editor-add-tune! [[tune-id]]
  ;; Adds to the draft only — no persist until Save. Picker stays open so
  ;; several tunes can be added in a row; the query resets each time.
  (swap! state/app-state
         (fn [s]
           (-> s
               (update-in [:set-editor :draft :tune-ids] se/add-tune tune-id)
               reset-typeahead))))

(defn editor-remove-tune! [[tune-id]]
  (swap! state/app-state update-in [:set-editor :draft :tune-ids] se/remove-tune tune-id))

(defn editor-reorder! [[from to]]
  (swap! state/app-state update-in [:set-editor :draft :tune-ids] se/reorder from to))

(defn editor-save! [_args]
  (let [s (deref state/app-state)
        {:keys [set-id draft]} (:set-editor s)]
    (when (and draft (se/can-save? draft))
      (let [id      (or set-id (state/next-set-id s))
            new-set (se/draft->set id draft)]
        (swap! state/app-state
               (fn [s']
                 (-> s'
                     (assoc-in [:sets id] new-set)
                     (assoc :active-set-id id :main-view :set :set-editor nil)
                     reset-typeahead)))
        (persist/save-sets!)))))
