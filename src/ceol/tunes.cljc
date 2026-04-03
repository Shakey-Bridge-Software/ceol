(ns ceol.tunes)

(def catalog
  [{:id 1  :name "Maggie in the Woods (p8)"            :type :polka    :time-sig "2/4" :key "G" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/1-Maggie-in-the-Woods.jpg"
    :session-id 291 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 2  :name "Kerry Polka (p5)"                     :type :polka    :time-sig "2/4" :key "D" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/2-Kerry-Polka.jpg"
    :session-id 39 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 3  :name "Rathlin Bog (p5)"                     :type :polka    :time-sig "2/4" :key "G" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/3-Rathlin-Bog.jpg"
    :session-id 583 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 4  :name "Terry Teahan's (p9)"                  :type :polka    :time-sig "2/4" :key "G" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/4-Terry-Teahans.jpg"
    :session-id 16443 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 5  :name "Tripping Out to the Well (p8)"        :type :polka    :time-sig "2/4" :key "G" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/5-Tripping-Out-to-the-Well.jpg"
    :session-id 4158 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 6  :name "Armagh Polka (p10)"                    :type :polka    :time-sig "2/4" :key "D" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/6-Armagh-Polka.jpg"
    :session-id 441 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 7  :name "Out on the Ocean (p17)"                :type :jig      :time-sig "6/8" :key "G" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/7-Out-on-the-Ocean.jpg"
    :session-id 108 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 8  :name "The Miller of Glanmire (p13)"          :type :jig      :time-sig "6/8" :key "A" :mode-name "Dorian"
    :jpg-path "comhaltas/setlist/8-The-Miller-of-Glanmire.jpg"
    :session-id 888 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 9  :name "The Leg of the Duck (p13)"             :type :jig      :time-sig "6/8" :key "G" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/9-The-Leg-of-the-Duck.jpg"
    :session-id 1388 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 10 :name "The Blackthorn Stick (p14)"            :type :jig      :time-sig "6/8" :key "G" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/10-The-Blackthorn-Stick.jpg"
    :session-id 702 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 11 :name "The Foggy Dew (p11)"                   :type :other    :time-sig "4/4" :key "E" :mode-name "Aeolian"
    :jpg-path "comhaltas/setlist/11-The-Foggy-Dew.jpg"
    :session-id 16734 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 12 :name "Shoe The Donkey (p11)"                 :type :other    :time-sig "2/4" :key "G" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/12-Shoe-The-Donkey.jpg"
    :session-id 2320 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 13 :name "The Haunted House"               :type :jig      :time-sig "6/8" :key "G" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/13-The-Haunted-House.jpg"
    :session-id 1098 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 14 :name "Crowley's Reel (p46)"                  :type :reel     :time-sig "4/4" :key "D" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/14-Crowleys-Reel.jpg"
    :session-id 759 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 15 :name "The Wind that Shakes the Barley (p44)" :type :reel     :time-sig "4/4" :key "D" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/15-The-Wind-that-Shakes-the-Barley.jpg"
    :session-id 116 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 16 :name "Anything For John Joe (p42)"           :type :reel     :time-sig "4/4" :key "D" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/16-Anything-For-John-Joe.jpg"
    :session-id 2425 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 17 :name "The Boys of Bluehill (p32)"            :type :hornpipe :time-sig "4/4" :key "D" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/17-The-Boys-of-Bluehill.jpg"
    :session-id 651 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 18 :name "The Harvest Home (p34)"                :type :hornpipe :time-sig "4/4" :key "D" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/18-The-Harvest-Home.jpg"
    :session-id 49 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 19 :name "The Navigator (p33)"                   :type :hornpipe :time-sig "4/4" :key "G" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/19-The-Navigator.jpg"
    :session-id 4376 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 20 :name "A Fig for a Kiss (p28)"                :type :slip-jig :time-sig "9/8" :key "E" :mode-name "Aeolian"
    :jpg-path "comhaltas/setlist/20-A-Fig-for-a-Kiss.jpg"
    :session-id 750 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 21 :name "Hunting the Hare (p28)"                :type :slip-jig :time-sig "9/8" :key "D" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/21-Hunting-the-Hare.jpg"
    :session-id 3653 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 22 :name "The Rocky Road to Dublin"        :type :slip-jig :time-sig "9/8" :key "E" :mode-name "Aeolian"
    :jpg-path "comhaltas/setlist/22-The-Rocky-Road-to-Dublin.jpg"
    :session-id 593 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   ;; --- Connie Doolans additions ---
   {:id 23 :name "The Butterfly (p27)"                  :type :slip-jig :time-sig "9/8" :key "E" :mode-name "Dorian"
    :session-id 10 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 24 :name "Planxty Irwin (p36)"                  :type :other    :time-sig "3/4" :key "C" :mode-name "Ionian"
    :session-id 790 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 25 :name "Fanny Power (p37)"                    :type :other    :time-sig "3/4" :key "G" :mode-name "Ionian"
    :session-id 957 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 26 :name "Drowsy Maggie (p44)"                  :type :reel     :time-sig "4/4" :key "E" :mode-name "Dorian"
    :session-id 27 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 27 :name "Dilín ó Deamhas (p7)"                :type :other    :time-sig "4/4" :key "D" :mode-name "Ionian"
    :session-id 13788 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 28 :name "The Brosna (p29)"                     :type :slide    :time-sig "12/8" :key "D" :mode-name "Ionian"
    :session-id 1414 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 29 :name "Art O'Keeffe's (p29)"                 :type :slide    :time-sig "12/8" :key "D" :mode-name "Ionian"
    :session-id 10600 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 30 :name "Denis Murphy's (p29)"                 :type :slide    :time-sig "12/8" :key "D" :mode-name "Ionian"
    :session-id 7617 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 31 :name "Kate Pat's (p31)"                     :type :slide    :time-sig "12/8" :key "G" :mode-name "Ionian"
    :session-id nil :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 32 :name "Paudy Scully's (p31)"                 :type :slide    :time-sig "12/8" :key "D" :mode-name "Ionian"
    :session-id 4153 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 33 :name "Pulling Bracken (p9)"                :type :polka    :time-sig "2/4" :key "D" :mode-name "Ionian"
    :session-id nil :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 34 :name "The Little Diamond (p8)"             :type :polka    :time-sig "2/4" :key "D" :mode-name "Ionian"
    :session-id 604 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 35 :name "An Ghaoth Aneas (p7)"                :type :other    :time-sig "3/4" :key "G" :mode-name "Ionian"
    :session-id 601 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 36 :name "Jim Ward's (p20)"                     :type :jig      :time-sig "6/8" :key "A" :mode-name "Dorian"
    :session-id 793 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 37 :name "The Connaughtman's Rambles (p20)"     :type :jig      :time-sig "6/8" :key "G" :mode-name "Ionian"
    :session-id 19 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 38 :name "Paddy Clancy's (p14)"                 :type :jig      :time-sig "6/8" :key "A" :mode-name "Dorian"
    :session-id 832 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 39 :name "The Kesh"                       :type :jig      :time-sig "6/8" :key "G" :mode-name "Ionian"
    :session-id 55 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 40 :name "Mo Ghile Mear (p11)"                  :type :other    :time-sig "4/4" :key "A" :mode-name "Aeolian"
    :session-id 1612 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 41 :name "Fáinne Geal an Lae (p4)"             :type :other    :time-sig "4/4" :key "D" :mode-name "Ionian"
    :session-id 1441 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 42 :name "The Three Flowers (p4)"              :type :other    :time-sig "4/4" :key "D" :mode-name "Ionian"
    :session-id 9088 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 43 :name "Ballydesmond No. 1"             :type :polka    :time-sig "2/4" :key "D" :mode-name "Ionian"
    :session-id 298 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 44 :name "Ballydesmond No. 2"             :type :polka    :time-sig "2/4" :key "D" :mode-name "Ionian"
    :session-id 238 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 45 :name "Sí Beag Sí Mór"                 :type :other    :time-sig "3/4" :key "G" :mode-name "Ionian"
    :session-id 449 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 46 :name "Eleanor Plunkett (p36)"               :type :other    :time-sig "3/4" :key "C" :mode-name "Ionian"
    :session-id 2575 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 47 :name "Tabhair Dom do Lámh"            :type :other    :time-sig "3/4" :key "G" :mode-name "Ionian"
    :session-id 454 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 48 :name "Humours of Tulla (p43)"               :type :reel     :time-sig "4/4" :key "D" :mode-name "Ionian"
    :session-id 141 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 49 :name "The Little Beggarman (p7)"           :type :reel     :time-sig "4/4" :key "D" :mode-name "Ionian"
    :session-id 566 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 50 :name "St. Anne's (p47)"                     :type :reel     :time-sig "4/4" :key "D" :mode-name "Ionian"
    :session-id 103 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 51 :name "Swinging on the Gate (p41)"           :type :reel     :time-sig "4/4" :key "D" :mode-name "Ionian"
    :session-id 236 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 52 :name "The Morning Star (p42)"               :type :reel     :time-sig "4/4" :key "E" :mode-name "Dorian"
    :session-id 13245 :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 53 :name "Julia Clifford's (p33)"               :type :hornpipe :time-sig "4/4" :key "D" :mode-name "Ionian"
    :session-id nil :abc nil :abc-status :none :midi-path nil :midi-status :none}
   {:id 54 :name "St. Patrick's Day (p38)"              :type :other    :time-sig "4/4" :key "D" :mode-name "Ionian"
    :session-id 385 :abc nil :abc-status :none :midi-path nil :midi-status :none}])

(defn tune-by-id [tunes id]
  (first (filter #(= id (:id %)) tunes)))

(defn tunes-by-type [tunes type]
  (if (= type :all)
    tunes
    (filterv #(= type (:type %)) tunes)))

(def tune-types [:all :polka :jig :reel :hornpipe :slip-jig :slide :other])

(def type-labels
  {:all      "All"
   :polka    "Polka"
   :jig      "Jig"
   :reel     "Reel"
   :hornpipe "Hornpipe"
   :slip-jig "Slip Jig"
   :slide    "Slide"
   :other    "Other"})

(defn type-label [type]
  (get type-labels type (name type)))

(defn status-icon [tune]
  (let [{:keys [abc-status midi-status local-abc?]} tune]
    (cond
      (= midi-status :ready)      (if local-abc? "[*>>]" "[>>>]")
      (= midi-status :converting) "[...]"
      (= midi-status :failed)     "[ ! ]"
      (= abc-status :ready)       (if local-abc? "[*AB]" "[ABC]")
      (= abc-status :fetching)    "[...]"
      (= abc-status :failed)      "[ ! ]"
      :else                       "[   ]")))

(defn next-filter [current]
  (let [idx (.indexOf tune-types current)
        next-idx (mod (inc idx) (count tune-types))]
    (nth tune-types next-idx)))

(defn resolve-setlist
  "Given a setlist map and the full tunes vector, return ordered tunes:
   set tunes (with :set-name, :set-position, :set-size) then loose tunes."
  [setlist tunes]
  (let [tune-index (into {} (map (juxt :id identity)) tunes)
        set-tunes (mapcat
                   (fn [{:keys [name tune-ids]}]
                     (let [size (count tune-ids)]
                       (->> tune-ids
                            (map-indexed
                             (fn [pos tid]
                               (when-let [t (get tune-index tid)]
                                 (assoc t
                                        :set-name name
                                        :set-position pos
                                        :set-size size))))
                            (remove nil?))))
                   (:sets setlist))
        set-tune-ids (set (mapcat :tune-ids (:sets setlist)))
        loose (keep (fn [tid] (get tune-index tid))
                    (remove set-tune-ids (:loose-ids setlist)))]
    (vec (concat set-tunes loose))))

(defn set-for-tune
  "Given a setlist and a tune-id, return the set containing that tune, or nil."
  [setlist tune-id]
  (first (filter (fn [s] (some #{tune-id} (:tune-ids s)))
                 (:sets setlist))))
