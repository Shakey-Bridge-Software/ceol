# 0001 — Tempo source of truth: one table in the beat-engine

- Status: accepted
- Date: 2026-07-16
- Issue: #53 (fixes the melody/accompaniment desync); prefactor #52

## Context

Tempo lived in two independent per-type tables:

- `ceol.abc/tempo-for-type` produced the ABC `Q:` field that drives the **melody**
  (banjo synth on the web, abc2midi on the TUI).
- `ceol.beat-engine/beats-for-tune` produced the BPM and beats-per-bar that drive
  the **accompaniment** (guitar, synced metronome, count-in).

For the named types they happened to hold the same BPM, so nobody noticed. But
they fell back differently for unmapped types (`:other` — ~13 catalog tunes —
plus `:mazourka` and anything else): the melody used a time-sig-aware tempo
(often 100 BPM) while the beat-engine used a fixed `default-params` of 120 BPM /
4 beats-per-bar. Whenever accompaniment was on, the guitar and metronome ran
~20% off the melody and in the wrong meter.

The #13 metronome-sync work silently depended on these two tables agreeing. That
agreement was accidental and unguarded.

## Decision

`ceol.beat-engine` is the single source of truth for tempo. It owns one per-type
table of `{:bpm :beat-unit}` and everything else derives from it:

- **beats-per-bar = time-sig ÷ beat-unit** (e.g. 6/8 ÷ 3/8 = 2, 4/4 ÷ 1/4 = 4).
  A type's meter follows its actual time signature rather than a second
  hard-coded number.
- **ms-per-beat / ms-per-bar** derive from the effective BPM.
- **One `clamp-bpm`** (minimum 40) shared by the scheduler and the melody's
  `adjust-abc-tempo`.
- **`ceol.abc/tempo-for-type` becomes a downstream formatter** — it renders the
  `Q:` string from the same `tempo-params` table. Its signature is unchanged, so
  every existing caller (TUI, melody build, BPM display) is untouched and every
  tune's `Q:` stays byte-identical to the #52 characterization golden.

**beat-unit is modelled as a string** (`"1/4"`, `"3/8"`) rather than a Clojure
ratio, so the one literal works in both Clojure (TUI) and ClojureScript (web).

**Fallback is melody-authoritative.** For types not in the table, the melody's
historical time-sig-aware tempo is the authority, and the accompaniment now
matches it (replacing the removed `default-params`):

| time-sig    | bpm | beat-unit |
|-------------|-----|-----------|
| 6/8, 9/8    | 100 | 3/8       |
| 3/4         | 120 | 1/4       |
| everything else / nil | 100 | 1/4 |

The melody/accompaniment agreement is now pinned by an exhaustive invariant test
(`ceol.web.tempo-invariant-test`) over type × time-sig × offset: melody `Q:` BPM
equals the scheduler's effective BPM, and melody meter equals the derived
beats-per-bar.

## Consequences

- The `:other`/`:mazourka`/unmapped desync is fixed: accompaniment BPM and meter
  now match the melody. These new accompaniment values (e.g. `:other` 4/4 →
  100 BPM instead of 120) are pinned in `beat-engine-test`.
- No tune sounds different on the melody side; TUI MIDI tempo is unchanged
  (byte-identical `Q:`).
- Adding or retuning a tune type is a one-line edit to a single table.
- The invariant can no longer drift apart silently — a change that breaks the
  agreement fails the invariant test.
