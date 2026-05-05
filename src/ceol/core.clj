(ns ceol.core
  "TUI entry point. Wires together charm/run with state/init-state,
   state/update-state, and view/render. Registers a shutdown hook to
   kill any orphaned FluidSynth processes on exit."
  (:require [charm.core :as charm]
            [ceol.state :as state]
            [ceol.view :as view]
            [babashka.process :as proc]))

(defn- kill-orphan-fluidsynths! []
  (try
    @(proc/process {:cmd ["pkill" "-f" "fluidsynth"]
                    :out :string :err :string})
    (catch Exception _)))

(defn -main [& args]
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. ^Runnable kill-orphan-fluidsynths!))
  (charm/run
   {:init state/init-state
    :update state/update-state
    :view view/render
    :alt-screen true
    :focus-reporting false}))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
