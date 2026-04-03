(ns ceol.web.core
  (:require [replicant.dom :as r]
            [ceol.web.state :as state]
            [ceol.web.views :as views]
            [ceol.web.abc-bridge :as abc-bridge]
            [ceol.web.chords :as chords]
            [ceol.abc :as abc]
            [cljs.reader :as reader]
            [clojure.walk :as walk]))

(defonce el (js/document.getElementById "app"))

(defn build-full-abc
  "Build a complete ABC string from a tune and its raw ABC body.
   Skips %%MIDI directive (not needed for abc.js web rendering)."
  [tune abc-body]
  (let [mode-abbrev (case (:mode-name tune)
                      "Ionian" ""
                      "Dorian" "dor"
                      "Aeolian" "m"
                      "")
        k-field (str (:key tune) mode-abbrev)
        tempo (abc/tempo-for-type (:type tune) (:time-sig tune))]
    (str "X:1\n"
         "T:" (:name tune) "\n"
         "M:" (:time-sig tune) "\n"
         "L:1/8\n"
         tempo "\n"
         "K:" k-field "\n"
         abc-body "\n")))

(defn add-line-breaks
  "Insert newlines after every n-th bar in ABC body for multi-line rendering.
   Counts single | barlines (not :| or |: which are repeat markers)."
  [abc-body bars-per-line]
  (let [chars (seq abc-body)]
    (loop [remaining chars
           bar-count 0
           result []]
      (if (empty? remaining)
        (apply str result)
        (let [c (first remaining)]
          (if (= c \|)
            (let [next-c (second remaining)
                  ;; Don't count :| |: || as a simple barline for line-break purposes
                  ;; but DO count them for output
                  simple-bar? (and (not= next-c \|)
                                   (not= next-c \:)
                                   ;; check if previous char was :
                                   (not= (peek result) \:))
                  new-count (if simple-bar? (inc bar-count) bar-count)
                  need-break? (and simple-bar?
                                   (pos? bars-per-line)
                                   (zero? (mod new-count bars-per-line)))]
              (recur (rest remaining)
                     new-count
                     (if need-break?
                       (conj result c \newline)
                       (conj result c))))
            (recur (rest remaining) bar-count (conj result c))))))))

(defn inject-chords-if-needed
  "If the ABC body doesn't already have chord annotations, suggest and inject them."
  [abc-body tune]
  (if (re-find #"\"[A-G]" abc-body)
    ;; Already has chords
    abc-body
    ;; Suggest and inject
    (let [chord-names (chords/suggest-chords abc-body (:key tune) (:mode-name tune))]
      (chords/inject-chords abc-body chord-names))))

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

(defn load-saved-edits!
  "Load abc-edits from localStorage."
  []
  (when-let [raw (.getItem js/localStorage "ceol-abc-edits")]
    (try
      (let [edits (reader/read-string raw)]
        (swap! state/app-state assoc :abc-edits edits))
      (catch :default _ nil))))

(defn resolve-event-placeholders [dispatch-data actions]
  (let [js-event (:replicant/js-event dispatch-data)]
    (walk/postwalk
     (fn [x]
       (if (and (keyword? x) (= "event" (namespace x)))
         (case x
           :event/target.value (some-> js-event .-target .-value)
           :event/target.checked (some-> js-event .-target .-checked)
           :event/key (some-> js-event .-key)
           x)
         x))
     actions)))

(defn handle-action! [action args]
  (case action
    :filter/set
    (let [[filter-type] args]
      (swap! state/app-state assoc :filter filter-type))

    :tab/set
    (let [[tab] args]
      (swap! state/app-state assoc :tab tab))

    :tune/select
    (let [[tune-id] args]
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
      (swap! state/app-state assoc :selected-tune-id tune-id))

    :abc/render nil

    :editor/toggle
    (swap! state/app-state update :editor-open? not)

    :editor/update
    (let [[tune-id new-val] args]
      (when (string? new-val)
        (swap! state/app-state assoc-in [:abc-edits tune-id] new-val)
        (schedule-save!)))

    :tune/add-to-set nil
    :playback/play nil
    :tempo/up nil
    :tempo/down nil

    (js/console.warn "Unknown action:" action args)))

(defn execute! [dispatch-data actions]
  (let [actions (resolve-event-placeholders dispatch-data actions)]
    (doseq [[action & args] actions]
      (handle-action! action args))))

(defn render-sheet-music!
  "Imperatively render ABC into the #sheet-music div if present."
  [s]
  (when-let [tune (state/selected-tune s)]
    (when-let [abc-body (state/edited-abc-for-tune s (:id tune))]
      (when (string? abc-body)
        ;; Defer so the DOM has updated from Replicant render
        (js/requestAnimationFrame
         (fn []
           (when-let [el (js/document.getElementById "sheet-music")]
             (let [full-abc (build-full-abc tune (add-line-breaks abc-body 4))]
               (abc-bridge/render-abc! el full-abc)))))))))

(defonce prev-render-key (atom nil))

(add-watch state/app-state ::render
           (fn [_ _ old-s s]
             (r/render el (views/app s))
             ;; Only re-render sheet music when tune or ABC changed
             (let [tune-id (:selected-tune-id s)
                   abc (state/edited-abc-for-tune s tune-id)
                   new-key [tune-id abc]]
               (when (not= new-key @prev-render-key)
                 (reset! prev-render-key new-key)
                 ;; Evict stale non-string edits (e.g. from earlier bug)
                 (when (and abc (not (string? abc)))
                   (swap! state/app-state update :abc-edits dissoc tune-id))
                 (render-sheet-music! s)))))

(defn load-abc-data!
  "Fetch local-abc.edn and merge into app state."
  []
  (-> (js/fetch "/data/local-abc.edn")
      (.then #(.text %))
      (.then (fn [text]
               (let [data (reader/read-string text)]
                 (swap! state/app-state assoc :abc-data data))))))

(defn init! []
  (r/set-dispatch! execute!)
  (load-abc-data!)
  (load-saved-edits!)
  (r/render el (views/app @state/app-state)))
