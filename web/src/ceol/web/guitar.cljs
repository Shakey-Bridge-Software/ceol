(ns ceol.web.guitar
  "Acoustic rhythm guitar accompaniment via Tone.js Sampler (CDN samples).
   Shares the abc.js AudioContext so note scheduling is on the same Web Audio
   clock as the melody. Public API: play!, stop!, set-muted!, muted?,
   extract-bar-chords. Timing comes from beat/beats-for-tune via ms-per-bar."
  (:require ["tone" :as Tone]
            [ceol.web.abc-bridge :as abc-bridge]
            [clojure.string :as str]))

;; --- Chord voicings (open position, MIDI note names) ---

(def chord-voicings
  {"G"  {:bass "G2" :chord ["B3" "D4" "G4"]}
   "D"  {:bass "D3" :chord ["F#3" "A3" "D4"]}
   "A"  {:bass "A2" :chord ["C#4" "E4" "A3"]}
   "C"  {:bass "C3" :chord ["E3" "G3" "C4"]}
   "Em" {:bass "E2" :chord ["B3" "E3" "G3"]}
   "Am" {:bass "A2" :chord ["C4" "E3" "A3"]}
   "Bm" {:bass "B2" :chord ["D3" "F#3" "B3"]}
   "F"  {:bass "F2" :chord ["A3" "C4" "F3"]}
   "Dm" {:bass "D3" :chord ["F3" "A3" "D4"]}
   "E"  {:bass "E2" :chord ["G#3" "B3" "E4"]}
   "Eb" {:bass "Eb3" :chord ["G3" "Bb3" "Eb4"]}
   "Bb" {:bass "Bb2" :chord ["D3" "F3" "Bb3"]}})

;; --- Guitar state ---

;; ---------------------------------------------------------------------------
;; guitar-state
;;
;; :synth         Tone.Sampler  loaded acoustic guitar (cached after first init)
;; :gain          Tone.Gain     master gain feeding destination
;; :muted?        bool          UI mute (gain ramps 0 ↔ 0.6)
;; :init-promise  Promise       cached load promise, shared by concurrent play! calls
;; :scheduler-id  int           setInterval ID for the lookahead scheduler;
;;                              cleared on stop! to halt future scheduling
;; ---------------------------------------------------------------------------
(defonce guitar-state (atom {:synth nil :gain nil :muted? false
                             :init-promise nil :scheduler-id nil}))

(def ^:private sample-base-url
  "https://cdn.jsdelivr.net/npm/tonejs-instrument-guitar-acoustic-mp3@1.1.2/")

(def ^:private sample-notes
  "Sparse set of notes — Tone.js Sampler interpolates between them."
  ["A2" "C3" "E3" "A3" "C4" "E4" "A4"])

(defn- init-synth!
  "Create a Sampler with acoustic guitar samples, sharing the abc.js AudioContext.
   Returns a promise that resolves when loaded."
  []
  (if-let [p (:init-promise @guitar-state)]
    p
    ;; Share the same AudioContext as abc.js so Tone.js scheduling is on the same clock.
    ;; Guard mirrors ceol.web.metronome/ensure-shared-context! — only create a
    ;; new Tone.Context when the current one doesn't already wrap the shared
    ;; AudioContext. Without this, the count-in path (which calls metro/ensure-synth!
    ;; first) would leave two competing Tone.Context instances alive, and Tone.js 15
    ;; assertions in Param.js can fail with "param must be an AudioParam".
    (let [raw (abc-bridge/get-audio-context)
          _ (when-not (identical? raw (some-> ^js (Tone/getContext) .-rawContext))
              (Tone/setContext (Tone/Context. raw)))
          gain (-> (Tone/Gain. 0.5) (.toDestination))
          urls (clj->js (reduce (fn [m note]
                                  (assoc m note (str note ".mp3")))
                                {} sample-notes))
          ;; Store sampler ref outside the callback
          sampler-ref (atom nil)
          p (js/Promise.
             (fn [resolve reject]
               (let [timeout (js/setTimeout
                              (fn []
                                (js/console.warn "Guitar samples failed to load (timeout)")
                                (reject (js/Error. "Sample load timeout")))
                              15000)
                     sampler (Tone/Sampler.
                              (clj->js {:urls urls
                                        :baseUrl sample-base-url
                                        :release 0.8
                                        :onload (fn []
                                                  (js/clearTimeout timeout)
                                                  (let [s @sampler-ref]
                                                    (swap! guitar-state assoc :synth s :gain gain)
                                                    (resolve s)))}))]
                 (reset! sampler-ref sampler)
                 (.connect sampler gain))))]
      (swap! guitar-state assoc :init-promise p)
      p)))

