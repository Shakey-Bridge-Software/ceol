(ns ceol.view
  (:require [charm.core :as charm]
            [charm.components.spinner :as spinner]
            [ceol.tunes :as tunes]
            [ceol.state :as state]
            [clojure.string :as str]))

;; -- Design System --

;; Palette — sage green, warm gold, cream (Irish music feel)
(def color-accent   (charm/rgb 130 180 120))  ; sage green
(def color-gold     (charm/rgb 220 185 100))  ; warm gold
(def color-text     (charm/rgb 220 215 200))  ; cream
(def color-dim      (charm/rgb 140 140 140))
(def color-muted    (charm/rgb 100 100 110))
(def color-faint    (charm/rgb 60 60 70))
(def color-danger   charm/red)
(def color-select-bg (charm/rgb 30 40 30))

;; Type colors
(def type-colors
  {:polka    (charm/rgb 180 160 220)   ; lavender
   :jig      (charm/rgb 120 170 220)   ; blue
   :reel     (charm/rgb 220 180 100)   ; amber
   :hornpipe (charm/rgb 160 210 120)   ; lime
   :slip-jig (charm/rgb 220 140 160)   ; rose
   :other    (charm/rgb 160 160 170)}) ; grey

;; Safe border — heavy box-drawing chars bypass JLine VT100 ACS conversion
(def safe-border
  {:top "━" :bottom "━" :left "┃" :right "┃"
   :top-left "╭" :top-right "╮" :bottom-left "╰" :bottom-right "╯"})

;; -- Helpers --

(defn content-width [state]
  (min 72 (max 40 (- (:width state 80) 4))))

