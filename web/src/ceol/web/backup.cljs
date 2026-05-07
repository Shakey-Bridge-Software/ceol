(ns ceol.web.backup
  "Export and import all user data (abc-edits, custom-tunes, sets, learned
   tune IDs) as a single EDN file. Used for offline backup and sharing
   between devices. The file is validated against Backup at the import
   boundary; invalid sections are dropped with a console.warn rather than
   clobbering existing state."
  (:require [ceol.web.state :as state]
            [ceol.web.persist :as persist]))

(def Backup
  [:map {:closed true}
   [:ceol/version [:= 1]]
   [:exported-at :string]
   [:data
    [:map {:closed true}
     [:abc-edits        {:optional true} persist/AbcEdits]
     [:custom-tunes     {:optional true} persist/CustomTunes]
     [:sets             {:optional true} persist/SetsByID]
     [:learned-tune-ids {:optional true} persist/LearnedTuneIds]]]])

(defn- now-iso []
  (.toISOString (js/Date.)))

(defn- backup-filename []
  (str "ceol-backup-"
       (.replace (subs (now-iso) 0 19) #":" "-")
       ".edn"))

(defn build-backup
  "Construct a backup map from the current app-state. Pure on the supplied
   state, used by export! and by tests."
  [state]
  {:ceol/version 1
   :exported-at  (now-iso)
   :data         (select-keys state [:abc-edits :custom-tunes
                                     :sets :learned-tune-ids])})

(defn- trigger-download! [filename text]
  (let [blob (js/Blob. #js [text] #js {:type "application/edn"})
        url  (.createObjectURL js/URL blob)
        a    (.createElement js/document "a")]
    (set! (.-href a) url)
    (set! (.-download a) filename)
    (.appendChild js/document.body a)
    (.click a)
    (.removeChild js/document.body a)
    (.revokeObjectURL js/URL url)))

(defn export!
  "Serialise current state to EDN and trigger a browser download."
  []
  (let [backup (build-backup @state/app-state)]
    (trigger-download! (backup-filename) (pr-str backup))))

(defn- merge-custom-tunes [s incoming]
  (let [merged (merge (:custom-tunes s) incoming)]
    (merge s {:custom-tunes merged}
           (state/merge-tunes state/base-tunes merged))))

(defn apply-to-state
  "Pure: fold a validated backup's :data into a state map. abc-edits and
   custom-tunes merge with existing state (incoming wins per key); sets
   and learned-tune-ids replace wholesale because they are collections
   the user manages as a whole."
  [s {:keys [abc-edits custom-tunes sets learned-tune-ids]}]
  (cond-> s
    abc-edits        (update :abc-edits merge abc-edits)
    custom-tunes     (merge-custom-tunes custom-tunes)
    sets             (assoc :sets sets)
    learned-tune-ids (assoc :learned-tune-ids learned-tune-ids)))

(defn apply-backup!
  "Merge a validated backup into app-state and persist each section."
  [{:keys [data]}]
  (swap! state/app-state apply-to-state data)
  (when (:abc-edits data) (persist/schedule-save!))
  (when (:custom-tunes data) (persist/save-custom-tunes!))
  (when (:sets data) (persist/save-sets!))
  (when (:learned-tune-ids data) (persist/save-learned!)))

(defn- read-file-text [file on-text]
  (let [reader (js/FileReader.)]
    (set! (.-onload reader) (fn [e] (on-text (.. e -target -result))))
    (set! (.-onerror reader) (fn [_]
                               (js/console.warn "backup: failed to read file")))
    (.readAsText reader file)))

(defn import-text!
  "Parse EDN backup text, validate, and apply. Returns true on success,
   false if the file was unparseable or schema-invalid."
  [text]
  (if-let [backup (persist/read-validated "backup-import" text Backup)]
    (do (apply-backup! backup) true)
    false))

(defn import!
  "Open a file picker, read the chosen file, and import."
  []
  (let [input (.createElement js/document "input")]
    (set! (.-type input) "file")
    (set! (.-accept input) ".edn,application/edn,text/plain")
    (set! (.-onchange input)
          (fn [e]
            (when-let [file (some-> e .-target .-files (aget 0))]
              (read-file-text
               file
               (fn [text]
                 (if (import-text! text)
                   (js/console.log "backup: import successful")
                   (js/console.warn "backup: import failed; state unchanged")))))))
    (.click input)))
