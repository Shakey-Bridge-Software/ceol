// Scenario: Wave 1 D — mobile always-expanded set cards (design 7VNKz).
//
// The mobile sets list used the desktop accordion (tap a card to expand). D
// replaces it with always-expanded cards: a numbered tune list visible without
// any tap, a learned-progress footer (one dot, green when all learned / amber
// otherwise), and a Play-set button. Tapping the card drills into the set detail;
// the Play button stops that bubble.
//
// Run via:  ./verify.sh waveD

import {
  evalJs, dispatch, navigate, shot, sleep, kwExpr, vecExpr, getInExpr, shutdown,
} from "../cdp.mjs";

const OUT = process.env.SCENARIO_OUT ?? "verify/out/waveD";
const text = (sel) => evalJs(
  `(function(){var e=document.querySelector(${JSON.stringify(sel)}); return e?e.textContent.trim():null;})()`);
const present = (sel) => evalJs(`document.querySelector(${JSON.stringify(sel)}) != null`);
const countIn = (cardSel, sel) => evalJs(
  `(function(){var c=document.querySelector(${JSON.stringify(cardSel)}); return c?c.querySelectorAll(${JSON.stringify(sel)}).length:-1;})()`);
const stateVal = async (k) => JSON.parse(await evalJs(getInExpr([k])));

await navigate("http://localhost:8280/index.html");
await sleep(600);
await dispatch(kwExpr("onboarding", "dismiss"));
await sleep(150);
await dispatch(kwExpr("tab", "set"), vecExpr([kwExpr("sets")]));
await sleep(200);

// Grab the first seeded set straight from state.
const set0 = JSON.parse(await evalJs(`(function(){
  var s = cljs.core.deref(ceol.web.state.app_state);
  var first = cljs.core.first.call(null, cljs.core.vals.call(null,
                cljs.core.get.call(null, s, cljs.core.keyword.call(null,"sets"))));
  return JSON.stringify(cljs.core.clj__GT_js(first));
})()`));
const setId = set0.id;
const tuneIds = set0["tune-ids"];
assert(tuneIds.length >= 1, `seed set has tunes (got ${tuneIds.length})`);

// Always expanded: the numbered tune list is visible with no tap, no accordion.
assert((await present(".mset-card")) === true, "mobile set card renders");
assert((await countIn(".mset-card", ".mset-tune")) === tuneIds.length,
       `tune list shown expanded by default (got ${await countIn(".mset-card", ".mset-tune")} of ${tuneIds.length})`);
assert((await text(".mset-card .mset-num")) === "1.", "tunes are numbered from 1");
assert((await text(".mset-card .mset-count")) === `${tuneIds.length} tune${tuneIds.length === 1 ? "" : "s"}`,
       `header shows the tune count (got ${await text(".mset-card .mset-count")})`);

// Footer progress: nothing learned yet → amber "K of N learned".
assert((await present(".mset-card .mset-progress.partial")) === true, "partial progress is amber");
assert((await text(".mset-card .mset-progress")) === `0 of ${tuneIds.length} learned`,
       `progress reads "0 of N learned" (got ${await text(".mset-card .mset-progress")})`);
assert((await present(".mset-card .mset-play")) === true, "Play set button present");
await shot(`${OUT}/01-partial.png`);

// Learn every tune in the set → green "All learned".
for (const id of tuneIds) await dispatch(kwExpr("learned", "toggle"), vecExpr([String(id)]));
await sleep(200);
assert((await present(".mset-card .mset-progress.all")) === true, "all-learned progress is green");
assert((await text(".mset-card .mset-progress")) === "All learned",
       `progress reads "All learned" (got ${await text(".mset-card .mset-progress")})`);
await shot(`${OUT}/02-all-learned.png`);

// Play button: stops the card's drill-in bubble and goes to the tune view.
await evalJs(`document.querySelector(".mset-card .mset-play").click()`);
await sleep(200);
assert((await stateVal("main-view")) === "tune",
       `Play set selects the tune (main-view tune, not set — :event/stop worked; got ${await stateVal("main-view")})`);

// Tapping the card body drills into the set detail (where ⋮ → action sheet lives).
await dispatch(kwExpr("tab", "set"), vecExpr([kwExpr("sets")]));
await sleep(150);
await evalJs(`document.querySelector(".mset-card").click()`);
await sleep(200);
assert((await stateVal("main-view")) === "set",
       `tapping the card drills into the set detail (got ${await stateVal("main-view")})`);
assert((await stateVal("active-set-id")) === setId, "the tapped set is the active set");

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
