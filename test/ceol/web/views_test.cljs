(ns ceol.web.views-test
  (:require [cljs.test :refer [deftest is]]
            [ceol.web.views :as views]))

(deftest mazurka-label-keeps-storage-key
  (is (= "Mazurka" (get views/tune-type-labels :mazourka)))
  (is (contains? (set views/tune-type-order) :mazourka)))
