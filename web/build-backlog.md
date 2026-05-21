# Mobile build backlog — designed screens with no real mobile implementation

Companion to `design-audit.md`. These five screens exist in `design.pen` but the app
either has no equivalent or only a desktop-style fallback. Unlike the audit's CSS
mismatches, these are **feature work** — new components + state, not restyling.

Date: 2026-05-21. Design tokens for each frame are in `design-tokens.md`.

Priority is a rough call: P1 = designed interaction is unreachable on mobile;
P2 = function works via a fallback but off-design; P3 = polish / nice-to-have.

---

## 1. Tune action sheet — `qgF2n` · P1

**Now:** `.tune-context-menu` is a desktop dropdown, `display:none` at ≤720px. On mobile
the per-tune actions are only reachable via swipe-left (Edit/Delete) — "Add to set",
"Duplicate", "Edit notation" have **no mobile entry point**.

**Design:** bottom sheet over dimmed list. `action-sheet` `#1F1F1F`, `cr=[20,20,0,0]`,
`h≈524`. Drag handle (40×4 `#444`), tune card (title fs=18/fw=500, meta fs=10 mono),
1px `#2A2A2A` divider, 5 action rows (`pad=[14,12]`, `gap=14`, icon 20×20 + label
Newsreader fs=16; Delete row in `#D9534F`), Cancel button (`#2A2A2A`, mono fs=13/fw=600).

**Scope:** new `action-sheet` component; reuse `:context-menu-tune-id` as the trigger
state (already set on mobile); the 5 actions all dispatch existing handlers. Mostly view +
CSS — no new state model. The "Edit notation" row opens the existing **inline** editor
(`:editor-open?`) — not a full-screen editor (see #2, rejected).

---

## 2. ABC notation editor — `rT1yT` · ~~P1~~ REJECTED

**Decision (2026-05-21):** the full-screen notation editor design `rT1yT` is **out**. The
app keeps **inline editing** — the `.editor-panel` textarea embedded in tune detail — so
that edits drive live sheet-music re-renders in place. The Pencil frame `rT1yT` is stale.

No build work. The design frame should be removed from `design.pen` (or marked rejected).
The reverse-audit "divergence" on the editor (see `design-audit.md`) is resolved in favour
of inline.

Optional polish only (not blocking, not the rejected full-screen flow): the inline
`.editor-panel` bg is `#1E1E1E` — could align to a chosen mobile dark value if desired.

---

## 3. New / edit set editor — `he1dM` · P2

**Now:** `.set-creation` is an inline bordered mini-form (`#2A2A2A` box, brown border,
`cr=4`) with only a single name input. No Save button, no TUNES section, no tune
management at create time.

**Design:** full-screen editor `#1A1A1A`. Top bar: Cancel / title ("New set",
fs=16/fw=500) / Save pill (`#8F5A3C`). NAME section — label (mono fs=10/ls=2) + input
(`#2A2A2A`, `cr=8`, value fs=18/fw=500). TUNES section — label + count, draggable tune
rows (`#2A2A2A`, `cr=8`, `pad=[12,14]`: grip icon + title/meta + ✕ remove), "Add tune"
dashed-stroke button (`#3A3A3A` 1px border, mono fs=12).

**Scope:** full-screen view on `:creating-set?`. State for `:creating-set-tunes` already
exists. New: drag-reorder tune rows (touch drag), add-tune picker, ✕ remove. Save commits
to `:sets`. Edit-set reuses the same view seeded from an existing set.

---

## 4. Set action sheet — `kihYP` · P2

**Now:** no set-level action menu at all. `set-detail-view` has a "⋮" button that
dispatches nothing. Sets cannot be edited / duplicated / deleted from a sheet.

**Design:** bottom sheet over dimmed sets list (mirror of #1). `saSheet` `#1F1F1F`,
`cr=[20,20,0,0]`, `h≈464`. Set summary card ("Friday Night Set", "3 tunes · all
learned"), 4 action rows: Play set / Edit set / Duplicate / Delete.

**Scope:** new `:context-menu-set-id` state key + a `set-action-sheet` component (clone
of #1's structure). Edit set → screen #3. Play / Duplicate / Delete need handlers — only
"play set" likely exists today; duplicate + delete-set may need new handlers.

---

## 5. Session-complete summary — `LQ3CL` · P3

**Now:** a session ends silently — `handlers/session.cljs` `:done` case sets
`:session-mode? false` and stops playback; the view reverts to the pre-session screen.
No summary is shown.

**Design:** centred summary screen `#1A1A1A`. Check icon, "Practice complete" heading,
"4 tunes · 12 minutes" stat line, two buttons — "Practice again" / "Done".

**Scope:** smallest item. Needs a completion flag (e.g. `:session-complete?` or a
`:session-result` map with tune count + duration — duration isn't tracked today, would
need a session start timestamp). New view branch in the session tab. "Practice again"
re-runs `:session/start`; "Done" clears the result.

---

## Suggested order

1. **#1 tune action sheet** — P1, unblocks the per-tune actions (Add to set, Duplicate,
   Edit notation) that have no mobile entry point today. "Edit notation" opens the inline
   editor.
2. **#3 new-set editor**, then **#4 set action sheet** — #4's "Edit set" routes into #3.
3. **#5 session-complete** — independent, low effort, do whenever.

Dropped: **#2 ABC editor** — full-screen notation editor rejected; inline editing kept.

Action sheets #1 and #4 share a structure — build one reusable bottom-sheet component and
parameterise the card + action rows.
