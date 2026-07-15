# Context — ceol

Ubiquitous language for the ceol domain. When code, issues, briefs, tests, or
commits name a concept below, use the term as defined here — don't drift to
synonyms. Add a term when a real distinction is settled (e.g. during triage /
grilling); don't pre-populate speculatively.

See `docs/agents/domain.md` for how the engineering skills consume this file.

## Glossary

- **Tune** — a single piece of Irish traditional music, identified by `:id`, with
  a `:type`, `:key`, `:mode-name`, `:time-sig`, and an ABC body. Sourced from the
  static catalog (`tunes.cljc`) or user-added.

- **Type** — the tune form: reel, jig, slip-jig, hornpipe, polka, slide, etc.
  Drives default tempo, beat math, and the guitar strum pattern.

- **Section** — a named structural part of a tune: `:a`, `:b`, or `nil` (= "All").
  Tied to tune structure. Playback and the sheet can be scoped to a section.
  Selected via the A / B / All controls.

- **Part** — a musical repeat unit of a tune (the "A part", "B part"). Currently
  hardcoded to A/B; auto-detecting all parts is future work (#42). Not to be
  conflated with **Section** (the current playback scope) though they align today.

- **Pickup / anacrusis** — the partial lead-in bar before a tune's first full
  bar. Its duration must offset accompaniment timing (see #11); it is not a full
  bar even though it contains notes.

- **Loop** — repeat the current playback scope continuously. Today the scope is a
  whole tune or a **Section**; the `:loop?` flag drives it.

- **Loop range** (a.k.a. **practice range**) — an *arbitrary* contiguous span of
  **whole bars** selected by the user (click-drag on the sheet music), independent
  of tune structure and **Section** boundaries. It is an overriding loop mode:
  while a loop range is active it supersedes A/B/All and playback loops that span
  continuously. Distinct from **Section** loop (structural) and from **Part**
  detection (#42). Ephemeral — not persisted. Introduced by #44.

- **Set** — an ordered collection of tunes played back-to-back, with count-in
  between items and short transition gaps. User-defined.

- **Session** (practice session) — a guided run through a queue of tunes/sets with
  count-in between items, producing a completion summary.

- **Count-in** — a bar of metronome clicks played before a tune starts, to
  establish tempo. Optional (`:count-in?`). For a **Loop range**, it plays once at
  loop start, not on every repeat (#44).

- **Beat params** — the derived per-tune timing bundle (`beats-for-tune`):
  `ms-per-bar`, beats-per-bar, etc., honouring the current tempo offset. The single
  source of truth for melody, guitar, and metronome scheduling.
