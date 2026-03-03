# CLAUDE.md

## Project overview

ceol is a terminal-based Irish traditional music player built with Babashka and [charm.clj](https://github.com/timokramer/charm.clj) (an Elm-architecture TUI framework). It fetches ABC notation from thesession.org, converts to MIDI, and plays via FluidSynth.

## Running

```bash
./run.sh          # run from source
./install.sh      # install system-wide
```

## Architecture

Elm architecture via charm.clj: `init → update → view` loop.

- **state.clj** — `init-state`, `update-state` (message handler), all key handlers and audio pipeline orchestration
- **view.clj** — pure rendering functions, no side effects
- **audio.clj** — async commands via `charm/cmd` for fetch, convert, play. Also contains ABC processing (tempo adjustment, section splitting)
- **data.clj** — file paths, cache read/write, soundfont discovery
- **tunes.clj** — static catalog, type filters, status icons
- **core.clj** — entry point, wires init/update/view into charm/run

## Key patterns

- **Async operations** use `charm/cmd` which runs a fn on a thread and delivers the result as a message to `update-state`
- **Audio pipeline**: fetch ABC → convert MIDI → play. Each step is a `charm/cmd` that returns a typed message (`:abc-fetched`, `:midi-ready`, `:playback-started`, etc.)
- **State is a flat map** — no nested atoms or refs. All mutation goes through `update-state` returning `[new-state cmd-or-nil]`
- **Proc identity matching** — `:playback-finished` messages include the Java Process object to distinguish stale finishes from current ones (prevents double-play during reconversion)

## MIDI file naming

Base: `~/.ceol/midi/{id}.mid`
With section: `{id}_a.mid`, `{id}_b.mid`
With tempo offset: `{id}_t10.mid`, `{id}_t-5.mid`
Combined: `{id}_a_t10.mid`

Only base paths are persisted in `cache.edn`. Tempo/section variants are ephemeral.

## Adding tunes

Add entries to the `catalog` vector in `tunes.clj`. Each tune needs:
- `:id` (unique integer)
- `:name`, `:type` (`:polka`, `:jig`, `:reel`, `:hornpipe`, `:slip-jig`, `:other`)
- `:time-sig`, `:key`, `:mode-name` (`"Ionian"`, `"Dorian"`, `"Aeolian"`)
- `:session-id` (thesession.org tune ID — find at `thesession.org/tunes/{id}`)

## Dependencies

- Babashka (bb) — Clojure scripting runtime
- abc2midi — ABC to MIDI conversion
- fluidsynth — MIDI playback
- A .sf2 soundfont file

## Testing

No test suite yet. Manual testing via `./run.sh`:
1. Navigate, prepare, play a tune
2. Test loop (`l`), tempo (`=`/`-`/`0`), sections (`1`/`2`)
3. Verify clean exit (`q`) with no orphan fluidsynth processes
