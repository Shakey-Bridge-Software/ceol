(ns ceol.web.handlers.set-editor
  "Pure state-transition helpers for the mobile full-screen set editor.

   The side-effectful action handlers live in ceol.web.handlers.set
   (`editor-open-new!`, `editor-save!`, etc.) and wrap these. Extracting the
   pure shape here lets us cover the contract in .cljc tests without touching
   the atom or localStorage.

   A set is {:id string :name string :tune-ids [int ...]} (see state/Set). The
   editor draft drops :id (minted/preserved at save) and holds the working
   name + tune-ids vector that add/remove/reorder mutate before commit."
  (:require [clojure.string :as str]))

(def SetDraft
  "Schema for the :set-editor draft map. Closed shape. :tune-ids is the live
   reorderable vector of int tune-ids; :name binds directly to the form input."
  [:map {:closed true}
   [:name :string]
   [:tune-ids [:vector :int]]])

(def blank-draft
  "Default draft used when opening the editor for a brand-new set."
  {:name "" :tune-ids []})

(defn set->draft
  "Clone an existing set into a draft. Used when opening :edit mode."
  [set-data]
  {:name     (or (:name set-data) "")
   :tune-ids (vec (:tune-ids set-data))})

(defn- coerce-name [s]
  (let [n (str/trim (or s ""))]
    (if (seq n) n "Untitled set")))

(defn draft->set
  "Build the set map committed on save. `id` is the existing set-id on :edit or
   a freshly minted one on :new. Result matches the closed state/Set schema."
  [id draft]
  {:id       id
   :name     (coerce-name (:name draft))
   :tune-ids (vec (:tune-ids draft))})

(defn can-save?
  "A set is saveable once it has a non-blank name and at least one tune —
   matches the wizard's finish-creation! gate."
  [draft]
  (and (not (str/blank? (:name draft)))
       (boolean (seq (:tune-ids draft)))))

(defn add-tune
  "Append tune-id to the draft's tune-ids unless already present (dedup —
   the set data model treats tune-ids as a set-with-order)."
  [tune-ids tune-id]
  (if (some #{tune-id} tune-ids)
    tune-ids
    (conj (vec tune-ids) tune-id)))

(defn remove-tune
  "Drop all occurrences of tune-id from the draft's tune-ids."
  [tune-ids tune-id]
  (vec (remove #{tune-id} tune-ids)))

(defn reorder
  "Move the tune-id at index `from` to index `to` (final-position semantics).
   No-op when indices are equal or out of bounds — safe to call from a drag
   handler that may compute a stale target."
  [tune-ids from to]
  (let [v (vec tune-ids)
        n (count v)]
    (if (or (= from to) (not (< -1 from n)) (not (< -1 to n)))
      v
      (let [item    (nth v from)
            without (into (subvec v 0 from) (subvec v (inc from)))]
        (into (conj (subvec without 0 to) item) (subvec without to))))))

(defn drop-target-index
  "Destination row index for a drag that started on row `from` of `n` rows,
   moved `dy` px vertically with a per-row slot height of `slot` px. Clamped to
   [0, n-1]. Pure so the drag handler in gesture.cljs is thin DOM glue."
  [from n dy slot]
  (if (or (zero? n) (<= slot 0))
    from
    (let [delta (Math/round (/ dy (double slot)))]
      (max 0 (min (dec n) (+ from delta))))))
