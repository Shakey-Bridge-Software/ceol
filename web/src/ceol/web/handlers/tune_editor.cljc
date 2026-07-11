(ns ceol.web.handlers.tune-editor
  "Pure state-transition helpers for the mobile tune-details editor.

   The side-effectful action handlers live in ceol.web.handlers.tune
   (`editor-open-new!`, `editor-save!`, etc.) and wrap these. Extracting the
   pure shape here lets us cover the contract in .cljc tests without touching
   the atom or localStorage."
  (:require [clojure.string :as str]))

(def ^:private default-fields
  "Single source for new-tune defaults. Used by `blank-draft` (initial form
   state on :new) and `tune->draft` (fallback fill when an existing tune
   is missing a field on :edit)."
  {:type :polka :time-sig "2/4" :key "G" :mode-name "Ionian"})

(def TuneDraft
  "Schema for the :tune-editor draft map. Closed shape — every field has a
   string representation suitable for direct binding to form inputs. The
   :session-id is held as a string and parsed to int (or nil) at save time."
  [:map {:closed true}
   [:name :string]
   [:type [:enum :polka :jig :reel :hornpipe :slip-jig :slide :mazourka :other]]
   [:time-sig :string]
   [:key :string]
   [:mode-name :string]
   [:session-id :string]])

(def blank-draft
  "Default draft used when opening the editor for a brand-new tune."
  (merge default-fields {:name "" :session-id ""}))

(defn tune->draft
  "Clone an existing tune into a draft. Used when opening :edit mode.
   Missing or nil tune fields fall back to `default-fields`."
  [tune]
  (merge default-fields
         (into {}
               (filter (fn [[_ v]] (some? v)))
               (select-keys tune (keys default-fields)))
         {:name       (or (:name tune) "")
          :session-id (if-let [sid (:session-id tune)] (str sid) "")}))

(defn parse-session-id
  "Convert the session-id draft string to an int, or nil if blank/invalid."
  [s]
  (let [trimmed (some-> s str/trim)]
    (when (and (string? trimmed) (seq trimmed))
      #?(:cljs
         (let [n (js/parseInt trimmed 10)]
           (when-not (js/isNaN n) n))
         :clj
         (try (Integer/parseInt trimmed) (catch Exception _ nil))))))

(defn- coerce-name [s]
  (let [n (str/trim (or s ""))]
    (if (seq n) n "Untitled tune")))

(defn draft->new-tune
  "Build the tune map saved on :new save. Drops blank session-id."
  [new-id draft]
  (cond-> {:id        new-id
           :name      (coerce-name (:name draft))
           :type      (:type draft)
           :time-sig  (:time-sig draft)
           :key       (:key draft)
           :mode-name (:mode-name draft)}
    (parse-session-id (:session-id draft))
    (assoc :session-id (parse-session-id (:session-id draft)))))

(defn draft->edit-updates
  "Build the [field value] pairs persisted on :edit save."
  [draft]
  [[:name       (coerce-name (:name draft))]
   [:type       (:type draft)]
   [:time-sig   (:time-sig draft)]
   [:key        (:key draft)]
   [:mode-name  (:mode-name draft)]
   [:session-id (parse-session-id (:session-id draft))]])


;; --- B4: Duplicate-tune name helper -----------------------------------------
;; Lives alongside the editor helpers because both serve handlers.tune and
;; both are pure (atom-free, no side effects). Side-effectful duplicate!
;; wraps this in handlers.tune.

(defn unique-copy-name
  "Append \" (copy)\" to a tune name, or \" (copy N)\" if a prior copy with the
   same base name already exists in `existing-names`."
  [base-name existing-names]
  (let [names (set existing-names)
        try1  (str base-name " (copy)")]
    (if-not (contains? names try1)
      try1
      (loop [n 2]
        (let [try-n (str base-name " (copy " n ")")]
          (if-not (contains? names try-n) try-n (recur (inc n))))))))
