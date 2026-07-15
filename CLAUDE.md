# CLAUDE.md

## Project overview

ceol is an Irish traditional music practice tool with two interfaces:

1. **TUI** — terminal player built with Babashka and [charm.clj](https://github.com/timokramer/charm.clj). Fetches ABC from thesession.org, converts to MIDI, plays via FluidSynth.
2. **Web** — browser app built with ClojureScript, Replicant, abc.js, and Tone.js. Renders sheet music with chord annotations, plays melody (banjo) + rhythm guitar (sampled acoustic).

Both share code via `.cljc` files.

## Running

```bash
# TUI
./run.sh

# Web (dev)
cd web && ./node_modules/.bin/shadow-cljs watch app
# then open http://localhost:8280
```

## Architecture

### Shared code (.cljc)
- **notation.cljc** — ABC tokenizer/parser, timeline builder with ms offsets
- **tunes.cljc** — static catalog (54 tunes), type filters, setlist resolution
- **abc.cljc** — pure ABC utils: `tempo-for-type`, `build-abc-string`, `adjust-abc-tempo`, `split-abc-parts`

### TUI (Babashka + charm.clj)
Elm architecture: `init → update → view` loop.
- **state.clj** — `init-state`, `update-state`, key handlers, audio pipeline, count-in, setlists, set queue
- **view.clj** — pure rendering (header, tune list, status bar, staff notation, help overlay)
- **audio.clj** — thesession.org API, MIDI conversion via abc2midi, FluidSynth playback. Delegates pure ABC fns to `ceol.abc`
- **data.clj** — file paths, cache read/write, soundfont discovery, local ABC, setlist loading
- **staff.clj** — terminal staff renderer (pitch→row grid, Unicode noteheads)
- **core.clj** — entry point, shutdown hook for orphan fluidsynth cleanup

### Web (ClojureScript + Replicant)
Elm-like architecture: atom + watcher + Replicant render.
- **core.cljs** — entry point, init, keyboard shortcuts, `dispatch-action!` router. Most actions delegate to `handlers/*`; trivial one-liners (filter, tab, tempo) stay inline.
- **state.cljs** — app-state atom, query functions, set/tune data model, malli schemas
- **views.cljs** — Replicant hiccup components (sidebar, sheet music, editor, playback bar, mobile layout)
- **persist.cljs** — localStorage I/O + remote data loading (custom tunes, sets, edits, notes, learned, ABC data); schema-validates at the boundary
- **render.cljs** — imperative abc.js sheet-music rendering, driven by a state watch
- **handlers/** — action handlers split by domain: `tune.cljs`, `editor.cljs`, `set.cljs`, `playback.cljs`, `session.cljs`
- **abc_bridge.cljs** — abc.js interop (render SVG, synth play/stop, shared AudioContext)
- **guitar.cljs** — Tone.js Sampler with CDN acoustic guitar samples, strumming patterns per tune type
- **metronome.cljs** — standalone self-correcting metronome click
- **beat_engine.cljc** — shared pure beat math (beats-for-tune, ms-per-beat/bar)
- **backup.cljs** — export/import all user data as EDN
- **gesture.cljs** — mobile touch gestures (swipe-peek, full-swipe delete, swipe-right learned)
- **chords.cljc** — chord suggestion algorithm (bar-level, mode-based, pitch-class weighting)

## Key patterns

### TUI
- **Async operations** use `charm/cmd` — runs fn on thread, delivers result as message to `update-state`
- **Audio pipeline**: fetch ABC → convert MIDI → play. Each step returns a typed message
- **State is a flat map** — all mutation via `update-state` returning `[new-state cmd-or-nil]`
- **Count-in**: optional woodblock click bar before tune playback
- **Set queue**: auto-advance through set tunes on playback finish

### Web
- **Replicant dispatch**: actions are vectors `[[:action/name arg1 arg2]]`, resolved via `postwalk` for `:event/target.value` placeholders
- **Imperative ABC rendering**: abc.js called from state watcher (not during Replicant render) via `requestAnimationFrame`
- **Guitar sync**: always scheduled alongside melody, mute/unmute via gain node toggle
- **abc.js quirks**: `wrap` option causes missing barlines — use ABC newlines for line breaks instead. `chordsOff: true` suppresses piano chord audio. `program: 105` for banjo.
- **Tone.js**: lazy init via `Tone.start()` on first user interaction. CDN samples from tonejs-instruments.

### Deliberate design divergences (web)
Cases where the app intentionally differs from `design.pen`, or a design-token
question was deliberately settled — do not "fix" these to match the mockups.
- **Slim mobile playback bar** — the mobile playback bar uses a 48px play button / `[0,16]` padding, not the design's 64px / 16. Intentional redesign (commit "replace playback bar with slim mobile design").
- **Inline notation editing kept** — the full-screen ABC notation editor (`design.pen` frame `rT1yT`) is rejected. The app keeps the inline `.editor-panel` textarea so edits drive live sheet-music re-renders in place; a separate editor would break that.
- **Full-swipe-left → delete-confirm modal** — a left swipe past the 140px threshold opens the styled delete-confirm modal directly. The design's intermediate "Release to delete" reveal (`qkOww`) is not implemented; the confirm-modal flow is the chosen gesture.
- **Sidebar `gap` = 20** — `design.pen` frames disagree (`d1p1` says 24; `d3p2`/`d4p1`/`d5p1` say 20). The app's 20 matches the majority; the `d1p1` frame's 24 is the design-side bug.
- **Set editor is mobile-only** — the full-screen new/edit set editor (`he1dM`, `:set-editor` slot) clones the mobile-gated `.te-overlay`, so it renders only ≤720px. Desktop deliberately keeps the inline `.set-creation` sidebar wizard (`:creating-set?`). The two set-creation surfaces coexist by design; don't "unify" them onto the overlay without making it responsive first.
- **`.tune-name` default colour `#D4D2CC`** — settled value for the base/unselected tune-row name (active `#F5F4F0`, learned `#A8A8A8`). The earlier `#aaa` was a bug.
- **Session hero card is shared, not mobile-gated** — `session-tab-pre`'s `.session-hero-card` (Wave 1 B, design `J8hkB`) renders in both the 390px mobile layout and the 280px desktop sidebar, because `session-tab` is the single shared session view (no desktop variant). Verified the 56px `heroBig` number doesn't clip the sidebar. Don't gate it to mobile-only — the bold treatment is intentional on both surfaces.
- **Settings list rows take horizontal padding from the card** — the Wave 1 E `.settings-list-row` (export/import, design `ddeLd`) uses `padding: 12px 0`; the `.settings-card` supplies the left/right inset (so the rows and `.settings-list-divider` are content-box-wide, not edge-to-edge). The design puts `padding:14` on each row and zero on the card frame (full-bleed rows + divider). The shared-card-padding reuse is deliberate — it keeps the BACKUP/ABOUT/DATA cards consistent; don't refactor it to full-bleed without restructuring all three cards.

## Data files

### ~/.ceol/
- `cache.edn` — fetched ABC and session IDs (TUI)
- `local-abc.edn` — hand-written ABC notation, keyed by tune ID
- `abc/` — ABC notation files (TUI temp)
- `midi/` — MIDI files: `{id}.mid`, `{id}_a.mid`, `{id}_t10.mid`
- `setlists/*.edn` — named setlists (TUI)
- `soundfont.sf2` — SoundFont for FluidSynth

### Web localStorage keys
- `ceol-abc-edits` — edited ABC per tune (with chord annotations)
- `ceol-custom-tunes` — user-added/edited tune metadata
- `ceol-sets` — set definitions
- `ceol-learned-tunes` — set of learned tune IDs
- `ceol-tune-notes` — practice notes per tune
- `ceol-onboarded` — first-launch coachmark dismissed flag

### Web bundled data (`web/resources/public/data/`)
Fetched at startup by `persist.cljs`; localStorage overrides these.
- `local-abc.edn` — hand-written ABC bodies, keyed by tune ID (see below)
- `default-abc-edits.edn` — seed ABC edits (with chord annotations) merged under any localStorage edits
- `default-sets.edn` — seed set definitions, used when no `ceol-sets` key exists

### local-abc.edn
Hand-written file at `~/.ceol/local-abc.edn`, also copied to `web/resources/public/data/`. Maps tune IDs to ABC body strings (no headers). Both TUI and web read from this.

## Adding tunes

### Via catalog (tunes.cljc)
Add to `catalog` vector: `:id`, `:name`, `:type`, `:time-sig`, `:key`, `:mode-name`, `:session-id`.

### Via web UI
Click "+" in sidebar → edit name/type/key inline in header → open editor to add ABC.

### ABC notation
Add body to `~/.ceol/local-abc.edn` keyed by tune ID. Copy to `web/resources/public/data/local-abc.edn` for web.

## Testing

```bash
# TUI tests (Babashka)
./bb -cp "src:test" -e "(require '[clojure.test :refer [run-tests]]) (require 'ceol.split-test) (run-tests 'ceol.split-test)"

# Web tests (CLJS via shadow-cljs)
cd web && ./node_modules/.bin/shadow-cljs compile test

# Web tests (pure .cljc via Babashka)
./bb -cp "src:web/src:test" -e "(require '[clojure.test :refer [run-tests]]) (require 'ceol.web.abc-test 'ceol.web.chords-test) (run-tests 'ceol.web.abc-test 'ceol.web.chords-test)"
```

80 tests, 5260 assertions (cljs total — most are generative) across chords, ABC, state, sets, beat engine, session, actions, backup, and the mobile tune-editor / duplicate / set-editor / session-summary.

### Browser end-to-end verification

For mobile flows (B-items in `web/build-backlog.md`), use the CDP harness at
`web/scripts/verify/`. Drives headless Chrome via DevTools Protocol — asserts
cljs state + screenshots at 390×844.

```bash
# Terminal 1: shadow-cljs watch app
# Terminal 2:
cd web/scripts
./verify.sh b1   # reference scenario: mobile tune-details editor
```

See `web/scripts/verify/README.md` for the helper API and gotchas
(coachmark, `<select value>` quirk, settle timing).

## Dependencies

### TUI
- Babashka (bb), abc2midi, fluidsynth, .sf2 soundfont

### Web
- Node.js, npm (shadow-cljs, abcjs, tone)
- Clojars: Replicant `2025.12.1`

## Project structure

```
ceol/
  src/ceol/
    notation.cljc   — ABC parser (shared)
    tunes.cljc      — tune catalog (shared)
    abc.cljc        — ABC utils (shared)
    audio.clj       — TUI: API, MIDI, playback
    state.clj       — TUI: state management
    view.clj        — TUI: rendering
    staff.clj       — TUI: staff notation
    data.clj        — TUI: file I/O, caching
    core.clj        — TUI: entry point
  web/
    shadow-cljs.edn
    package.json
    resources/public/
      index.html
      css/style.css       — entry; imports css/modules/*
      css/modules/        — split CSS modules
      data/local-abc.edn        — hand-written ABC bodies
      data/default-abc-edits.edn — seed ABC edits
      data/default-sets.edn      — seed sets
    src/ceol/web/
      core.cljs       — entry, init, keyboard shortcuts, dispatch router
      state.cljs      — app state, queries, schemas
      views.cljs      — UI components (desktop + mobile)
      persist.cljs    — localStorage I/O + remote data loading
      render.cljs     — imperative abc.js sheet rendering
      abc_bridge.cljs — abc.js interop
      guitar.cljs     — Tone.js guitar
      metronome.cljs  — standalone metronome click
      beat_engine.cljc — shared beat math
      backup.cljs     — export/import user data
      gesture.cljs    — mobile touch gestures
      chords.cljc     — chord algorithm
      handlers/
        tune.cljs            — tune CRUD + mobile tune-editor wrappers
        tune_editor.cljc     — pure helpers behind mobile tune-editor
        editor.cljs          — ABC editor + inline-field actions
        set.cljs             — set actions + mobile set-editor wrappers
        set_editor.cljc      — pure helpers behind mobile set editor
        playback.cljs        — play/stop orchestration
        session.cljs         — practice-session actions
        session_summary.cljc — pure session-complete summary helpers
    scripts/
      cdp.mjs                — CDP harness (Node WebSocket → headless Chrome)
      verify.sh              — chrome launcher + scenario runner
      verify/
        README.md            — verification process doc
        b1.mjs               — reference scenario (mobile tune-details editor)
  test/
    ceol/
      split_test.clj  — ABC splitting tests
      web/
        abc_test.cljc        — ABC processing tests
        chords_test.cljc     — chord algorithm tests
        state_test.cljc      — state query tests
        sets_test.cljc       — set logic tests
        beat_engine_test.cljc — beat math tests
        session_test.cljc    — session logic tests
        actions_test.cljc    — action dispatch tests
        tune_editor_test.cljc — mobile tune-editor draft helpers
        set_editor_test.cljc  — mobile set-editor draft + reorder helpers
        session_summary_test.cljc — session-complete count/format helpers
        generative_test.cljc — generative/property tests
        backup_test.cljs     — backup export/import tests
        runner.cljs          — CLJS test runner
  local-abc.edn        — hand-written ABC (also at ~/.ceol/)
  design.pen            — UI mockups (Pencil)
```

## Agent skills

### Issue tracker

Issues tracked as GitHub issues (`gh` CLI), repo `Shakey-Bridge-Software/ceol`. See `docs/agents/issue-tracker.md`.

### Triage labels

Default canonical labels (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `CONTEXT.md` + `docs/adr/` at the repo root (created lazily by `/domain-modeling`). See `docs/agents/domain.md`.
