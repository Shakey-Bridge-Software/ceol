(ns ceol.web.runner
  (:require [cljs.test :refer [run-tests]]
            [ceol.web.abc-test]
            [ceol.web.chords-test]
            [ceol.web.state-test]
            [ceol.web.sets-test]
            [ceol.web.beat-engine-test]
            [ceol.web.session-test]
            [ceol.web.actions-test]))

(defn main []
  (run-tests 'ceol.web.abc-test
             'ceol.web.chords-test
             'ceol.web.state-test
             'ceol.web.sets-test
             'ceol.web.beat-engine-test
             'ceol.web.session-test
             'ceol.web.actions-test))
