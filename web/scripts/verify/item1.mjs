// Scenario: Item #1 mobile tune action sheet + B4 :tune/duplicate.
//
// Drives the ⋮ → sheet open → each row → expected state path.
// Run via:  ./verify.sh item1

import {
  evalJs, dispatch, dispatchExpr, navigate, shot, sleep, kwExpr,
  vecExpr, getInExpr, shutdown,
} from "../cdp.mjs";

const OUT = process.env.SCENARIO_OUT ?? "verify/out/item1";

await navigate("http://localhost:8280/index.html");
await dispatch(kwExpr("onboarding", "dismiss"));
await sleep(200);

// Step 1 — open the action sheet for tune id=1 (Maggie in the Woods, a base tune)
await dispatch(kwExpr("menu", "open"), vecExpr(["1"]));
await sleep(200);
await shot(`${OUT}/01-sheet-open.png`);

const menuId = await evalJs(getInExpr(["context-menu-tune-id"]));
assert(menuId === "1", `:context-menu-tune-id should be 1, got ${menuId}`);

// Step 2 — Duplicate row (B4). Expect a new custom tune with " (copy)" name
// and the editor closed (selected-tune-id = the new tune)
await dispatch(kwExpr("tune", "duplicate"), vecExpr(["1"]));
await sleep(400);
await shot(`${OUT}/02-after-duplicate.png`);

const dupState = JSON.parse(await evalJs(`
  (function () {
    var s = cljs.core.deref(ceol.web.state.app_state);
    var sid = cljs.core.get.call(null, s, cljs.core.keyword.call(null, "selected-tune-id"));
    var t = ceol.web.state.tune_by_id(s, sid);
    var custom = cljs.core.get.call(null, s, cljs.core.keyword.call(null, "custom-tunes"));
    return JSON.stringify({
      selectedId: sid,
      tune: t ? cljs.core.clj__GT_js(t) : null,
      customCount: cljs.core.count(custom)
    });
  })()
`));
console.log("duplicate result:", dupState);
assert(dupState.selectedId !== 1, "duplicate should select the new tune, not the source");
assert(/ \(copy\)$/.test(dupState.tune?.name),
       `copy should end with ' (copy)' — got '${dupState.tune?.name}'`);
assert(dupState.customCount === 1, "exactly one custom tune should exist now");
assert(dupState.tune?.["session-id"] == null,
       "copy should not carry the source's thesession.org id");

// Step 3 — duplicate the duplicate. Expect ' (copy 2)' bump.
await dispatch(kwExpr("menu", "open"), vecExpr([String(dupState.selectedId)]));
await sleep(150);
await dispatch(kwExpr("tune", "duplicate"), vecExpr([String(dupState.selectedId)]));
await sleep(400);

const dup2 = JSON.parse(await evalJs(`
  (function () {
    var s = cljs.core.deref(ceol.web.state.app_state);
    var sid = cljs.core.get.call(null, s, cljs.core.keyword.call(null, "selected-tune-id"));
    var t = ceol.web.state.tune_by_id(s, sid);
    return JSON.stringify({ tune: cljs.core.clj__GT_js(t) });
  })()
`));
console.log("second duplicate:", dup2);
assert(/\(copy 2\)$/.test(dup2.tune.name) || /\(copy\)/.test(dup2.tune.name),
       `second copy name should reflect collision: got ${dup2.tune.name}`);

// Step 4 — Delete row dispatches [[:menu/close] [:delete/request id]] in
// the real UI. Fire both here so the modal sits alone (no sheet overlap).
await dispatch(kwExpr("menu", "open"), vecExpr(["1"]));
await sleep(150);
await dispatch(kwExpr("menu", "close"));
await dispatch(kwExpr("delete", "request"), vecExpr(["1"]));
await sleep(200);
await shot(`${OUT}/03-delete-confirm.png`);

const confirmId = await evalJs(getInExpr(["delete-confirm-tune-id"]));
const menuClosed = await evalJs(getInExpr(["context-menu-tune-id"]));
assert(confirmId === "1", "delete/request should open the confirm modal for the tune");
assert(menuClosed === null, "action sheet should be closed when the modal is up");

// Step 5 — Cancel the confirm + tap "Edit details" row equivalent → opens
// the B1 mobile editor with this tune cloned.
await dispatch(kwExpr("delete", "cancel"));
await sleep(150);
await dispatch(kwExpr("menu", "open"), vecExpr(["1"]));
await sleep(150);
await dispatch(kwExpr("tune-editor", "open-edit"), vecExpr(["1"]));
await sleep(250);
await shot(`${OUT}/04-edit-details-from-sheet.png`);

const teMode = JSON.parse(await evalJs(getInExpr(["tune-editor", "mode"])));
assert(teMode === "edit", `open-edit from action sheet should put editor in :edit mode, got ${teMode}`);

// Step 6 — close everything
await dispatch(kwExpr("tune-editor", "cancel"));
await sleep(150);
await dispatch(kwExpr("menu", "close"));
await sleep(150);
await shot(`${OUT}/05-closed.png`);

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
