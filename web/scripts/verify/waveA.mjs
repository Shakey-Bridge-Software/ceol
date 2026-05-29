// Scenario: Wave 1 A — notes sheet title block + footer (design vWmaK).
//
// The notes panel had only a label + textarea. A adds a title block (tune title
// + meta) above and a footer (Saved indicator + live char count) below, and
// relabels NOTES → PRACTICE NOTES.
//
// Run via:  ./verify.sh waveA

import {
  evalJs, dispatch, navigate, shot, sleep, kwExpr, vecExpr, shutdown,
} from "../cdp.mjs";

const OUT = process.env.SCENARIO_OUT ?? "verify/out/waveA";
const text = (sel) => evalJs(
  `(function(){var e=document.querySelector(${JSON.stringify(sel)}); return e?e.textContent.trim():null;})()`);
const present = (sel) => evalJs(`document.querySelector(${JSON.stringify(sel)}) != null`);

await navigate("http://localhost:8280/index.html");
await sleep(600);
await dispatch(kwExpr("onboarding", "dismiss"));
await sleep(150);

// Select a seed tune + open the notes panel.
await dispatch(kwExpr("tune", "select"), vecExpr([1]));
await sleep(150);
await dispatch(kwExpr("notes", "toggle"));
await sleep(200);

assert((await present(".notes-panel.open")) === true, "notes panel opens");
assert((await text(".notes-label")) === "PRACTICE NOTES", "label is PRACTICE NOTES");

// Title block reflects the open tune.
assert((await text(".notes-tune-title")) === "Maggie in the Woods",
       `title block shows the tune name (got ${await text(".notes-tune-title")})`);
assert((await text(".notes-tune-meta")) === "Polka · 2/4 · G Major",
       `meta line shows type · time-sig · key (got ${await text(".notes-tune-meta")})`);

// Footer: Saved indicator + live char count (empty notes → 0 characters).
assert((await present(".notes-footer")) === true, "footer present");
assert((await text(".notes-saved")) === "Saved", "saved indicator reads Saved");
assert((await text(".notes-count")) === "0 characters", "count starts at 0 characters");
await shot(`${OUT}/01-empty.png`);

// Type notes → live count updates (plural + singular).
await dispatch(kwExpr("notes", "update"), vecExpr([1, JSON.stringify("reels are hard")]));
await sleep(150);
assert((await text(".notes-count")) === "14 characters",
       `count updates live (got ${await text(".notes-count")})`);
await dispatch(kwExpr("notes", "update"), vecExpr([1, JSON.stringify("x")]));
await sleep(120);
assert((await text(".notes-count")) === "1 character", "singular character (no plural s)");
await shot(`${OUT}/02-typed.png`);

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
