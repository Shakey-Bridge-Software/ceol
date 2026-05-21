# Mobile design audit — ceol web app vs `design.pen`

Method: live DOM measurement via shadow-cljs nREPL at iPhone size (390×844), each screen's
state set then `getBoundingClientRect` + `getComputedStyle` walked and diffed against the
token reference in `design-tokens.md`. Date: 2026-05-21.

Status-bar (44px) / home-indicator (34px) frames in the design have no app equivalent — the
browser app has no native chrome. Noted once here; not flagged per screen.

**Update 2026-05-21 — Wave 1 CSS fixes applied.** A CSS fix pass resolved most mismatches
in this audit. Resolved rows are struck through below with `→ **newvalue**` and `✓ RESOLVED`.
Two ambiguous mismatches are **WAIVED** pending a human decision (`.tune-name` default colour;
no others mobile-specific — see desktop audit for the sidebar `gap` waiver). Five **structural**
gaps need new/restructured component markup beyond Wave 1's CSS scope — these are marked
WAIVED here and tracked in `build-backlog.md` under "Wave 1 markup gaps".

## Summary

| screen | matches | mismatches | worst issue |
|---|---|---|---|
| tune-list | ✓ | 1 → 1 resolved | tune-name default colour #aaa → #D4D2CC |
| tune-list-empty | ✓ | 4 → 4 resolved | all resolved by Wave 1 |
| controls-sheet | ✓ | 8 → 8 resolved | all resolved by Wave 1 |
| notes-sheet | partial | 7 → 5 resolved, 2 waived | CSS theming fixed; title block + footer waived (structural) |
| delete-confirm | ✓ | 6 → 6 resolved | all resolved by Wave 1 |
| tune-action-sheet | not impl | — | mobile action sheet absent (desktop dropdown only) |
| abc-editor | not impl | — | full-screen editor absent (inline textarea only) |
| playback-detail | ✓ | 3 → 3 resolved (+1 deliberate) | top-bar theme fixed; slim bar deliberate |
| sets-empty | ✓ | 5 → 5 resolved | markup added; all resolved by Wave 1 |
| sets-populated | partial | 3 → 2 resolved, 1 waived | accordion vs always-expanded waived (structural) |
| set-detail | ✓ | 6 → 6 resolved | all resolved by Wave 1 |
| new-set | not impl | — | full-screen set editor absent (inline mini-form only) |
| settings | partial | 4 → 3 resolved, 1 waived | CSS fixed; Export/Import row layout waived (structural) |
| session-empty | ✓ | 4 → 4 resolved | markup added; all resolved by Wave 1 |
| session-ready | partial | 6 → 4 resolved, 2 waived (1 structural item) | CSS fixed; hero card + big number waived (structural) |
| session-live | partial | 6 → 2 resolved, 4 waived | card CSS fixed; now-name/meta/controls/next detail waived (structural) |
| swipe-right | not impl | — | right-swipe "Learned" reveal has no static state |
| swipe-left | ✓ | 3 → 3 resolved | all resolved by Wave 1 |
| full-swipe-delete | not impl | — | no "Release to delete" reveal; jumps straight to confirm modal |
| onboarding | ✓ | 1 → 1 resolved | header top padding resolved |

Wave 1 outcome: of 47 CSS mismatch rows, **40 resolved**, **7 waived** — 1 ambiguous
(`.tune-name` colour) + 6 structural (across notes-sheet, sets-populated, settings,
session-ready, session-live; 5 distinct markup gaps tracked in `build-backlog.md`).

## tune-list

(design ZV3rw absent — header/tabs/filters compared to 91gc3, which are identical)

| element | property | app | design |
|---|---|---|---|
| ~~`.tune-name`~~ | ~~color~~ | ~~#aaa~~ → **#D4D2CC** | #D4D2CC (default/unselected) — ✓ RESOLVED |