(defn set-muted! [muted?]
  (swap! guitar-state assoc :muted? muted?)
  (when-let [^js gain (:gain @guitar-state)]
    (.. gain -gain (rampTo (if muted? 0 0.6) 0.05))))

(defn muted? [] (:muted? @guitar-state))

;; --- Extract chords from ABC body ---

(defn extract-bar-chords
  "Extract chord names from ABC body. Returns a vector of chord names per bar.
   Walks the string looking for bar boundaries and chord annotations.
   Drops leading/trailing pseudo-bars created by `|:` / `:|` repeat markers
   (content with no note characters) so the chord count matches the audible
   bar count. Mid-tune nil bars are preserved — the caller fills them."
  [abc-body]
  (let [chords-at (loop [i 0 result []]
                    (if-let [idx (str/index-of abc-body "\"" i)]
                      (let [end (str/index-of abc-body "\"" (inc idx))]
                        (if end
                          (let [chord (subs abc-body (inc idx) end)]
                            (recur (inc end) (conj result {:pos idx :chord chord})))
                          result))
                      result))
        bar-positions (loop [i 0 result [0]]
                        (if (>= i (count abc-body))
                          result
                          (if (= (nth abc-body i) \|)
                            (let [j (loop [k (inc i)]
                                      (if (and (< k (count abc-body))
                                               (contains? #{\| \:} (nth abc-body k)))
                                        (recur (inc k))
                                        k))]
                              (recur j (conj result j)))
                            (recur (inc i) result))))
        all-positions (conj bar-positions (count abc-body))
        bar-count (dec (count all-positions))
        bars (mapv (fn [bar-idx]
                     (let [start   (nth all-positions bar-idx)
                           end     (nth all-positions (inc bar-idx))
                           content (subs abc-body start end)
                           naked   (str/replace content #"\"[^\"]*\"" "")
                           note?   (boolean (re-find #"[A-Ga-gz]" naked))
                           chord   (first (filter (fn [{:keys [pos]}]
                                                    (and (>= pos start) (< pos end)))
                                                  chords-at))]
                       {:chord (:chord chord) :note? note?}))
                   (range bar-count))]
    (->> bars
         (drop-while (complement :note?))
         reverse
         (drop-while (complement :note?))
         reverse
         (mapv :chord))))

;; --- Strumming patterns ---

(def strum-patterns
  {:polka    [{:time 0.0 :type :bass} {:time 0.5 :type :chord}]
   :jig      [{:time 0.0 :type :bass} {:time 0.333 :type :chord} {:time 0.667 :type :chord}]
   :reel     [{:time 0.0 :type :bass} {:time 0.25 :type :chord} {:time 0.5 :type :bass} {:time 0.75 :type :chord}]
   :hornpipe [{:time 0.0 :type :bass} {:time 0.25 :type :chord} {:time 0.5 :type :bass} {:time 0.75 :type :chord}]
   :slip-jig [{:time 0.0 :type :bass} {:time 0.111 :type :chord} {:time 0.222 :type :chord}
              {:time 0.333 :type :bass} {:time 0.444 :type :chord} {:time 0.555 :type :chord}
              {:time 0.667 :type :bass} {:time 0.778 :type :chord} {:time 0.889 :type :chord}]
   :slide    [{:time 0.0 :type :bass} {:time 0.25 :type :chord} {:time 0.5 :type :bass} {:time 0.75 :type :chord}]})

(def ^:private attack-compensation-s
  "Empirical advance to align guitar onset with the melody. The acoustic
   guitar sample has a slower attack envelope than the abcjs banjo, so
   triggering slightly earlier puts perceived peaks together. Cannot
   compensate for the cold-start lag on the very first play after page
   load — that's a fundamental Web Audio cost. Tune by ear."
  0.020)

(defn build-events
  "Flatten the chord-per-bar list into a sorted seq of {:t when-s :note name}.
   when-s is absolute AudioContext seconds (start-at + bar offset + sub-bar),
   with attack-compensation-s subtracted to align perceived onset.
   pickup-offset-s shifts the entire schedule so the guitar enters on the
   first full-bar downbeat instead of on the pickup (0 for tunes without one)."
  [chords tune-type ms-per-bar start-at & [pickup-offset-s]]
  (let [offset  (or pickup-offset-s 0.0)
        pattern (get strum-patterns tune-type (:reel strum-patterns))
        bar-s   (/ ms-per-bar 1000.0)]
    (vec
     (mapcat
      (fn [bar-idx chord-name]
        (when-let [voicing (get chord-voicings (or chord-name "G"))]
          (mapcat
           (fn [{:keys [time type]}]
             (let [t     (- (+ start-at offset (* bar-idx bar-s) (* time bar-s))
                            attack-compensation-s)
                   notes (if (= type :bass) [(:bass voicing)] (:chord voicing))]
               (map (fn [n] {:t t :note n}) notes)))
           pattern)))
      (range (count chords))
      chords))))

(def ^:private lookahead-s
  "How far ahead to pre-schedule events into Web Audio. 0.25s gives the
   browser plenty of buffer while keeping the post-stop tail short."
  0.25)

(def ^:private tick-ms
  "How often the scheduler wakes up to top up the queue. Must be < lookahead
   so events don't slip past the lookahead window between ticks."
  100)

(defn- schedule-notes!
  "Lookahead scheduler. Pre-schedules only events within the next
   lookahead-s seconds via triggerAttackRelease, so stop! (clearInterval)
   leaves at most lookahead-s of audio in the queue. Each pre-scheduled
   note is sample-accurate on the AudioContext clock."
  [chords tune-type ms-per-bar start-at & [pickup-offset-s]]
  (let [events (build-events chords tune-type ms-per-bar start-at pickup-offset-s)
        dur-s  (/ ms-per-bar 2000.0)
        ctx    (abc-bridge/get-audio-context)
        cursor (atom 0)
        tick   (fn tick []
                 (when-let [^js s (:synth @guitar-state)]
                   (let [horizon (+ (.-currentTime ctx) lookahead-s)]
                     (loop [i @cursor]
                       (if (and (< i (count events))
                                (< (:t (nth events i)) horizon))
                         (do (try
                               (.triggerAttackRelease s (:note (nth events i))
                                                      dur-s
                                                      (:t (nth events i)))
                               (catch :default _))
                             (recur (inc i)))
                         (reset! cursor i))))))]
    ;; Schedule the first slice synchronously so bar 0 doesn't depend on
    ;; the first setInterval firing.
    (tick)
    (let [id (js/setInterval tick tick-ms)]
      (swap! guitar-state assoc :scheduler-id id))))

(defn stop!
  "Halt the lookahead scheduler and silence ringing notes. Up to
   lookahead-s of already-queued events may still play out — this is
   the trade-off for sample-accurate scheduling. releaseAll cuts
   sustains so the tail is brief."
  []
  (when-let [id (:scheduler-id @guitar-state)]
    (js/clearInterval id))
  (when-let [^js synth (:synth @guitar-state)]
    (try (.releaseAll synth) (catch :default _)))
  (swap! guitar-state assoc :scheduler-id nil))

(defn play!
  "Start guitar accompaniment.
   ms-per-bar comes from beat/beats-for-tune so tempo offset is respected.
   start-at is AudioContext.currentTime (seconds) when bar 0 should begin.
   pickup-offset-s, when non-zero, drops the first chord (pickup bar) and
   shifts the schedule so the first strum lands on the first full-bar
   downbeat.
   If start-at has already passed (e.g. samples still loading on first use),
   re-anchors to now + 50 ms so notes don't burst."
  [chords tune-type ms-per-bar start-at & [pickup-offset-s]]
  (let [pickup?  (boolean (and pickup-offset-s (pos? pickup-offset-s)))
        chords'  (if pickup? (subvec chords 1) chords)]
    (stop!)
    (.start Tone)
    (-> (init-synth!)
        (.then (fn [_synth]
                 (when-let [^js gain (:gain @guitar-state)]
                   (.. gain -gain (rampTo (if (:muted? @guitar-state) 0 0.6) 0.01)))
                 (let [ctx (abc-bridge/get-audio-context)
                       now (.-currentTime ctx)
                       t0  (if (> start-at now) start-at (+ now 0.05))]
                   (schedule-notes! chords' tune-type ms-per-bar t0 pickup-offset-s))))
        (.catch (fn [e]
                  (js/console.warn "Guitar playback failed:" e))))))
