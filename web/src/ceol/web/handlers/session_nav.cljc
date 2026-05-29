(ns ceol.web.handlers.session-nav
  "Pure read-only navigation over a session queue: which tune is playing now and
   which plays next. No state mutation, no audio — the session-live view reads
   these to render the now-playing and next-up cards. Returns plain refs of
   {:tune-id ... [:set-id :set-name]}; the view resolves ids to tune data.")

(defn current-ref
  "Ref for the tune currently playing, given the queue and the two session
   indices. A :set item resolves to the tune at `set-index`. nil if out of range."
  [queue index set-index]
  (when-let [item (nth queue index nil)]
    (case (:type item)
      :tune {:tune-id (:tune-id item)}
      :set  {:set-id   (:set-id item)
             :set-name (:name item)
             :tune-id  (nth (:tune-ids item) set-index nil)}
      nil)))

(defn next-ref
  "Ref for the next tune after the current position, or nil at the end of the
   queue. Advances within a set first, then to the next queue item (a set's
   first tune, or a standalone tune)."
  [queue index set-index]
  (let [item (nth queue index nil)]
    (cond
      (nil? item) nil
      (and (= :set (:type item))
           (< (inc set-index) (count (:tune-ids item))))
      {:set-id   (:set-id item)
       :set-name (:name item)
       :tune-id  (nth (:tune-ids item) (inc set-index) nil)}
      :else (current-ref queue (inc index) 0))))
