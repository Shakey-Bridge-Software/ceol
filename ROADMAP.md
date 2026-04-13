# ceol-web Roadmap

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
- [x] Documentation (CLAUDE.md, README.md)

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

## Polish

- [ ] Decrease gap between set tunes (seamless auto-advance, minimal silence)
- [ ] Editor panel resize (draggable split handle)
- [ ] Responsive layout (mobile-friendly sidebar collapse)
- [ ] Accessibility audit (focus management, ARIA labels, screen reader)
- [ ] Delete tune confirmation dialog
- [ ] Hardcoded CSS colors → CSS variables
- [ ] Tune list scroll position preservation on filter change
- [ ] Loading state for guitar samples (first play delay)

## Known bugs

_(None currently tracked — add bugs here as discovered)_

## Future ideas

- Hosting/deployment (static site, GitHub Pages or similar)
- Share sets/tunes via URL or export
- Chord quality improvements (more sophisticated algorithm, manual library)
- Print/export sheet music to PDF
- Mobile app (React Native or PWA)
- Sync between devices (optional backend)
- Metronome / click track
- Recording — record yourself playing along
- Tune difficulty rating
- Practice log / stats tracking
