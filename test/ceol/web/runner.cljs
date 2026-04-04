(ns ceol.web.runner
  (:require [cljs.test :refer [run-tests]]
            [ceol.web.abc-test]
            [ceol.web.chords-test]
            [ceol.web.state-test]))

(defn main []
  (run-tests 'ceol.web.abc-test
             'ceol.web.chords-test
             'ceol.web.state-test))
