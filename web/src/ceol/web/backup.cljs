(ns ceol.web.backup
  "Export and import all user data (abc-edits, custom-tunes, sets, learned
  tune IDs) as a single EDN file. Used for offline backup and sharing
  between devices. The file is validated against Backup at the import
  boundary; invalid sections are dropped with a console.warn rather than
  clobbering existing state."
  (:require [ceol.tunes :as tunes]
            [ceol.web.state :as state]
            [ceol.web.persist :as persist]))

(def Backup
  [:map {:closed true}
   [:ceol/version [:= 1]]
   [:exported-at :string]
   [:data
    [:map {:closed true}
     [:abc-edits        {:optional true} persist/AbcEdits]
     [:tunes            {:optional true} persist/PersistedTunes]
     [:sets             {:optional true} persist/SetsByID]
     [:learned-tune-ids {:optional true} persist/LearnedTuneIds]
     [:tune-notes       {:optional true} persist/TuneNotes]]]])

(defn- now-iso []
  (.toISOString (js/Date.)))

(defonce ^:private status-timer (atom nil))

(defn set-status!
  "Show a transient banner above the sidebar footer. Cleared after 3s."
  [kind message]
  (swap! state/app-state assoc :backup-status {:kind kind :message message})
  (when-let [t @status-timer] (js/clearTimeout t))
  (reset! status-timer
          (js/setTimeout
            (fn []
              (swap! state/app-state assoc :backup-status nil))
            3000)))

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
   :data         (select-keys state [:abc-edits :tunes
                                     :sets :learned-tune-ids
                                     :tune-notes])})

(defn- anchor-download! [filename text]
  (let [blob (js/Blob. #js [text] #js {:type "application/edn"})
        url  (.createObjectURL js/URL blob)
        a    (.createElement js/document "a")]
    (set! (.-href a) url)
    (set! (.-download a) filename)
    (.appendChild js/document.body a)
    (.click a)
    (.removeChild js/document.body a)
    (.revokeObjectURL js/URL url)))

(defn- save-file-picker-supported? []
  (some? (.-showSaveFilePicker js/window)))

(defn- save-via-picker! [filename text]
  (-> (.showSaveFilePicker
        js/window
        #js {:suggestedName filename
             :types #js [#js {:description "ceol backup (EDN)"
                              :accept #js {"application/edn" #js [".edn"]}}]})
      (.then (fn [^js handle]
               (-> (.createWritable handle)
                   (.then (fn [^js writable]
                            (-> (.write writable text)
                                (.then (fn [_] (.close writable)))))))))
      (.then (fn [_] (set-status! :success "Backup saved")))
      (.catch (fn [^js e]
                ;; AbortError = user cancelled; stay silent.
                (when (not= "AbortError" (.-name e))
                  (js/console.warn "backup: export failed" e)
                  (set-status! :error "Backup failed"))))))

(defn export!
  "Serialise current state to EDN. Uses showSaveFilePicker where supported
  so we get a real save/cancel signal; falls back to a plain anchor
  download elsewhere — which can't observe the system save dialog, so
  the message is the honest \"Backup ready\"."
  []
  (try
    (let [backup (build-backup @state/app-state)
          text   (pr-str backup)
          name*  (backup-filename)]
      (if (save-file-picker-supported?)
        (save-via-picker! name* text)
        (do (anchor-download! name* text)
            (set-status! :success "Backup ready"))))
    (catch :default e
      (js/console.warn "backup: export failed" e)
      (set-status! :error "Backup failed"))))

(defn apply-to-state
  "Pure: fold a validated backup's :data into a state map. abc-edits and
  tune-notes merge with existing state (incoming wins per key); tunes, sets,
  and learned-tune-ids replace wholesale because they are collections the
  user manages as a whole — a plain merge could never un-delete a tune (or
  a set, or a learned flag) the backup no longer has, since merge only adds
  or overwrites keys present in the incoming map."
  [s {:keys [abc-edits tunes sets learned-tune-ids tune-notes]}]
  (cond-> s
    abc-edits        (update :abc-edits merge abc-edits)
    tunes            (merge (state/prepare-tunes tunes))
    sets             (assoc :sets sets)
    learned-tune-ids (assoc :learned-tune-ids learned-tune-ids)
    tune-notes       (update :tune-notes merge tune-notes)))

(defn apply-backup!
  "Merge a validated backup into app-state and persist each section."
  [{:keys [data]}]
  (swap! state/app-state apply-to-state data)
  (when (:abc-edits data) (persist/schedule-save!))
  (when (:tunes data) (persist/save-tunes!))
  (when (:sets data) (persist/save-sets!))
  (when (:learned-tune-ids data) (persist/save-learned!))
  (when (:tune-notes data) (persist/schedule-save-notes!)))

(defn- read-file-text [file on-text]
  (let [reader (js/FileReader.)]
    (set! (.-onload reader) (fn [e] (on-text (.. e -target -result))))
    (set! (.-onerror reader) (fn [_]
                               (js/console.warn "backup: failed to read file")
                               (set-status! :error "Could not read file")))
    (.readAsText reader file)))

(defn- migrate-backup-data
  "Backups exported before catalog/custom-tunes were unified stored only the
  user's custom tunes under :custom-tunes — a delta over the base catalog,
  not the full tune set. The current :tunes is the whole tune set and imports
  wholesale-replace it, so aliasing the bare delta would wipe the catalog
  (leaving abc-edits/sets/learned pointing at now-missing tunes). Rebuild the
  full set by merging the catalog underneath the custom tunes, mirroring the
  legacy load path in persist/load-tunes!."
  [backup]
  (cond-> backup
    (contains? (:data backup) :custom-tunes)
    (update :data #(-> %
                       (assoc :tunes (merge tunes/catalog (:custom-tunes %)))
                       (dissoc :custom-tunes)))))

(defn import-text!
  "Parse EDN backup text, validate, and apply. Returns true on success,
  false if the file was unparseable or schema-invalid."
  [text]
  (if-let [backup (persist/read-validated "backup-import" text Backup migrate-backup-data)]
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
                    (do (js/console.log "backup: import successful")
                        (set-status! :success "Backup restored"))
                    (do (js/console.warn "backup: import failed; state unchanged")
                        (set-status! :error "Restore failed: invalid file"))))))))
    (.click input)))
