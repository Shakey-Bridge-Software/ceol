// Scenario: Wave 1 B — session-ready hero card (design J8hkB).
//
// The session-ready (pre) view used a flat summary line + Start button. B wraps
// these in a .session-hero-card: "READY TO PRACTICE" label, a big learned-count
// number beside a two-line sub-label (learned tunes / N sets ready), and the
// Start button inside the card. Empty (no learned) still shows .session-empty.
//
// Run via:  ./verify.sh waveB

import {
  evalJs, dispatch, navigate, shot, sleep, kwExpr, vecExpr, getInExpr, shutdown,
} from "../cdp.mjs";

const OUT = process.env.SCENARIO_OUT ?? "verify/out/waveB";
const text = (sel) => evalJs(
  `(function(){var e=document.querySelector(${JSON.stringify(sel)}); return e?e.textContent.trim():null;})()`);
const present = (sel) => evalJs(`document.querySelector(${JSON.stringify(sel)}) != null`);

await navigate("http://localhost:8280/index.html");
await sleep(600);
await dispatch(kwExpr("onboarding", "dismiss"));
await sleep(150);

// Land on the Session tab (pre state — no session running, no result).
await dispatch(kwExpr("tab", "set"), vecExpr([kwExpr("session")]));
await sleep(200);

// No learned tunes yet → empty queue → empty state, no hero card.
assert((await present(".session-empty")) === true, "empty state when nothing learned");
assert((await present(".session-hero-card")) === false, "no hero card when queue empty");
await shot(`${OUT}/00-empty.png`);

// Grab three real seed tune ids from app-state.
const ids = Object.keys(JSON.parse(await evalJs(getInExpr(["custom-tunes"])))).map(Number);
assert(ids.length >= 3, `seed has at least 3 tunes (got ${ids.length})`);

// Learn exactly one → singular "learned tune", count = 1.
await dispatch(kwExpr("learned", "toggle"), vecExpr([String(ids[0])]));
await sleep(200);
assert((await present(".session-hero-card")) === true, "hero card appears once a tune is learned");
assert((await text(".session-hero-label")) === "READY TO PRACTICE", "hero label");
assert((await text(".session-hero-big")) === "1", `big number = learned count (got ${await text(".session-hero-big")})`);
const sub1 = await evalJs(
  `(function(){var ds=document.querySelectorAll(".session-hero-sub div"); return [...ds].map(d=>d.textContent.trim());})()`);
assert(sub1[0] === "learned tune", `singular learned (got ${JSON.stringify(sub1)})`);
assert(sub1[1] === "0 sets ready", `plural sets, none ready (got ${JSON.stringify(sub1)})`);
await shot(`${OUT}/01-one-learned.png`);

// Learn two more → plural "learned tunes", count = 3.
await dispatch(kwExpr("learned", "toggle"), vecExpr([String(ids[1])]));
await dispatch(kwExpr("learned", "toggle"), vecExpr([String(ids[2])]));
await sleep(200);
assert((await text(".session-hero-big")) === "3", `big number updates to 3 (got ${await text(".session-hero-big")})`);
const sub3 = await evalJs(
  `(function(){var ds=document.querySelectorAll(".session-hero-sub div"); return [...ds].map(d=>d.textContent.trim());})()`);
assert(sub3[0] === "learned tunes", `plural learned (got ${JSON.stringify(sub3)})`);

// Start button: shuffle icon + label, inside the card, wired to :session/start.
assert((await present(".session-hero-card .session-start")) === true, "Start button lives inside the hero card");
assert((await present(".session-start .session-start-icon")) === true, "Start button has the shuffle icon");
assert((await text(".session-start")) === "Start session", `Start button label (got ${await text(".session-start")})`);
await shot(`${OUT}/02-three-learned.png`);

// Click the real button → session starts (session-mode? true). Audio is a no-op
// in headless; we assert only the synchronous state transition.
await evalJs(`document.querySelector(".session-start").click()`);
await sleep(250);
assert((await evalJs(getInExpr(["session-mode?"]))) === "true",
       "clicking Start flips :session-mode? on");

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
