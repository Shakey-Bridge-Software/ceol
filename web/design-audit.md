# Mobile design audit — ceol web app vs `design.pen`

Method: live DOM measurement via shadow-cljs nREPL at iPhone size (390×844), each screen's
state set then `getBoundingClientRect` + `getComputedStyle` walked and diffed against the
token reference in `design-tokens.md`. Date: 2026-05-21.

Status-bar (44px) / home-indicator (34px) frames in the design have no app equivalent — the
browser app has no native chrome. Noted once here; not flagged per screen.

## Summary

| screen | matches | mismatches | worst issue |
|---|---|---|---|
| tune-list | mostly | 1 | tune-name default colour #A8A8A8 (design F5F4F0) |
| tune-list-empty | partial | 4 | icon circle 48px vs design 64px; title fs/fw off |
| controls-sheet | partial | 8 | sheet bg #1A1A1A vs design #0A0A0A; card colours, weights |
| notes-sheet | no | 7 | whole sheet light-themed; design is a dark sheet |
| delete-confirm | no | 6 | modal light-themed; design is dark (#262626) |
| tune-action-sheet | not impl | — | mobile action sheet absent (desktop dropdown only) |
| abc-editor | not impl | — | full-screen editor absent (inline textarea only) |
| playback-detail | partial | 4 | top-bar dark vs design light; sizes off |
| sets-empty | no | 5 | empty state is plain text; design has icon+title+sub |
| sets-populated | partial | 3 | set card collapsed by default; design always-expanded |
| set-detail | no | 6 | whole screen light-themed; design is dark |
| new-set | not impl | — | full-screen set editor absent (inline mini-form only) |
| settings | no | 4 | whole screen light-themed; design is dark |
| session-empty | no | 4 | empty state plain text; design has icon+title+sub |
| session-ready | no | 6 | no hero card / big number; design centrepiece missing |
| session-live | no | 6 | no now-playing card border / controls / next detail |
| swipe-right | not impl | — | right-swipe "Learned" reveal has no static state |
| swipe-left | partial | 3 | action cells 60px vs design 80px; edit colour |
| full-swipe-delete | not impl | — | no "Release to delete" reveal; jumps straight to confirm modal |
| onboarding | ✓ | 1 | header top padding 20 vs design 12 |

Three worst screens by issue count: **controls-sheet (8)**, **notes-sheet (7)**, **set-detail / settings / session-ready / session-live (6 each)**.

## tune-list

(design ZV3rw absent — header/tabs/filters compared to 91gc3, which are identical)

| element | property | app | design |
|---|---|---|---|
| `.tune-name` | color | #A8A8A8 | #F5F4F0 (selected) / #D4D2CC (unselected) |

`.tune-row`, `.filter-chip`, `.tune-meta`, `.tune-info` match design. The default tune title
colour #A8A8A8 sits between the design's selected (#F5F4F0) and unselected (#D4D2CC) values —
neither state matches.

## tune-list-empty

Design 91gc3 `fEmptyBody`.

| element | property | app | design |
|---|---|---|---|
| `.mobile-empty-state` | padding | 80 / 40 / 80 / 40 | 40 all sides |
| `.empty-icon` | width/height | 48×48 | 64×64 |
| `.empty-icon` | bg | #2A2A2A | #222222 |
| `.empty-title` | fontSize / weight | 18 / 400 | 22 / 500 |

## controls-sheet

Design rfcbO.

| element | property | app | design |
|---|---|---|---|
| `.controls-sheet` | bg | #1A1A1A | #0A0A0A |
| `.controls-sheet-title` | fontSize / weight | 24 / 400 | 26 / 500 |
| `.cs-tempo-val` | fontSize / weight | 34 / 400 | 42 / 500 |
| `.controls-sheet-tempo` (bpm card) | bg | #2A2A2A | #1F1F1F |
| `.cs-toggle` | bg / cornerRadius / weight | #2A2A2A / 10 / 400 | #1F1F1F / 12 / 600 |
| `.cs-part` | bg / weight | #2A2A2A / 400 | #1F1F1F container / 600 |
| `.controls-sheet-play` | fontWeight | 400 | 700 |
| `.controls-sheet` | cornerRadius | 16 | 20 |

## notes-sheet

Design vWmaK — a dark bottom sheet. The app's `.notes-panel` is a **light** panel.

| element | property | app | design |
|---|---|---|---|
| `.notes-panel` | bg | #FAFAF8 | #0A0A0A |
| `.notes-label` | color / content | #666666 / "NOTES" | #8F5A3C / "PRACTICE NOTES" |
| `.notes-textarea` | bg | #FFFFFF | #1F1F1F (nTextWrap) |
| `.notes-textarea` | color | #1A1A1A | #F5F4F0 |
| `.notes-textarea` | fontSize | 12 | 13 |
| (missing) | tune title block | absent | nTitleBlock — title fs=22 + meta |
| (missing) | footer | absent | nFoot — "Saved" + char count |

## delete-confirm

Design JyFJG — a dark modal (#262626). The app's `.modal` is **light** (#F5F4F0).

| element | property | app | design |
|---|---|---|---|
| `.modal` | bg | #F5F4F0 | #262626 |
| `.modal` | cornerRadius | 12 | 14 |
| `.modal-title` | fontWeight | 400 | 600 |
| `.modal-body` | fontFamily / color | Newsreader / #666666 | IBM Plex Mono / #A8A8A8 |
| `.modal-cancel` | fontFamily / weight | Newsreader / 400 | IBM Plex Mono / 600 |
| `.modal-destructive` | fontFamily / weight | Newsreader / 400 | IBM Plex Mono / 600 |

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
| `.mobile-top-bar` | bg | #1A1A1A | #FAFAF8 (top-bar) |
| `.mobile-title` | fontSize | 15 | 18 (titleText) |
| `.mobile-title-meta` | fontSize | 9 | 10 (metaText) |
| `.mobile-playback-bar` | play btn / layout | slim 48px play, pad [0,16] | 64px play, pad 16 |

The slim mobile playback bar is a deliberate redesign (commit "replace playback bar with slim
mobile design") — sizes are expected to differ. The dark/light theme inversion of the top bar
is the real flag.

## sets-empty

Design SVtwq — rich empty state: 64×64 icon circle (#222222, layers icon), title
"No sets yet" fs=22 fw=500, subtitle fs=11 mono, plus a FAB.

| element | property | app | design |
|---|---|---|---|
| `.sets-empty` | (whole) | plain text node | icon circle + styled title + subtitle |
| `.sets-empty` | fontSize / family / color | 12 / Geist / #555555 | n/a — should be title fs=22 + sub fs=11 |
| (missing) | icon circle | absent | eEmptyIcon 64×64 #222222 |
| (missing) | subtitle | absent | "Tap New Set to get started" |
| `.add-set-btn` | fontFamily / size / weight | Newsreader / 14 / 400 | mono / 13 / 600 (eFab text) |

## sets-populated

Design 7VNKz — each set card always shows title, count, numbered tune list, ready dot and a
"Play set" button.

| element | property | app | design |
|---|---|---|---|
| `.set-card` | content (collapsed) | header only (name + count + chevron) | always shows tunes + ready + Play set |
| `.set-name` | fontWeight | 400 | 500 |
| `.add-set-btn` | style | brown fill, Newsreader 14 | bordered `newSetBtn`, mono 11 |

Set cards are collapse-by-default accordions; the design has no expand/collapse — every card
is fully expanded inline.

## set-detail

Design sgPmc — dark theme (#1A1A1A, rows #2A2A2A, titles #F5F4F0). The app's `.set-detail`
is **light-themed** (desktop styling reused on mobile).

| element | property | app | design |
|---|---|---|---|
| `.set-detail-header` | bg | #FAFAF8 | (transparent on #1A1A1A) |
| `.set-detail-tune-row` | bg | #FFFFFF + 1px border | #2A2A2A, no border |
| `.set-detail-title` | fontSize | 28 | 16 |
| `.set-detail-tune-row` | padding | 12 / 16 | 14 / 12 |
| `.set-detail-play` | fontSize / weight | 14 / 400 | 13 / 600 |
| `.set-detail-list-label` | padding | 16 / 32 / 8 / 32 | 0 / 20 / 8 / 20 |

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
| `.settings-card` | bg | #FFFFFF | #2A2A2A |
| `.settings-card` | cornerRadius | 6 | 10 |
| Export / Import rows | layout | label + brown action button | icon + text + chevron list row |
| `.settings-action` | fontFamily | Newsreader | (design rows use mono) |

## session-empty

Design NBenq — 64×64 shuffle-icon circle, title "No tunes ready yet" fs=22 fw=500, subtitle
fs=11 mono.

| element | property | app | design |
|---|---|---|---|
| `.session-empty` | (whole) | plain text node | icon circle + styled title + subtitle |
| `.session-empty` | fontSize / family / color | 12 / Geist / #555555 | should be title fs=22 + sub fs=11 |
| (missing) | icon circle | absent | se2IcWr 64×64 #222222 |
| `.session-summary` | extra line | "0 learned tunes · 0 sets ready" | not in design |

## session-ready

Design J8hkB — a `heroCard` (#2A2A2A cr=12 pad=20) containing READY TO PRACTICE label, big
"12" number fs=56, learned-tunes/sets sub-lines, and the Start button inside the card.

| element | property | app | design |
|---|---|---|---|
| (missing) | hero card + big number | absent | heroCard with heroBig fs=56 |
| `.session-summary` | style | thin mono text line | replaced by hero card |
| `.session-start` | fontFamily / weight / radius | Newsreader / 500 / 10 | IBM Plex Mono / 600 / 8 |
| `.session-item` | cornerRadius / padding | 8 / [12,14] | 6 / [10,12] |
| `.session-item-name` | fontFamily | Geist | IBM Plex Mono |
| `.session-preview-label` | fontSize / ls / color | 10 / 1px / #888888 | 9 / 2px / #555555 |

## session-live

Design XwIFG — a `nowCard` (#2A2A2A cr=12 pad=18, brown 1px stroke) with NOW PLAYING dot+label,
title fs=22, meta line, and skip/pause controls; a `nextCard` showing the next tune's name+meta.

| element | property | app | design |
|---|---|---|---|
| `.session-now-playing` | cornerRadius / padding | 4 / 12 | 12 / 18 |
| `.session-now-playing` | border | none | 1px #8F5A3C stroke |
| `.session-now-name` | fontSize / family | 14 / Geist | 22 / Newsreader (nowTitle) |
| (missing) | now-playing meta line | absent | nowMeta — type/key/bpm |
| (missing) | skip / pause controls | absent | nowSkip + nowPause 48px |
| `.session-next` | content | "?" teaser only | actual next tune name + meta |

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
| `.tune-row-peek-actions` | width | 120 | 160 |
| `.tune-row-peek-edit` / `-delete` | width | 60 each | 80 each |
| `.tune-row-peek-edit` | bg | #2A2A2A | #666666 (swEditAct) |

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
| `.coachmark-app-header` | padding-top | 20 | 12 (hBgHead) |

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
