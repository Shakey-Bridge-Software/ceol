# Desktop design audit — ceol web app vs `design.pen`

Method: live DOM measurement via shadow-cljs nREPL at desktop size (browser ≈1920 wide;
`resizeTo` did not shrink the window — fine, the persistent-sidebar desktop layout still
renders). Each screen's state was set, then `getBoundingClientRect` + `getComputedStyle`
were walked and diffed against the token reference in `design-tokens-desktop.md`. Date: 2026-05-21.

Design canvas frames are 1440-wide (sidebar + main) or 280/1160-wide partials. The desktop
sidebar is a fixed 280px; the main-area is fluid. Per the brief, fluid widths/heights of the
main-area and its children are NOT flagged — only the sidebar's fixed width, paddings, gaps,
corner radii, fonts, weights, letter-spacing and colours are compared.

**Update 2026-05-21 — 3 mismatches resolved.** `.tune-row` padding + cornerRadius and
`.tune-name` fontSize were flagged below as desktop mismatches. They were a regression from
commit `13ae792` (the mobile tune-list fix edited shared base rules). Fixed by re-scoping:
the base rules now carry the desktop design values, with mobile values moved to a
`.mobile-tune-list` override inside the `@media (max-width: 720px)` block. Resolved entries
are struck through below.

## Summary

| screen | matches | mismatches | worst issue |
|---|---|---|---|
| tune-view (no selection) | ✓ | 0 (3 resolved) | tune-row pad/radius — fixed, see Update note |
| tune-view (selected) | ✓ | 0 (3 resolved) | tune-name/tune-row — fixed, see Update note |
| tune editing (ABC editor) | ✓ | 1 | sheet/editor split divider has visible 1px line; design plain |
| tune notes open | ✓ | 1 | drawer gap 8 vs design 10 (sub-visible) |
| tune context menu | ✓ | 0 | matches design |
| sets list | partial | 4 | set-name fw normal vs design 500; card not padded |
| set creating (name) | partial | 2 | form fill #2A2A2A vs design #222222; radius 4 vs 6 |
| set creating (add tunes) | partial | 2 | typeahead highlight name fw normal vs design 500 |
| set detail | partial | 7 | title fs28/fw400 vs 24/500; play-btn font + radius; rows pad/radius |
| session pre-session | partial | 4 | queue set-items lack #2A2A2A fill + radius 4 |
| session active | ✓ | 1 | next-teaser fs28 vs design 24 |
| settings | partial | 6 | title fs28/fw400 vs 24/500; action buttons font + weight + radius |
| delete confirm | no design frame | — | reverse-audit (no desktop modal frame) |
| onboarding | n/a — hidden | — | coachmark is `display:none` on desktop (mobile-only) |

Three worst screens by issue count: **set detail (7)**, **settings (6)**, **sets list (4)** /
**session pre-session (4)**.

## tune-view (no selection)

Design hGr4o/XVJiB/INQFW/6ufOa/vdMf8 — sidebar reference. Main-area shows `.sheet-empty`
(empty abc.js container); no design frame for the no-selection empty state — see reverse-audit.

| element | property | app | design |
|---|---|---|---|
| ~~`.tune-row` / `.tune-row.learned`~~ | ~~padding~~ | ~~14 / 12~~ → **10 / 12** | 10 / 12 — ✓ RESOLVED |
| ~~`.tune-row`~~ | ~~cornerRadius~~ | ~~6px~~ → **4px** | 4px — ✓ RESOLVED |
| `.sidebar` | gap | 20px | 24px (d1p1) — 20 in d3p2/d4p1/d5p1 |

