# ceol

Irish traditional music practice tool. Learn tunes, build sets, play with rhythm guitar accompaniment.

Two interfaces: a **terminal player** (TUI) and a **web app** with sheet music rendering.

## Features

### TUI (terminal)
- Browse a catalog of 54 polkas, jigs, reels, hornpipes, and slip jigs
- Fetch ABC notation from thesession.org automatically
- Play MIDI via FluidSynth with a SoundFont
- Loop whole tunes or individual A/B sections
- Tempo adjust +/-40 BPM in 5 BPM increments
- Staff notation display in terminal (Unicode)
- Count-in click before playback
- Setlists with auto-advance through sets

### Web
- Sheet music rendering via abc.js (SVG)
- Melody playback (banjo) with abc.js synth
- Acoustic rhythm guitar via Tone.js (sampled)
- Chord annotations above the stave (algorithmically suggested, editable)
- Live ABC notation editor with instant preview
- A/B section selection, loop, tempo control
- Sets: group tunes, play through in order, loop the set
- Add/edit tunes from the UI (inline editing, no forms)
- Keyboard shortcuts for all controls
- Local-first: all data in localStorage, no login

## Quick start

### TUI

```bash
# Install dependencies (macOS)
brew install abcmidi fluid-synth

# Place a SoundFont at ~/.ceol/soundfont.sf2

# Run
./run.sh
```

### Web

```bash
cd web
npm install
./node_modules/.bin/shadow-cljs watch app
# Open http://localhost:8280
```

## TUI keybindings

| Key | Action |
|-----|--------|
| `j/k` or arrows | Navigate |
| `Enter/Space` | Play/stop |
| `s` | Stop |
| `p` | Prepare (fetch + convert) |
| `f` | Cycle type filter |
| `l` | Toggle loop |
| `=/-` | Tempo +5/-5 BPM |
| `0` | Reset tempo |
| `1/2` | Section A/B |
| `m` | Toggle staff notation |
| `c` | Toggle count-in |
| `S` | Cycle setlist |
| `g` | Play full set |
| `?` | Help |
| `q` | Quit |

## Web keyboard shortcuts

| Key | Action |
|-----|--------|
| `Space` | Play/stop |
| `l` | Toggle loop |
| `g` | Toggle guitar |
| `e` | Toggle ABC editor |
| `m` | Toggle metronome |
| `c` | Toggle count-in |
| `n` | Toggle notes drawer |
| `k` | Toggle learned flag (selected tune) |
| `=/-` | Tempo +5/-5 BPM |
| `0` | Reset tempo |
| `1/2/3` | Section A/B/All |
| arrows | Navigate tune list |

## ABC notation

Tunes are written in ABC notation in `~/.ceol/local-abc.edn`. The file maps tune IDs (integers) to ABC body strings:

```clojure
{1 "|:GDG3/2A/2|Bee2|dBGA|BAA2:|||:gf#ed|Bee2|dBGA|BAA2:||"
 2 "|:fABA|fABA|d2e>f|edBA:|||:faf>e|edBA|d2e>f|edBA:||"}
```

The web app also reads this file from `web/resources/public/data/local-abc.edn`.

## How it works

1. Tunes are defined in `src/ceol/tunes.cljc` with metadata (name, type, key, mode, time signature)
2. ABC notation lives in `local-abc.edn` (hand-written) or is fetched from thesession.org (TUI only)
3. **TUI**: ABC is converted to MIDI via `abc2midi`, played via FluidSynth
4. **Web**: ABC is rendered as SVG sheet music by abc.js, played by abc.js synth (melody) + Tone.js (guitar)
5. Chord symbols are suggested algorithmically from the melody + key/mode, then injected into the ABC as inline annotations (`"G"`, `"Am"`)
6. Everything is editable: tune metadata (click to cycle), ABC notation (live editor), chords (in the ABC text)

## Dependencies

### TUI
- [Babashka](https://github.com/babashka/babashka)
- [abc2midi](https://ifdo.ca/~seymour/runabc/top.html)
- [FluidSynth](https://www.fluidsynth.org/)
- A SoundFont (`.sf2`)

### Web
- Node.js + npm
- [shadow-cljs](https://shadow-cljs.github.io/docs/UsersGuide.html)
- [abcjs](https://www.abcjs.net/) (sheet music rendering + synth)
- [Tone.js](https://tonejs.github.io/) (guitar samples)
- [Replicant](https://github.com/cjohansen/replicant) (UI framework)

## Testing

```bash
# All web tests (CLJS)
cd web && ./node_modules/.bin/shadow-cljs compile test

# Pure logic tests (Babashka, faster)
bb -cp "src:web/src:test" -e "..."
```

68 tests, 5207 assertions (most are generative) covering chord algorithm, ABC processing, state queries, set logic, beat engine, session logic, actions, backup round-trip, and the mobile tune-editor / duplicate.

## Project structure

```
src/ceol/
  notation.cljc  — ABC parser (shared)
  tunes.cljc     — tune catalog (shared)
  abc.cljc       — ABC string utils (shared)
  state.clj      — TUI state management
  view.clj       — TUI rendering
  audio.clj      — TUI: API, MIDI, playback
  staff.clj      — TUI: staff notation
  data.clj       — TUI: file I/O
  core.clj       — TUI: entry point

web/src/ceol/web/
  core.cljs       — Web: entry, init, keyboard shortcuts, dispatch router
  state.cljs      — Web: app state, queries, schemas
  views.cljs      — Web: UI components (desktop + mobile)
  persist.cljs    — Web: localStorage I/O + remote data loading
  render.cljs     — Web: imperative abc.js sheet rendering
  abc_bridge.cljs — Web: abc.js interop
  guitar.cljs     — Web: Tone.js guitar
  metronome.cljs  — Web: standalone metronome click
  beat_engine.cljc — Web: shared beat math
  backup.cljs     — Web: export/import user data
  gesture.cljs    — Web: mobile touch gestures
  chords.cljc     — Web: chord algorithm
  handlers/       — Web: action handlers (tune, editor, set, playback, session)

test/ceol/web/   — Tests (.cljc + .cljs)
design.pen       — UI mockups
local-abc.edn    — ABC notation data
```
