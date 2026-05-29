(ns ceol.web.handlers.tune
  "Tune CRUD + mobile tune-details editor action handlers. Each fn takes the
  [args] vec from dispatch-action! and performs state mutation + persistence
  side effects. Pure helpers behind the mobile editor live in
  ceol.web.handlers.tune-editor. inject-chords-if-needed sits here because
  it is only used by select! when annotating freshly-loaded ABC."
  (:require [ceol.web.state :as state]
            [ceol.web.chords :as chords]
            [ceol.web.persist :as persist]
            [ceol.web.handlers.tune-editor :as ed]))

(defn- inject-chords-if-needed
  "If the ABC body has no chord annotations, suggest and inject them."
  [abc-body tune]
  (if (re-find #"\"[A-G]" abc-body)
    abc-body
    (let [chord-names (chords/suggest-chords abc-body (:key tune) (:mode-name tune))]
      (chords/inject-chords abc-body chord-names))))

(defn select! [[tune-id]]
  (let [s @state/app-state
        raw-abc (get (:abc-data s) tune-id)
        existing-edit (get (:abc-edits s) tune-id)]
    (when (and (string? raw-abc) (not existing-edit))
      (try
        (let [tune (state/tune-by-id s tune-id)
              annotated (inject-chords-if-needed raw-abc tune)]
          (swap! state/app-state assoc-in [:abc-edits tune-id]
                 (if (string? annotated) annotated raw-abc)))
        (catch :default e
          (js/console.warn "Chord injection failed for tune" tune-id e)
          (swap! state/app-state assoc-in [:abc-edits tune-id] raw-abc)))))
  (swap! state/app-state assoc :selected-tune-id tune-id
         :set-playing? false :set-tune-index 0
         :context-menu-tune-id nil
         :main-view :tune
         :mobile-view :detail))

(defn add! [_args]
  (let [new-id (state/next-tune-id @state/app-state)
        new-tune {:id new-id :name "Untitled tune" :type :polka :time-sig "2/4"
                  :key "G" :mode-name "Ionian"}]
    (swap! state/app-state
           #(merge %
                   (state/prepare-tunes (assoc (:tunes %) new-id new-tune))
                   {:selected-tune-id new-id
                    :editing-field    :name
                    :main-view        :tune
                    :editor-open?     true
                    :tab              :tunes}))
    (persist/save-tunes!)))

(defn update-field! [[tune-id field value]]
  (persist/update-tune-field! tune-id field value)
  (swap! state/app-state assoc :editing-field nil))

(defn update-key-mode! [[tune-id key-name mode-name]]
  (persist/update-tune-field! tune-id :key key-name)
  (persist/update-tune-field! tune-id :mode-name mode-name)
  (swap! state/app-state assoc :editing-field nil))

(defn delete! [[tune-id]]
  ;; Scrub every per-tune keyspace, not just :tunes. :abc-edits, :tune-notes
  ;; and :learned-tune-ids key by tune ID independently — leaving them
  ;; orphaned both leaks the deleted tune's data and lets next-tune-id
  ;; re-allocate the id with a stranger's note/learned flag attached.
  (swap! state/app-state
         #(-> (merge %
                     (state/prepare-tunes (dissoc (:tunes %) tune-id))
                     {:selected-tune-id nil})
              (update :abc-edits dissoc tune-id)
              (update :tune-notes dissoc tune-id)
              (update :learned-tune-ids disj tune-id)))
  (persist/save-tunes!)
  (persist/schedule-save!)
  (persist/schedule-save-notes!)
  (persist/save-learned!))


(defn duplicate! [[tune-id]]
  ;; Clone any tune (catalog or user-added) into a fresh tune with " (copy)"
  ;; suffix. Carries the ABC body across (from :abc-edits, falling back to
  ;; :abc-data) so the duplicate immediately renders. Selects the new tune.
  (let [s        @state/app-state
        src      (state/tune-by-id s tune-id)]
    (when src
      (let [new-id    (state/next-tune-id s)
            all-names (mapv :name (vals (:tunes s)))
            new-name  (ed/unique-copy-name (:name src) all-names)
            new-tune  (-> src
                          (assoc :id new-id :name new-name)
                          ;; Drop session-id — a copy is no longer the canonical
                          ;; thesession.org reference.
                          (dissoc :session-id))
            src-abc   (state/edited-abc-for-tune s tune-id)]
        (swap! state/app-state
               (fn [s]
                 (cond-> (merge s
                                (state/prepare-tunes (assoc (:tunes s) new-id new-tune))
                                {:selected-tune-id new-id
                                 :main-view :tune
                                 :mobile-view :detail
                                 :context-menu-tune-id nil})
                   src-abc (assoc-in [:abc-edits new-id] src-abc))))
        (persist/save-tunes!)
        (when src-abc (persist/schedule-save!))))))

(defn add-to-set! [[tune-id]]
  (let [s    @state/app-state
        sets (:sets s)]
    (cond
      (empty? sets)
      (js/console.warn "No sets — create one first")

      (= 1 (count sets))
      (let [set-id (key (first sets))]
        (swap! state/app-state update-in [:sets set-id :tune-ids]
               (fn [ids] (if (some #{tune-id} ids) ids (conj (or ids []) tune-id))))
        (persist/save-sets!))

      (:active-set-id s)
      (let [set-id (:active-set-id s)]
        (swap! state/app-state update-in [:sets set-id :tune-ids]
               (fn [ids] (if (some #{tune-id} ids) ids (conj (or ids []) tune-id))))
        (persist/save-sets!))

      :else
      (js/console.warn "Multiple sets — select one first"))))

;; --- Mobile tune-details editor ---------------------------------------------
;; The desktop tune-header has click-to-cycle fields for name/type/key/etc, but
;; the mobile layout hides it. These handlers drive a mobile-only full-screen
;; editor with draft semantics (Cancel discards, Save commits). Pure helpers
;; live in ceol.web.handlers.tune-editor (`ed/`); side effects stay here.

(defn editor-open-new! [_args]
  (swap! state/app-state assoc
         :tune-editor {:mode :new :tune-id nil :draft ed/blank-draft}
         :context-menu-tune-id nil))

(defn editor-open-edit! [[tune-id]]
  (let [tune (state/tune-by-id @state/app-state tune-id)]
    (when tune
      (swap! state/app-state assoc
             :tune-editor {:mode :edit :tune-id tune-id :draft (ed/tune->draft tune)}
             :context-menu-tune-id nil))))

(defn editor-cancel! [_args]
  (swap! state/app-state assoc :tune-editor nil))

(defn editor-update-draft! [[field value]]
  (swap! state/app-state assoc-in [:tune-editor :draft field] value))

(defn- save-new! [draft]
  (let [new-id   (state/next-tune-id @state/app-state)
        new-tune (ed/draft->new-tune new-id draft)]
    (swap! state/app-state
           #(merge %
                   (state/prepare-tunes (assoc (:tunes %) new-id new-tune))
                   {:selected-tune-id new-id
                    :main-view :tune
                    :mobile-view :detail
                    :tab :tunes
                    :tune-editor nil}))
    (persist/save-tunes!)))

(defn- save-edit! [tune-id draft]
  (doseq [[field value] (ed/draft->edit-updates draft)]
    (persist/update-tune-field! tune-id field value))
  (swap! state/app-state assoc :tune-editor nil :editing-field nil))

(defn editor-save! [_args]
  (when-let [{:keys [mode tune-id draft]} (:tune-editor @state/app-state)]
    (case mode
      :new  (save-new! draft)
      :edit (save-edit! tune-id draft)
      nil)))
