(ns ceol.web.handlers.tune
  "Tune CRUD action handlers: select, add, update fields, delete, and
   add-to-set. Pure dispatch targets — each function takes the [args] vec
   from the action and performs the state mutation + persistence side
   effects. inject-chords-if-needed lives here because it is only used by
   select! when annotating freshly-loaded ABC."
  (:require [ceol.web.state :as state]
            [ceol.web.chords :as chords]
            [ceol.web.persist :as persist]))

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
           (fn [s]
             (let [custom (assoc (:custom-tunes s) new-id new-tune)
                   merged (state/merge-tunes state/base-tunes custom)]
               (merge s {:custom-tunes custom} merged
                      {:selected-tune-id new-id
                       :editing-field :name
                       :main-view :tune
                       :editor-open? true
                       :tab :tunes}))))
    (persist/save-custom-tunes!)))

(defn update-field! [[tune-id field value]]
  (persist/update-tune-field! tune-id field value)
  (swap! state/app-state assoc :editing-field nil))

(defn update-key-mode! [[tune-id key-name mode-name]]
  (persist/update-tune-field! tune-id :key key-name)
  (persist/update-tune-field! tune-id :mode-name mode-name)
  (swap! state/app-state assoc :editing-field nil))

(defn delete! [[tune-id]]
  (when (state/custom-tune? tune-id)
    (swap! state/app-state
           (fn [s]
             (let [custom (dissoc (:custom-tunes s) tune-id)
                   merged (state/merge-tunes state/base-tunes custom)]
               (merge s {:custom-tunes custom} merged {:selected-tune-id nil}))))
    (persist/save-custom-tunes!)))

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
