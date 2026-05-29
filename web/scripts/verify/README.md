# Browser verification harness

Automated end-to-end checks for the web app's mobile flows. Drives a real
Chrome instance via the Chrome DevTools Protocol over Node's native
WebSocket — no third-party deps.

Each scenario can:
- dispatch `ceol.web.core/dispatch-action!` directly (skips event wiring)
- read the cljs `app-state` atom and assert its shape
- screenshot the page at a 390×844 mobile viewport
- run unattended in CI / from the CLI

Use this when a flow has enough surface area to merit a repeatable check
(B-items typically). Don't reach for it on a one-shot CSS tweak.

## How it compares to the manual audit

The Wave-1 audit was screenshot-by-hand: user dumped `Screenshot YYYY-MM-DD
at HH.MM.SS.png` at the repo root, an agent read each PNG and diffed against
`mcp__pencil__get_screenshot` of the matching `design.pen` frame. Pixel-only,
no state visibility, manual every step.

This harness is **driven + introspective**: the test asserts the cljs state
shape before it screenshots. The first B1 run had six identical-md5 PNGs —
clear sign the onboarding coachmark was covering everything before any of
the actual editor rendered. A pixel-diff against `design.pen` would have
seen "two screens look the same" and shrugged. The md5 + state-assert pair
flagged the bug in seconds.

Trade-off: headless Chrome is not iOS Safari. Native select pickers, system
fonts, safe-area insets, and gesture inertia all differ. Real-device pass
is still the final gate.

## Usage

```bash
# Terminal 1 — keep the dev server running
cd web
./node_modules/.bin/shadow-cljs watch app

# Terminal 2 — run a scenario (Chrome launches headless)
cd web/scripts
./verify.sh b1
```

Artifacts land in `web/scripts/verify/out/<scenario>/` (gitignored).

## Writing a new scenario

Copy `b1.mjs` to `<name>.mjs` and adjust. The harness API is in `../cdp.mjs`:

```js
import {
  navigate, dispatch, dispatchExpr, evalJs, shot, sleep,
  kwExpr, vecExpr, getInExpr, shutdown,
} from "../cdp.mjs";

await navigate("http://localhost:8280/index.html");

// First-run coachmark blocks everything — always dismiss it first.
await dispatch(kwExpr("onboarding", "dismiss"));
await sleep(200);

// Drive an action: ceol.web.core/dispatch-action! [:my/action [arg]]
await dispatch(
  kwExpr("my", "action"),
  vecExpr([JSON.stringify("arg-value")])
);

// Assert state shape
const value = JSON.parse(await evalJs(getInExpr(["some-key"])));
console.log(value);

// Screenshot
await shot("verify/out/myscenario/01-initial.png");
```

### Helper cheatsheet

| Helper                          | Builds JS expression                                        |
|---------------------------------|-------------------------------------------------------------|
| `kwExpr("foo")`                 | `cljs.core.keyword.call(null, "foo")`                       |
| `kwExpr("ns", "name")`          | `cljs.core.keyword.call(null, "ns", "name")`                |
| `vecExpr([...])`                | `cljs.core.PersistentVector.fromArray([...])`               |
| `dispatchExpr(kw, args)`        | `ceol.web.core.dispatch_action_BANG_(kw, args)`             |
| `getInExpr(["a", "b"])`         | reads `(get-in @app-state [:a :b])` as a JSON string        |

`dispatch(kw, args)` is the awaited version of `dispatchExpr`.

### Gotchas

- **Onboarding coachmark** — `:onboarded?` defaults to false, so the
  coachmark is z-stacked above everything on every fresh run. Dispatch
  `:onboarding/dismiss` before doing anything else, or screenshots are
  useless.
- **`<select value=…>` is a no-op** — HTML's `<select>` ignores the `value`
  attribute. If you're adding a dropdown to the app, put `:selected true`
  on the matching `<option>` instead. (Caught in the B1 verification run.)
- **CLJS munging** — `dispatch-action!` → `dispatch_action_BANG_`, `clj->js`
  → `clj__GT_js`. Use the helpers (`kwExpr`, `vecExpr`, `dispatchExpr`,
  `getInExpr`) rather than hand-rolling these in `evalJs`.
- **Identical md5s are a signal** — if `md5 verify/out/<scenario>/*.png`
  shows duplicates where you expect difference, something is covering the
  app or the dispatch silently failed. Always sanity-check.
- **Settle timing** — give Replicant a tick after dispatch (200ms is
  usually enough), longer (400ms+) after Save/operations that touch
  persist+re-render.
- **Fresh profile per run** — `verify.sh` blows away the user-data-dir each
  time. If you want sticky localStorage for cross-run testing, that's a
  custom scenario.

## Files

```
scripts/
  cdp.mjs                — WebSocket + CDP helpers (reusable)
  verify.sh              — Chrome launcher + scenario runner
  verify/
    README.md            — this file
    b1.mjs               — reference scenario (mobile tune-details editor)
    out/                  — artifacts, gitignored
```

## Coverage status

- `b1` — Mobile tune-details editor — passes (commit `150b0e4`).
- `item1` — Tune action sheet + B4 duplicate — passes (commit `6aff231`).
- `b2` — Generic confirm modal (set-delete + import) — passes.
- `item3` — mobile new/edit set editor (draft slot, add/remove/reorder, save,
  edit-seed, cancel) + synthetic-touch grip drag-to-reorder — passes. The drag
  *pixel* behaviour on real iOS still needs a device pass; the gesture wiring
  and reorder action are covered headless.
- `item4` — mobile set action sheet — passes. Clicks the real `.as-row` buttons
  (Edit/Duplicate/Delete) to cover the view→dispatch wiring end-to-end; Delete
  routes through the B2 confirm. Play is asserted present (not clicked — audio).
- `b3` — mobile backup-status toast — passes. Drives `backup/set-status!`
  directly (export/import can't run headless) and asserts the success/error
  banner renders in the fixed `.mobile-backup-status` toast.
- `item5` — session-complete summary — passes. Stows a `:session-result`
  directly (a real `:done` needs the full audio queue), asserts the summary +
  stat line, and the Done / Practice-again wiring (the latter's synchronous
  state effects only).
- `waveA` — notes sheet title block + footer — passes. Opens the notes panel,
  asserts the PRACTICE NOTES label, title/meta block, and the live char count.

Add a row when you ship a new B-item with a scenario.
