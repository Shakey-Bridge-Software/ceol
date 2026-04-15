# ceol Roadmap

## Completed
- [x] Sheet music rendering (abc.js SVG)
- [x] Melody playback (banjo, program 105)
- [x] Chord algorithm + injection (bar-level, mode-based)
- [x] ABC text editor with live preview + auto-save
- [x] A/B section selection, loop, play/stop
- [x] Acoustic rhythm guitar (Tone.js sampled, CDN)
- [x] Guitar mute/unmute toggle (during playback)
- [x] Sets: creation (inline typeahead), expand/collapse, playback with auto-advance, loop whole set
- [x] Add/edit tunes via UI (+ button, inline header editing, click-to-cycle metadata)
- [x] Tempo controls (+/- 5 BPM, reset, double-click to reset)
- [x] Keyboard shortcuts (space, l, g, e, =, -, 0, 1/2/3, arrows)
- [x] Tests: 22 tests, 118 assertions (chords, ABC, state, sets)
- [x] Code review + fixes (parse-long portability, guitar timeout, watch dedup, fetch error handling)
- [x] TUI: count-in, setlists, set queue playback, local ABC, orphan cleanup
- [x] Documentation (CLAUDE.md, README.md, ROADMAP.md)

## Features — next up

### Session mode
Simulates a real trad session. Shuffle learned tunes, no lookahead.
- [ ] `:learned?` boolean flag on tunes (toggle in UI, persist to localStorage)
- [ ] Visual indicator on learned tunes in sidebar (checkmark or dot)
- [ ] Session mode toggle button
- [ ] Shuffle all learned tunes/sets into random queue
- [ ] Count-in before each tune
- [ ] Configurable pause between tunes (2-3 seconds)
- [ ] No peeking — UI doesn't show what's next until it starts
- [ ] Auto-stop after all tunes played (or loop the session)

### Melody mute
Practice rhythm guitar by silencing the melody.
- [ ] Melody on/off toggle button (separate from guitar)
- [ ] Gain node on abc.js synth output for mute/unmute
- [ ] Works during playback (instant toggle)

### Guitar volume
Separate volume control for guitar track.
- [ ] Volume slider or +/- buttons for guitar gain
- [ ] Independent from melody volume (melody = system volume)
- [ ] Persist preference to localStorage

### Melody instrument picker
Choose the melody sound.
- [ ] Cycle through instruments: banjo (105), fiddle (40), flute (73), piano (0), whistle (78)
- [ ] Button or dropdown in playback bar
- [ ] Persist preference to localStorage

### Count-in click (web)
Already in TUI, bring to web. Shares beat-timing infrastructure with metronome (see below).
- [ ] Woodblock or click sound before tune starts
- [ ] Respects time signature and tempo
- [ ] Toggle on/off (keyboard shortcut: `c`)

### Half-speed quick button
One-tap slow practice alongside existing tempo controls.
- [ ] Button that toggles between current tempo and 50% speed
- [ ] Keyboard shortcut (e.g. `h`)
- [ ] Visual indicator when half-speed is active

### Print-friendly sheet music
Clean layout for printing and bringing to sessions.
- [ ] Print stylesheet — hide sidebar, playback bar, editor
- [ ] Single tune: full page, large staves, chord names, tune header
- [ ] Full set: all tunes in order with set name header
- [ ] Ctrl+P / print button

### Tune incipit index
Compact visual reference showing first 1-2 bars per tune. **Implementation note:** 54+ tunes means 54+ abc.js render calls — render lazily (IntersectionObserver, render only when scrolled into view) to avoid blocking the UI.
- [ ] Grid or list view with small abc.js renders
- [ ] Lazy rendering on scroll into viewport
- [ ] Sortable by name and type
- [ ] Quick navigation — click incipit to select tune

### Metronome
Standalone click, independent of playback. **Implementation note:** shares beat-timing infrastructure with count-in — extract a common `beat-engine` module (BPM, time-sig, beat scheduling, accent patterns) used by both.
- [ ] Configurable BPM
- [ ] Time signature aware (accent on beat 1)
- [ ] Visual beat indicator
- [ ] Can run alongside playback or solo

## Polish

- [ ] Seamless transitions between set tunes (minimal gap on auto-advance)
- [ ] Responsive layout (mobile/tablet — sidebar collapse, touch-friendly)
- [ ] Hardcoded CSS colors → CSS variables (unblocks theming, print vs screen)
- [ ] Accessibility audit (focus management, ARIA labels, screen reader)
- [ ] Delete tune confirmation dialog
- [ ] Editor panel drag handle (low priority)
- [ ] Tune list scroll position preservation on filter change
- [ ] Loading state for guitar samples (first play delay)

## Known bugs

- **Guitar drifts out of sync with melody** — guitar uses setTimeout scheduling which drifts from abc.js Web Audio clock over time. Same root cause as the metronome drift. Fix: use Web Audio scheduling or self-correcting clock for guitar too.
- **Guitar doesn't start with melody after count-in** — when count-in is enabled, guitar/play! is called at the same time as abc-bridge/start! but guitar scheduling starts from time 0 immediately, while the melody may have slight startup latency. Fix: ensure guitar starts in the same callback as abc-bridge/start!.

## Future ideas

- Hosting/deployment (static site, GitHub Pages or similar)
- Share sets/tunes via URL or export
- Chord quality improvements (more sophisticated algorithm, manual library)
- Mobile app (PWA)
- Sync between devices (optional backend)
- Recording — record yourself playing along
- Tune difficulty rating
- Practice log / stats tracking
