(ns ceol.tunes)

(def catalog
  [{:id 1  :name "Maggie in the Woods"            :type :polka    :time-sig "2/4" :key "G" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/1-Maggie-in-the-Woods.jpg"
    :session-id 291 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 2  :name "Kerry Polka"                     :type :polka    :time-sig "2/4" :key "D" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/2-Kerry-Polka.jpg"
    :session-id 39 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 3  :name "Rathlin Bog"                     :type :polka    :time-sig "2/4" :key "G" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/3-Rathlin-Bog.jpg"
    :session-id 583 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 4  :name "Terry Teahan's"                  :type :polka    :time-sig "2/4" :key "G" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/4-Terry-Teahans.jpg"
    :session-id 16443 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 5  :name "Tripping Out to the Well"        :type :polka    :time-sig "2/4" :key "G" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/5-Tripping-Out-to-the-Well.jpg"
    :session-id 4158 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 6  :name "Armagh Polka"                    :type :polka    :time-sig "2/4" :key "D" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/6-Armagh-Polka.jpg"
    :session-id 441 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 7  :name "Out on the Ocean"                :type :jig      :time-sig "6/8" :key "G" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/7-Out-on-the-Ocean.jpg"
    :session-id 108 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 8  :name "The Miller of Glanmire"          :type :jig      :time-sig "6/8" :key "A" :mode-name "Dorian"
    :jpg-path "comhaltas/setlist/8-The-Miller-of-Glanmire.jpg"
    :session-id 888 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 9  :name "The Leg of the Duck"             :type :jig      :time-sig "6/8" :key "G" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/9-The-Leg-of-the-Duck.jpg"
    :session-id 1388 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 10 :name "The Blackthorn Stick"            :type :jig      :time-sig "6/8" :key "G" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/10-The-Blackthorn-Stick.jpg"
    :session-id 702 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 11 :name "The Foggy Dew"                   :type :other    :time-sig "4/4" :key "E" :mode-name "Aeolian"
    :jpg-path "comhaltas/setlist/11-The-Foggy-Dew.jpg"
    :session-id 16734 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 12 :name "Shoe The Donkey"                 :type :other    :time-sig "2/4" :key "G" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/12-Shoe-The-Donkey.jpg"
    :session-id 2320 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 13 :name "The Haunted House"               :type :jig      :time-sig "6/8" :key "G" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/13-The-Haunted-House.jpg"
    :session-id 1098 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 14 :name "Crowley's Reel"                  :type :reel     :time-sig "4/4" :key "D" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/14-Crowleys-Reel.jpg"
    :session-id 759 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 15 :name "The Wind that Shakes the Barley" :type :reel     :time-sig "4/4" :key "D" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/15-The-Wind-that-Shakes-the-Barley.jpg"
    :session-id 116 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 16 :name "Anything For John Joe"           :type :reel     :time-sig "4/4" :key "D" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/16-Anything-For-John-Joe.jpg"
    :session-id 2425 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 17 :name "The Boys of Bluehill"            :type :hornpipe :time-sig "4/4" :key "D" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/17-The-Boys-of-Bluehill.jpg"
    :session-id 651 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 18 :name "The Harvest Home"                :type :hornpipe :time-sig "4/4" :key "D" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/18-The-Harvest-Home.jpg"
    :session-id 49 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 19 :name "The Navigator"                   :type :hornpipe :time-sig "4/4" :key "G" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/19-The-Navigator.jpg"
    :session-id 4376 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 20 :name "A Fig for a Kiss"                :type :slip-jig :time-sig "9/8" :key "E" :mode-name "Aeolian"
    :jpg-path "comhaltas/setlist/20-A-Fig-for-a-Kiss.jpg"
    :session-id 750 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 21 :name "Hunting the Hare"                :type :slip-jig :time-sig "9/8" :key "D" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/21-Hunting-the-Hare.jpg"
    :session-id 3653 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 22 :name "The Rocky Road to Dublin"        :type :slip-jig :time-sig "9/8" :key "E" :mode-name "Aeolian"
    :jpg-path "comhaltas/setlist/22-The-Rocky-Road-to-Dublin.jpg"
    :session-id 593 :abc nil :abc-status :none :midi-path nil :midi-status :none}])

(defn tune-by-id [tunes id]
  (first (filter #(= id (:id %)) tunes)))

(defn tunes-by-type [tunes type]
  (if (= type :all)
    tunes
    (filterv #(= type (:type %)) tunes)))

(def tune-types [:all :polka :jig :reel :hornpipe :slip-jig :other])

(def type-labels
  {:all      "All"
   :polka    "Polka"
   :jig      "Jig"
   :reel     "Reel"
   :hornpipe "Hornpipe"
   :slip-jig "Slip Jig"
   :other    "Other"})

(defn type-label [type]
  (get type-labels type (name type)))

(defn status-icon [tune]
  (let [{:keys [abc-status midi-status]} tune]
    (cond
      (= midi-status :ready)     "[>>>]"
      (= midi-status :converting) "[...]"
      (= midi-status :failed)    "[ ! ]"
      (= abc-status :ready)      "[ABC]"
      (= abc-status :fetching)   "[...]"
      (= abc-status :failed)     "[ ! ]"
      :else                      "[   ]")))

(defn next-filter [current]
  (let [order [:all :polka :jig :reel :hornpipe :slip-jig :other]]
    (let [idx (.indexOf order current)
          next-idx (mod (inc idx) (count order))]
      (nth order next-idx))))
