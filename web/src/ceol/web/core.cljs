(ns ceol.web.core
  (:require [replicant.dom :as r]
            [ceol.web.state :as state]
            [ceol.web.views :as views]
            [ceol.web.abc-bridge :as abc-bridge]
            [ceol.web.chords :as chords]
            [ceol.web.guitar :as guitar]
            [ceol.web.metronome :as metro]
            [ceol.web.beat-engine :as beat]
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

(defn save-sets!
  "Save sets to localStorage."
  []
  (.setItem js/localStorage "ceol-sets" (pr-str (:sets @state/app-state))))

(defn load-sets!
  "Load sets from localStorage."
  []
  (when-let [raw (.getItem js/localStorage "ceol-sets")]
    (try
      (let [sets (reader/read-string raw)]
        (swap! state/app-state assoc :sets sets))
      (catch :default _ nil))))

(defn save-custom-tunes!
  "Save custom tunes to localStorage."
  []
  (let [custom (:custom-tunes @state/app-state)]
    (.setItem js/localStorage "ceol-custom-tunes" (pr-str custom))))

(defn load-custom-tunes!
  "Load custom tunes from localStorage and merge into state."
  []
  (when-let [raw (.getItem js/localStorage "ceol-custom-tunes")]
    (try
      (let [custom (reader/read-string raw)]
        (swap! state/app-state (fn [s]
                                 (-> s
                                     (assoc :custom-tunes custom)
                                     (assoc :tunes (state/merge-tunes state/base-tunes custom))))))
      (catch :default _ nil))))

(defn- tune-by-id-from-base [tune-id]
  (first (filter #(= tune-id (:id %)) state/base-tunes)))

(defn update-tune-field!
  "Update a field on a tune and persist."
  [tune-id field value]
  (swap! state/app-state
         (fn [s]
           (let [custom (update (:custom-tunes s) tune-id
                                (fn [existing]
                                  (merge (or existing
                                             (tune-by-id-from-base tune-id))
                                         {:id tune-id field value})))
                 merged (state/merge-tunes state/base-tunes custom)]
             (assoc s :custom-tunes custom :tunes merged))))
  (save-custom-tunes!))

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

(defn split-abc-body
  "Split an ABC body (no headers) into A and B parts.
   Returns {:a \"...\" :b \"...\"} or nil if can't split.
   Each part gets proper barlines so it can stand alone."
  [body]
  (let [idx (.indexOf body ":|||:")]
    (if (pos? idx)
      (let [a (.trim (.substring body 0 (+ idx 2)))
            b (.trim (.substring body (+ idx 3)))]
        (when (and (seq a) (seq b))
          {:a a :b b}))
      (let [idx (.indexOf body ":|")]
        (when (pos? idx)
          (let [after-idx (+ idx 2)
                rest-body (.trim (.substring body after-idx))]
            (when (seq rest-body)
              {:a (.trim (.substring body 0 after-idx))
               :b rest-body})))))))

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
      (swap! state/app-state assoc :selected-tune-id tune-id
             :set-playing? false :set-tune-index 0))

    :abc/render nil

    :editor/toggle
    (swap! state/app-state update :editor-open? not)

    :editor/update
    (let [[tune-id new-val] args]
      (when (string? new-val)
        (swap! state/app-state assoc-in [:abc-edits tune-id] new-val)
        (schedule-save!)))

    :tune/add-to-set
    (let [[tune-id] args
          s @state/app-state
          sets (:sets s)]
      (cond
        (empty? sets)
        (js/console.warn "No sets — create one first")

        (= 1 (count sets))
        (let [set-id (key (first sets))]
          (handle-action! :set/add-tune [set-id tune-id]))

        (:active-set-id s)
        (handle-action! :set/add-tune [(:active-set-id s) tune-id])

        :else
        (js/console.warn "Multiple sets — select one first")))

    :tune/add
    (let [new-id (state/next-tune-id @state/app-state)
          new-tune {:id new-id :name "New Tune" :type :polka :time-sig "2/4"
                    :key "G" :mode-name "Ionian"}]
      (swap! state/app-state
             (fn [s]
               (let [custom (assoc (:custom-tunes s) new-id new-tune)
                     merged (state/merge-tunes state/base-tunes custom)]
                 (assoc s :custom-tunes custom :tunes merged
                        :selected-tune-id new-id :editing-field :name))))
      (save-custom-tunes!))

    :tune/update-field
    (let [[tune-id field value] args]
      (update-tune-field! tune-id field value)
      (swap! state/app-state assoc :editing-field nil))

    :tune/update-key-mode
    (let [[tune-id key-name mode-name] args]
      (update-tune-field! tune-id :key key-name)
      (update-tune-field! tune-id :mode-name mode-name)
      (swap! state/app-state assoc :editing-field nil))

    :tune/delete
    (let [[tune-id] args]
      (when (state/custom-tune? tune-id)
        (swap! state/app-state
               (fn [s]
                 (let [custom (dissoc (:custom-tunes s) tune-id)
                       merged (state/merge-tunes state/base-tunes custom)]
                   (assoc s :custom-tunes custom :tunes merged
                          :selected-tune-id nil))))
        (save-custom-tunes!)))

    :field/edit
    (let [[field] args]
      (swap! state/app-state assoc :editing-field field))

    :field/cancel
    (swap! state/app-state assoc :editing-field nil)

    :field/keydown
    (let [[key] args]
      (case key
        "Enter" (when-let [el (js/document.querySelector ".inline-edit-title")]
                  (.blur el))
        "Escape" (swap! state/app-state assoc :editing-field nil)
        nil))

    :playback/play
    (if (abc-bridge/playing?)
      (do (abc-bridge/stop!)
          (guitar/stop!)
          (metro/stop!)
          (swap! state/app-state assoc :playing? false :current-beat nil))
      (let [s @state/app-state
            tune (state/selected-tune s)
            abc-body (state/edited-abc-for-tune s (:id tune))
            in-set? (and (:active-set-id s) (= :sets (:tab s)))
            set-advancing? (:set-advancing? s)
            beat-params (beat/beats-for-tune tune (:tempo-offset s))]
        (swap! state/app-state assoc :playing? true
               :playing-section (when-not in-set? (:section s))
               :set-playing? (boolean in-set?)
               :set-tune-index (if in-set? (or (:set-tune-index s) 0) 0)
               :set-advancing? false)
        (letfn [(start-melody-and-guitar! []
                  ;; Start melody
                  (abc-bridge/play!
                   {:on-end (fn []
                              (guitar/stop!)
                              (let [s @state/app-state]
                                (if (:set-playing? s)
                                  (let [result (state/advance-set (:sets s) (:active-set-id s)
                                                                  (:set-tune-index s) (:loop? s))]
                                    (case (:action result)
                                      (:play :loop)
                                      (do (swap! state/app-state assoc
                                                 :set-tune-index (:index result)
                                                 :selected-tune-id (:tune-id result)
                                                 :set-advancing? true)
                                          (js/setTimeout #(handle-action! :playback/play nil) 500))
                                      (do (metro/stop!)
                                          (swap! state/app-state assoc :playing? false :playing-section nil
                                                 :set-playing? false :set-tune-index 0 :current-beat nil))))
                                  (do (swap! state/app-state assoc :playing? false :playing-section nil
                                             :current-beat nil)
                                      (when (:loop? s)
                                        (handle-action! :playback/play nil))))))})
                  ;; Start guitar
                  (when (and tune abc-body (string? abc-body))
                    (guitar/set-muted! (not (:guitar? s)))
                    (let [section (:section s)
                          parts (split-abc-body abc-body)
                          tonic (:key tune)
                          bar-chords (if section
                                       (let [part-body (if parts (get parts section abc-body) abc-body)
                                             chords (guitar/extract-bar-chords part-body)]
                                         (into chords chords))
                                       (if parts
                                         (let [a-chords (guitar/extract-bar-chords (:a parts))
                                               b-chords (guitar/extract-bar-chords (:b parts))]
                                           (vec (concat a-chords a-chords b-chords b-chords)))
                                         (let [chords (guitar/extract-bar-chords abc-body)]
                                           (into chords chords))))
                          filled (reduce (fn [acc c]
                                           (conj acc (or c (peek acc) tonic)))
                                         [] bar-chords)]
                      (guitar/play! filled (:type tune) (:time-sig tune)))))]
          (if (and (:metronome? s) (not set-advancing?))
            ;; Metronome on: count-in, then start melody + guitar + continuous metronome
            (metro/count-in! beat-params
                             (fn []
                               (start-melody-and-guitar!)
                               (metro/start-clicking! beat-params
                                                      {:on-beat (fn [{:keys [beat]}]
                                                                  (swap! state/app-state assoc :current-beat beat))})))
            ;; Metronome off (or set auto-advancing): play immediately
            (start-melody-and-guitar!)))))

    :playback/stop
    (do (abc-bridge/stop!)
        (guitar/stop!)
        (metro/stop!)
        (swap! state/app-state assoc :playing? false :playing-section nil
               :set-playing? false :set-tune-index 0 :current-beat nil))

    :guitar/toggle
    (let [new-val (not (:guitar? @state/app-state))]
      (swap! state/app-state assoc :guitar? new-val)
      (guitar/set-muted! (not new-val)))

    :section/set
    (let [[section] args]
      (swap! state/app-state assoc :section section))

    :loop/toggle
    (swap! state/app-state update :loop? not)

    :metronome/toggle
    (let [new-val (not (:metronome? @state/app-state))]
      (swap! state/app-state assoc :metronome? new-val :current-beat nil)
      ;; If turning off while playing, stop the clicks
      (when (and (not new-val) (metro/running?))
        (metro/stop!)))

    :tempo/up
    (swap! state/app-state update :tempo-offset #(min 40 (+ (or % 0) 5)))

    :tempo/down
    (swap! state/app-state update :tempo-offset #(max -40 (- (or % 0) 5)))

    :tempo/reset
    (swap! state/app-state assoc :tempo-offset 0)

    ;; --- Set actions ---

    :set/start-create
    (swap! state/app-state assoc :creating-set? true :creating-set-name nil
           :creating-set-tunes [] :typeahead-query "" :typeahead-index 0)

    :set/name-keydown
    (let [[key value] args]
      (case key
        "Enter" (when (and (string? value) (seq (.trim value)))
                  (swap! state/app-state assoc :creating-set-name (.trim value)))
        "Escape" (swap! state/app-state assoc :creating-set? false)
        nil))

    :set/typeahead
    (let [[query] args]
      (swap! state/app-state assoc :typeahead-query (or query "") :typeahead-index 0))

    :set/tune-keydown
    (let [[key] args
          s @state/app-state
          query (:typeahead-query s)
          results (state/search-tunes s query 5)
          idx (:typeahead-index s)]
      (case key
        "Enter"
        (if (seq (.trim (or query "")))
          ;; Pick highlighted result
          (when-let [tune (get results idx)]
            (let [tune-id (:id tune)
                  existing (:creating-set-tunes s)]
              (when-not (some #{tune-id} existing)
                (swap! state/app-state update :creating-set-tunes conj tune-id))
              (swap! state/app-state assoc :typeahead-query "" :typeahead-index 0)))
          ;; Empty enter = done
          (let [name (:creating-set-name s)
                tune-ids (:creating-set-tunes s)]
            (when (and name (seq tune-ids))
              (let [set-id (state/next-set-id s)
                    new-set {:id set-id :name name :tune-ids tune-ids}]
                (swap! state/app-state
                       (fn [s]
                         (assoc s :sets (assoc (:sets s) set-id new-set)
                                :creating-set? false
                                :active-set-id set-id
                                :selected-tune-id (first tune-ids))))
                (save-sets!)))))

        "Escape"
        (swap! state/app-state assoc :creating-set? false)

        "ArrowDown"
        (swap! state/app-state update :typeahead-index
               #(min (dec (count results)) (inc %)))

        "ArrowUp"
        (swap! state/app-state update :typeahead-index
               #(max 0 (dec %)))
        nil))

    :set/pick-tune
    (let [[tune-id] args
          s @state/app-state
          existing (:creating-set-tunes s)]
      (when-not (some #{tune-id} existing)
        (swap! state/app-state update :creating-set-tunes conj tune-id))
      (swap! state/app-state assoc :typeahead-query "" :typeahead-index 0))

    :set/uncreate-tune
    (let [[tune-id] args]
      (swap! state/app-state update :creating-set-tunes
             (fn [ids] (vec (remove #{tune-id} ids)))))

    :set/toggle
    (let [[set-id] args
          s @state/app-state]
      (if (= set-id (:active-set-id s))
        (swap! state/app-state assoc :active-set-id nil)
        (let [s-data (get (:sets s) set-id)
              first-tune-id (first (:tune-ids s-data))]
          (swap! state/app-state assoc :active-set-id set-id
                 :selected-tune-id first-tune-id))))

    :set/select-tune
    (let [[_set-id tune-id] args]
      (swap! state/app-state assoc :selected-tune-id tune-id))

    :set/add-tune
    (let [[set-id tune-id] args]
      (swap! state/app-state update-in [:sets set-id :tune-ids]
             (fn [ids]
               (if (some #{tune-id} ids) ids (conj (or ids []) tune-id))))
      (save-sets!))

    :set/remove-tune
    (let [[set-id tune-id] args]
      (swap! state/app-state update-in [:sets set-id :tune-ids]
             (fn [ids] (vec (remove #{tune-id} ids))))
      (save-sets!))

    :set/start-adding
    (let [[set-id] args]
      (swap! state/app-state assoc :adding-to-set set-id :typeahead-query "" :typeahead-index 0))

    :set/add-tune-keydown
    (let [[set-id key] args
          s @state/app-state
          query (:typeahead-query s)
          results (state/search-tunes s query 5)
          idx (:typeahead-index s)]
      (case key
        "Enter"
        (if (seq (.trim (or query "")))
          (when-let [tune (get results idx)]
            (swap! state/app-state update-in [:sets set-id :tune-ids]
                   (fn [ids]
                     (let [tid (:id tune)]
                       (if (some #{tid} ids) ids (conj (or ids []) tid)))))
            (swap! state/app-state assoc :typeahead-query "" :typeahead-index 0)
            (save-sets!))
          ;; Empty enter = done adding
          (swap! state/app-state assoc :adding-to-set nil))

        "Escape"
        (swap! state/app-state assoc :adding-to-set nil)

        "ArrowDown"
        (swap! state/app-state update :typeahead-index #(min (dec (count results)) (inc %)))

        "ArrowUp"
        (swap! state/app-state update :typeahead-index #(max 0 (dec %)))

        nil))

    :set/delete
    (let [[set-id] args]
      (swap! state/app-state (fn [s]
                               (-> s
                                   (update :sets dissoc set-id)
                                   (cond-> (= set-id (:active-set-id s))
                                     (assoc :active-set-id nil)))))
      (save-sets!))

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
        (let [section (:section s)
              body (if section
                     (let [parts (split-abc-body abc-body)]
                       (if parts
                         (get parts section abc-body)
                         abc-body))
                     abc-body)
              raw-abc (build-full-abc tune (add-line-breaks body 4))
              final-abc (abc/adjust-abc-tempo raw-abc (or (:tempo-offset s) 0))]
          ;; Defer so the DOM has updated from Replicant render
          (js/requestAnimationFrame
           (fn []
             (when-let [el (js/document.getElementById "sheet-music")]
               (abc-bridge/render-abc! el final-abc)))))))))

(defonce prev-render-key (atom nil))

(remove-watch state/app-state ::render)
(add-watch state/app-state ::render
           (fn [_ _ old-s s]
             (r/render el (views/app s))
             ;; Only re-render sheet music when tune or ABC changed
             (let [tune-id (:selected-tune-id s)
                   abc (state/edited-abc-for-tune s tune-id)
                   new-key [tune-id abc (:section s) (:tempo-offset s)]]
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
                 (swap! state/app-state assoc :abc-data data))))
      (.catch (fn [e]
                (js/console.error "Failed to load ABC data:" e)))))

(defn- input-focused? []
  (let [tag (some-> js/document .-activeElement .-tagName str)]
    (contains? #{"INPUT" "TEXTAREA" "SELECT"} tag)))

(defn- handle-keydown [e]
  (when-not (input-focused?)
    (let [key (.-key e)]
      (case key
        " "       (do (.preventDefault e) (handle-action! :playback/play nil))
        "l"       (handle-action! :loop/toggle nil)
        "g"       (handle-action! :guitar/toggle nil)
        "e"       (handle-action! :editor/toggle nil)
        "m"       (handle-action! :metronome/toggle nil)
        "="       (handle-action! :tempo/up nil)
        "-"       (handle-action! :tempo/down nil)
        "0"       (handle-action! :tempo/reset nil)
        "1"       (handle-action! :section/set [:a])
        "2"       (handle-action! :section/set [:b])
        "3"       (handle-action! :section/set [nil])
        "ArrowUp" (do (.preventDefault e)
                      (let [s @state/app-state
                            tunes (state/filtered-tunes s)
                            idx (.indexOf (mapv :id tunes) (:selected-tune-id s))
                            new-idx (max 0 (dec idx))]
                        (when (seq tunes)
                          (handle-action! :tune/select [(:id (nth tunes new-idx))]))))
        "ArrowDown" (do (.preventDefault e)
                        (let [s @state/app-state
                              tunes (state/filtered-tunes s)
                              idx (.indexOf (mapv :id tunes) (:selected-tune-id s))
                              new-idx (min (dec (count tunes)) (inc idx))]
                          (when (seq tunes)
                            (handle-action! :tune/select [(:id (nth tunes new-idx))]))))
        nil))))

(defonce _keydown-listener
  (.addEventListener js/document "keydown" handle-keydown))

(defn init! []
  (r/set-dispatch! execute!)
  (load-custom-tunes!)
  (load-sets!)
  (load-abc-data!)
  (load-saved-edits!)
  (r/render el (views/app @state/app-state)))
