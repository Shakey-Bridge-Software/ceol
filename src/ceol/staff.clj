(ns ceol.staff
  (:require [charm.core :as charm]
            [ceol.notation :as notation]
            [clojure.string :as str]))

;; --- Staff notation renderer for terminal ---
;; Renders parsed ABC notes onto a 5-line staff using Unicode characters.

;; Staff rows: 13 rows covering D4 to B6 (wide enough for Irish trad)
;; Row 0 = top (highest pitch), row 12 = bottom (lowest pitch)
;;
;; Pitch-to-row mapping (standard treble clef):
;;   Row 0:  A5 (space above line 5)    — ledger space
;;   Row 1:  G5 (line 5 — top staff line)
;;   Row 2:  F5 (space)
;;   Row 3:  E5 (line 4)
;;   Row 4:  D5 (space)
;;   Row 5:  C5 (line 3 — middle)
;;   Row 6:  B4 (space)
;;   Row 7:  A4 (line 2)
;;   Row 8:  G4 (space)
;;   Row 9:  F4 (line 1 — bottom staff line)
;;   Row 10: E4 (space below)
;;   Row 11: D4 (ledger line)
;;   Row 12: C4 (ledger space below)

(def ^:private staff-rows 13)

;; Which rows are staff lines (solid horizontal lines)
(def ^:private staff-line-rows #{1 3 5 7 9})

;; Which rows are ledger line rows (drawn only when a note is on them)
(def ^:private ledger-line-rows #{11})

(def ^:private pitch->row
  ;; Maps {:note "X" :octave N} to row index
  ;; Octave 4: C4=12, D4=11, E4=10, F4=9, G4=8, A4=7, B4=6
  ;; Octave 5: C5=5, D5=4, E5=3, F5=2, G5=1, A5=0
  ;; Octave 6: B5 and above go off the top — clamp
  (let [note-offsets {"C" 0 "D" 1 "E" 2 "F" 3 "G" 4 "A" 5 "B" 6}]
    (fn [{:keys [note octave]}]
      (let [base-row (case octave
                       4 12
                       5 5
                       6 -2
                       (if (< octave 4) 12 -2))
            offset (get note-offsets note 0)
            row (- base-row offset)]
        (max 0 (min (dec staff-rows) row))))))

;; --- Colors ---

(def ^:private color-staff-line (charm/rgb 60 60 70))
(def ^:private color-notehead   (charm/rgb 220 215 200))
(def ^:private color-highlight  (charm/rgb 255 220 80))
(def ^:private color-played     (charm/rgb 90 90 100))
(def ^:private color-barline    (charm/rgb 80 80 90))
(def ^:private color-accidental (charm/rgb 180 160 130))
(def ^:private color-bg-highlight (charm/rgb 60 50 20))

;; --- Grid building ---

(defn- accidental-str [{:keys [accidental]}]
  (case accidental
    :sharp "\u266F"
    :flat  "\u266D"
    :natural "\u266E"
    nil))

(defn build-grid
  "Build a character grid for staff display.
   Returns {:grid [[char ...] ...] :note-cols [col-idx ...] :width W :height H}

   Parameters:
   - timeline: parsed note timeline from notation/parse-abc
   - max-width: maximum character width available
   - current-note-idx: index of currently playing note (nil if not playing)"
  [timeline max-width current-note-idx]
  (let [;; Filter to notes and barlines only
        events (filterv #(#{:note :barline :rest} (:type %)) timeline)
        note-events (filterv #(= :note (:type %)) timeline)

        ;; Calculate columns needed
        ;; Reserve 2 cols for clef area, 1 col per barline, 2 cols per note min
        num-notes (count note-events)
        num-barlines (count (filter #(= :barline (:type %)) events))

        ;; Allocate space
        clef-cols 3
        barline-cols (* 1 num-barlines)
        available (- max-width clef-cols 2) ; 2 for margins
        note-width (if (pos? num-notes)
                     (max 2 (min 4 (int (/ (- available barline-cols) (max 1 num-notes)))))
                     2)

        ;; Build column assignments
        ;; Each event gets a column position
        col-assignments (loop [i 0 col clef-cols assignments []]
                          (if (>= i (count events))
                            assignments
                            (let [evt (nth events i)]
                              (case (:type evt)
                                :barline
                                (recur (inc i) (inc col)
                                       (conj assignments {:event evt :col col :width 1}))

                                (:note :rest)
                                (recur (inc i) (+ col note-width)
                                       (conj assignments {:event evt :col col :width note-width}))))))

        total-width (if (seq col-assignments)
                      (let [last-a (peek col-assignments)]
                        (+ (:col last-a) (:width last-a) 1))
                      (+ clef-cols 4))
        total-width (min total-width max-width)

        ;; Build the grid row by row
        grid (vec (for [row (range staff-rows)]
                    (let [is-staff-line (contains? staff-line-rows row)
                          base-char (if is-staff-line "─" " ")
                          line-arr (vec (repeat total-width base-char))]
                      ;; Place clef area
                      (reduce (fn [arr {:keys [event col width]}]
                                (let [tw (count arr)]
                                  (case (:type event)
                                    :barline
                                    (let [bc (min col (dec tw))]
                                      (if (and (>= bc 0) (< bc tw))
                                        (assoc arr bc "\u2502")
                                        arr))

                                    :note
                                    (let [note-row (pitch->row (:pitch event))
                                          acc (accidental-str (:pitch event))]
                                      (if (= note-row row)
                                        (let [head-col (min (+ col (quot width 2)) (dec tw))]
                                          (if (and (>= head-col 0) (< head-col tw))
                                            (let [arr' (assoc arr head-col "\u25CF")]
                                              (if (and acc (> head-col 0) (< (dec head-col) tw))
                                                (assoc arr' (dec head-col) acc)
                                                arr'))
                                            arr))
                                        arr))

                                    :rest
                                    (if (= row 4)
                                      (let [head-col (min (+ col (quot width 2)) (dec total-width))]
                                        (if (< head-col (count arr))
                                          (assoc arr head-col "-")
                                          arr))
                                      arr)

                                    ;; default
                                    arr)))
                              line-arr
                              col-assignments))))

        ;; Map note indices to their column positions
        note-col-map (vec (keep (fn [{:keys [event col width]}]
                                  (when (= :note (:type event))
                                    {:col (+ col (quot width 2)) :width width :start-col col}))
                                col-assignments))]

    {:grid grid
     :note-cols note-col-map
     :width total-width
     :height staff-rows
     :current-note-idx current-note-idx}))

(defn- cell-style-key
  "Return a style key for a cell to enable segment grouping."
  [cell is-staff-line is-ledger in-highlight in-played]
  (let [is-notehead (= cell "\u25CF")
        is-accidental (#{"\u266F" "\u266D" "\u266E"} cell)
        is-barline (= cell "\u2502")
        is-rest (= cell "-")]
    (cond
      (and in-highlight is-notehead) :highlight-note
      (and in-highlight is-barline)  :barline
      in-highlight                   :highlight
      (and in-played is-notehead)    :played
      is-notehead                    :note
      is-accidental                  :accidental
      is-barline                     :barline
      is-rest                        :rest
      is-staff-line                  :staff-line
      (and is-ledger (= cell "\u2500")) :ledger-space
      :else                          :space)))

(defn- style-segment [text style-key]
  (case style-key
    :highlight-note (charm/styled text :fg color-highlight :bold true)
    :highlight      (charm/styled text :fg color-highlight)
    :played         (charm/styled text :fg color-played)
    :note           (charm/styled text :fg color-notehead :bold true)
    :accidental     (charm/styled text :fg color-accidental)
    :barline        (charm/styled text :fg color-barline)
    :rest           (charm/styled text :fg color-notehead)
    :staff-line     (charm/styled text :fg color-staff-line)
    :ledger-space   " "
    text))

(defn render-staff
  "Render staff grid to a styled string for terminal display.
   Groups consecutive same-styled cells to minimize ANSI sequences."
  [timeline max-width current-note-idx]
  (when (seq timeline)
    (let [{:keys [grid note-cols width height]} (build-grid timeline max-width current-note-idx)

          highlight-cols (when (and current-note-idx
                                    (< current-note-idx (count note-cols)))
                           (let [{:keys [start-col width]} (nth note-cols current-note-idx)]
                             (set (range start-col (+ start-col width)))))

          played-cols (when current-note-idx
                        (set (mapcat (fn [nc]
                                       (range (:start-col nc) (+ (:start-col nc) (:width nc))))
                                     (take current-note-idx note-cols))))]

      (str/join "\n"
                (for [row (range height)]
                  (let [is-staff-line (contains? staff-line-rows row)
                        is-ledger (contains? ledger-line-rows row)
                        ;; Build segments: group consecutive cells with same style
                        cells (for [col (range width)]
                                (let [cell (or (get-in grid [row col]) " ")
                                      in-highlight (and highlight-cols (contains? highlight-cols col))
                                      in-played (and played-cols (contains? played-cols col))
                                      sk (cell-style-key cell is-staff-line is-ledger in-highlight in-played)]
                                  {:char cell :style sk}))
                        ;; Group consecutive same-style cells
                        segments (reduce (fn [acc {:keys [char style]}]
                                           (if (and (seq acc) (= style (:style (peek acc))))
                                             (update-in acc [(dec (count acc)) :text] str char)
                                             (conj acc {:text char :style style})))
                                         []
                                         cells)]
                    (apply str (map #(style-segment (:text %) (:style %)) segments))))))))

(defn render-staff-compact
  "Render a compact staff view that fits in limited vertical space.
   Wraps notes into multiple lines if needed, like real sheet music."
  [timeline max-width current-note-idx]
  (let [note-events (filterv #(#{:note :rest :barline} (:type %)) timeline)
        ;; Estimate how many events fit per line
        events-per-line (max 8 (int (/ (- max-width 6) 3)))
        ;; Split into lines
        line-groups (partition-all events-per-line note-events)]
    (str/join "\n\n"
              (map-indexed
               (fn [line-idx group]
                 (let [;; Adjust current-note-idx relative to this group
                       group-start (* line-idx events-per-line)
                       note-count-before (count (filter #(= :note (:type %))
                                                        (take group-start note-events)))
                       notes-in-group (count (filter #(= :note (:type %)) group))
                       local-idx (when current-note-idx
                                   (let [adj (- current-note-idx note-count-before)]
                                     (when (and (>= adj 0) (< adj notes-in-group))
                                       adj)))]
                   (render-staff (vec group) max-width local-idx)))
               line-groups))))
