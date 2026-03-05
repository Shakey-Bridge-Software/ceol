(ns ceol.data
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

(def ceol-dir (str (System/getProperty "user.home") "/.ceol"))
(def cache-file (str ceol-dir "/cache.edn"))
(def abc-dir (str ceol-dir "/abc"))
(def midi-dir (str ceol-dir "/midi"))

(defn ensure-dirs! []
  (doseq [d [ceol-dir abc-dir midi-dir]]
    (let [f (io/file d)]
      (when-not (.exists f)
        (.mkdirs f)))))

(defn load-cache []
  (let [f (io/file cache-file)]
    (if (.exists f)
      (edn/read-string (slurp f))
      {})))

(defn save-cache! [cache]
  (ensure-dirs!)
  (spit cache-file (pr-str cache)))

(defn hydrate-tunes [tunes]
  (let [cache (load-cache)]
    (mapv (fn [tune]
            (if-let [cached (get cache (:id tune))]
              (merge tune
                     (when (:session-id cached) {:session-id (:session-id cached)})
                     (when (:abc cached) {:abc (:abc cached) :abc-status :ready})
                     (when (:midi-path cached)
                       (if (.exists (io/file (:midi-path cached)))
                         {:midi-path (:midi-path cached) :midi-status :ready}
                         {})))
              tune))
          tunes)))

(defn update-cache! [tune-id data]
  (let [cache (load-cache)
        existing (get cache tune-id {})
        updated (merge existing data)]
    (save-cache! (assoc cache tune-id updated))))

(defn soundfont-path []
  (let [custom (str ceol-dir "/soundfont.sf2")
        candidates ["/opt/homebrew/share/fluid-synth/sf2/VintageDreamsWaves-v2.sf2"
                    "/opt/homebrew/share/soundfonts/default.sf2"
                    "/usr/share/sounds/sf2/FluidR3_GM.sf2"]]
    (or (when (.exists (io/file custom)) custom)
        (first (filter #(.exists (io/file %)) candidates)))))

(defn abc-file-path [tune-id]
  (str abc-dir "/" tune-id ".abc"))

(defn midi-file-path [tune-id]
  (str midi-dir "/" tune-id ".mid"))

(defn midi-file-path-for
  "MIDI path for a specific tempo-offset/section/loop combo.
   e.g. 1.mid, 1_a.mid, 1_t10.mid, 1_a_t-5_loop.mid"
  [tune-id tempo-offset section & {:keys [loop?]}]
  (let [section-str (when section (str "_" (name section)))
        tempo-str (when (and tempo-offset (not (zero? tempo-offset)))
                    (str "_t" tempo-offset))
        loop-str (when loop? "_loop")]
    (str midi-dir "/" tune-id (or section-str "") (or tempo-str "") (or loop-str "") ".mid")))
