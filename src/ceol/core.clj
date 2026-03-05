(ns ceol.core
  (:require [charm.core :as charm]
            [ceol.state :as state]
            [ceol.view :as view]))

(defn -main [& args]
  (charm/run
   {:init state/init-state
    :update state/update-state
    :view view/render
    :alt-screen true
    :focus-reporting false}))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
