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
- **core.cljs** — entry point, event dispatch (`handle-action!`), ABC rendering, playback orchestration, localStorage persistence
- **state.cljs** — app-state atom, query functions, set/tune data model
- **views.cljs** — Replicant hiccup components (sidebar, sheet music, editor, playback bar)
- **abc_bridge.cljs** — abc.js interop (render SVG, synth play/stop, shared AudioContext)
- **guitar.cljs** — Tone.js Sampler with CDN acoustic guitar samples, strumming patterns per tune type
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

22 tests, 118 assertions across chords, ABC processing, state, and sets.

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
      css/style.css
      data/local-abc.edn
    src/ceol/web/
      core.cljs       — entry, dispatch, persistence
      state.cljs      — app state, queries
      views.cljs      — UI components
      abc_bridge.cljs — abc.js interop
      guitar.cljs     — Tone.js guitar
      chords.cljc     — chord algorithm
  test/
    ceol/
      split_test.clj  — ABC splitting tests
      web/
        abc_test.cljc    — ABC processing tests
        chords_test.cljc — chord algorithm tests
        state_test.cljc  — state query tests
        sets_test.cljc   — set logic tests
        runner.cljs      — CLJS test runner
  local-abc.edn        — hand-written ABC (also at ~/.ceol/)
  design.pen            — UI mockups (Pencil)
```
