# ceol

Terminal-based Irish traditional music player. Browse a curated setlist, fetch ABC notation from [thesession.org](https://thesession.org), convert to MIDI, and play back with FluidSynth — all from the keyboard.

Built for learning tunes by ear alongside sheet music.

## Features

- **Browse** a catalog of polkas, jigs, reels, hornpipes, and slip jigs
- **Fetch** ABC notation from thesession.org automatically
- **Play** MIDI via FluidSynth with a SoundFont of your choice
- **Loop** whole tunes or individual A/B sections
- **Tempo** adjust ±40 BPM in 5 BPM increments
- **Sections** isolate part A or B for focused practice

## Dependencies

- [Babashka](https://github.com/babashka/babashka) (bundled as `bb` symlink, or install separately)
- [abc2midi](https://ifdo.ca/~seymour/runabc/top.html) — converts ABC notation to MIDI
- [FluidSynth](https://www.fluidsynth.org/) — MIDI playback
- A SoundFont (`.sf2`) file — place at `~/.ceol/soundfont.sf2` or install one via Homebrew

```bash
# macOS
brew install abcmidi fluid-synth
```

## Usage

```bash
./run.sh
```

Or install system-wide:

```bash
./install.sh
ceol
```

## Keybindings

| Key | Action |
|-----|--------|
| `j/k` | Navigate up/down |
| `Enter/Space` | Play or stop tune |
| `s` | Stop playback |
| `p` | Prepare (fetch + convert) |
| `f` | Cycle type filter |
| `l` | Toggle loop |
| `=` | Tempo +5 BPM |
| `-` | Tempo -5 BPM |
| `0` | Reset tempo to default |
| `1` | Toggle section A |
| `2` | Toggle section B |
| `?` | Help overlay |
| `q` | Quit |

## How it works

1. Tunes are defined in `src/ceol/tunes.clj` with metadata (name, type, key, time signature, thesession.org ID)
2. On first play/prepare, ABC notation is fetched from thesession.org and cached in `~/.ceol/`
3. ABC is converted to MIDI via `abc2midi` with appropriate tempo for the tune type
4. MIDI is played via `fluidsynth` in non-interactive mode
5. Tempo adjustment modifies the Q: field in ABC before re-converting
6. Section splitting finds the `:|...|:` boundary in the ABC body

## Project structure

```
src/ceol/
  core.clj   — entry point, charm/run loop
  state.clj  — state management, key handlers, audio pipeline
  view.clj   — TUI rendering (header, tune list, status bar, help)
  audio.clj  — thesession.org API, ABC processing, MIDI conversion, playback
  data.clj   — file paths, caching, soundfont discovery
  tunes.clj  — tune catalog and helpers
```

## Data directory

`~/.ceol/` stores:
- `cache.edn` — fetched ABC and session IDs
- `abc/` — ABC notation files
- `midi/` — converted MIDI files (including tempo/section variants)
