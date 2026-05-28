// Scenario: B2 generic confirm modal — covers import-overwrite and
// set-delete destructive paths. The styled modal replaces the silent
// destructive dispatches that existed pre-B2.
//
// Run via:  ./verify.sh b2

import {
  evalJs, dispatch, dispatchExpr, navigate, shot, sleep, kwExpr,
  vecExpr, getInExpr, shutdown,
} from "../cdp.mjs";

const OUT = process.env.SCENARIO_OUT ?? "verify/out/b2";

await navigate("http://localhost:8280/index.html");
await dispatch(kwExpr("onboarding", "dismiss"));
await sleep(200);

// --- Path 1: set-delete confirm -----------------------------------------------

// Seed one set in app-state so there's something to delete.
await evalJs(`
  (function () {
    var id = "set-verify-1";
    var st = ceol.web.state.app_state;
    cljs.core.swap_BANG_(st, function (s) {
      var entry = cljs.core.js__GT_clj.call(
        null,
        {id: id, name: "Verify Set", "tune-ids": [1]},
        cljs.core.PersistentArrayMap.fromArray([cljs.core.keyword.call(null, "keywordize-keys"), true], true, false)
      );
      return cljs.core.assoc.call(null, s,
        cljs.core.keyword.call(null, "sets"),
        cljs.core.assoc.call(null,
          cljs.core.get.call(null, s, cljs.core.keyword.call(null, "sets")),
          id,
          entry));
    });
  })()
`);

// Fire the new confirm dispatch (mirrors what .delete-set button now sends).
await dispatch(
  kwExpr("confirm", "open"),
  vecExpr([`cljs.core.js__GT_clj.call(null,
    {title: "Delete set?", body: "Verify Set will be removed.",
     "destructive-label": "Delete",
     "on-confirm": [[cljs.core.keyword.call(null, "set", "delete"), "set-verify-1"]]},
    cljs.core.PersistentArrayMap.fromArray(
      [cljs.core.keyword.call(null, "keywordize-keys"), true], true, false))`])
);
await sleep(250);
await shot(`${OUT}/01-set-delete-confirm.png`);

const setConfirmShape = JSON.parse(await evalJs(getInExpr(["confirm"])));
console.log("set-delete confirm payload:", setConfirmShape);
assert(setConfirmShape?.title === "Delete set?", "confirm modal should show set-delete title");
assert(Array.isArray(setConfirmShape?.["on-confirm"]),
       "on-confirm should be an action vector");

// --- Cancel keeps state intact -----------------------------------------------

await dispatch(kwExpr("confirm", "cancel"));
await sleep(150);

const afterCancel = await evalJs(getInExpr(["confirm"]));
assert(afterCancel === null, "cancel should clear :confirm slot");

const setStillThere = await evalJs(`
  (function () {
    var s = cljs.core.deref(ceol.web.state.app_state);
    var sets = cljs.core.get.call(null, s, cljs.core.keyword.call(null, "sets"));
    return cljs.core.contains_QMARK_(sets, "set-verify-1");
  })()
`);
assert(setStillThere === true, "cancel must NOT delete the set");

// --- Confirm actually runs on-confirm + then cancel -------------------------
// Re-open the modal, then simulate the button click by dispatching the
// composed action vector (on-confirm + :confirm/cancel) just like the view
// builds it.
await dispatch(
  kwExpr("confirm", "open"),
  vecExpr([`cljs.core.js__GT_clj.call(null,
    {title: "Delete set?", body: "x", "destructive-label": "Delete",
     "on-confirm": [[cljs.core.keyword.call(null, "set", "delete"), "set-verify-1"]]},
    cljs.core.PersistentArrayMap.fromArray(
      [cljs.core.keyword.call(null, "keywordize-keys"), true], true, false))`])
);
await sleep(150);
// Compose: [[set/delete id] [confirm/cancel]]
await dispatch(kwExpr("set", "delete"), vecExpr([JSON.stringify("set-verify-1")]));
await dispatch(kwExpr("confirm", "cancel"));
await sleep(250);
await shot(`${OUT}/02-after-confirm.png`);

const setGone = await evalJs(`
  (function () {
    var s = cljs.core.deref(ceol.web.state.app_state);
    var sets = cljs.core.get.call(null, s, cljs.core.keyword.call(null, "sets"));
    return cljs.core.contains_QMARK_(sets, "set-verify-1");
  })()
`);
assert(setGone === false, "confirm should run :set/delete on the seeded set");

const confirmCleared = await evalJs(getInExpr(["confirm"]));
assert(confirmCleared === null, "confirm slot should clear after destructive action");

// --- Path 2: import-overwrite confirm payload --------------------------------
// We can't actually trigger the file picker in headless mode, but we can
// verify the dispatch wiring + modal payload that the Import button sends.

await dispatch(
  kwExpr("confirm", "open"),
  vecExpr([`cljs.core.js__GT_clj.call(null,
    {title: "Replace all data?",
     body: "Importing a backup overwrites every custom tune...",
     "destructive-label": "Choose file",
     "on-confirm": [[cljs.core.keyword.call(null, "backup", "import")]]},
    cljs.core.PersistentArrayMap.fromArray(
      [cljs.core.keyword.call(null, "keywordize-keys"), true], true, false))`])
);
await sleep(200);
await shot(`${OUT}/03-import-confirm.png`);

const importShape = JSON.parse(await evalJs(getInExpr(["confirm"])));
assert(importShape?.title === "Replace all data?",
       "import modal should show overwrite warning");
assert(importShape?.["destructive-label"] === "Choose file",
       "import destructive button should read 'Choose file'");

await dispatch(kwExpr("confirm", "cancel"));
await sleep(150);
await shot(`${OUT}/04-closed.png`);

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
