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

(defonce guitar-state (atom {:synth nil :gain nil :muted? false :scheduled nil :started? false :init-promise nil}))

(defn- ensure-started!
  "Resume Tone.js AudioContext on first use (requires user gesture)."
  []
  (when-not (:started? @guitar-state)
    (-> (.start Tone)
        (.then (fn [] (swap! guitar-state assoc :started? true))))))

(def ^:private sample-base-url
  "https://cdn.jsdelivr.net/npm/tonejs-instrument-guitar-acoustic-mp3@1.1.2/")

(def ^:private sample-notes
  "Sparse set of notes — Tone.js Sampler interpolates between them."
  ["A2" "C3" "E3" "A3" "C4" "E4" "A4"])

(defn- init-synth!
  "Create a Sampler with acoustic guitar samples. Returns a promise that resolves when loaded."
  []
  (if-let [p (:init-promise @guitar-state)]
    p
    (let [gain (-> (Tone/Gain. 0.5) (.toDestination))
          urls (clj->js (reduce (fn [m note]
                                  (assoc m note (str note ".mp3")))
                                {} sample-notes))
          ;; Store sampler ref outside the callback
          sampler-ref (atom nil)
          p (js/Promise.
             (fn [resolve _reject]
               (let [sampler (Tone/Sampler.
                              (clj->js {:urls urls
                                        :baseUrl sample-base-url
                                        :release 0.8
                                        :onload (fn []
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
        ;; For each bar region, find the first chord annotation in it
        bar-count (dec (count bar-positions))]
    (mapv (fn [bar-idx]
            (let [start (nth bar-positions bar-idx)
                  end (if (< (inc bar-idx) (count bar-positions))
                        (nth bar-positions (inc bar-idx))
                        (count abc-body))
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
   :slide    [{:time 0.0 :type :bass} {:time 0.333 :type :chord} {:time 0.667 :type :chord}]})

(defn- ms-per-bar
  "Calculate milliseconds per bar from tune type."
  [tune-type]
  (let [[bpm beats-per-bar] (case tune-type
                              :polka    [120 2]
                              :jig      [100 2]
                              :reel     [100 4]
                              :hornpipe [100 4]
                              :slip-jig [100 3]
                              :slide    [100 2]
                              [100 4])]
    (* (/ 60000.0 bpm) beats-per-bar)))

;; --- Playback ---

(defn- schedule-notes!
  "Schedule all guitar notes using setTimeout. Returns a cancel fn."
  [chords tune-type]
  (let [pattern (get strum-patterns tune-type (:reel strum-patterns))
        bar-ms (ms-per-bar tune-type)
        timeouts (atom [])]
    (doseq [[bar-idx chord-name] (map-indexed vector chords)]
      (when-let [voicing (get chord-voicings (or chord-name "G"))]
        (doseq [{:keys [time type]} pattern]
          (let [ms (+ (* bar-idx bar-ms) (* time bar-ms))
                notes (if (= type :bass)
                        [(:bass voicing)]
                        (:chord voicing))
                dur (/ bar-ms 2000)]
            (doseq [note notes]
              (let [t (js/setTimeout
                       (fn []
                         (when-not (:muted? @guitar-state)
                           (when-let [^js s (:synth @guitar-state)]
                             (try
                               (.triggerAttackRelease s note dur)
                               (catch :default _)))))
                       ms)]
                (swap! timeouts conj t)))))))
    (fn []
      (doseq [t @timeouts]
        (js/clearTimeout t))
      (reset! timeouts []))))

(defn stop!
  "Stop guitar playback."
  []
  (when-let [cancel (:scheduled @guitar-state)]
    (cancel))
  (swap! guitar-state assoc :scheduled nil))

(defn play!
  "Start guitar accompaniment. Waits for samples to load."
  [chords tune-type time-sig]
  (stop!)
  (ensure-started!)
  (-> (init-synth!)
      (.then (fn [_synth]
               (let [cancel (schedule-notes! chords tune-type)]
                 (swap! guitar-state assoc :scheduled cancel))))))
