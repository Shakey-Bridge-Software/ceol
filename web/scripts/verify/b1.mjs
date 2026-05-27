// Reference scenario: B1 mobile tune-details editor.
//
// Run via:  ./verify.sh b1   (from web/scripts/)
//
// Drives the FAB → editor → save → re-open → cancel flow at a 390×844 mobile
// viewport, asserts state shape at each step, and writes PNGs to
// verify/out/b1/. Designed as the template for B2-B5 scenarios.

import {
  evalJs, dispatch, dispatchExpr, navigate, shot, sleep, kwExpr,
  vecExpr, getInExpr, shutdown,
} from "../cdp.mjs";

const OUT = process.env.SCENARIO_OUT ?? "verify/out/b1";

await navigate("http://localhost:8280/index.html");

// First-run coachmark sits over everything — dismiss via the same dispatch
// "Got it" fires, so persistence is exercised too.
await dispatch(kwExpr("onboarding", "dismiss"));
await sleep(200);

// (1) Mobile list — FAB visible bottom-right
await shot(`${OUT}/01-list.png`);

// (2) Open new — blank draft, mode :new
await dispatch(kwExpr("tune-editor", "open-new"));
await sleep(200);
await shot(`${OUT}/02-editor-new.png`);

const initialDraft = await evalJs(getInExpr(["tune-editor", "draft"]));
console.log("editor draft on open-new:", initialDraft);
assert(initialDraft && JSON.parse(initialDraft).name === "",
       "open-new should start with a blank name");

// (3) Fill out via update-draft dispatches — mirrors what the UI handlers fire
const upd = (fieldKw, valueExpr) =>
  dispatchExpr(kwExpr("tune-editor", "update-draft"),
               vecExpr([kwExpr(fieldKw), valueExpr]));
await evalJs(upd("name",       JSON.stringify("Boys of Bluehill")));
await evalJs(upd("type",       kwExpr("hornpipe")));
await evalJs(upd("time-sig",   JSON.stringify("4/4")));
await evalJs(upd("key",        JSON.stringify("D")));
await evalJs(upd("session-id", JSON.stringify("604")));
await sleep(200);
await shot(`${OUT}/03-editor-filled.png`);

// (4) Save — editor closes, new tune is selected
await dispatch(kwExpr("tune-editor", "save"));
await sleep(400);
await shot(`${OUT}/04-after-save.png`);

const saved = JSON.parse(await evalJs(`
  (function () {
    var s = cljs.core.deref(ceol.web.state.app_state);
    var sid = cljs.core.get.call(null, s, cljs.core.keyword.call(null, "selected-tune-id"));
    var t = ceol.web.state.tune_by_id(s, sid);
    return JSON.stringify({selectedId: sid, tune: t ? cljs.core.clj__GT_js(t) : null});
  })()
`));
console.log("after save:", saved);
assert(saved.tune?.name === "Boys of Bluehill", "saved tune name should match");
assert(saved.tune?.["session-id"] === 604, "session-id should parse to int");
assert(saved.tune?.type === "hornpipe", "type should round-trip");

// (5) Re-open in :edit mode — pre-filled, title "Edit tune"
await evalJs(`
  var s = cljs.core.deref(ceol.web.state.app_state);
  var sid = cljs.core.get.call(null, s, cljs.core.keyword.call(null, "selected-tune-id"));
  ${dispatchExpr(kwExpr("tune-editor", "open-edit"),
                 `cljs.core.PersistentVector.fromArray([sid])`)}
`);
await sleep(200);
await shot(`${OUT}/05-editor-edit.png`);

const editDraft = JSON.parse(await evalJs(getInExpr(["tune-editor", "draft"])));
assert(editDraft.name === "Boys of Bluehill",
       "edit-mode draft should clone the saved tune");

// (6) Cancel — slot clears, detail view restored
await dispatch(kwExpr("tune-editor", "cancel"));
await sleep(200);
await shot(`${OUT}/06-after-cancel.png`);

const afterCancel = await evalJs(getInExpr(["tune-editor"]));
assert(afterCancel == null, "cancel should clear :tune-editor");

shutdown();
process.exit(0);

// --- tiny assert helper, no third-party deps ---------------------------------
function assert(cond, msg) {
  if (!cond) {
    console.error(`✗ ${msg}`);
    shutdown();
    process.exit(2);
  }
  console.log(`✓ ${msg}`);
}
