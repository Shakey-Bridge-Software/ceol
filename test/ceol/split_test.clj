(ns ceol.split-test
  (:require [ceol.abc :as abc]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing run-tests]]))

;; --- Helpers ---

(defn- body-of
  "Extract just the body (non-header lines) from an ABC string."
  [abc]
  (let [ls (str/split-lines abc)]
    (str/join "\n" (drop-while #(or (re-matches #"^[A-Z]:.*" %)
                                    (str/starts-with? % "%%")
                                    (str/blank? %))
                               ls))))

(defn- make-abc
  "Build a full ABC string from a body, like build-abc-string would."
  [name time-sig tempo-q key-str body]
  (str "X:1\n"
       "T:" name "\n"
       "M:" time-sig "\n"
       tempo-q "\n"
       "K:" key-str "\n"
       "%%MIDI program 105\n"
       body "\n"))

(defn- split-bodies
  "Split an ABC string and return {:a body-a :b body-b} or nil."
  [abc-str]
  (when-let [parts (abc/split-abc-parts abc-str)]
    {:a (body-of (:a parts))
     :b (body-of (:b parts))}))

;; --- Test fixtures: ABC bodies as fetched from thesession.org ---
;; These are the clean bodies (after |! → newline conversion) that
;; the app would produce via build-abc-string + fetch.
;; Each fixture is verified to split correctly as of 2026-03-06.

;; Polkas

(def ^:private maggie-abc
  (make-abc "Maggie in the Woods" "2/4" "Q:1/4=70" "G"
            (str "|:B/A/|GD GA|Be e/f/g/e/|dB B/A/G/A/|BA A/c/B/A/|\n"
                 "GD GA|Be e/f/g/e/|dB AB/A/|G2 G:||\n"
                 "|:d|g>f ed|ef g>e|dB B/A/G/A/|BA A>d|\n"
                 "g>f ed|ef g>e|dB AB|G2 G2:|")))

(def ^:private kerry-abc
  (make-abc "Kerry Polka" "2/4" "Q:1/4=70" "D"
            (str "|:fA BA|fA BA|d2 e>f|ed BA|\n"
                 "fA BA|fA BA|d2 e>f|ed d2:||\n"
                 "|:fa f>e|ed BA|d2 e>f|ed BA|\n"
                 "fa f>e|ed BA|d2 e>f|ed d2:|")))

(def ^:private rathlin-abc
  (make-abc "Rathlin Bog" "2/4" "Q:1/4=70" "G"
            (str "|:B2 BA|GE EF/E/|DG GA|BA A2|\n"
                 "B2 BA|GE EF/E/|Dd dB|1 AG GA:|2 AG G2:||\n"
                 "|:BG AG/A/|BG AG/A/|Bd dB|AG A2|\n"
                 "BG AG/A/|BG AG/A/|Bd dB|1 AG G2:|2 AG GA:|")))

(def ^:private terry-abc
  (make-abc "Terry Teahan's" "2/4" "Q:1/4=70" "G"
            (str "|:A|GA/G/ ED|E/F/G AB|de/d/ cA|AG ED|\n"
                 "GA/G/ ED|E/F/G AB|de/d/ cA|AG G:||\n"
                 "B|d2 dB/d/|ed BG|A2 AB|AG ED|\n"
                 "d2 dB/d/|ed BG|A/B/c AB|AG GB|\n"
                 "d2 dB/d/|ed BG|A/B/c AB|AG ED|\n"
                 "GA/G/ ED|E/F/G AB|de/d/ cA|AG G2||")))

(def ^:private tripping-abc
  (make-abc "Tripping Out to the Well" "2/4" "Q:1/4=70" "G"
            (str "|:GA BG|EG ED|GA Bd|B2 A2|\n"
                 "GA BG|EG ED|BG DF|A2 G2:||\n"
                 "|:dB GA|BG ED|dB GA|B2 A2|\n"
                 "dB GA|BG ED|BG DF|A2 G2:|")))

(def ^:private armagh-abc
  (make-abc "Armagh Polka" "2/4" "Q:1/4=70" "D"
            (str "|:dd B/c/d/B/|AF ED|dd B/c/d/B/|AF E2|\n"
                 "dd B/c/d/B/|AF Ad|fd ec|d2 d2:||\n"
                 "|:fd de/f/|gf ed|fd de/f/|gf a2|\n"
                 "fd de/f/|gf ed|fd ec|d2 d2:|")))

;; Jigs

