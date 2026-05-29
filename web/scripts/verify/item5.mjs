// Scenario: Item #5 — session-complete summary (design LQ3CL).
//
// A real session :done requires playing the whole audio queue, so we stow a
// :session-result directly (the same shape handlers.session :done builds) and
// assert the summary view + its Done / Practice-again wiring. session-start!
// (Practice again) is exercised for its synchronous state effects only.
//
// Run via:  ./verify.sh item5

import {
  evalJs, dispatch, navigate, shot, sleep, kwExpr, vecExpr, getInExpr, shutdown,
} from "../cdp.mjs";

const OUT = process.env.SCENARIO_OUT ?? "verify/out/item5";

const text = (sel) => evalJs(
  `(function(){var e=document.querySelector(${JSON.stringify(sel)}); return e?e.textContent:null;})()`);
const present = (sel) => evalJs(`document.querySelector(${JSON.stringify(sel)}) != null`);
const click = (sel) => evalJs(
  `(function(){var e=document.querySelector(${JSON.stringify(sel)}); if(!e)return false; e.click(); return true;})()`);
// Stow a :session-result (simulating :done) + land on the session tab.
const setResult = (tc, ms) => evalJs(`
  (function(){
    var kw=function(n){return cljs.core.keyword.call(null,n);};
    cljs.core.swap_BANG_.call(null, ceol.web.state.app_state, function(s){
      s = cljs.core.assoc_in.call(null, s,
        cljs.core.PersistentVector.fromArray([kw("session-result"),kw("tune-count")]), ${tc});
      s = cljs.core.assoc_in.call(null, s,
        cljs.core.PersistentVector.fromArray([kw("session-result"),kw("duration-ms")]), ${ms});
      return cljs.core.assoc.call(null, s, kw("session-mode?"), false, kw("tab"), kw("session"));
    });
    return true;
  })()`);

await navigate("http://localhost:8280/index.html");
await sleep(600);
await dispatch(kwExpr("onboarding", "dismiss"));
await sleep(150);

// Mark the seed tunes learned so "Practice again" can build a real queue.
for (const id of [1, 3, 7]) {
  await dispatch(kwExpr("learned", "toggle"), vecExpr([id]));
}
await sleep(120);

// 1) Stow a result (4 tunes, 12 minutes) → the summary renders in the session tab.
await setResult(4, 12 * 60000);
await sleep(200);
assert((await present(".session-complete")) === true, "session-complete summary renders");
assert((await text(".session-complete-title")) === "Practice complete", "heading reads 'Practice complete'");
assert((await text(".session-complete-stats")) === "4 tunes · 12 minutes",
       `stat line (got ${await text(".session-complete-stats")})`);
assert((await present(".session-complete-again")) === true, "Practice again button present");
assert((await present(".session-complete-done")) === true, "Done button present");
await shot(`${OUT}/01-summary.png`);

// 2) Done → clears the result, back to the pre-session screen.
assert((await click(".session-complete-done")) === true, "Done is clickable");
await sleep(200);
assert(JSON.parse(await evalJs(getInExpr(["session-result"]))) == null, "Done clears :session-result");
assert((await present(".session-complete")) === false, "summary gone after Done");
assert((await present(".session-start")) === true, "pre-session screen returns (Start Session)");

// 3) Re-stow, then Practice again → starts a session (clears result, sets a
//    fresh start timestamp). Stop immediately to halt audio.
await setResult(2, 90000);
await sleep(150);
assert((await text(".session-complete-stats")) === "2 tunes · 1 minute", "singular-minute stat line");
assert((await click(".session-complete-again")) === true, "Practice again is clickable");
await sleep(200);
assert(JSON.parse(await evalJs(getInExpr(["session-mode?"]))) === true,
       "Practice again starts a session (session-mode? true)");
assert(JSON.parse(await evalJs(getInExpr(["session-result"]))) == null,
       "Practice again clears the prior summary");
const startedAt = JSON.parse(await evalJs(getInExpr(["session-started-at"])));
assert(typeof startedAt === "number" && startedAt > 0, "session-start records a start timestamp");
await dispatch(kwExpr("session", "stop"));
await sleep(150);
await shot(`${OUT}/02-after-again.png`);

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
