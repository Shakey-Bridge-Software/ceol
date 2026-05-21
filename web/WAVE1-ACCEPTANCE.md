# Wave 1 — acceptance criteria

CSS mismatch fixes from the design audit. Status: in progress (2026-05-21).

## Scope

Resolve every mismatch in `design-audit.md` (mobile, 81) and `design-audit-desktop.md`
(desktop, 28 open) — ~109 flagged. Plus `views.cljs` empty-state markup for sets/session.
Plus split `style.css` into modules.

## Acceptance criteria

1. **Every audited mismatch resolved or waived.**
   - Resolved = re-measured, app value equals the design token.
   - Waived = documented reason (deliberate divergence / stale design token / ambiguous).
     Written down, never silently skipped.

2. **Verification = the audit, re-run.** Re-measure computed styles, diff vs
   `design-tokens.md` / `design-tokens-desktop.md`. Desktop: rendered measurement via the
   `audit-tree` REPL helper at the live viewport. Mobile: the browser cannot be resized
   below 720px via script, so mobile `@media`/`.mobile-*` rules are verified by CSSOM
   inspection — read each rule's declared values from `document.styleSheets` and diff vs
   the design token. Each fixed property: `app == design`.

3. **Both platforms clean simultaneously — no regression.** Shared rule where
   mobile ≠ desktop design → base rule carries the desktop value, mobile value goes in a
   `.mobile-*` override (inside `@media (max-width: 720px)`). Re-check both audits.

4. **Build green.** `shadow-cljs` compiles, no new warnings. `views.cljs` empty-state
   markup compiles and renders.

5. **`style.css` split — rendering identical.** Modularization is a pure refactor;
   computed styles for a sample of screens must be unchanged before/after the split.

6. **Deliberate divergences left alone.** Slim playback bar, inline editor,
   full-swipe→modal — audit marked these intentional. Not "fixed."

7. **Audit docs updated.** `design-audit.md` + `design-audit-desktop.md` — every
   mismatch marked resolved or waived-with-reason.

## Gate

Done = audit re-run across all 19 mobile + 11 desktop screens → 0 open mismatches
(each resolved or waived), both platforms clean, build compiles.

## Ambiguous items — reviewed 2026-05-21

All three "ambiguous" items were re-examined. Two were mislabelled.

- `.tune-name` default colour — **RESOLVED.** Not ambiguous: the app already has all
  three row states (active `#F5F4F0`, learned `#A8A8A8`); only the base/default colour
  was wrong (`#aaa`). Fixed → `#D4D2CC`.
- sidebar `gap` 20 vs 24 — **WAIVED (design bug).** `design.pen` frames disagree
  (`d1p1`=24, `d3p2`/`d4p1`/`d5p1`=20). App's 20 matches the majority. No app change;
  `d1p1` should be corrected to 20 in `design.pen`.
- `.set-name` fontWeight — **WAIVED (false positive).** The audit compared the app's
  *inactive* set-name (fw400) against the design's *active* card (fw500). App's active
  card is already fw500; inactive matches design's inactive cards. No mismatch.