(def ^:private out-on-ocean-abc
  (make-abc "Out on the Ocean" "6/8" "Q:3/8=70" "G"
            (str "|:GE|D2B BAG|BdB A2B|GED G2A|B2B AGE|\n"
                 "D2B BAG|BdB A2B|GED G2A|BGE G:||\n"
                 "|:Bd|e2e edB|ege edB|d2B def|gfe dBA|\n"
                 "G2A B2d|ege d2B|AGE G2A|BGE G:|")))

(def ^:private miller-abc
  (make-abc "The Miller of Glanmire" "6/8" "Q:3/8=70" "Ador"
            (str "|:g/f/|:eAA fAA|~g3 age|dBG GAG|~B3 Bcd|\n"
                 "eAA fAA|~g3 age|dBd gdB|ABA ABd:||\n"
                 "|:efg ~a3|aba age|dBd ~g3|gba ged|\n"
                 "efg ~g3|aba age|dBd gdB|ABA ABd|\n"
                 "efg ~a3|aba age|dBd ~g3|gba ged|\n"
                 "efg a2b|^c'ac' age|dBd gdB|ABA A2:|")))

(def ^:private leg-of-duck-abc
  (make-abc "The Leg of the Duck" "6/8" "Q:3/8=70" "G"
            (str "|:DGG GAG|F2D D2c|~B3 GBd|cAG ~F3|\n"
                 "D2G GAG|F2DD Bc|ded cAF|1 AGF GFE:|2 AGF GBc:||\n"
                 "|:dgg dff|dgg cBc|dgg dff|GAB ABc|\n"
                 "def gab|agf g2e|fed cAF|1 AGF GBc:|2 AGF GFE:|")))

(def ^:private blackthorn-abc
  (make-abc "The Blackthorn Stick" "6/8" "Q:3/8=70" "G"
            (str "|:d|gfg ege|dBG AGE|DGG FGA|BGB A2 d|\n"
                 "gfg age|dBG AGE|DGG FGA|BGG G2:||\n"
                 "|:d|edd gdd|edd gdd|e2 e gfg|edB A2 d|\n"
                 "gfg age|dBG AGE|DGG FGA|BGG G2:|")))

(def ^:private haunted-house-abc
  (make-abc "The Haunted House" "6/8" "Q:3/8=70" "G"
            (str "|:D|:GFG AGA|BGE EDE|GBd e2 e|ege dBA|\n"
                 "GFG AGA|BGE EDE|GBd ege|dBA G2 D:||\n"
                 "|:GBd e2 e|ege dBA|GAB d2 B|ded BAG|\n"
                 "[1 GBd e2 e|ege dBA|GBd ege|dBA G3:|\n"
                 "[2 GFG AGA|BGE EDE|GBd ege|dBA G2 D:|")))

;; Other

(def ^:private foggy-dew-abc
  (make-abc "The Foggy Dew" "4/4" "Q:1/4=60" "Em"
            (str "|:cd||\"A\"e2c2e2|\"F#Dim\"f4f2|\"A\"efec BA|\n"
                 "E4 EF|A3 A A2|A2 B2 A2|\"D\"F6|\n"
                 "(3EFE D4|f6|\"E\" g6|\"F#min\" fgfe cA|\"B\" B4 Bc|\n"
                 "\"E\" e3 e e2|e2 f2 e2|\"A\" c4 c2|(3BcB A2 cd:||\n"
                 "|:\"A\" e2c2e2|\"F#Dim\" f4 f2|\"A\" efec BA|E4 EF|\n"
                 "A3 A A2|A2 B2 A2|\"D\" F6|(3EFE D4|\n"
                 "F2 E2 F2|\"D#Dim\" B2 A2 B2|\"E\" c2 e2 g2|\"F#\" f4 ee|\n"
                 "\"B7\" c6|\"E\" c4 B2|\"A\" A4 AB|A4 cd:|")))

(def ^:private shoe-donkey-abc
  (make-abc "Shoe The Donkey" "2/4" "Q:1/4=60" "G"
            (str "|:DG|:B2 B2 DG|B2 B2 DG|B2 c2 B2|A4 DF|\n"
                 "A2 A2 DF|A2 A2 DF|A2 B2 A2|1 G4 DG:|2 G3 ABc:||\n"
                 "|:d2 g2 f2|A3 GAB|c2 e2 d2|B4 BB|\n"
                 "B2 A2 B2|c3 Bcd|1 e2 d2 G2|B3 ABc:|2 e2 d2 F2|G4 DG:|")))

