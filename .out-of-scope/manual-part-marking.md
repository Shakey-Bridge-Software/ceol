# Manual Part Marking

ceol does not provide a dedicated feature for users to *manually mark and name*
the parts of a tune (e.g. hand-defining "Part A", "Part B", "Part C" boundaries
and labels that persist per tune).

## Why this is out of scope

The underlying user need — **"zoom in and practice a specific part"** — is met by
two other features, so a separate manual-marking surface would be redundant:

- **Arbitrary loop range** (#44) — the user click-drags over any contiguous span
  of bars on the sheet music and loops exactly that span, independent of tune
  structure. This directly serves the "drill a specific part" motivation for tunes
  with any number of parts, including non-ITM pieces with many sections, without
  requiring the user to pre-define named parts. It is more flexible than fixed part
  boundaries because the span is arbitrary.
- **Auto-detect all parts** (#42) — removes the hardcoded A/B assumption and derives
  the actual part count from the tune, covering the "tunes in 3 or 4 parts" case
  automatically.

Between an arbitrary practice range and automatic multi-part detection, a third
system for manually drawing and labelling persistent part boundaries adds UI and
state (per-tune stored labels, a marking gesture that overlaps #44's drag) for
little additional value. Today the tune model only splits into A/B
(`split-abc-parts` in `ceol.abc`); the chosen direction is to grow the *automatic*
detection (#42) and the *arbitrary* practice range (#44) rather than a manual
part-editor.

If a concrete need for **named, persisted** parts emerges that #42 + #44 genuinely
can't cover, delete this file and re-triage — the concept isn't forbidden, just not
justified as a distinct feature at present.

## Prior requests

- #4 — "Suggestion: Ability to mark different parts" (Reefersleep)
