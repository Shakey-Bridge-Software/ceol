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
- [x] Metronome: standalone click (self-correcting clock), stops when playback starts
- [x] Count-in: pre-primed abc.js synth, time-sig aware taps (4 taps for short bars, 1 bar for others), accent on downbeats
- [x] Beat engine: shared pure beat math module (beats-for-tune, ms-per-beat/bar)
- [x] Editor Esc to blur (restore keyboard shortcuts), auto-focus on open
- [x] Session mode: learned flag (k hotkey), queue building (sets as units when all tunes learned), shuffle, count-in between items, 2s pause, no lookahead, third sidebar tab
- [x] Session UI: clean read-only main panel (no controls), progress bar, NOW PLAYING, PLAYED history
- [x] Promise-based render sync: render-abc! synchronous, render-sheet-music! creates promise, wait-for-render! for session advance
- [x] Tests: 43 tests, 183 assertions (chords, ABC, state, sets, beat engine, session)

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

## Polish

- [ ] Seamless transitions between set tunes (minimal gap on auto-advance)
- [ ] Responsive layout (mobile/tablet — sidebar collapse, touch-friendly)
- [ ] Hardcoded CSS colors → CSS variables (unblocks theming, print vs screen)
- [ ] Accessibility audit (focus management, ARIA labels, screen reader)
- [ ] Delete tune confirmation dialog
- [ ] Editor panel drag handle (low priority)
- [ ] Tune list scroll position preservation on filter change
- [ ] Loading state for guitar samples (first play delay)
- [ ] Help button / hotkey reference overlay (question mark icon)
- [ ] Playback bar layout stability — BPM shifts when loop/guitar toggled, count-in/metro buttons shift when guitar toggled. Fix width of playback bar zones so controls don't move.
- [ ] Set rows in sidebar need more vertical breathing room — bottoms of rows are being cut off

## Known bugs

- **Guitar drifts out of sync with melody** — guitar uses setTimeout scheduling which drifts from abc.js Web Audio clock over time. Same root cause as the metronome drift. Fix: use Web Audio scheduling (e.g. `AudioContext.currentTime` + pre-scheduled `AudioBufferSourceNode.start(time)`) or the self-correcting `performance.now()` clock pattern used in the metronome. Both bugs share the same underlying issue: setTimeout is driven by the JS event loop, abc.js synth is driven by the Web Audio hardware clock — they diverge over time.
- **Guitar doesn't start with melody after count-in** — when count-in is enabled, `guitar/play!` is called at the same time as `abc-bridge/start!` but guitar scheduling starts from time 0 immediately via setTimeout, while abc.js `.start()` goes through the Web Audio pipeline. Fix: ensure guitar timing starts from the same reference point as the melody. Could pre-schedule guitar notes relative to `AudioContext.currentTime` rather than setTimeout from "now".
- **Guitar strums over the lead-in bar** — tunes with a pickup/anacrusis (e.g. 2-beat lead-in in 4/4) get guitar accompaniment on the pickup instead of waiting for the first downbeat of the first full bar. Could be either: (a) lead-in not correctly denoted in ABC (user-authored content may not use a partial-length first bar), or (b) guitar scheduler treats bar 1 as a full bar regardless. Investigate both: how existing tunes encode the pickup, and whether `guitar/play!` offsets by the pickup length.
- **Deleted custom tune leaves greyed-out sheet music behind** — after `:tune/delete`, the empty-state text "Select a tune to view sheet music" shows alongside the previously-rendered SVG. abc.js writes the SVG into `#sheet-music` outside Replicant's vdom, so when Replicant swaps `[:div#sheet-music]` → `[:div.sheet-empty]` the orphan SVG survives. Fix: in `render-sheet-music!` (`web/src/ceol/web/core.cljs:203`), add an else branch that clears `#sheet-music` innerHTML when no tune is selected. Optionally add `:replicant/key` to force a clean remount.
- None of the melody/metronome/guitar seem to care about while-playing BPM changes
- If you turn the metronome on while the melody is playing, the metronome doesn't sync up with the beat
- work on the triplets, visually confusing

## Future ideas

- Hosting/deployment (static site, GitHub Pages or similar)
- Share sets/tunes via URL or export
- Chord quality improvements (more sophisticated algorithm, manual library)
- Mobile app (PWA)
- Sync between devices (optional backend)
- Recording — record yourself playing along
- Tune difficulty rating
- Practice log / stats tracking
- mobile friendly version
- version number & changelog
- multiple devices
- import/export
- transpose keys
- hotkey / feature helper
- tutorial mode
