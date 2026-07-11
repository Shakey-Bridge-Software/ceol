(ns ceol.web.session-nav-test
  (:require [clojure.test :refer [deftest testing is]]
            [ceol.web.handlers.session-nav :as nav]))

(def q
  [{:type :set :set-id 1 :name "Polkas" :tune-ids [10 11]}
   {:type :tune :tune-id 20}
   {:type :tune :tune-id 21}])

(deftest current-ref-test
  (testing "a set item resolves to the tune at set-index, carrying set context"
    (is (= {:set-id 1 :set-name "Polkas" :tune-id 10} (nav/current-ref q 0 0)))
    (is (= {:set-id 1 :set-name "Polkas" :tune-id 11} (nav/current-ref q 0 1))))
  (testing "a tune item resolves to just its id"
    (is (= {:tune-id 20} (nav/current-ref q 1 0)))
    (is (= {:tune-id 21} (nav/current-ref q 2 0))))
  (testing "out of range is nil"
    (is (nil? (nav/current-ref q 3 0)))
    (is (nil? (nav/current-ref [] 0 0)))))

(deftest next-ref-test
  (testing "advances within a set before moving to the next item"
    (is (= {:set-id 1 :set-name "Polkas" :tune-id 11} (nav/next-ref q 0 0))))
  (testing "at the end of a set, advances to the next queue item"
    (is (= {:tune-id 20} (nav/next-ref q 0 1))))
  (testing "from a standalone tune, advances to the next item"
    (is (= {:tune-id 21} (nav/next-ref q 1 0))))
  (testing "the last item has no next"
    (is (nil? (nav/next-ref q 2 0))))
  (testing "a single-tune set advances straight to the following item"
    (is (= {:tune-id 5}
           (nav/next-ref [{:type :set :set-id 9 :name "S" :tune-ids [99]}
                          {:type :tune :tune-id 5}]
                         0 0)))))
