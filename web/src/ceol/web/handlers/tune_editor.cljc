(ns ceol.web.handlers.tune-editor
  "Pure state-transition helpers for the mobile tune-details editor.

   The side-effectful action handlers live in ceol.web.handlers.tune
   (`editor-open-new!`, `editor-save!`, etc.) and wrap these. Extracting the
   pure shape here lets us cover the contract in .cljc tests without touching
   the atom or localStorage."
  (:require [clojure.string :as str]))

(def blank-draft
  "Default draft used when opening the editor for a brand-new tune."
  {:name       ""
   :type       :polka
   :time-sig   "2/4"
   :key        "G"
   :mode-name  "Ionian"
   :session-id ""})

(defn tune->draft
  "Clone an existing tune into a draft. Used when opening :edit mode."
  [tune]
  {:name       (or (:name tune) "")
   :type       (or (:type tune) :polka)
   :time-sig   (or (:time-sig tune) "2/4")
   :key        (or (:key tune) "G")
   :mode-name  (or (:mode-name tune) "Ionian")
   :session-id (if-let [sid (:session-id tune)] (str sid) "")})

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
