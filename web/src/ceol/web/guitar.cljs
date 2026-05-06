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
;; :synth          Tone.Sampler   loaded acoustic guitar (cached after first init)
;; :gain           Tone.Gain      master gain feeding destination
;; :muted?         bool           UI mute (gain ramps 0 ↔ 0.6)
;; :init-promise   Promise        cached load promise, shared by concurrent play! calls
;; :generation     int            incremented on stop!; pending setTimeout callbacks
;;                                that captured the previous generation become no-ops
;; :timeouts       [int]          live setTimeout IDs for the active schedule
;; ---------------------------------------------------------------------------
(defonce guitar-state (atom {:synth nil :gain nil :muted? false
                             :init-promise nil :generation 0 :timeouts []}))

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
  "Schedule guitar notes via setTimeout, gated by a generation counter so
   stop! cleanly aborts. start-at is AudioContext.currentTime (seconds)
   when bar 0 should play; we convert each note's offset to a wall-clock
   ms delay relative to now. Stores live timeout IDs in guitar-state so
   stop! can clearTimeout them.

   Generation counter handles the race where a setTimeout fires between
   stop! and play!: the captured gen will not match the current gen, so
   the callback is a no-op."
  [chords tune-type ms-per-bar start-at]
  (let [pattern (get strum-patterns tune-type (:reel strum-patterns))
        bar-ms  ms-per-bar
        dur-s   (/ ms-per-bar 2000.0)
        ctx     (abc-bridge/get-audio-context)
        gen     (:generation @guitar-state)
        ids     (atom [])]
    (when-let [^js s (:synth @guitar-state)]
      (doseq [[bar-idx chord-name] (map-indexed vector chords)]
        (when-let [voicing (get chord-voicings (or chord-name "G"))]
          (doseq [{:keys [time type]} pattern]
            (let [;; Wall-clock ms delay until this strum:
                  ;;   (start-at - now) seconds + bar offset + sub-bar offset
                  bar-offset-ms (+ (* bar-idx bar-ms) (* time bar-ms))
                  start-delay-ms (* 1000.0 (- start-at (.-currentTime ctx)))
                  delay-ms (max 0 (+ start-delay-ms bar-offset-ms))
                  notes    (if (= type :bass) [(:bass voicing)] (:chord voicing))
                  id (js/setTimeout
                      (fn []
                        (when (and (= gen (:generation @guitar-state))
                                   (:synth @guitar-state))
                          (doseq [note notes]
                            (try
                              (.triggerAttackRelease s note dur-s)
                              (catch :default _)))))
                      delay-ms)]
              (swap! ids conj id))))))
    (swap! guitar-state assoc :timeouts @ids)))

(defn stop!
  "Cancel pending strums + silence currently-sounding notes. Increments
   the generation counter so any setTimeout already in flight aborts when
   it fires. Safe to call repeatedly."
  []
  (doseq [id (:timeouts @guitar-state)]
    (js/clearTimeout id))
  (when-let [^js synth (:synth @guitar-state)]
    (try (.releaseAll synth) (catch :default _)))
  (swap! guitar-state (fn [s]
                        (-> s
                            (update :generation inc)
                            (assoc :timeouts [])))))

(defn play!
  "Start guitar accompaniment.
   ms-per-bar comes from beat/beats-for-tune so tempo offset is respected.
   start-at is AudioContext.currentTime (seconds) when bar 0 should begin.
   If start-at has already passed (e.g. samples still loading on first use),
   re-anchors to now + 50 ms so notes don't burst."
  [chords tune-type ms-per-bar start-at]
  (stop!)
  (.start Tone)
  (-> (init-synth!)
      (.then (fn [_synth]
               (when-let [^js gain (:gain @guitar-state)]
                 (.. gain -gain (rampTo (if (:muted? @guitar-state) 0 0.6) 0.01)))
               (let [ctx (abc-bridge/get-audio-context)
                     now (.-currentTime ctx)
                     t0  (if (> start-at now) start-at (+ now 0.05))]
                 (schedule-notes! chords tune-type ms-per-bar t0))))
      (.catch (fn [e]
                (js/console.warn "Guitar playback failed:" e)))))
