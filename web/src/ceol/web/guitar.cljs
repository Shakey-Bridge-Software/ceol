(ns ceol.web.guitar
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

(defonce guitar-state (atom {:synth nil :gain nil :muted? false :scheduled nil :init-promise nil}))

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
    (let [_ (Tone/setContext (Tone/Context. (abc-bridge/get-audio-context)))
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
   Walks the string looking for bar boundaries and chord annotations."
  [abc-body]
  (let [;; Find all chord annotations with their positions
        chords-at (loop [i 0 result []]
                    (if-let [idx (str/index-of abc-body "\"" i)]
                      (let [end (str/index-of abc-body "\"" (inc idx))]
                        (if end
                          (let [chord (subs abc-body (inc idx) end)]
                            (recur (inc end) (conj result {:pos idx :chord chord})))
                          result))
                      result))
        ;; Find all bar positions
        bar-positions (loop [i 0 result [0]] ;; start of string is bar 0
                        (if (>= i (count abc-body))
                          result
                          (if (= (nth abc-body i) \|)
                            ;; Skip consecutive |:| characters
                            (let [j (loop [k (inc i)]
                                      (if (and (< k (count abc-body))
                                               (contains? #{\| \:} (nth abc-body k)))
                                        (recur (inc k))
                                        k))]
                              (recur j (conj result j)))
                            (recur (inc i) result))))
        ;; Add end-of-string as final boundary so last bar is included
        all-positions (conj bar-positions (count abc-body))
        bar-count (dec (count all-positions))]
    (mapv (fn [bar-idx]
            (let [start (nth all-positions bar-idx)
                  end (nth all-positions (inc bar-idx))
                  chord (first (filter (fn [{:keys [pos]}]
                                         (and (>= pos start) (< pos end)))
                                       chords-at))]
              (:chord chord)))
          (range bar-count))))

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

(defn- schedule-notes!
  "Schedule all guitar notes on the Web Audio clock using absolute AudioContext times.
   start-at is AudioContext.currentTime (seconds) when bar 0 beat 0 should play.
   Returns a cancel fn that silences via gain ramp."
  [chords tune-type ms-per-bar start-at]
  (let [pattern (get strum-patterns tune-type (:reel strum-patterns))
        bar-s   (/ (ms-per-bar tune-type) 1000.0)
        dur-s   (/ (ms-per-bar tune-type) 2000.0)]
    (when-let [^js s (:synth @guitar-state)]
      (doseq [[bar-idx chord-name] (map-indexed vector chords)]
        (when-let [voicing (get chord-voicings (or chord-name "G"))]
          (doseq [{:keys [time type]} pattern]
            (let [when-s (+ start-at (* bar-idx bar-s) (* time bar-s))
                  notes  (if (= type :bass) [(:bass voicing)] (:chord voicing))]
              (doseq [note notes]
                (try
                  (.triggerAttackRelease s note dur-s when-s)
                  (catch :default _)))))))))
  ;; Cancel = silence immediately via gain ramp (pre-scheduled Web Audio events
  ;; can't be individually cancelled, so we mute the output instead)
  (fn []
    (when-let [^js gain (:gain @guitar-state)]
      (.. gain -gain (rampTo 0 0.02)))))

(defn stop!
  "Stop guitar playback by silencing the gain node."
  []
  (when-let [cancel (:scheduled @guitar-state)]
    (cancel))
  (swap! guitar-state assoc :scheduled nil))

(defn play!
  "Start guitar accompaniment, scheduling notes on the Web Audio clock.
   ms-per-bar comes from beat/beats-for-tune so tempo offset is respected.
   start-at is AudioContext.currentTime (seconds) when bar 0 should begin.
   If start-at has already passed (e.g. samples still loading on first use),
   re-anchors to now + 50 ms so notes don't burst."
  [chords tune-type ms-per-bar start-at]
  (stop!)
  (.start Tone)
  (-> (init-synth!)
      (.then (fn [_synth]
               ;; Restore gain (stop! may have silenced it)
               (when-let [^js gain (:gain @guitar-state)]
                 (.. gain -gain (rampTo (if (:muted? @guitar-state) 0 0.6) 0.01)))
               ;; Re-anchor if samples took too long to load
               (let [ctx  (abc-bridge/get-audio-context)
                     now  (.-currentTime ctx)
                     t0   (if (> start-at now) start-at (+ now 0.05))
                     cancel (schedule-notes! chords tune-type ms-per-bar t0)]
                 (swap! guitar-state assoc :scheduled cancel))))
      (.catch (fn [e]
                (js/console.warn "Guitar playback failed:" e)))))