`.tune-row`, `.filter-chip`, `.tune-meta`, `.tune-info` match design. The earlier WAIVER was a
measurement artefact: the audit compared the *learned* row (correctly #A8A8A8) against the
design's *default* row. The app already has all three states — `.tune-row.active .tune-name`
#F5F4F0, `.tune-row.learned .tune-name` #A8A8A8 — only the base/default colour was wrong
(#aaa). Fixed: base `.tune-name` → #D4D2CC. Not ambiguous.

## tune-list-empty

Design 91gc3 `fEmptyBody`.

| element | property | app | design |
|---|---|---|---|
| ~~`.mobile-empty-state`~~ | ~~padding~~ | ~~80 / 40 / 80 / 40~~ → **40 all sides** | 40 all sides — ✓ RESOLVED |
| ~~`.empty-icon`~~ | ~~width/height~~ | ~~48×48~~ → **64×64** | 64×64 — ✓ RESOLVED |
| ~~`.empty-icon`~~ | ~~bg~~ | ~~#2A2A2A~~ → **#222222** | #222222 — ✓ RESOLVED |
| ~~`.empty-title`~~ | ~~fontSize / weight~~ | ~~18 / 400~~ → **22 / 500** | 22 / 500 — ✓ RESOLVED |

## controls-sheet

Design rfcbO.

| element | property | app | design |
|---|---|---|---|
| ~~`.controls-sheet`~~ | ~~bg~~ | ~~#1A1A1A~~ → **#0A0A0A** | #0A0A0A — ✓ RESOLVED |
| ~~`.controls-sheet-title`~~ | ~~fontSize / weight~~ | ~~24 / 400~~ → **26 / 500** | 26 / 500 — ✓ RESOLVED |
| ~~`.cs-tempo-val`~~ | ~~fontSize / weight~~ | ~~34 / 400~~ → **42 / 500** | 42 / 500 — ✓ RESOLVED |
| ~~`.controls-sheet-tempo` (bpm card)~~ | ~~bg~~ | ~~#2A2A2A~~ → **#1F1F1F** | #1F1F1F — ✓ RESOLVED |
| ~~`.cs-toggle`~~ | ~~bg / cornerRadius / weight~~ | ~~#2A2A2A / 10 / 400~~ → **#1F1F1F / 12 / 600** | #1F1F1F / 12 / 600 — ✓ RESOLVED |
| ~~`.cs-part`~~ | ~~bg / weight~~ | ~~#2A2A2A / 400~~ → **#1F1F1F / 600** | #1F1F1F container / 600 — ✓ RESOLVED |
| ~~`.controls-sheet-play`~~ | ~~fontWeight~~ | ~~400~~ → **700** | 700 — ✓ RESOLVED |
| ~~`.controls-sheet`~~ | ~~cornerRadius~~ | ~~16~~ → **20** | 20 — ✓ RESOLVED |

## notes-sheet

Design vWmaK — a dark bottom sheet. The app's `.notes-panel` is a **light** panel.

| element | property | app | design |
|---|---|---|---|
| ~~`.notes-panel`~~ | ~~bg~~ | ~~#FAFAF8~~ → **#0A0A0A** | #0A0A0A — ✓ RESOLVED |
| ~~`.notes-label`~~ | ~~color~~ | ~~#666666~~ → **#8F5A3C** | #8F5A3C — ✓ RESOLVED |
| `.notes-label` | content | "NOTES" | "PRACTICE NOTES" — **WAIVED** (structural — needs component markup, see build-backlog.md) |
| ~~`.notes-textarea`~~ | ~~bg~~ | ~~#FFFFFF~~ → **#1F1F1F** | #1F1F1F (nTextWrap) — ✓ RESOLVED |
| ~~`.notes-textarea`~~ | ~~color~~ | ~~#1A1A1A~~ → **#F5F4F0** | #F5F4F0 — ✓ RESOLVED |
| ~~`.notes-textarea`~~ | ~~fontSize~~ | ~~12~~ → **13** | 13 — ✓ RESOLVED |
| (missing) | tune title block | absent | nTitleBlock — title fs=22 + meta — **WAIVED** (structural — needs component markup, see build-backlog.md) |
| (missing) | footer | absent | nFoot — "Saved" + char count — **WAIVED** (structural — needs component markup, see build-backlog.md) |

## delete-confirm

Design JyFJG — a dark modal (#262626). The app's `.modal` is **light** (#F5F4F0).

| element | property | app | design |
|---|---|---|---|
| ~~`.modal`~~ | ~~bg~~ | ~~#F5F4F0~~ → **#262626** | #262626 — ✓ RESOLVED |
| ~~`.modal`~~ | ~~cornerRadius~~ | ~~12~~ → **14** | 14 — ✓ RESOLVED |
| ~~`.modal-title`~~ | ~~fontWeight~~ | ~~400~~ → **600** | 600 — ✓ RESOLVED |
| ~~`.modal-body`~~ | ~~fontFamily / color~~ | ~~Newsreader / #666666~~ → **IBM Plex Mono / #A8A8A8** | IBM Plex Mono / #A8A8A8 — ✓ RESOLVED |
| ~~`.modal-cancel`~~ | ~~fontFamily / weight~~ | ~~Newsreader / 400~~ → **IBM Plex Mono / 600** | IBM Plex Mono / 600 — ✓ RESOLVED |
| ~~`.modal-destructive`~~ | ~~fontFamily / weight~~ | ~~Newsreader / 400~~ → **IBM Plex Mono / 600** | IBM Plex Mono / 600 — ✓ RESOLVED |

## tune-action-sheet

Design qgF2n (`.action-sheet`) — a full bottom sheet with handle, tune card, 5 action rows
(Edit details / Edit notation / Add to set / Duplicate / Delete) and a Cancel button.

The app has no mobile action sheet. `.tune-context-menu` is a desktop dropdown rendered with
`display:none` at ≤720px. There is no `.action-sheet` in the DOM. **Not implemented on mobile.**

## abc-editor

Design rT1yT — a full-screen editor: top bar (Cancel / title / Save), A/B/Both section
tabs, line-numbered editor body, keyboard accessory bar.

The app has no full-screen ABC editor. `.abc-editor` does not exist; the editor is an inline
`.editor-panel` (a bare `.editor-textarea` with a hint header) embedded in the tune detail.
No top bar, no Cancel/Save, no section tabs, no line numbers, no keyboard accessory.
`.editor-panel` bg #1E1E1E vs design abc-editor #0A0A0A.

**Intentional — `rT1yT` rejected (2026-05-21).** Inline editing is the chosen approach
(live sheet-music re-render). `rT1yT` is a stale design frame, not a defect. See the
"Design divergence — RESOLVED" section below.

## playback-detail

Design 7AtwI — light theme (#FAFAF8). The app's mobile detail top-bar is **dark**.

| element | property | app | design |
|---|---|---|---|
| ~~`.mobile-top-bar`~~ | ~~bg~~ | ~~#1A1A1A~~ → **#FAFAF8** | #FAFAF8 (top-bar) — ✓ RESOLVED |
| ~~`.mobile-title`~~ | ~~fontSize~~ | ~~15~~ → **18** | 18 (titleText) — ✓ RESOLVED |
| ~~`.mobile-title-meta`~~ | ~~fontSize~~ | ~~9~~ → **10** | 10 (metaText) — ✓ RESOLVED |
| `.mobile-playback-bar` | play btn / layout | slim 48px play, pad [0,16] | 64px play, pad 16 — deliberate (slim redesign) |

The slim mobile playback bar is a deliberate redesign (commit "replace playback bar with slim
mobile design") — sizes are expected to differ. The dark/light theme inversion of the top bar
is the real flag.

## sets-empty

Design SVtwq — rich empty state: 64×64 icon circle (#222222, layers icon), title
"No sets yet" fs=22 fw=500, subtitle fs=11 mono, plus a FAB.

| element | property | app | design |
|---|---|---|---|
| ~~`.sets-empty`~~ | ~~(whole)~~ | ~~plain text node~~ → **icon circle + styled title + subtitle** | icon circle + styled title + subtitle — ✓ RESOLVED (markup added) |
| ~~`.sets-empty`~~ | ~~fontSize / family / color~~ | ~~12 / Geist / #555555~~ → **title fs=22 + sub fs=11** | title fs=22 + sub fs=11 — ✓ RESOLVED |
| ~~(missing)~~ | ~~icon circle~~ | ~~absent~~ → **added** | eEmptyIcon 64×64 #222222 — ✓ RESOLVED |
| ~~(missing)~~ | ~~subtitle~~ | ~~absent~~ → **added** | "Tap New Set to get started" — ✓ RESOLVED |
| ~~`.add-set-btn`~~ | ~~fontFamily / size / weight~~ | ~~Newsreader / 14 / 400~~ → **mono / 13 / 600** | mono / 13 / 600 (eFab text) — ✓ RESOLVED |

## sets-populated

Design 7VNKz — each set card always shows title, count, numbered tune list, ready dot and a
"Play set" button.

| element | property | app | design |
|---|---|---|---|
| `.set-card` | content (collapsed) | header only (name + count + chevron) | always shows tunes + ready + Play set — **WAIVED** (structural — needs component markup, see build-backlog.md) |
| ~~`.set-name`~~ | ~~fontWeight~~ | ~~400~~ → **500** | 500 — ✓ RESOLVED |
| ~~`.add-set-btn`~~ | ~~style~~ | ~~brown fill, Newsreader 14~~ → **bordered, mono 11** | bordered `newSetBtn`, mono 11 — ✓ RESOLVED |

**WAIVED** — set cards are collapse-by-default accordions; the design has no expand/collapse
(every card fully expanded inline). Structural — needs component markup, see `build-backlog.md`.

## set-detail

Design sgPmc — dark theme (#1A1A1A, rows #2A2A2A, titles #F5F4F0). The app's `.set-detail`
is **light-themed** (desktop styling reused on mobile).

| element | property | app | design |
|---|---|---|---|
| ~~`.set-detail-header`~~ | ~~bg~~ | ~~#FAFAF8~~ → **transparent on #1A1A1A** | (transparent on #1A1A1A) — ✓ RESOLVED |
| ~~`.set-detail-tune-row`~~ | ~~bg~~ | ~~#FFFFFF + 1px border~~ → **#2A2A2A, no border** | #2A2A2A, no border — ✓ RESOLVED |
| ~~`.set-detail-title`~~ | ~~fontSize~~ | ~~28~~ → **16** | 16 — ✓ RESOLVED |
| ~~`.set-detail-tune-row`~~ | ~~padding~~ | ~~12 / 16~~ → **14 / 12** | 14 / 12 — ✓ RESOLVED |
| ~~`.set-detail-play`~~ | ~~fontSize / weight~~ | ~~14 / 400~~ → **13 / 600** | 13 / 600 — ✓ RESOLVED |
| ~~`.set-detail-list-label`~~ | ~~padding~~ | ~~16 / 32 / 8 / 32~~ → **0 / 20 / 8 / 20** | 0 / 20 / 8 / 20 — ✓ RESOLVED |

## new-set

Design he1dM — a full-screen set editor: top bar (Cancel / "New set" / Save), a NAME section
(label + input fs=18), a TUNES section (label + count + draggable tune rows + Add tune).

The app's `.set-creation` is an inline bordered mini-form (#2A2A2A box, brown border, cr=4)
containing only a single name input. No top bar, no NAME label, no Save button, no TUNES
section at step 1. `.set-input` fs=14 fw=400 vs design stNameVal fs=18 fw=500.
**Not implemented as designed.**

## settings

Design ddeLd — dark theme (#1A1A1A, cards #2A2A2A cr=10). The app's `.settings-view` is
**light-themed**.

| element | property | app | design |
|---|---|---|---|
| ~~`.settings-card`~~ | ~~bg~~ | ~~#FFFFFF~~ → **#2A2A2A** | #2A2A2A — ✓ RESOLVED |
| ~~`.settings-card`~~ | ~~cornerRadius~~ | ~~6~~ → **10** | 10 — ✓ RESOLVED |
| Export / Import rows | layout | label + brown action button | icon + text + chevron list row — **WAIVED** (structural — needs component markup, see build-backlog.md) |
| ~~`.settings-action`~~ | ~~fontFamily~~ | ~~Newsreader~~ → **IBM Plex Mono** | (design rows use mono) — ✓ RESOLVED |

## session-empty

Design NBenq — 64×64 shuffle-icon circle, title "No tunes ready yet" fs=22 fw=500, subtitle
fs=11 mono.

| element | property | app | design |
|---|---|---|---|
| ~~`.session-empty`~~ | ~~(whole)~~ | ~~plain text node~~ → **icon circle + styled title + subtitle** | icon circle + styled title + subtitle — ✓ RESOLVED (markup added) |
| ~~`.session-empty`~~ | ~~fontSize / family / color~~ | ~~12 / Geist / #555555~~ → **title fs=22 + sub fs=11** | title fs=22 + sub fs=11 — ✓ RESOLVED |
| ~~(missing)~~ | ~~icon circle~~ | ~~absent~~ → **added** | se2IcWr 64×64 #222222 — ✓ RESOLVED |
| ~~`.session-summary`~~ | ~~extra line~~ | ~~"0 learned tunes · 0 sets ready"~~ → **removed** | not in design — ✓ RESOLVED |

## session-ready

Design J8hkB — a `heroCard` (#2A2A2A cr=12 pad=20) containing READY TO PRACTICE label, big
"12" number fs=56, learned-tunes/sets sub-lines, and the Start button inside the card.

| element | property | app | design |
|---|---|---|---|
| (missing) | hero card + big number | absent | heroCard with heroBig fs=56 — **WAIVED** (structural — needs component markup, see build-backlog.md) |
| `.session-summary` | style | thin mono text line | replaced by hero card — **WAIVED** (structural — needs component markup, see build-backlog.md) |
| ~~`.session-start`~~ | ~~fontFamily / weight / radius~~ | ~~Newsreader / 500 / 10~~ → **IBM Plex Mono / 600 / 8** | IBM Plex Mono / 600 / 8 — ✓ RESOLVED |
| ~~`.session-item`~~ | ~~cornerRadius / padding~~ | ~~8 / [12,14]~~ → **6 / [10,12]** | 6 / [10,12] — ✓ RESOLVED |
| ~~`.session-item-name`~~ | ~~fontFamily~~ | ~~Geist~~ → **IBM Plex Mono** | IBM Plex Mono — ✓ RESOLVED |
| ~~`.session-preview-label`~~ | ~~fontSize / ls / color~~ | ~~10 / 1px / #888888~~ → **9 / 2px / #555555** | 9 / 2px / #555555 — ✓ RESOLVED |

## session-live

Design XwIFG — a `nowCard` (#2A2A2A cr=12 pad=18, brown 1px stroke) with NOW PLAYING dot+label,
title fs=22, meta line, and skip/pause controls; a `nextCard` showing the next tune's name+meta.

| element | property | app | design |
|---|---|---|---|
| ~~`.session-now-playing`~~ | ~~cornerRadius / padding~~ | ~~4 / 12~~ → **12 / 18** | 12 / 18 — ✓ RESOLVED |
| ~~`.session-now-playing`~~ | ~~border~~ | ~~none~~ → **1px #8F5A3C stroke** | 1px #8F5A3C stroke — ✓ RESOLVED |
| `.session-now-name` | fontSize / family | 14 / Geist | 22 / Newsreader (nowTitle) — **WAIVED** (structural — needs component markup, see build-backlog.md) |
| (missing) | now-playing meta line | absent | nowMeta — type/key/bpm — **WAIVED** (structural — needs component markup, see build-backlog.md) |
| (missing) | skip / pause controls | absent | nowSkip + nowPause 48px — **WAIVED** (structural — needs component markup, see build-backlog.md) |
| (missing) | next tune detail | "?" teaser only | actual next tune name + meta — **WAIVED** (structural — needs component markup, see build-backlog.md) |

## swipe-right

Design azGEi — tune list mid right-swipe revealing a green "Learned" action (`srLearnAct`,
#3B6B4F, w=160).

The app's `.tune-row-learn-hint` (the right-swipe reveal) is `display:none` — it only appears
as a transient animation during an active drag gesture, not as a settled state. There is no
`:swipe-peek` direction for right-swipe; `:swipe-peek-tune-id` only drives the left-swipe
(Edit/Delete) reveal. **Right-swipe Learned reveal not implemented as a static state.**

## swipe-left

Design uNQW0 — left-swiped row revealing Edit + Delete action cells (`swSwipedActions` w=160,
two 80×64 cells).

| element | property | app | design |
|---|---|---|---|
| ~~`.tune-row-peek-actions`~~ | ~~width~~ | ~~120~~ → **160** | 160 — ✓ RESOLVED |
| ~~`.tune-row-peek-edit` / `-delete`~~ | ~~width~~ | ~~60 each~~ → **80 each** | 80 each — ✓ RESOLVED |
| ~~`.tune-row-peek-edit`~~ | ~~bg~~ | ~~#2A2A2A~~ → **#666666** | #666666 (swEditAct) — ✓ RESOLVED |

## full-swipe-delete

Design qkOww — tune list with one row dragged fully left past the delete threshold. The
row (`fdRDelRow`, `#2A2A2A`, opacity 0.6) has slid off-screen revealing a red background
`fdRDelBg` (`#D9534F`, `cr=6`, `h=64`, content right-aligned): a `trash-2` icon (22×22
`#FFFFFF`) + "Release to delete" label (`IBM Plex Mono` fs=13 / fw=600 / ls=1 `#FFFFFF`).
An intermediate confirm-by-release affordance, shown *before* the user lifts their finger.

The app does not implement this. `gesture.cljs`: `peek-threshold 60`, `delete-threshold
140` — a left swipe past 140px fires the delete-confirm modal (`JyFJG`, audited above)
**directly**, with no red "Release to delete" reveal in between. The only swipe reveals in
the app are the left peek (`.tune-row-peek-actions` — Edit/Delete cells) and the
right-swipe learn hint. **Not implemented as a static state** — same pattern as the
right-swipe "Learned" reveal (`azGEi`).

Not a defect per se — the app's "drag far → confirm modal" flow is a valid alternative to
the design's "drag far → release-to-delete" flow. Flag it as a deliberate design decision
to make: keep the modal, or add the inline release-to-delete reveal.

## onboarding

Design ohq9m — essentially matches.

| element | property | app | design |
|---|---|---|---|
| ~~`.coachmark-app-header`~~ | ~~padding-top~~ | ~~20~~ → **12** | 12 (hBgHead) — ✓ RESOLVED |

Logo, tagline, peek-row cell, learned-action chip (#3B6B4F33) and captions all match. The
"Got it" button is an app addition (design has no equivalent token — not flagged).

## Not implemented

Design frames with no corresponding app screen, confirmed absent from the DOM:

- **kihYP — set action sheet**: no set-level action sheet exists.
- **LQ3CL — session-complete summary**: session ends silently (`:session-mode? false`);
  there is no end-of-session summary screen.

Additionally, three mobile frames are designed but only partially realised (covered above):
**qgF2n** (tune action sheet — desktop dropdown only), **rT1yT** (abc-editor — inline textarea
only), **he1dM** (new-set — inline mini-form only).

## Cross-cutting

**Dark-theme inversion (≥4 screens).** The notes-sheet, delete-confirm modal, set-detail and
settings screens are all rendered with the **light** desktop palette (#FAFAF8 / #FFFFFF
backgrounds, dark text) while the design specifies a **dark** treatment for every one of them
(#0A0A0A / #1A1A1A / #262626 / #2A2A2A). These screens reuse desktop CSS unchanged on mobile
instead of adopting the mobile dark sheets/cards in the design. This is the single biggest
recurring divergence — fixing the mobile theming of these four would resolve roughly half of
all mismatches in this audit.

**Empty states under-styled (3 screens).** sets-empty, session-empty and (to a lesser degree)
tune-list-empty render the empty message as a bare text node where the design specifies a
64×64 icon circle + Newsreader fs=22 fw=500 title + mono fs=11 subtitle. tune-list-empty has
the structure but with a 48px icon and fs=18 title.

**Button typography (3+ screens).** Primary/secondary buttons across delete-confirm, settings,
set-detail, sets-empty and session-ready use `Newsreader` at weight 400 where the design
consistently calls for `IBM Plex Mono` at weight 600 for button labels.

---

# Reverse audit — app states with no design

The sections above run design → app (does the app match `design.pen`?). This section runs
app → design (does every app state have a Pencil frame?). The 20 mobile frames in
`design-tokens.md` were cross-referenced against an exhaustive enumeration of the app's
mobile render branches (`views.cljs` / `core.cljs` / `handlers/`).

Conclusion: the main screens are all covered. A tail of ~6 real states plus one design
divergence are **not** covered.

## App UI with no Pencil frame

| app state | what it is | render trigger | needs design? |
|---|---|---|---|
| sheet-music empty | tune selected but has no ABC notation — `.sheet-empty` placeholder | tune with no ABC | yes — real screen |
| set add-to-set typeahead | inline search dropdown for adding a tune to an existing set | `:adding-to-set` set | yes — interactive UI |
| set-creation typeahead | tune-picker dropdown in the create-set flow, step 2 | `:creating-set?` + name set | yes — interactive UI |
| backup status banner | success / error banner after export or import | `:backup-status` | yes — has success + error variants |
| empty tune library | genuinely zero tunes (design `91gc3` only covers an empty *filter* result, not an empty library / first run) | no tunes at all | yes — first-run state |
| delete-set confirm | confirm modal for deleting a set — only delete-*tune* (`JyFJG`) is designed | "⋮" → delete on a set | yes — mirror of JyFJG |

## Design divergence — RESOLVED

**Editors: inline kept, full-screen designs rejected (2026-05-21).** Two Pencil frames
design full-screen editors the app does not implement:

- `rT1yT` — full-screen ABC **notation** editor.
- `ut3Om` — full-screen tune-**details** editor (name / type / key / mode / time-sig).

Decision: the app keeps **inline editing** — ABC edits via the inline `.editor-panel`
textarea in tune detail, metadata via click-to-cycle `.meta-field`s in the detail header
(`:editing-field`). Rationale: inline edits drive live sheet-music re-renders in place,
which a separate full-screen editor would break. Both `rT1yT` and `ut3Om` are therefore
**stale design frames** and should be removed from `design.pen`. No app work — the inline
flows already exist. See `build-backlog.md` #2.

## Transient / utility states — likely no mockup needed

Flagged for completeness; most are momentary and arguably don't warrant a dedicated frame.
Confirm with the designer rather than assume:

- count-in indicator (`:count-in?`) — audio only, no on-screen beat counter
- session paused (`:session-pausing?`) — brief gap between queue items
- mid-playback note highlight / elapsed indicator — design `7AtwI` shows only the idle Play state
- metronome-only mode (`:metronome?` + not playing) — no dedicated UI
- loading / buffering — while ABC renders or the audio context spins up
- set mid-advance gap (`:set-advancing?`) — ~500ms between tunes in a set

## Summary of all gaps (both directions)

- **design → app:** 81 CSS mismatches across 19 screens (above) + 5 designed-but-unbuilt
  screens (`build-backlog.md`) + 2 designed swipe states not realised inline (`azGEi`
  right-swipe "Learned" reveal, `qkOww` full-swipe "Release to delete") — both deliberate:
  the app uses snap-back / confirm-modal flows instead.
- **app → design:** 6 real app states with no Pencil frame + 1 divergence (`ut3Om`).

Coverage: all 23 unique mobile-portrait design frames are now accounted for (20 diffed,
2 unbuilt, `qkOww` documented here). The `l9p2` landscape frame and the desktop frames
(`d1p1`–`d6p2`) are out of scope for this mobile audit.
