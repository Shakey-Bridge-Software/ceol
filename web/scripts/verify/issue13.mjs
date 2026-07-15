// Scenario: issue #13 — metronome locks to the melody's beat grid.
//
// Run via:  ./verify.sh issue13   (from web/scripts/)
//
// Audio alignment itself can't be heard headless (that stays a human check).
// What this asserts is the *wiring* the fix introduces, end-to-end in the real
// app: the melody phase is persisted on anchor, re-anchors on replay, the
// metronome toggle routes to synced-vs-standalone by playback state, and stop
// clears the phase. The numeric beat-time math is covered by the pure
// beat-engine unit tests.

import {
  evalJs, dispatch, navigate, shot, sleep, kwExpr, getInExpr, shutdown,
} from "../cdp.mjs";

const OUT = process.env.SCENARIO_OUT ?? "verify/out/issue13";

function assert(cond, msg) {
  if (!cond) { console.error(`✗ ${msg}`); shutdown(); process.exit(2); }
  console.log(`✓ ${msg}`);
}

// swap! assoc a single kw→value into app-state
const setKey = (kw, valueExpr) => evalJs(
  `cljs.core.swap_BANG_.call(null, ceol.web.state.app_state, cljs.core.assoc,` +
  ` cljs.core.keyword.call(null, ${JSON.stringify(kw)}), ${valueExpr});`
);
// read one top-level key as JSON
const getKey = async (kw) => JSON.parse(await evalJs(getInExpr([kw])));
// Coerce to a plain string — a bare boolean occasionally trips CDP's
// returnByValue serializer after many evals ("reference chain too long").
const running = async () => (await evalJs(`String(ceol.web.metronome.running_QMARK_())`)) === "true";
// Toggle the metronome, discarding the return. dispatch-action!'s off-branch
// returns the metro-state map (which holds the Tone synth); serializing that
// via CDP returnByValue trips "reference chain too long". The real app discards
// the dispatch return, so this only matters for the harness.
const toggleMetro = () => evalJs(
  `(function(){ ceol.web.core.dispatch_action_BANG_(` +
  `  cljs.core.keyword.call(null,"metronome","toggle"), null); return null; })()`
);
// call playback/anchor-metronome! with the selected tune and a synthetic start-at
const anchorAt = (startAt) => evalJs(
  `(function(){` +
  `  var s = cljs.core.deref(ceol.web.state.app_state);` +
  `  var t = ceol.web.state.selected_tune(s);` +
  `  ceol.web.handlers.playback.anchor_metronome_BANG_(s, t, ${startAt});` +
  `})()`
);

await navigate("http://localhost:8280/index.html");
await dispatch(kwExpr("onboarding", "dismiss"));
await sleep(200);

// Select the first tune so the beat grid is deterministic.
await evalJs(
  `(function(){` +
  `  var s = cljs.core.deref(ceol.web.state.app_state);` +
  `  var id = cljs.core.first.call(null, cljs.core.get.call(null, s, cljs.core.keyword.call(null,"tune-order")));` +
  `  cljs.core.swap_BANG_.call(null, ceol.web.state.app_state, cljs.core.assoc, cljs.core.keyword.call(null,"selected-tune-id"), id);` +
  `})()`
);

// The tune's expected beat grid (pure, no audio).
const grid = JSON.parse(await evalJs(
  `(function(){` +
  `  var s = cljs.core.deref(ceol.web.state.app_state);` +
  `  var t = ceol.web.state.selected_tune(s);` +
  `  var off = cljs.core.get.call(null, s, cljs.core.keyword.call(null,"tempo-offset"));` +
  `  var bg = ceol.web.beat_engine.beats_for_tune(t, off);` +
  `  var g = function(k){ return cljs.core.get.call(null, bg, cljs.core.keyword.call(null,k)); };` +
  `  return JSON.stringify({mspb: g("ms-per-beat"), bpb: g("beats-per-bar")});` +
  `})()`
));
console.log("selected tune beat grid:", grid);

// (1) anchor-metronome! persists the melody phase — with metronome OFF it only
//     stores the reference, starting no clock (no audio dependency).
await setKey("metronome?", "false");
await setKey("playing?", "true");
await anchorAt("42.0");
const p1 = { start: await getKey("melody-start-at"),
             mspb:  await getKey("melody-ms-per-beat"),
             bpb:   await getKey("melody-beats-per-bar") };
console.log("phase after anchor:", p1);
assert(p1.start === 42.0, "anchor persists :melody-start-at");
assert(p1.mspb === grid.mspb, "anchor persists :melody-ms-per-beat matching beats-for-tune");
assert(p1.bpb === grid.bpb, "anchor persists :melody-beats-per-bar matching beats-for-tune");
assert((await running()) === false, "metronome off: anchor starts no clock");

// (2) re-anchor (tempo/section change, loop repeat) refreshes the start-at.
await anchorAt("99.5");
assert((await getKey("melody-start-at")) === 99.5,
       "re-anchor updates :melody-start-at to the fresh start");

// (3) toggling the metronome ON during playback enters synced mode and stays on
//     (previously play force-cleared :metronome?).
await toggleMetro();
await sleep(150);
assert((await getKey("metronome?")) === true,
       "toggle turns metronome on during playback");
assert((await running()) === true, "synced metronome clock is running");
await shot(`${OUT}/01-synced-playing.png`);

// (4) stop clears the melody phase and halts the clock.
await dispatch(kwExpr("playback", "stop"));
await sleep(150);
assert((await getKey("melody-start-at")) === null
       && (await getKey("melody-ms-per-beat")) === null
       && (await getKey("melody-beats-per-bar")) === null,
       "stop clears the melody phase");
assert((await running()) === false, "stop halts the metronome clock");

// (5) standalone path unchanged: with nothing playing, toggle starts a clock.
await setKey("playing?", "false");
await setKey("metronome?", "false");
await toggleMetro();
await sleep(150);
assert((await getKey("metronome?")) === true, "standalone toggle turns metronome on");
assert((await running()) === true, "standalone metronome clock starts when nothing plays");
await toggleMetro();
await sleep(100);
assert((await running()) === false, "toggling off stops the metronome");

console.log("\nAll issue-13 wiring assertions passed.");
shutdown();
process.exit(0);