(defn strip-ansi [s]
  (str/replace (str s) #"\033\[[0-9;]*m" ""))

(defn pad-right [s width]
  (let [len (count (strip-ansi s))
        pad (max 0 (- width len))]
    (str s (apply str (repeat pad " ")))))

(defn divider [w]
  (charm/styled (apply str (repeat w "\u2501")) :fg color-faint))

;; -- Header --

(defn render-header [state]
  (let [w (content-width state)
        filter-type (:filter state)
        title (charm/styled " ceol" :fg color-accent :bold true)
        shamrock (charm/styled "\u2618" :fg color-accent)
        left-str (str shamrock title)

        filter-str (charm/styled (tunes/type-label filter-type) :fg color-dim)
        count-str (charm/styled (str (count (state/visible-tunes state)) " tunes") :fg color-muted)
        right-str (str filter-str "  " count-str)

        left-w (count (strip-ansi left-str))
        right-w (count (strip-ansi right-str))
        gap (max 1 (- w left-w right-w))]
    (str left-str (apply str (repeat gap " ")) right-str)))

;; -- Footer --

(defn render-footer [state]
  (let [bindings (charm/help-from-pairs
                  "j/k" "nav" "\u21B5" "play" "l" "loop"
                  "=/-" "tempo" "1/2" "part" "?" "help")
        h (charm/help bindings
                      :width (content-width state)
                      :separator " \u00B7 "
                      :key-style (charm/style :fg color-text :bold true)
                      :desc-style (charm/style :fg color-muted)
                      :separator-style (charm/style :fg color-faint))]
    (charm/help-view h)))

;; -- Status bar --

(defn render-status-bar [state]
  (let [w (content-width state)]
    (cond
      (:playing state)
      (let [tune (tunes/tune-by-id (:tunes state) (:playing state))
            spinner-str (if (:spinner state)
                          (spinner/spinner-view (:spinner state))
                          "\u266A")
            section (:section state)
            section-str (when section
                          (str " [" (str/upper-case (name section)) "]"))
            tempo-offset (:tempo-offset state)
            tempo-str (when (and tempo-offset (not (zero? tempo-offset)))
                        (str "  " (when (pos? tempo-offset) "+") tempo-offset " BPM"))
            loop-str (when (:loop state) "  \u21BB")
            msg (str "  " (charm/styled spinner-str :fg color-gold)
                     " " (charm/styled (str "Playing: " (:name tune)
                                            (or section-str "")
                                            (or tempo-str "")
                                            (or loop-str ""))
                                       :fg color-gold))]
        msg)

      (:loading state)
      (let [tune (tunes/tune-by-id (:tunes state) (:loading state))
            spinner-str (if (:spinner state)
                          (spinner/spinner-view (:spinner state))
                          "...")
            msg (str "  " (charm/styled spinner-str :fg color-accent)
                     " " (charm/styled (str "Loading: " (:name tune)) :fg color-dim))]
        msg)

      :else nil)))

;; -- Tune row --

(defn render-tune-row [tune selected? w playing?]
  (let [type-color (get type-colors (:type tune) color-dim)

        ;; Cursor
        cursor-str (if selected?
                     (charm/styled "> " :fg color-accent :bold true)
                     "  ")

        ;; ID (3ch)
        id-str (charm/styled (format "%2d " (:id tune)) :fg color-muted)

        ;; Name (variable width)
        name-color (if playing? color-gold type-color)
        name-str (charm/styled (:name tune) :fg name-color :bold selected?)

        ;; Type + time sig + key (right portion)
        type-str (charm/styled (tunes/type-label (:type tune)) :fg type-color)
        sig-str (charm/styled (:time-sig tune) :fg color-dim)
        key-str (charm/styled (str (:key tune) " " (subs (:mode-name tune) 0 3))
                              :fg color-dim)

        ;; Status icon
        status (tunes/status-icon tune)
        status-color (case (:midi-status tune)
                       :ready color-accent
                       :failed color-danger
                       (case (:abc-status tune)
                         :ready color-gold
                         :failed color-danger
                         color-faint))
        status-str (charm/styled status :fg status-color)

        ;; Playing indicator
        play-str (when playing?
                   (charm/styled " \u266A" :fg color-gold))

        left-part (str cursor-str id-str name-str)
        right-part (str type-str " " sig-str "  " key-str "  " status-str (or play-str ""))

        left-w (count (strip-ansi left-part))
        right-w (count (strip-ansi right-part))
        gap (max 1 (- w left-w right-w))
        line (str left-part (apply str (repeat gap " ")) right-part)]

    (if selected?
      (charm/styled line :bg color-select-bg)
      line)))

;; -- Tune list --

(defn render-tune-list [state]
  (let [w (content-width state)
        visible (state/visible-tunes state)
        cursor (:cursor state)
        playing (:playing state)
        ;; Scroll window
        max-visible (max 3 (- (:height state 24) 10))
        total (count visible)
        scroll-start (cond
                       (<= total max-visible) 0
                       (< cursor (quot max-visible 2)) 0
                       (> cursor (- total (quot max-visible 2))) (- total max-visible)
                       :else (- cursor (quot max-visible 2)))
        scroll-end (min total (+ scroll-start max-visible))
        windowed (subvec (vec visible) scroll-start scroll-end)]
    (if (empty? visible)
      (charm/styled "  no tunes match filter" :fg color-muted :italic true)
      (->> windowed
           (map-indexed (fn [idx tune]
                          (let [actual-idx (+ scroll-start idx)]
                            (render-tune-row tune
                                             (= actual-idx cursor)
                                             w
                                             (= (:id tune) playing)))))
           (str/join "\n")))))

;; -- Help overlay --

(defn render-help-overlay []
  (let [section (fn [title]
                  (charm/styled title :fg color-accent :bold true))
        key-line (fn [keys desc]
                   (str "  " (charm/styled keys :fg color-text :bold true)
                        (apply str (repeat (- 16 (count keys)) " "))
                        (charm/styled desc :fg color-dim)))]
    (charm/styled
     (str/join "\n"
               [(section "navigate")
                (key-line "j/k, \u2191/\u2193" "move cursor")
                (key-line "f" "cycle filter")
                ""
                (section "playback")
                (key-line "Enter/Space" "play or stop tune")
                (key-line "s" "stop playback")
                (key-line "p" "prepare (fetch + convert)")
                (key-line "l" "toggle loop")
                ""
                (section "tempo")
                (key-line "=" "tempo +5 BPM")
                (key-line "-" "tempo -5 BPM")
                (key-line "0" "reset tempo")
                ""
                (section "sections")
                (key-line "1" "toggle section A")
                (key-line "2" "toggle section B")
                ""
                (section "info")
                (key-line "?" "toggle help")
                (key-line "q" "quit")
                ""
                (charm/styled "ceol \u2014 Irish trad sheet music player" :fg color-muted :italic true)
                ""
                (charm/styled "? or esc to close" :fg color-faint :italic true)])
     :border safe-border :border-fg color-faint :padding [1 2 1 2])))

;; -- Flash --

(defn render-flash [state]
  (when-let [f (:flash state)]
    (let [warning? (or (str/includes? f "failed") (str/includes? f "error"))
          color (if warning? color-danger color-accent)]
      (charm/styled (str "  " f) :fg color))))

;; -- Main render --

(defn ensure-min-height [text min-h]
  (let [lines (str/split-lines text)
        current (count lines)
        pad (max 0 (- min-h current))]
    (str/join "\n" (concat lines (repeat pad "")))))

(defn render [state]
  (case (:mode state)
    :help (render-help-overlay)

    ;; Default: browse
    (let [w (content-width state)
          header (render-header state)
          top-div (divider w)
          tune-list (ensure-min-height (render-tune-list state) 5)
          status-bar (render-status-bar state)
          flash-msg (render-flash state)
          bot-div (divider w)
          footer (render-footer state)

          body (charm/join-vertical :left
                                    header
                                    top-div
                                    (str/join "\n" (filterv some?
                                                            [""
                                                             tune-list
                                                             status-bar
                                                             flash-msg
                                                             ""]))
                                    bot-div
                                    footer)]
      (charm/styled body :border safe-border :border-fg color-faint :padding [0 1 0 1]))))
