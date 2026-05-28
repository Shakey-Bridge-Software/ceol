(ns ceol.web.tune-editor-test
  "Tests for the pure state-transition helpers behind the mobile tune-details
   editor (handlers.tune-editor). The side-effectful wrappers in handlers.tune
   are exercised manually via the browser; here we cover draft shape, name
   coercion, and session-id parsing."
  (:require [clojure.test :refer [deftest testing is]]
            [ceol.web.handlers.tune-editor :as ed]))

(deftest blank-draft-shape
  (testing "blank-draft has all editor fields"
    (is (= #{:name :type :time-sig :key :mode-name :session-id}
           (set (keys ed/blank-draft)))))
  (testing "blank-draft starts with empty name + sensible defaults"
    (is (= "" (:name ed/blank-draft)))
    (is (= :polka (:type ed/blank-draft)))
    (is (= "2/4" (:time-sig ed/blank-draft)))
    (is (= "G" (:key ed/blank-draft)))
    (is (= "Ionian" (:mode-name ed/blank-draft)))
    (is (= "" (:session-id ed/blank-draft)))))

(deftest tune->draft-clones-fields
  (testing "an existing tune populates every draft field"
    (let [tune {:id 1 :name "Maggie in the Woods" :type :polka
                :time-sig "2/4" :key "G" :mode-name "Ionian" :session-id 125}
          d    (ed/tune->draft tune)]
      (is (= "Maggie in the Woods" (:name d)))
      (is (= :polka (:type d)))
      (is (= "2/4" (:time-sig d)))
      (is (= "G" (:key d)))
      (is (= "Ionian" (:mode-name d)))
      (is (= "125" (:session-id d))
          "session-id is rendered as a string for the input field")))
  (testing "missing fields fall back to blank-draft defaults"
    (let [d (ed/tune->draft {:id 99})]
      (is (= "" (:name d)))
      (is (= :polka (:type d)))
      (is (= "Ionian" (:mode-name d)))
      (is (= "" (:session-id d)))))
  (testing "nil session-id renders as empty string"
    (is (= "" (:session-id (ed/tune->draft {:id 1 :session-id nil}))))))

(deftest parse-session-id-spec
  (testing "blank / nil / whitespace → nil"
    (is (nil? (ed/parse-session-id nil)))
    (is (nil? (ed/parse-session-id "")))
    (is (nil? (ed/parse-session-id "   "))))
  (testing "numeric string → int"
    (is (= 125 (ed/parse-session-id "125")))
    (is (= 125 (ed/parse-session-id "  125  "))))
  (testing "garbage → nil"
    (is (nil? (ed/parse-session-id "abc")))))

(deftest draft->new-tune-builds-tune-map
  (testing "with all fields filled"
    (let [draft {:name "Boys of Bluehill" :type :hornpipe :time-sig "4/4"
                 :key "D" :mode-name "Ionian" :session-id "604"}
          t     (ed/draft->new-tune 9001 draft)]
      (is (= 9001 (:id t)))
      (is (= "Boys of Bluehill" (:name t)))
      (is (= :hornpipe (:type t)))
      (is (= "4/4" (:time-sig t)))
      (is (= "D" (:key t)))
      (is (= "Ionian" (:mode-name t)))
      (is (= 604 (:session-id t)) "string session-id coerced to int")))
  (testing "blank name → \"Untitled tune\""
    (is (= "Untitled tune" (:name (ed/draft->new-tune 1 ed/blank-draft))))
    (is (= "Untitled tune" (:name (ed/draft->new-tune 1 (assoc ed/blank-draft :name "   "))))))
  (testing "name is trimmed"
    (is (= "Reel" (:name (ed/draft->new-tune 1 (assoc ed/blank-draft :name "  Reel  "))))))
  (testing "blank session-id is omitted from saved tune"
    (let [t (ed/draft->new-tune 1 ed/blank-draft)]
      (is (not (contains? t :session-id))))))

(deftest draft->edit-updates-yields-field-pairs
  (testing "returns the six tune fields in deterministic order"
    (let [draft (assoc ed/blank-draft :name "Foo" :session-id "10")
          pairs (ed/draft->edit-updates draft)]
      (is (= [:name :type :time-sig :key :mode-name :session-id]
             (mapv first pairs)))
      (is (= "Foo" (second (first pairs))))
      (is (= 10 (second (last pairs))) "session-id parsed to int")))
  (testing "blank name still becomes \"Untitled tune\""
    (is (= "Untitled tune" (second (first (ed/draft->edit-updates ed/blank-draft))))))
  (testing "blank session-id parses to nil"
    (is (nil? (second (last (ed/draft->edit-updates ed/blank-draft)))))))

(deftest unique-copy-name-spec
  (testing "no prior copy → \" (copy)\""
    (is (= "Maggie in the Woods (copy)"
           (ed/unique-copy-name "Maggie in the Woods" []))))
  (testing "self in existing list is fine (only collisions matter)"
    (is (= "Foo (copy)" (ed/unique-copy-name "Foo" ["Foo"]))))
  (testing "first collision bumps to \" (copy 2)\""
    (is (= "Foo (copy 2)"
           (ed/unique-copy-name "Foo" ["Foo" "Foo (copy)"]))))
  (testing "subsequent collisions keep incrementing"
    (is (= "Foo (copy 4)"
           (ed/unique-copy-name "Foo"
                                ["Foo" "Foo (copy)" "Foo (copy 2)" "Foo (copy 3)"]))))
  (testing "unrelated names don't interfere"
    (is (= "Foo (copy)"
           (ed/unique-copy-name "Foo" ["Bar" "Baz (copy)" "Qux"])))))
