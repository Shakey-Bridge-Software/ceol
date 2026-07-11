# GOAL — Finish the mobile build backlog

Complete every remaining item in `build-backlog.md` end-to-end, one task at a
time, with Clojure craft as the standard throughout. A task is **done** only
when it has been written, tested, refactored, re-tested, browser-verified, and
committed — in that order.

Source of truth for scope/design per item: `web/build-backlog.md`.

---

## Per-task loop (strict order)

1. **REPL-prototype.** brepl per-defn before editing. Pure `.cljc` evaluates
   directly on the JVM; cljs runtime needs `shadow-cljs cljs-repl app`. Verify
   the data shape before writing any transform. When changing existing
   behaviour, REPL the current behaviour first and capture it.
2. **Write.** View + state + handlers. A malli schema for any new central or
   persisted data shape, defined at the top of the owning namespace. A schema
   comment above any new dispatcher case. Mechanism/policy split — pure helpers
   (`.cljc`) at the bottom, effectful adapters above, orchestration on top.
3. **Test.** Add or extend pure tests (`.cljc`, plain `(is (= expected ...))`),
   plus a generative test where a real property exists. Run the bb suite green.
4. **Verify.** A CDP scenario in `web/scripts/verify/<name>.mjs` for **every**
   task — asserts cljs state shape, then screenshots at 390×844. For
   view+CSS-only tasks the scenario asserts the new markup/state renders (element
   present, value bound) and captures the screenshot. Add a coverage-status row
   to `verify/README.md`.
5. **Feature commit.** Conventional commit — feature + its tests + its verify
   scenario together. `feat(web): … (item #N)`.
6. **clj-refactor.** Run the skill on the task's touched files. Apply the
   structural fixes it surfaces — mechanism/policy separation, extraction at the
   3rd occurrence, precise naming. Behaviour-preserving only; not a rewrite.
7. **Test again.** Re-run the bb suite + the task's CDP scenario. Confirm the
   refactor changed nothing observable.
8. **Refactor commit.** A distinct commit — `refactor(web): …`. Keeps the
   structural change reviewable in isolation.
9. **Next task.**

---

## Task order (dependencies respected)

### 1. Item #3 — New / edit set editor — `he1dM` · P2 · largest
Full-screen view on `:creating-set?`. Top bar Cancel / title / Save pill. NAME
input. TUNES section: draggable tune rows (grip + title/meta + ✕ remove),
"Add tune" picker. Save commits to `:sets`; edit-set reuses the same view seeded
from an existing set. `:creating-set-tunes` state already exists.
Verify: `verify/item3.mjs`.

### 2. Item #4 — Set action sheet — `kihYP` · P2 · gap G4
Clone of item #1's bottom-sheet structure. New `:context-menu-set-id` state key.
Rows: Play set / Edit set / Duplicate / Delete. Edit → Item #3. Duplicate and
Delete-set need new handlers; Delete routes through the **B2** confirm.
**On landing, extract a `mobile-bottom-sheet` shell** shared with item #1 — the
3rd occurrence is reached, so the abstraction is now earned (do it in this
task's refactor commit). Verify: `verify/item4.mjs`.

### 3. B3 — Mobile backup-status banner — P2 · gap G9
Render the existing backup-status banner in the mobile layout, driven by the
same `:backup-status` state. View placement + CSS; no new component, no new
state. Verify: `verify/b3.mjs`.

### 4. Item #5 — Session-complete summary — `LQ3CL` · P3 · gap G9
A `:session-result` map (tune count + duration). Duration needs a session-start
timestamp, not tracked today — add it on `:session/start`. Centred summary view:
check icon, "Practice complete", stat line, "Practice again" (re-runs
`:session/start`) / "Done" (clears the result). Verify: `verify/item5.mjs`.

### 5. Wave 1 markup gaps A–E — P2/P3 · opportunistic, in order
Each its own write→test→verify→feat→refactor→refactor-commit cycle.
- **A. Notes sheet title block + footer** — `vWmaK`. Title block (tune title +
  meta) above textarea; footer (saved indicator + live char count) below;
  relabel "NOTES" → "PRACTICE NOTES". Verify: `verify/waveA.mjs`.
- **B. Session-ready hero card** — `J8hkB`. `hero-card` wrapping the existing
  `:session/start` button; big number binds to learned-tune count.
  Verify: `verify/waveB.mjs`.
- **C. Session-live now-playing detail + controls** — `XwIFG`. Meta line
  (type/key/bpm) + 48px skip/pause transport (wire to existing handlers);
  replace the "?" next-tune teaser with the resolved next tune.
  Verify: `verify/waveC.mjs`.
- **D. Sets-populated always-expanded cards** — `7VNKz`. Drop the accordion;
  render each set card fully expanded (numbered tune list + ready dots + Play-set
  button inline). Removes the collapse state. Verify: `verify/waveD.mjs`.
- **E. Settings Export/Import list rows** — `ddeLd`. Restructure as tappable
  list rows (leading icon + text + trailing chevron); same handlers.
  Verify: `verify/waveE.mjs`.

---

## Invariants (hold for every task)

- REPL-driven, not save-and-pray. Eval each defn + a smoke call before moving on.
- Plain maps + malli schemas, not records. Validate at I/O boundaries
  (localStorage, fetched EDN).
- No speculative abstraction — wait for the 3rd occurrence. The
  `mobile-bottom-sheet` extraction (Item #4) is the one already earned; don't
  pre-extract it.
- Scope discipline — make only the requested change. Surface tangents as
  follow-ups; never bundle unrelated refactors into a feature commit.
- The bb suite must be green before every commit. Current baseline: 68 tests,
  5207 assertions.
- Two commits per task: `feat(web): …` then `refactor(web): …`. Wave 1 items
  follow the same two-commit shape.
- Update `build-backlog.md` (mark shipped) and `verify/README.md` (coverage row)
  as each task lands.

## Definition of done (the whole goal)

All five sections shipped. `build-backlog.md` shows no unshipped P1/P2/P3 items.
Every task has a passing CDP scenario in `verify/`. bb suite green.
