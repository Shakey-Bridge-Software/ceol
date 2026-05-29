# Mobile build backlog — designed screens with no real mobile implementation

Feature work for the web app's mobile layout — new components + state, not CSS
restyling. Two waves feed this list:

- **Wave 1** (2026-05-21) — five designed screens with no mobile equivalent, plus
  five "markup gaps" the Wave 1 CSS pass had to waive (need restructured markup).
- **Wave 2** (2026-05-22) — five build items (B1–B5) surfaced by the mobile
  user-flow audit. Source: `flow-gaps.md` (gaps G1–G9, triage, decisions).

Priority: P1 = a designed interaction is unreachable on mobile; P2 = it works via
an off-design fallback; P3 = polish / nice-to-have.

`flow-gaps.md` is the transient working doc behind the B-items; this file is the
durable backlog.

---

## Suggested build order

Re-prioritized 2026-05-22 after the Wave 2 audit.

**Shipped**

- ✓ **B1 — Mobile tune-details editor** (commit `150b0e4`) — FAB now opens
  the editor; persists name/type/key/mode/time-sig/session-id. Verify scenario
  `web/scripts/verify/b1.mjs`.
- ✓ **Item #1 — Tune action sheet** + **B4 Duplicate handler** (commit `6aff231`)
  — bottom sheet with Edit details / Edit notation / Add to set / Duplicate /
  Delete. Swipe-peek removed (G8). Verify `web/scripts/verify/item1.mjs`.
- ✓ **B2 — Confirm dialogs** — generic `:confirm/open` action + `confirm-modal`
  view. Wired into import-overwrite ("Replace all data?") and set-delete
  ("Delete set?"). Reuses existing `.modal` CSS. Verify `web/scripts/verify/b2.mjs`.
- **B5 — Reduce web seed 54 → 3 deletable starter tunes** — superseded, not
  shipped as speced. PR #9 ("Allow deletion of catalog tunes") solved the same
  empty-state-reachability gap differently: the full catalog stays as the seed,
  but every tune (catalog or custom) is deletable, so the empty state is
  reached by deleting rather than by starting small. Adopted that direction
  instead.
- ✓ **Item #3 — New / edit set editor** — mobile full-screen editor
  (`:set-editor` draft slot, design `he1dM`): NAME input, reorderable tune
  rows (grip drag + ✕ remove), add-tune picker, Cancel/Save. Pure helpers in
  `handlers/set_editor.cljc`; drag-to-reorder in `gesture.cljs`. **Mobile-only**
  — desktop keeps the inline `.set-creation` wizard. Reached from the mobile
  "+ New Set" button and the set-detail `⋮`. Verify `web/scripts/verify/item3.mjs`.