;; Reels

(def ^:private crowleys-abc
  (make-abc "Crowley's Reel" "4/4" "Q:1/4=60" "D"
            (str "|:B|A3d B2dB|ADFD ADFD|A3A BABc|dfeg fddB|\n"
                 "ADFA B2 dB|ADFD ADFD|A3A BABc|d2 eg fdde:||\n"
                 "|:f3a g2fg|eA c/B/A eA c/B/A|f3a g2 fg|afge fdde|\n"
                 "f3a g2fg|eA c/B/A eA c/B/A|f3a g2 fg|afge fd d:|")))

(def ^:private wind-barley-abc
  (make-abc "The Wind that Shakes the Barley" "4/4" "Q:1/4=60" "D"
            (str "|:A2AB AFED|B2BA BcdB|A2AB AFED|gfed BcdB|\n"
                 "A2AB AFED|B2BA BcdB|A2AB AFED|gfed Bcde:||\n"
                 "|:f2fd g2ge|f2fd Bcde|f2fd g2fg|afed Bcde|\n"
                 "f2fd g2ge|f2fd Bcde|defg a2ab|afed BcdB:|")))

(def ^:private anything-jj-abc
  (make-abc "Anything For John Joe" "4/4" "Q:1/4=60" "D"
            (str "|:DE|F2AF G2BG|F2AF EFDE|F2AF G2BG|ABcA d2:||\n"
                 "|:de|f2df e2de|f2af e2de|f2df e2dB|ABcA d2de|\n"
                 "f2df e2de|f2af e3d|defg a3z|ABcA d2:|")))

;; Hornpipes

(def ^:private boys-bluehill-abc
  (make-abc "The Boys of Bluehill" "4/4" "Q:1/4=40" "D"
            (str "|:FA|BA FA D2 FA|BA (3Bcd e2 de|fa gf eg fe|df ed B2 dB|\n"
                 "BA FA D2 FA|BA (3Bcd e2 de|fa gf eg fe|d2 f2 d2:||\n"
                 "|:fg|af df a2 g2|ef ga b2 ag|fa gf eg fe|df ed B2 dB|\n"
                 "BA FA D2 FA|BA (3Bcd e2 de|fa gf eg fe|d2 f2 d2:|")))

(def ^:private harvest-home-abc
  (make-abc "The Harvest Home" "4/4" "Q:1/4=40" "D"
            (str "|:AF|DAFA DAFA|defe dcBA|eAfA gAfA|(3efe (3dcB AGFE|\n"
                 "DAFA DAFA|defe dcBA|eAfA gfec|d2 f2 d2:||\n"
                 "|:cd|eAAA fAAA|gAfA eAAA|eAfA gAfA|(3efe (3dcB (3ABA (3GFE|\n"
                 "DAFA DAFA|defe dcBA|eAfA gfec|d2 f2 d2:|")))

(def ^:private navigator-abc
  (make-abc "The Navigator" "4/4" "Q:1/4=40" "G"
            (str "|:Bc|\"G\"dgfe dcBA|\"G\"GBDG B2(3BAG|\"D7\"FADF A2(3AGF|\"G\"GBDG \"D7\"B2Bc|\n"
                 "\"G\"dgfe (3ded(3cBA|\"G\"GBDG B2AG|\"D7\"FGAB cdef|\"G\"g2G2 G2:||\n"
                 "|:AG|\"D7\"FADF A2(3AGF|\"G\"GBDG B2(3BAG|\"D7\"FADF A2(3AGF|\"G\"GDEF \"D7\"GABc|\n"
                 "\"G\"dgfe (3ded(3cBA|\"G\"GBDG B2AG|\"D7\"FGAB cdef|\"G\"g2G2 G2:|")))

;; Slip jigs

(def ^:private fig-kiss-abc
  (make-abc "A Fig for a Kiss" "9/8" "Q:3/8=70" "Em"
            (str "|:G2B E2B BAG|F2A D2A AGF|G2B E2B BAG|B/c/dB AGF DEF|\n"
                 "G2B E2B BAG|F2A D2A AGF|G2B E2B BAG|B/c/dB AGF E3:||\n"
                 "|:g2e g2e edB|f2d dcd fed|g2e g2e edB|dBG GBd e2f|\n"
                 "g2e g2e edB|f2d dcd fed|gfe fed ecA|B/c/dB AGF E2F:|")))

