(ns ceol.core
  (:require [charm.core :as charm]
            [ceol.state :as state]
            [ceol.view :as view]))

(defn -main [& args]
  ;; Enter alternate screen buffer and clear
  (print "\033[?1049h\033[2J\033[H")
  (flush)
  (charm/run
   {:init state/init-state
    :update state/update-state
    :view view/render
    :alt-screen true
    :focus-reporting false})
  ;; Restore cursor visibility
  (println "\033[?25h"))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