The sidebar fixed width (280px), `.tab-bar` (pad 3, cr 6, bg #111111), `.tab` / `.tab.active`
(fs12, active fw500/#F5F4F0/#2A2A2A), `.filter-chip` / `.active.filter-chip`
(pad 6/12, cr 14, fs11, active #8F5A3C/#F5F4F0), `.new-tune-btn` (pad 10/0, stroke #3A3A3A,
fs13, #888888), `.app-name` (fs28 fw300 Newsreader ls1 #F5F4F0), `.app-tagline`
(fs10 IBM Plex Mono ls2 #666666) and `.sidebar-settings-btn` all match design.
The sidebar `gap` is ambiguous: design d1p1 says 24 but the later sidebar-only frames
(d3p2, d4p1, d5p1) say 20 — the app's 20 matches the majority.

## tune-view (selected)

Design hGr4o/INQFW/6ufOa — sidebar + `.tune-main`. The tune-header / playback-bar are fluid;
only fonts/colours/radii/paddings flagged.

| element | property | app | design |
|---|---|---|---|
| ~~`.tune-name` (sidebar list)~~ | ~~fontSize~~ | ~~16px~~ → **14px** | 14px — ✓ RESOLVED |
| ~~`.tune-row`~~ | ~~cornerRadius~~ | ~~6px~~ → **4px** | 4px — ✓ RESOLVED |
| ~~`.tune-row`~~ | ~~padding~~ | ~~14 / 12~~ → **10 / 12** | 10 / 12 — ✓ RESOLVED |

`.tune-header` (pad 20/32), `.tune-title` (fs24 fw normal Newsreader #1A1A1A),
`.tune-title-meta` (fs11 IBM Plex Mono ls0.5 #666666), `.section-btn` /
`.active.section-btn` (pad 5/12, cr 4, fs12 IBM Plex Mono, active #8F5A3C/#F5F4F0/fw600),
`.edit-toggle`, `.play-btn` (pad 10/20, cr 4, fs13 fw500, #8F5A3C/#F5F4F0),
`.control-btn` / `.tempo-btn` (pad 6/14, cr 4, fs12, stroke #CCCCCC) and `.guitar-btn`
(pad 8/14, cr 4, #2A2A2A/#F5F4F0) all match design. The sidebar tune-name default colour
is #A8A8A8 (rgb 168,168,168) — same finding as the mobile audit: it sits between the
design's selected #F5F4F0 and unselected #AAAAAA values. (Counted once under "no selection".)

## tune editing (ABC editor)

Design XB8tV / CHnL3 (`n1` / `n2`) — split-view variants.

| element | property | app | design |
|---|---|---|---|
| `.split-divider` | — | renders visible 1px `.split-line` rules above/below grip | design `eDivSplit` is a plain grip with no rules |

`.editor-panel` (pad 16/32, gap 8, bg #1E1E1E) matches design `e-editor`.
`.editor-label` (fs11 fw500 IBM Plex Mono ls1 #888888), `.editor-hint` (fs10 #555555),
`.editor-hint.accent` (#8F5A3C), `.editor-textarea` (fs13 IBM Plex Mono #D4D4D4),
`.editing-strip` (pad 8/32, bg #8F5A3C, fs11 fw600 IBM Plex Mono ls2 white) all match the
design's editor banner + code area. The split divider is the only cosmetic difference: the
app draws two thin rule lines flanking the grip; the design shows only the grip glyph.

## tune notes open

Design k6eKu (`d2p2`) `notesDrawer`.

| element | property | app | design |
|---|---|---|---|
| `.notes-panel` | gap | 8px | 10px |

`.notes-panel.open` (pad 12/32, h 200, bg #FAFAF8, top stroke), `.notes-label`
(fs10 fw500 IBM Plex Mono ls2 #666666), `.notes-textarea` (pad 12/14, cr 4, bg #FFFFFF,
stroke #E5E4E0, fs12 IBM Plex Mono #1A1A1A) and `.notes-close` (~20×20, #888888) all match.
Only the row gap is 2px short of design — sub-visible.

## tune context menu

Design Hh5RX (`d3p2`) `context-menu`.

✓ matches design. `.tune-context-menu` (w 180, pad 4, gap 1, cr 6, bg #2A2A2A,
stroke #3A3A3A), `.cm-item` (pad 8/10, cr 4, gap 8, fs12 fw normal Newsreader #EEEEEE) and
`.cm-divider` (1px #3A3A3A) match the design node-for-node.

## sets list

Design 5zyod (`d4p1`).

| element | property | app | design |
|---|---|---|---|
| `.set-name` | fontWeight | 400 | 500 (`s1name`, active card) |
| `.set-card` | padding | 0 (header carries 10/12) | 12 all sides (`s1`) |
| `.set-card-header` | padding | 10 / 12 | 12 / 12 |
| `.add-set-btn` | color | #888888 | #666666 (`newSetBtn`) |

The active `.set-card` correctly fills #2A2A2A, the active `.set-name` is #F5F4F0 fw500, and
the card expands to show its tune list — all matching design `s1`. The inactive cards
(transparent, #AAAAAA name) match `s2`–`s4`. The mismatch is that the design pads the whole
card body at 12px while the app puts the 10/12 padding on `.set-card-header` only, and the
default (non-active) `.set-name` weight is 400 where the design's reference card uses 500.

## set creating (name)

Design MEvqr (`d4p2`) `new-set-form`.

| element | property | app | design |
|---|---|---|---|
| `.set-creation` | fill | #2A2A2A | #222222 |
| `.set-creation` | cornerRadius | 4px | 6px |

`.set-creation` correctly carries the #8F5A3C accent stroke. The first row pad (10/12)
matches `cNameRow`. The container fill is one step lighter than design and the corner radius
is 4 vs 6.

## set creating (add tunes)

Design MEvqr (`d4p2`) — `cSearchInput`, `typeahead-dropdown`, `cTuneAdded`.

| element | property | app | design |
|---|---|---|---|
| `.highlighted.typeahead-item` (name) | fontWeight | 400 | 500 (`dd1name`) |
| `.set-creation` | fill / cornerRadius | #2A2A2A / 4px | #222222 / 6px |

`.set-input` (pad 6/8, cr 3, bg #1A1A1A, stroke #444444, fs12 #F5F4F0) matches `cSearchInput`.
`.typeahead-item.highlighted` correctly fills #8F5A3C; `.set-creation-tune-row` (#AAAAAA,
fs12) and `.set-tune-num` (fs10 IBM Plex Mono #555555) match. The highlighted result's name
should be fw500 per `dd1name`; the app renders it fw400. (Container fill/radius counted once
under the previous screen.)

## set detail

Design FVwlf (`d4p3`) — main-area-only frame.

| element | property | app | design |
|---|---|---|---|
| `.set-detail-title` | fontSize | 28px | 24px |
| `.set-detail-title` | fontWeight | 400 | 500 |
| `.set-detail-header` | padding | 24 / 32 | 20 / 32 |
| `.set-detail-play` | fontFamily / weight | Newsreader / 400 | IBM Plex Mono / 600 |
| `.set-detail-play` | fontSize / cornerRadius | 14 / 4px | 12 / 6px |
| `.set-detail-tune-row` | padding / cornerRadius | 12-16 / 6px | 14-20 / 8px |
| `.set-detail-tune-name` | fontSize / weight | 15 / 400 | 16 / 500 |
| `.set-detail-num` | fontSize / weight | 13 / 400 | 14 / 600 |

Structurally correct (white tune-row cards with #E0E0E0 stroke, green `circle-check`,
`grip-vertical` handle, `TUNES` label, brown play button). The mismatches are typographic:
the design's "Play set" button uses an uppercase IBM Plex Mono fw600 label at fs12 with a 6px
radius, where the app uses Newsreader fw400 fs14 at 4px radius; tune-row numbers and names
are a step smaller / lighter than design; header padding is 4px tall over design.

## session pre-session

Design KK76c (`d5p1`).

| element | property | app | design |
|---|---|---|---|
| `.session-item` (set) | fill | transparent | #2A2A2A (`spSet1`) |
| `.session-item` (set) | cornerRadius | 0 | 4px |
| `.session-item` (set) | padding | 6 / 10 | 8 / 10 |
| `.session-preview-label` | fontWeight | 400 | 500 (`spLabel`) |

`.session-start` (pad 12, cr 4, bg #8F5A3C, fs13 fw500 #F5F4F0) matches `spStart`.
`.session-summary` (fs11 IBM Plex Mono #888888) matches `spCount`. `.session-item-name`
(fs12 fw500 #CCCCCC) and `.session-item-icon` (#8F5A3C) match. The session-queue *set*
preview rows should sit on a #2A2A2A panel with a 4px radius (design `spSet1`/`spSet2`);
the app renders them flat/transparent. The preview section label should be fw500.

## session active

Design fVHy5 (`d5p2`).

| element | property | app | design |
|---|---|---|---|
| `.session-next-val` | fontSize | 28px | 24px (`siNextVal`) |

Strong match. `.session-active-label` (fs9 fw500 IBM Plex Mono ls1 #8F5A3C),
`.session-active-count` (fs11 fw500 #F5F4F0), `.session-now-playing` (pad 12, cr 4,
bg #2A2A2A), `.session-now-name` (fs14 fw500 #F5F4F0), `.session-next` (pad 10/12, cr 4,
stroke #333333), `.session-progress-bar` (h4, cr2, #333333), `.session-progress-fill`
(#8F5A3C) and `.session-end` (pad 10, cr 4, stroke #555555, #888888) all match design.
Only the "?" next-teaser glyph is fs28 vs the design's fs24.

## settings

Design i5sLp (`d6p2`) — main-area-only frame.

| element | property | app | design |
|---|---|---|---|
| `.settings-title` | fontSize | 28px | 24px |
| `.settings-title` | fontWeight | 400 | 500 |
| `.settings-header` | padding | 24 / 32 | 20 / 32 |
| `.settings-card` | cornerRadius | 6px | 8px |
| `.settings-card-label` | fontWeight | 400 | 500 (`BACKUP`/`ABOUT`/`DATA`) |
| `.settings-action` (Export) | fontFamily / weight | Newsreader / 400 | IBM Plex Mono / 600 |
| `.settings-action` (Export) | fontSize / cornerRadius | 13 / 4px | 11 / 6px |

Structurally a strong match (white cards, #E0E0E0 stroke, section labels, brown Export
button, red `Clear all data` row with #D9534F). The mismatches repeat the set-detail
pattern: the action buttons should use uppercase IBM Plex Mono fw600 at fs11 / 6px radius
(design `expBtn`), card radius is 8 not 6, the page title is 24/500 not 28/400, the section
labels are fw500, and the header is 4px taller than design.

## delete confirm

No desktop design frame exists for the delete-confirm modal — see reverse-audit. The app's
`.modal` is light-themed (bg #F5F4F0, cr 12, pad 24, w 360), with `.modal-destructive`
filled #D9534F. There is nothing in `design-tokens-desktop.md` to diff against.

## onboarding

The `.coachmark-overlay` is `display:none` at desktop width — it is a mobile-only
swipe-tutorial overlay (teaches right-swipe = Learned, left-swipe = Edit/Delete). The
desktop layout has no swipe gestures and no onboarding coachmark, and there is no desktop
design frame for it. Not applicable to the desktop audit.

## Reverse-audit — desktop app states with no design frame

These app states render on desktop but have no corresponding frame in `design-tokens-desktop.md`:

- **tune-view empty state** (`.sheet-empty`) — when no tune is selected the main-area shows
  an empty abc.js container with no placeholder content. No design frame covers it. (Mobile
  has a designed empty state; desktop does not.)
- **delete-confirm modal** (`.modal` / `.modal-backdrop`) — light-themed modal, no desktop
  design frame. The mobile audit found the mobile *design* used a dark modal (#262626) while
  the mobile app was light; for desktop there is simply no frame to compare against.
- **onboarding coachmark** (`.coachmark-overlay`) — present in the DOM but `display:none` on
  desktop; mobile-only feature, no desktop frame.
- **backup-status banner** (`.backup-status`) — transient success/error banner above the
  sidebar footer; no design frame.
- **set-detail empty / set with unlearned tunes** — only the all-learned variant (`d4p3`)
  is in the design; partial-learned set-detail has no frame.

## Cross-cutting patterns (repeating across ≥3 screens)

1. **Button typography: Newsreader fw400 where design wants IBM Plex Mono fw600.**
   The "Play set" button (set detail) and the "Export" / settings action buttons render in
   Newsreader at fw400, but the design specifies uppercase **IBM Plex Mono fw600** labels at
   a smaller fs (11–12) with a 6px corner radius. This is the same "button typography"
   pattern flagged in the mobile audit — the app's primary action buttons systematically use
   the body serif font instead of the design's mono uppercase treatment. Affects set detail,
   settings, and (to a lesser extent) is consistent with the playback-bar play button which
   *does* correctly use a small fs/weight.

2. **Page/section titles one step too large and too light.**
   `.set-detail-title` and `.settings-title` are fs28/fw400; both design frames specify
   **fs24/fw500**. Section labels (`.settings-card-label`, `.session-preview-label`) and
   set-detail/sets-list names are fw400 where the design uses **fw500**. A consistent
   "headings under-weighted, over-sized" drift across set detail, settings, sets list and
   session pre-session.

3. **Corner radius inconsistently one step off the design.**
   `.set-creation` (4 vs 6), `.set-detail-play` (4 vs 6), `.settings-card` (6 vs 8),
   `.set-detail-tune-row` (6 vs 8) — radii are one step off across 4 screens, in both
   directions. (`.tune-row` was also flagged 6 vs 4 but is now resolved — see the Update
   note at the top.)

Note: unlike the mobile audit, there is **no dark-theme inversion** problem on desktop — the
sidebar is correctly dark (#1A1A1A) and the main-area correctly light (#F5F4F0/#FAFAF8),
matching the design. The mobile audit's recurring "whole sheet/modal light-themed where the
design is dark" finding does **not** reproduce on desktop; the desktop dark/light split is
faithful. The "button typography" cross-cutting pattern, however, **does** match the mobile
audit's findings.