(def ^:private hunting-hare-abc
  (make-abc "Hunting the Hare" "9/8" "Q:3/8=70" "D"
            (str "|:F2A AFA d2A|Bcd ~e2d cBA|F2A AFA d2A|1 Bcd edc dBA:|2 Bcd edc d2e:||\n"
                 "|:f2d def g2e|a2f efd cBA|fef def g2e|1 a2f edc d2e:|2 a2f edc dBA:|")))

(def ^:private rocky-road-abc
  (make-abc "The Rocky Road to Dublin" "9/8" "Q:3/8=70" "Em"
            (str "|:e=fe d2 B A3|E2 A A2 A Bcd|e=fe d2 B A2 c|B2 A G2 A Bcd:|\n"
                 "e2 a a2 f g3|e2 a a2 A Bcd|1 e2 a a2 f g2 e|d2 B G2 A Bcd:|2 efg fga gfe|d2 B G2 A Bcd:||\n"
                 "|:ecA ecA A2 a|ecA ecA Bcd|1 ecA ecA a2 c|d2 B G2 A Bcd:|2 efg fga gfe|d2 B G2 A Bcd:|")))

;; --- Tests ---

(deftest polka-splits
  (testing "Maggie in the Woods"
    (let [{:keys [a b]} (split-bodies maggie-abc)]
      (is (some? a) "should split")
      (is (str/includes? a "GD GA") "part A has opening phrase")
      (is (str/includes? b "g>f ed") "part B has opening phrase")
      (is (not (str/includes? a "g>f")) "part A doesn't contain B material")))

  (testing "Kerry Polka"
    (let [{:keys [a b]} (split-bodies kerry-abc)]
      (is (some? a))
      (is (str/includes? a "fA BA") "part A")
      (is (str/includes? b "fa f>e") "part B")))

  (testing "Rathlin Bog"
    (let [{:keys [a b]} (split-bodies rathlin-abc)]
      (is (some? a))
      (is (str/includes? a "B2 BA") "part A")
      (is (str/includes? b "BG AG/A/") "part B")))

  (testing "Terry Teahan's — B part has no :| repeat"
    (let [{:keys [a b]} (split-bodies terry-abc)]
      (is (some? a) "should split even though B part lacks :|")
      (is (str/includes? a "GA/G/ ED") "part A")
      (is (str/includes? b "d2 dB/d/") "part B")
      (is (not (str/includes? a "d2 dB/d/")) "part A doesn't contain B material")))

  (testing "Tripping Out to the Well"
    (let [{:keys [a b]} (split-bodies tripping-abc)]
      (is (some? a))
      (is (str/includes? a "GA BG") "part A")
      (is (str/includes? b "dB GA") "part B")))

  (testing "Armagh Polka"
    (let [{:keys [a b]} (split-bodies armagh-abc)]
      (is (some? a))
      (is (str/includes? a "dd B/c/d/B/") "part A")
      (is (str/includes? b "fd de/f/") "part B"))))

(deftest jig-splits
  (testing "Out on the Ocean"
    (let [{:keys [a b]} (split-bodies out-on-ocean-abc)]
      (is (some? a))
      (is (str/includes? a "D2B BAG") "part A")
      (is (str/includes? b "e2e edB") "part B")))

  (testing "The Miller of Glanmire — B part is 16 bars"
    (let [{:keys [a b]} (split-bodies miller-abc)]
      (is (some? a))
      (is (str/includes? a "eAA fAA") "part A")
      (is (str/includes? b "efg ~a3") "part B")
      (is (str/includes? b "^c'ac'") "part B has the high phrase")))

  (testing "The Leg of the Duck — has 1st/2nd endings"
    (let [{:keys [a b]} (split-bodies leg-of-duck-abc)]
      (is (some? a))
      (is (str/includes? a "DGG GAG") "part A")
      (is (str/includes? b "dgg dff") "part B")))

  (testing "The Blackthorn Stick"
    (let [{:keys [a b]} (split-bodies blackthorn-abc)]
      (is (some? a))
      (is (str/includes? a "gfg ege") "part A")
      (is (str/includes? b "edd gdd") "part B")))

  (testing "The Haunted House — has [1 [2 endings"
    (let [{:keys [a b]} (split-bodies haunted-house-abc)]
      (is (some? a))
      (is (str/includes? a "GFG AGA") "part A")
      (is (str/includes? b "GBd e2 e") "part B"))))

