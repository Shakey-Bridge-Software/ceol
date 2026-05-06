(ns ceol.tunes
  "Static tune catalog (54+ entries) and pure catalog query functions.
   Catalog entries contain only identity data: id, name, type, time-sig,
   key, mode-name, session-id. Runtime state (abc, midi status) is added
   by data/hydrate-tunes in the TUI and is never part of catalog data.")

;; ---------------------------------------------------------------------------
;; Tune schema
;;
;; Open map covering both base catalog entries and user-added custom tunes.
;; Required fields are present in every tune. Optional fields appear on a
;; subset (e.g. :session-id missing on user-created tunes; :jpg-path only
;; on the comhaltas catalog). Runtime keys (:abc, :abc-status, :midi-path)
;; are added by hydration and are not validated here — they belong to the
;; runtime state, not the tune entity itself.
;; ---------------------------------------------------------------------------

(def Tune
  [:map
   [:id :int]
   [:name :string]
   [:type [:enum :polka :jig :reel :hornpipe :slip-jig :slide :other]]
   [:time-sig :string]
   [:key :string]
   [:mode-name :string]
   [:session-id {:optional true} [:maybe :int]]
   [:jpg-path {:optional true} :string]])

(def catalog
  [{:id 1  :name "Maggie in the Woods (p8)"            :type :polka    :time-sig "2/4" :key "G" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/1-Maggie-in-the-Woods.jpg"
    :session-id 291}
   {:id 2  :name "Kerry Polka (p5)"                     :type :polka    :time-sig "2/4" :key "D" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/2-Kerry-Polka.jpg"
    :session-id 39}
   {:id 3  :name "Rathlin Bog (p5)"                     :type :polka    :time-sig "2/4" :key "G" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/3-Rathlin-Bog.jpg"
    :session-id 583}
   {:id 4  :name "Terry Teahan's (p9)"                  :type :polka    :time-sig "2/4" :key "G" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/4-Terry-Teahans.jpg"
    :session-id 16443}
   {:id 5  :name "Tripping Out to the Well (p8)"        :type :polka    :time-sig "2/4" :key "G" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/5-Tripping-Out-to-the-Well.jpg"
    :session-id 4158}
   {:id 6  :name "Armagh Polka (p10)"                    :type :polka    :time-sig "2/4" :key "D" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/6-Armagh-Polka.jpg"
    :session-id 441}
   {:id 7  :name "Out on the Ocean (p17)"                :type :jig      :time-sig "6/8" :key "G" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/7-Out-on-the-Ocean.jpg"
    :session-id 108}
   {:id 8  :name "The Miller of Glanmire (p13)"          :type :jig      :time-sig "6/8" :key "A" :mode-name "Dorian"
    :jpg-path "comhaltas/setlist/8-The-Miller-of-Glanmire.jpg"
    :session-id 888}
   {:id 9  :name "The Leg of the Duck (p13)"             :type :jig      :time-sig "6/8" :key "G" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/9-The-Leg-of-the-Duck.jpg"
    :session-id 1388}
   {:id 10 :name "The Blackthorn Stick (p14)"            :type :jig      :time-sig "6/8" :key "G" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/10-The-Blackthorn-Stick.jpg"
    :session-id 702}
   {:id 11 :name "The Foggy Dew (p11)"                   :type :other    :time-sig "4/4" :key "E" :mode-name "Aeolian"
    :jpg-path "comhaltas/setlist/11-The-Foggy-Dew.jpg"
    :session-id 16734}
   {:id 12 :name "Shoe The Donkey (p11)"                 :type :other    :time-sig "2/4" :key "G" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/12-Shoe-The-Donkey.jpg"
    :session-id 2320}
   {:id 13 :name "The Haunted House"               :type :jig      :time-sig "6/8" :key "G" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/13-The-Haunted-House.jpg"
    :session-id 1098}
   {:id 14 :name "Crowley's Reel (p46)"                  :type :reel     :time-sig "4/4" :key "D" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/14-Crowleys-Reel.jpg"
    :session-id 759}
   {:id 15 :name "The Wind that Shakes the Barley (p44)" :type :reel     :time-sig "4/4" :key "D" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/15-The-Wind-that-Shakes-the-Barley.jpg"
    :session-id 116}
   {:id 16 :name "Anything For John Joe (p42)"           :type :reel     :time-sig "4/4" :key "D" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/16-Anything-For-John-Joe.jpg"
    :session-id 2425}
   {:id 17 :name "The Boys of Bluehill (p32)"            :type :hornpipe :time-sig "4/4" :key "D" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/17-The-Boys-of-Bluehill.jpg"
    :session-id 651}
   {:id 18 :name "The Harvest Home (p34)"                :type :hornpipe :time-sig "4/4" :key "D" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/18-The-Harvest-Home.jpg"
    :session-id 49}
   {:id 19 :name "The Navigator (p33)"                   :type :hornpipe :time-sig "4/4" :key "G" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/19-The-Navigator.jpg"
    :session-id 4376}
   {:id 20 :name "A Fig for a Kiss (p28)"                :type :slip-jig :time-sig "9/8" :key "E" :mode-name "Aeolian"
    :jpg-path "comhaltas/setlist/20-A-Fig-for-a-Kiss.jpg"
    :session-id 750}
   {:id 21 :name "Hunting the Hare (p28)"                :type :slip-jig :time-sig "9/8" :key "D" :mode-name "Ionian"
    :jpg-path "comhaltas/setlist/21-Hunting-the-Hare.jpg"
    :session-id 3653}
   {:id 22 :name "The Rocky Road to Dublin"        :type :slip-jig :time-sig "9/8" :key "E" :mode-name "Aeolian"
    :jpg-path "comhaltas/setlist/22-The-Rocky-Road-to-Dublin.jpg"
    :session-id 593}
   ;; --- Connie Doolans additions ---
   {:id 23 :name "The Butterfly (p27)"                  :type :slip-jig :time-sig "9/8" :key "E" :mode-name "Dorian"
    :session-id 10}
   {:id 24 :name "Planxty Irwin (p36)"                  :type :other    :time-sig "3/4" :key "C" :mode-name "Ionian"
    :session-id 790}
   {:id 25 :name "Fanny Power (p37)"                    :type :other    :time-sig "3/4" :key "G" :mode-name "Ionian"
    :session-id 957}
   {:id 26 :name "Drowsy Maggie (p44)"                  :type :reel     :time-sig "4/4" :key "E" :mode-name "Dorian"
    :session-id 27}
   {:id 27 :name "Dilín ó Deamhas (p7)"                :type :other    :time-sig "4/4" :key "D" :mode-name "Ionian"
    :session-id 13788}
   {:id 28 :name "The Brosna (p29)"                     :type :slide    :time-sig "12/8" :key "D" :mode-name "Ionian"
    :session-id 1414}
   {:id 29 :name "Art O'Keeffe's (p29)"                 :type :slide    :time-sig "12/8" :key "D" :mode-name "Ionian"
    :session-id 10600}
   {:id 30 :name "Denis Murphy's (p29)"                 :type :slide    :time-sig "12/8" :key "D" :mode-name "Ionian"
    :session-id 7617}
   {:id 31 :name "Kate Pat's (p31)"                     :type :slide    :time-sig "12/8" :key "G" :mode-name "Ionian"
    :session-id nil}
   {:id 32 :name "Paudy Scully's (p31)"                 :type :slide    :time-sig "12/8" :key "D" :mode-name "Ionian"
    :session-id 4153}
   {:id 33 :name "Pulling Bracken (p9)"                :type :polka    :time-sig "2/4" :key "D" :mode-name "Ionian"
    :session-id nil}
   {:id 34 :name "The Little Diamond (p8)"             :type :polka    :time-sig "2/4" :key "D" :mode-name "Ionian"
    :session-id 604}
   {:id 35 :name "An Ghaoth Aneas (p7)"                :type :other    :time-sig "3/4" :key "G" :mode-name "Ionian"
    :session-id 601}
   {:id 36 :name "Jim Ward's (p20)"                     :type :jig      :time-sig "6/8" :key "A" :mode-name "Dorian"
    :session-id 793}
   {:id 37 :name "The Connaughtman's Rambles (p20)"     :type :jig      :time-sig "6/8" :key "G" :mode-name "Ionian"
    :session-id 19}
   {:id 38 :name "Paddy Clancy's (p14)"                 :type :jig      :time-sig "6/8" :key "A" :mode-name "Dorian"
    :session-id 832}
   {:id 39 :name "The Kesh"                       :type :jig      :time-sig "6/8" :key "G" :mode-name "Ionian"
    :session-id 55}
   {:id 40 :name "Mo Ghile Mear (p11)"                  :type :other    :time-sig "4/4" :key "A" :mode-name "Aeolian"
    :session-id 1612}
   {:id 41 :name "Fáinne Geal an Lae (p4)"             :type :other    :time-sig "4/4" :key "D" :mode-name "Ionian"
    :session-id 1441}
   {:id 42 :name "The Three Flowers (p4)"              :type :other    :time-sig "4/4" :key "D" :mode-name "Ionian"
    :session-id 9088}
   {:id 43 :name "Ballydesmond No. 1"             :type :polka    :time-sig "2/4" :key "D" :mode-name "Ionian"
    :session-id 298}
   {:id 44 :name "Ballydesmond No. 2"             :type :polka    :time-sig "2/4" :key "D" :mode-name "Ionian"
    :session-id 238}
   {:id 45 :name "Sí Beag Sí Mór"                 :type :other    :time-sig "3/4" :key "G" :mode-name "Ionian"
    :session-id 449}
   {:id 46 :name "Eleanor Plunkett (p36)"               :type :other    :time-sig "3/4" :key "C" :mode-name "Ionian"
    :session-id 2575}
   {:id 47 :name "Tabhair Dom do Lámh"            :type :other    :time-sig "3/4" :key "G" :mode-name "Ionian"
    :session-id 454}
   {:id 48 :name "Humours of Tulla (p43)"               :type :reel     :time-sig "4/4" :key "D" :mode-name "Ionian"
    :session-id 141}
   {:id 49 :name "The Little Beggarman (p7)"           :type :reel     :time-sig "4/4" :key "D" :mode-name "Ionian"
    :session-id 566}
   {:id 50 :name "St. Anne's (p47)"                     :type :reel     :time-sig "4/4" :key "D" :mode-name "Ionian"
    :session-id 103}
   {:id 51 :name "Swinging on the Gate (p41)"           :type :reel     :time-sig "4/4" :key "D" :mode-name "Ionian"
    :session-id 236}
   {:id 52 :name "The Morning Star (p42)"               :type :reel     :time-sig "4/4" :key "E" :mode-name "Dorian"
    :session-id 13245}
   {:id 53 :name "Julia Clifford's (p33)"               :type :hornpipe :time-sig "4/4" :key "D" :mode-name "Ionian"
    :session-id nil}
   {:id 54 :name "St. Patrick's Day (p38)"              :type :other    :time-sig "4/4" :key "D" :mode-name "Ionian"
    :session-id 385}
   {:id 55 :name "Stack of Barley (p32)"                :type :hornpipe :time-sig "4/4" :key "G" :mode-name "Ionian"
    :session-id nil}
   {:id 56 :name "Cronin's Hornpipe (p34)"              :type :hornpipe :time-sig "4/4" :key "G" :mode-name "Ionian"
    :session-id nil}])

(defn tune-by-id [tunes id]
  (first (filter #(= id (:id %)) tunes)))

(defn tunes-by-type [tunes type]
  (if (= type :all)
    tunes
    (filterv #(= type (:type %)) tunes)))

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
