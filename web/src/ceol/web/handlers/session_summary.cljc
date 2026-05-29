(ns ceol.web.handlers.session-summary
  "Pure helpers for the session-complete summary (Item #5, design LQ3CL).

   The session ends in handlers.session (`:done`), which builds a
   :session-result map with these fns and stows it for the summary view. Kept
   pure + .cljc so the count/format contract is unit-testable without the atom
   or a wall clock.")

(defn queue-tune-count
  "Total tunes practised in a session queue — 1 per :tune item, (count
   tune-ids) per :set item."
  [queue]
  (reduce (fn [n item]
            (+ n (case (:type item)
                   :set (count (:tune-ids item))
                   1)))
          0 queue))

(defn result
  "Build the :session-result map from the played queue and elapsed wall time.
   Negative elapsed (clock skew) clamps to 0."
  [queue elapsed-ms]
  {:tune-count  (queue-tune-count queue)
   :duration-ms (max 0 elapsed-ms)})

(defn format-duration
  "Human duration for the summary stat line. Under a minute reads
   'less than a minute'; otherwise whole minutes."
  [ms]
  (let [mins (quot ms 60000)]
    (cond
      (< ms 60000) "less than a minute"
      (= 1 mins)   "1 minute"
      :else        (str mins " minutes"))))

(defn summary-line
  "The stat line under the heading, e.g. \"4 tunes · 12 minutes\"."
  [{:keys [tune-count duration-ms]}]
  (str tune-count " tune" (when (not= 1 tune-count) "s")
       " · " (format-duration duration-ms)))
