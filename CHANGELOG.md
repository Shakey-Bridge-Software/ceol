# Changelog

All notable changes to ceol are documented here.

## [0.4.0] - 2026-08-02

This release focuses on the **mobile experience** (still WIP but usable), **sessions**, and **backup/data hardening**.

### Mobile (work in progress)
- New mobile shell: top bar + bottom sheet, full-width tune list with FAB, and a detail view with full-bleed sheet music + fixed playback bar — a work-in-progress redesign; usable but several flows are still being polished
- Action-sheet bottom modals replace inline menus; swipe-left to peek/delete, right-swipe to mark learned
- Full-screen New/Edit set editor and set cards; dedicated mobile Session + Settings layouts; landscape support + first-launch coachmark

### Sessions
- Session-ready hero card and live "now playing" detail with transport
- Session-complete summary view ("Practice again" / done state)
- Shared session-advance state machine; inter-tune gap timer cancellation on skip/pause/stop

### Settings & data
- Export/Import as Settings rows; mobile backup-status banner toast
- Generic confirm modal for destructive actions (delete tune/set)

### Fixes & hardening
- Tempo source-of-truth unified into a single beat-engine table; metronome now syncs to the melody beat grid
- Beat engine guards against malformed time signatures
- Backup import replaces `:tunes` wholesale + migrates pre-unification backups; full catalog preserved for existing users
- Catalog tunes correctly uneditable / non-deletable; per-tune data scrubbed on delete; fixed next-tune-id reuse
- Sheet music refreshes on any change that affects it

[0.4.0]: https://github.com/Shakey-Bridge-Software/ceol/releases/tag/v0.4.0
