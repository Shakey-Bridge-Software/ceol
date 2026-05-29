// Scenario: Wave 1 C — session-live now-playing detail + controls (design XwIFG).
//
// The live session view showed only a small tune name, a "?" next teaser, and no
// transport. C adds: a now-playing meta line (type · key · BPM), Skip + Pause
// controls, and a resolved NEXT UP card. Skip advances the queue; Pause toggles
// :session-paused? and swaps the button icon. The "next becomes now" invariant
// after a skip exercises session-nav/next-ref end-to-end.
//
// Audio can't run headless; :session/start sets live state and the async play is
// a no-op here, which is all this scenario needs.
//
// Run via:  ./verify.sh waveC

import {
  evalJs, dispatch, dispatchExpr, navigate, shot, sleep, kwExpr, vecExpr, getInExpr, shutdown,
} from "../cdp.mjs";

const OUT = process.env.SCENARIO_OUT ?? "verify/out/waveC";

// Fire-and-forget dispatch: session start/skip/pause RETURN the async audio-play
// promise (render→play), which never resolves headless. Discard it so the eval
// doesn't hang waiting on audio — the synchronous state swap has already landed.
const fire = (actionExpr, argsExpr = "null") =>
  evalJs(`(function(){ ${dispatchExpr(actionExpr, argsExpr)}; return true; })()`,
         { awaitPromise: false });
const text = (sel) => evalJs(
  `(function(){var e=document.querySelector(${JSON.stringify(sel)}); return e?e.textContent.trim():null;})()`);
const present = (sel) => evalJs(`document.querySelector(${JSON.stringify(sel)}) != null`);
const state = (k) => evalJs(getInExpr([k]));

await navigate("http://localhost:8280/index.html");
await sleep(600);
await dispatch(kwExpr("onboarding", "dismiss"));
await sleep(150);
await dispatch(kwExpr("tab", "set"), vecExpr([kwExpr("session")]));
await sleep(150);

// Learn three standalone tunes so the queue has a real next (and a next-next).
const ids = Object.keys(JSON.parse(await evalJs(getInExpr(["custom-tunes"])))).map(Number);
assert(ids.length >= 3, `seed has at least 3 tunes (got ${ids.length})`);
for (const id of ids.slice(0, 3)) await dispatch(kwExpr("learned", "toggle"), vecExpr([String(id)]));
await sleep(150);

// Start a live session.
await fire(kwExpr("session", "start"));
await sleep(250);
assert((await state("session-mode?")) === "true", "session is live after start");

// Now-playing card: dot + label, a real tune name, and a meta line with BPM.
assert((await present(".session-now-playing")) === true, "now-playing card present");
assert((await present(".session-now-dot")) === true, "now-playing accent dot present");
assert((await text(".session-now-label")) === "NOW PLAYING", "now-playing label");
const now0 = await text(".session-now-name");
assert(now0 && now0 !== "Unknown", `now-playing shows a tune (got ${now0})`);
const meta0 = await text(".session-now-meta");
assert(/ BPM$/.test(meta0 || ""), `meta line ends with BPM (got ${meta0})`);
assert((meta0.match(/·/g) || []).length === 3, `meta has type · time · key · bpm (got ${meta0})`);

// Next-up card resolved (no more "?").
const next0 = await text(".session-next-name");
assert((await text(".session-next-label")) === "NEXT UP", "next-up label");
assert(next0 && next0 !== "?" && next0 !== now0, `next-up shows the next tune (got ${next0})`);
assert((await present(".session-next-meta")) === true, "next-up has a meta line");

// Transport present; not paused → button shows the Pause (two bars) icon.
assert((await present(".session-skip")) === true, "Skip button present");
assert((await present(".session-pause")) === true, "Pause button present");
assert((await present(".session-pause rect")) === true, "shows pause bars while playing");
assert((await present(".session-pause polygon")) === false, "no play triangle while playing");
await shot(`${OUT}/01-now-playing.png`);

// Skip → queue advances; the tune that was NEXT is now NOW (next-ref invariant).
await fire(kwExpr("session", "skip"));
await sleep(200);
assert((await state("session-index")) === "1", `skip advances the queue index (got ${await state("session-index")})`);
const now1 = await text(".session-now-name");
assert(now1 === next0, `the previous next tune is now playing (next0=${next0}, now1=${now1})`);
await shot(`${OUT}/02-after-skip.png`);

// Pause → flag flips, icon swaps to a play triangle.
await fire(kwExpr("session", "pause"));
await sleep(150);
assert((await state("session-paused?")) === "true", "pause flips :session-paused?");
assert((await present(".session-pause polygon")) === true, "shows play triangle while paused");
assert((await present(".session-pause rect")) === false, "no pause bars while paused");
await shot(`${OUT}/03-paused.png`);

// Pause again → resume.
await fire(kwExpr("session", "pause"));
await sleep(150);
assert((await state("session-paused?")) === "false", "second press resumes (paused? false)");
assert((await present(".session-pause rect")) === true, "pause bars return after resume");

shutdown();
process.exit(0);

function assert(cond, msg) {
  if (!cond) {
    console.error(`✗ ${msg}`);
    shutdown();
    process.exit(2);
  }
  console.log(`✓ ${msg}`);
}