(deftest other-splits
  (testing "The Foggy Dew"
    (let [{:keys [a b]} (split-bodies foggy-dew-abc)]
      (is (some? a))
      (is (str/includes? a "e2c2e2") "part A")
      (is (str/includes? b "F2 E2 F2") "part B has distinct material")))

  (testing "Shoe The Donkey — has 1st/2nd endings"
    (let [{:keys [a b]} (split-bodies shoe-donkey-abc)]
      (is (some? a))
      (is (str/includes? a "B2 B2 DG") "part A")
      (is (str/includes? b "d2 g2 f2") "part B"))))

(deftest reel-splits
  (testing "Crowley's Reel"
    (let [{:keys [a b]} (split-bodies crowleys-abc)]
      (is (some? a))
      (is (str/includes? a "A3d B2dB") "part A")
      (is (str/includes? b "f3a g2fg") "part B")))

  (testing "The Wind that Shakes the Barley"
    (let [{:keys [a b]} (split-bodies wind-barley-abc)]
      (is (some? a))
      (is (str/includes? a "A2AB AFED") "part A")
      (is (str/includes? b "f2fd g2ge") "part B")))

  (testing "Anything For John Joe"
    (let [{:keys [a b]} (split-bodies anything-jj-abc)]
      (is (some? a))
      (is (str/includes? a "F2AF G2BG") "part A")
      (is (str/includes? b "f2df e2de") "part B"))))

(deftest hornpipe-splits
  (testing "The Boys of Bluehill"
    (let [{:keys [a b]} (split-bodies boys-bluehill-abc)]
      (is (some? a))
      (is (str/includes? a "BA FA D2 FA") "part A")
      (is (str/includes? b "af df a2 g2") "part B")))

  (testing "The Harvest Home"
    (let [{:keys [a b]} (split-bodies harvest-home-abc)]
      (is (some? a))
      (is (str/includes? a "DAFA DAFA") "part A")
      (is (str/includes? b "eAAA fAAA") "part B")))

  (testing "The Navigator"
    (let [{:keys [a b]} (split-bodies navigator-abc)]
      (is (some? a))
      (is (str/includes? a "dgfe dcBA") "part A")
      (is (str/includes? b "FADF A2") "part B"))))

(deftest slip-jig-splits
  (testing "A Fig for a Kiss"
    (let [{:keys [a b]} (split-bodies fig-kiss-abc)]
      (is (some? a))
      (is (str/includes? a "G2B E2B BAG") "part A")
      (is (str/includes? b "g2e g2e edB") "part B")))

  (testing "Hunting the Hare"
    (let [{:keys [a b]} (split-bodies hunting-hare-abc)]
      (is (some? a))
      (is (str/includes? a "F2A AFA d2A") "part A")
      (is (str/includes? b "f2d def g2e") "part B")))

  (testing "The Rocky Road to Dublin — multi-section with endings"
    (let [{:keys [a b]} (split-bodies rocky-road-abc)]
      (is (some? a))
      (is (str/includes? a "e=fe d2 B") "part A")
      (is (str/includes? b "ecA ecA") "part B"))))

(deftest split-preserves-headers
  (testing "split parts retain all header fields"
    (let [parts (abc/split-abc-parts maggie-abc)]
      (is (str/includes? (:a parts) "M:2/4") "part A has time sig")
      (is (str/includes? (:a parts) "Q:1/4=70") "part A has tempo")
      (is (str/includes? (:a parts) "K:G") "part A has key")
      (is (str/includes? (:b parts) "M:2/4") "part B has time sig")
      (is (str/includes? (:b parts) "K:G") "part B has key"))))

(deftest split-adds-repeat-markers
  (testing "split parts have |: and :| markers"
    (let [parts (abc/split-abc-parts maggie-abc)
          a-body (body-of (:a parts))
          b-body (body-of (:b parts))]
      (is (str/starts-with? a-body "|:") "part A starts with |:")
      (is (str/ends-with? a-body ":|") "part A ends with :|")
      (is (str/starts-with? b-body "|:") "part B starts with |:")
      (is (str/ends-with? b-body ":|") "part B ends with :|"))))

(deftest no-split-returns-nil
  (testing "single-part ABC returns nil"
    (let [single (make-abc "Single Part" "2/4" "Q:1/4=70" "G"
                           "|:GABc dBAG|GABc dBAG:|")]
      (is (nil? (abc/split-abc-parts single))))))

(defn -main [& _args]
  (let [{:keys [fail error]} (run-tests 'ceol.split-test)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
