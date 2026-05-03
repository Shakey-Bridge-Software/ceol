 ────────────────────────────────────────────────────────────────────────────────

 Plan

 ### Group 1 — Bug fix (do first, isolated)

 1.1 Fix bar-pitch-classes in chords.cljc
 Change the when-let inside reduce to if-let ... acc, so a nil pitch class falls back to the accumulator rather than
 replacing it with nil. One line change, add a test case.

 ────────────────────────────────────────────────────────────────────────────────

 ### Group 2 — Easy cleanups (self-contained, no cross-file dependencies)

 2.1 Remove unnecessary indirection in audio.clj
 Delete the three def aliases and the private tempo-for-type wrapper. Replace all their usages in the file with direct
 abc/... calls.

 2.2 Fix shuffle-queue in web/state.cljs
 Replace (vec (sort-by (fn [_] (rand)) queue)) with (into [] (shuffle queue)).

 2.3 Fix flash severity in TUI
 Change the flash representation from a plain string to a map {:msg "..." :type :info/:error}. Add a flash-error
 helper alongside flash. Update render-flash in view.clj to read :type from the map rather than sniffing the string.
 Update all (flash state "failed...") / (flash state "error...") call sites to use flash-error. This is the most
 call-site-heavy change in this group but it's all within the TUI.

 2.4 Implement session NEXT in views.cljs
 session-tab-active has a hardcoded "?" for the upcoming item. Wire it up: look one step ahead in the queue using the
 same logic advance-session uses, and display the tune or set name.

 ────────────────────────────────────────────────────────────────────────────────

 ### Group 3 — abc.cljc additions (prerequisite for Group 4)

 Three pure functions currently living in the wrong place get moved or added here. This group is the prerequisite for
 cleaning up web duplication.

 3.1 Add split-abc-body to abc.cljc
 A body-only split (no headers) extracted from core.cljs. The existing split-abc-parts works on full ABC strings and
 reconstructs headers — this new function is simpler: takes a raw body string, returns {:a "..." :b "..."} or nil.
 split-abc-parts can then call it internally, eliminating its own split-body-at private helper.

 3.2 Add add-line-breaks to abc.cljc
 Lift it straight from core.cljs. It's a pure ABC string transformation with no web dependencies. Add it to the
 existing test file.

 3.3 Add a :midi? option to build-abc-string
 The only difference between build-full-abc (web) and build-abc-string (TUI/shared) is the %%MIDI program 105 line.
 Add & [{:keys [midi?] :or {midi? true}}] to build-abc-string. When midi? is false, omit the directive. The TUI path
 stays unchanged. The web will pass {:midi? false}.

 ────────────────────────────────────────────────────────────────────────────────

 ### Group 4 — Web duplication removal (depends on Group 3)

 4.1 Replace build-full-abc in core.cljs
 Delete it. Replace the one call site with (abc/build-abc-string tune body {:midi? false}).

 4.2 Replace split-abc-body in core.cljs
 Delete it. Replace the three call sites with abc/split-abc-body.

 4.3 Replace add-line-breaks inline call in core.cljs
 Replace with abc/add-line-breaks.

 4.4 Extract start-guitar! to a named function in core.cljs
 The near-identical local closure defined twice becomes one top-level start-guitar! function that takes [s abc-body
 tune start-at]. Both :playback/play and :session/play-current call it. The small difference between the two (one
 checks :section) is handled by passing the full state map s.

 ────────────────────────────────────────────────────────────────────────────────

 ### Group 5 — Catalog cleanup in tunes.cljc

 5.1 Remove runtime state fields from catalog entries
 Strip :abc nil :abc-status :none :midi-path nil :midi-status :none from every entry. These 54 × 5 = 270 nil/keyword
 values currently sit in what should be pure catalog data.

 5.2 Move TUI-only functions out of tunes.cljc
 type-label, status-icon, and next-filter are display/navigation concerns for the TUI only. Move them to view.clj
 (type-label, status-icon) and state.clj (next-filter). type-labels and tune-types defs move with them.

 5.3 Update hydrate-tunes to initialise status fields explicitly
 Since the catalog no longer carries :abc-status :none etc., hydrate-tunes in data.clj needs to provide those defaults
 itself when merging cache data, so the TUI's state atom still has the fields it expects.

 ────────────────────────────────────────────────────────────────────────────────

 ### Group 6 — TUI pipeline extraction in state.clj

 6.1 Extract the play pipeline decision tree
 The cond logic — MIDI file exists? → play; ABC ready, no MIDI? → convert; nothing? → fetch — is duplicated across
 play-or-stop, play-tune-by-id, and reconvert-current. Extract it into a private start-tune-pipeline function that
 takes [state tune] and returns [state cmd]. All three callers delegate to it.

 6.2 Replace effective-tempo-str with abc/adjust-abc-tempo
 effective-tempo-str in audio.clj reimplements the same regex replacement already in abc/adjust-abc-tempo. Replace it:
 get the base Q: string from abc/tempo-for-type, then pass it through abc/adjust-abc-tempo with the offset.

 ────────────────────────────────────────────────────────────────────────────────

 ### Group 7 — Guitar beat-engine integration

 7.1 Remove ms-per-bar from guitar.cljs
 Replace the private ms-per-bar function with beat/beats-for-tune. The caller in core.cljs already has beat-params
 computed — pass :ms-per-bar from it into guitar/play! as a parameter rather than recomputing it from tune type alone.
 This also means the guitar automatically respects the tempo offset, which it currently ignores entirely (bug noted in
 ROADMAP: "none of the melody/metronome/guitar seem to care about while-playing BPM changes").

 ────────────────────────────────────────────────────────────────────────────────

 ### Group 8 — tune-by-id indexing (lowest priority)

 8.1 Index tunes by ID in web state
 Change the :tunes key in app-state from a vector to a map of {id → tune}, maintaining a separate :tune-order vector
 of IDs for ordered display. All tune-by-id calls in state.cljs become O(1) map lookups. filtered-tunes and
 merge-tunes are updated accordingly. The payoff is small with 54 tunes today but the shape becomes correct.

 ────────────────────────────────────────────────────────────────────────────────

 ### Order of execution

 ```
   1 → 2 → 3 → 4 → 5 → 6 → 7 → 8
 ```

 Groups 1 and 2 are independent of each other and can be done in either order. Group 3 must land before Group 4.
 Groups 5, 6, and 7 are independent of each other after Group 2. Group 8 is last because it touches the most web code
 and is the lowest-priority change.