- ✓ **Item #4 — Set action sheet** (`kihYP`, gap **G4**) — bottom sheet on the
  set-detail `⋮` (`:context-menu-set-id`): Play set / Edit set (→ Item #3 editor)
  / Duplicate (`:set/duplicate`, " (copy)" name) / Delete (→ B2 confirm). Reuses
  the `.as-*` action-sheet shell. Verify `web/scripts/verify/item4.mjs`.

- ✓ **B3 — Mobile backup-status banner** (gap **G9**) — the export/import
  feedback banner rendered only in the desktop sidebar (`display:none` ≤720px).
  Extracted `backup-status-banner`; mobile now shows it as a fixed bottom toast
  (`mobile-backup-status`, same `:backup-status` state). Verify
  `web/scripts/verify/b3.mjs`.

**P3 — polish**

- ✓ **Item #5 — Session-complete summary** (`LQ3CL`, gap **G9**) — `:session-result`
  {tune-count, duration} built on natural `:done` from a `:session-started-at`
  stamp; centred summary (check / "Practice complete" / stat line) with
  Practice again (`:session/start`) + Done (`:session/dismiss-summary`). Pure
  count/format in `handlers/session_summary.cljc`. Verify `verify/item5.mjs`.
6. Wave 1 markup gaps **A–E** — restructured component markup; do opportunistically.

Item #4 (set action sheet) and B2 confirm dialogs share structure with item #1.
When item #4 lands, extract a reusable `mobile-bottom-sheet` shell — single-use
today doesn't justify the indirection.

Dropped: **Item #2** — full-screen notation editor (`rT1yT`) — rejected; inline
editing kept.

---

## B1. Mobile tune-details editor — `FvkOu` + `kMKsH` · P1 · large

Gap **G2**. The mobile `.tune-header` — the only UI that sets a tune's
name/type/key/mode/time-sig (click-to-cycle fields) — is `display:none` at ≤720px
(`mobile.css:137`). The `mobile-top-bar` shows the same fields **read-only**. The
FAB dispatches `[:tune/add]`, which creates a tune the mobile user then cannot
configure. Both **add** and **edit** are affected.

**Design:** `FvkOu` is currently a verbatim copy of `kMKsH` "Edit tune"
(pre-filled, titled "Edit tune"). `FvkOu` must be reworked into a **blank**
tune-details form; `kMKsH` is the edit-existing variant.

**Scope:** a mobile tune-details editor — name input, type / key / mode / time-sig
pickers — reachable both from the FAB (blank) and from the action sheet's "Edit
details" row (pre-filled). New view + state; existing `:tune/update-field` /
`:tune/update-key-mode` handlers cover the writes.

---

## Item #1. Tune action sheet — `qgF2n` · P1

**Now:** `.tune-context-menu` is a desktop dropdown, `display:none` at ≤720px. On
mobile the per-tune actions are only reachable via swipe-left (Edit/Delete) — "Add
to set", "Duplicate", "Edit notation", "Edit details" have **no mobile entry
point**. Verified (gap G8): there is no mobile tune action sheet at all.

**Design:** bottom sheet over dimmed list. `action-sheet` `#1F1F1F`,
`cr=[20,20,0,0]`, `h≈524`. Drag handle (40×4 `#444`), tune card (title fs=18/fw=500,
meta fs=10 mono), 1px `#2A2A2A` divider, 5 action rows (`pad=[14,12]`, `gap=14`,
icon 20×20 + label Newsreader fs=16; Delete row in `#D9534F`), Cancel button
(`#2A2A2A`, mono fs=13/fw=600).

**Scope:** new `action-sheet` component; reuse `:context-menu-tune-id` as the
trigger state (already set on mobile). Mostly view + CSS — no new state model.
The "Edit notation" row opens the existing **inline** editor (`:editor/open`) — not
a full-screen editor (see item #2, rejected). The "Edit details" row opens **B1**.

**Correction (2026-05-22):** this item previously claimed "the 5 actions all
dispatch existing handlers." Not true — **"Duplicate" has no handler anywhere**;
it needs **B4**. "Edit details" needs **B1**. Only Add-to-set / Edit-notation /
Delete dispatch existing handlers.

**G8 decision — replaces the swipe-peek.** The action sheet *replaces* the
swipe-left Edit/Delete peek. This item also entails **removing the 60px swipe-peek**
(`peek-threshold`) from `gesture.cljs` — all per-tune actions go via the sheet.
Full-swipe-left → delete and swipe-right → learned are unaffected.

---

## Item #2. ABC notation editor — `rT1yT` · ~~P1~~ REJECTED

**Decision (2026-05-21, reaffirmed by gap G1):** the full-screen notation editor
design `rT1yT` is **out**. The app keeps **inline editing** — the `.editor-panel`
textarea embedded in tune detail — so that edits drive live sheet-music re-renders
in place. The Pencil frame `rT1yT` is stale.

No build work. The design frame should be removed from `design.pen` (or marked
rejected). The missing piece is a *design* frame showing the inline-edit state, not
app code.

Optional polish only: the inline `.editor-panel` bg is `#1E1E1E` — could align to a
chosen mobile dark value if desired.

---

## B2. Confirm dialogs — P2

Gap **G6**. Three destructive actions fire with no styled confirmation:

- **Import backup** overwrites *all* localStorage with no warning at all.
- **Set delete** (`:set/delete`) fires with no confirm.
- **"Clear all data"** confirms via native `js/confirm`
  (`core.cljs` `:data/clear-confirm`), not the styled modal.

The styled `delete-confirm-modal` currently covers only *tune* deletion.

**Scope:** an import-overwrite confirm + a set-delete confirm; optionally restyle
"Clear all data" off `js/confirm` onto the styled modal. Build one reusable
confirm-modal component (shares structure with the action sheets) and parameterise
title / body / destructive-label.

---

## Item #3. New / edit set editor — `he1dM` · P2 · ✓ SHIPPED

**Shipped (mobile-only):** a full-screen editor over a dedicated `:set-editor`
draft slot (mirrors `:tune-editor`), reusing the `.te-*` overlay shell. NAME
input + reorderable tune rows (grip drag-to-reorder + ✕ remove) + add-tune
picker + Cancel/Save. Pure helpers (`draft`, `reorder`, `can-save?`,
`drop-target-index`) in `handlers/set_editor.cljc`; touch drag in `gesture.cljs`.
**Decision:** mobile-only — desktop keeps the inline `.set-creation` wizard
(the editor clones the mobile-gated `.te-overlay`). Reached from the mobile
"+ New Set" button (`.add-set-btn--mobile`) and the set-detail `⋮`.

**Now (original):** `.set-creation` is an inline bordered mini-form (`#2A2A2A` box, brown
border, `cr=4`) with only a single name input. No Save button, no TUNES section, no
tune management at create time.

**Design:** full-screen editor `#1A1A1A`. Top bar: Cancel / title ("New set",
fs=16/fw=500) / Save pill (`#8F5A3C`). NAME section — label (mono fs=10/ls=2) +
input (`#2A2A2A`, `cr=8`, value fs=18/fw=500). TUNES section — label + count,
draggable tune rows (`#2A2A2A`, `cr=8`, `pad=[12,14]`: grip icon + title/meta + ✕
remove), "Add tune" dashed-stroke button (`#3A3A3A` 1px border, mono fs=12).

**Scope:** full-screen view on `:creating-set?`. State for `:creating-set-tunes`
already exists. New: drag-reorder tune rows (touch drag), add-tune picker, ✕ remove.
Save commits to `:sets`. Edit-set reuses the same view seeded from an existing set.

---

## Item #4. Set action sheet — `kihYP` · P2 · ✓ SHIPPED

**Shipped:** bottom sheet on the set-detail `⋮`, gated on a new
`:context-menu-set-id` state key, reusing the `.as-*` action-sheet shell.
Rows Play set / Edit set (`:set-editor/open-edit` → Item #3) / Duplicate
(`:set/duplicate`, reuses `ed/unique-copy-name`) / Delete (routes through the
B2 `:confirm/open` modal). The refactor commit extracts a shared
`mobile-bottom-sheet` shell across the tune + set sheets.

Gap **G4**. **Now (original):** no set-level action menu at all. `set-detail-view` has a "⋮"
button that dispatches nothing. Sets cannot be edited / duplicated / deleted from a
sheet.

**Design:** bottom sheet over dimmed sets list (mirror of item #1). `saSheet`
`#1F1F1F`, `cr=[20,20,0,0]`, `h≈464`. Set summary card ("Friday Night Set", "3 tunes
· all learned"), 4 action rows: Play set / Edit set / Duplicate / Delete.

**Scope:** new `:context-menu-set-id` state key + a `set-action-sheet` component
(clone of item #1's structure). Edit set → item #3. Play exists; **Duplicate and
Delete-set need new handlers**. Set-delete should route through the **B2** confirm.

---

## Item #5. Session-complete summary — `LQ3CL` · P3 · ✓ SHIPPED

**Shipped:** session-start! stamps `:session-started-at`; the natural `:done`
path builds `:session-result {:tune-count :duration-ms}` (pure helpers in
`handlers/session_summary.cljc`). The session tab shows a centred summary when
`:session-result` is set. Practice again → `:session/start`; Done →
`:session/dismiss-summary`. A manual "End Session" (`:session/stop`) stays
silent (no summary) — the summary is for natural completion only.

Part of gap **G9**. **Now (original):** a session ends silently — `handlers/session.cljs`
`:done` case sets `:session-mode? false` and stops playback; the view reverts to the
pre-session screen. No summary is shown.

**Design:** centred summary screen `#1A1A1A`. Check icon, "Practice complete"
heading, "4 tunes · 12 minutes" stat line, two buttons — "Practice again" / "Done".

**Scope:** smallest item. Needs a completion flag (e.g. `:session-complete?` or a
`:session-result` map with tune count + duration — duration isn't tracked today,
would need a session start timestamp). New view branch in the session tab.
"Practice again" re-runs `:session/start`; "Done" clears the result.

---

## B3. Mobile backup-status banner — P2 · ✓ SHIPPED

**Shipped:** extracted the inline sidebar banner into `backup-status-banner`
and mounted a `mobile-backup-status` fixed bottom toast (mobile-only via CSS;
desktop keeps the sidebar copy). Same `:backup-status` state, no new state.

Gap **G9**. **Now (original):** The backup-status banner component exists, but it renders **only inside
the desktop `sidebar`**. Mobile users get no success/error feedback after an
export or import.

**Scope:** render the existing banner in the mobile layout too (driven by the same
`:backup-status` state). View placement + CSS; no new component, no new state.

---

## B4. "Duplicate tune" handler — P2 · small

Gap **G8**. A "Duplicate tune" handler **does not exist anywhere** in the codebase.
Item #1's design includes a "Duplicate" action row; that row needs this handler.

**Scope:** a `:tune/duplicate` handler — clone an existing tune (and its ABC) into a
new custom tune with a fresh ID and a "(copy)"-style name, persisted via
`save-custom-tunes!`. Small. Wave 2's `flow-gaps.md` notes the old item #1 wrongly
assumed this handler already existed.

---

## B5. Reduce web seed 54 → 3 deletable starter tunes — P2 · small

Gap **G3**. The app always seeds **54** catalog tunes, so a zero-tunes state is
**unreachable** and the designed first-run / "No tunes yet" empty state (`DEMlt`) is
unreachable too. The onboarding coachmark also only renders over a non-empty list.

**Decision (2026-05-22):** seed ~**3** tunes (one per common type) as **deletable**
library entries, plus a demo set. First run = a 3-tune list + coachmark (gestures
still demoable). `DEMlt` becomes the empty state reached by deleting all 3. No
catalog-browse, no library/catalog split — library = seeds + user-added.

**Catch:** catalog tunes are currently **undeletable** — the `tune-header` Delete is
gated on `custom-tune?` (`views.cljs:404`), and catalog IDs are not in
`:custom-tunes`. The 3 seeds must be deletable, so they need to be seeded *as*
custom/library tunes (or the delete gate generalised). Small build.

---

## Wave 1 markup gaps

Discovered by the Wave 1 CSS fix pass (2026-05-21). The fix pass resolved every
straightforward CSS mismatch; these five items were **waived** because they need new
or restructured component markup, not restyling. Do opportunistically (P2/P3).

### A. Notes sheet — title block + footer — `vWmaK` · P2 · ✓ SHIPPED

**Shipped:** added `.notes-title-block` (tune title + meta) above the textarea
and `.notes-footer` (✓ Saved + live char count) below; relabelled NOTES →
PRACTICE NOTES. View + CSS only. Verify `verify/waveA.mjs`.

**Now (original):** `.notes-panel` has only the label + textarea. Dark theming is fixed
(Wave 1), but the design's `nTitleBlock` (tune title fs=22 + meta line) and `nFoot`
("Saved" status + character count) have no markup. The label reads "NOTES"; design
`nLabel` is "PRACTICE NOTES".

**Scope:** add a title block above the textarea (read from the open tune) and a
footer row below it (saved indicator + live char count); change the label text.
View + CSS only.

### B. Session-ready — hero card — `J8hkB` · P2 · ✓ SHIPPED

**Now:** the session-ready screen lists items but has no `heroCard`. CSS for the
surrounding items is fixed (Wave 1); the design centrepiece — a `#2A2A2A` cr=12 card
with a "READY TO PRACTICE" label, the big learned-tune number (`heroBig` fs=56),
sub-lines, and the Start button *inside* the card — is absent.

**Scope:** new `hero-card` component wrapping the existing `:session/start` button;
the big number binds to the learned-tune count. View + CSS; no new state.

### C. Session-live — now-playing detail + controls — `XwIFG` · P2 · ✓ SHIPPED

**Now:** `.session-now-playing` card theming is fixed (Wave 1) but it shows only a
small name. Missing: the `nowMeta` line (type/key/bpm), the `nowSkip` + `nowPause`
48px transport controls, and a real `nextCard` (the next tune's name + meta —
currently a "?" teaser).

**Scope:** expand the now-playing card with a meta line + 48px skip/pause buttons
(wire to existing session-advance / pause handlers) and replace the "?" teaser with
the resolved next tune's name + meta. View + CSS; handlers likely exist.

### D. Sets-populated — always-expanded set cards — `7VNKz` · P2 · ✓ SHIPPED

**Now:** `.set-card` is a collapse-by-default accordion (header only until tapped).
The design has no expand/collapse — every card is always fully expanded, showing a
numbered tune list, a ready dot per tune, and a "Play set" button.

**Scope:** drop the accordion behaviour; render each card fully expanded with the
numbered tune list + ready indicators + Play-set button inline. View change + CSS;
removes the collapse state.

### E. Settings — Export/Import list rows — `ddeLd` · P3

**Now:** Export and Import are label + brown action-button rows. Card theming is
fixed (Wave 1); the design wants list rows in the `icon + text + chevron` pattern
(matching the mobile list-row style) instead of label+button.

**Scope:** restructure the Export/Import rows as tappable list rows (leading icon,
text, trailing chevron). View + CSS; same handlers.
