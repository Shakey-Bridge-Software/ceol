// Scenario: Item #3 — mobile full-screen new/edit set editor (design he1dM).
//
// Drives the :set-editor/* draft actions and asserts the slot + committed
// :sets shape. The touch drag-to-reorder gesture itself is real-device-gated
// (headless Chrome ≠ iOS touch); the reorder *action* it dispatches is
// verified here directly.
//
// Run via:  ./verify.sh item3

import {
  cdp, evalJs, dispatch, navigate, shot, sleep, kwExpr,
  vecExpr, getInExpr, shutdown,
} from "../cdp.mjs";

const OUT = process.env.SCENARIO_OUT ?? "verify/out/item3";

const slot = async () => JSON.parse(await evalJs(getInExpr(["set-editor"])));
const tuneIds = async () =>
  JSON.parse(await evalJs(getInExpr(["set-editor", "draft", "tune-ids"])));
const setsCount = async () =>
  JSON.parse(await evalJs(`JSON.stringify(cljs.core.count(
    cljs.core.get.call(null, cljs.core.deref(ceol.web.state.app_state),
      cljs.core.keyword.call(null, "sets"))))`));

await navigate("http://localhost:8280/index.html");
await sleep(600); // default sets/tunes fetch is async
await dispatch(kwExpr("onboarding", "dismiss"));
await sleep(150);

const setsAtStart = await setsCount();
console.log("sets at start:", setsAtStart);

// 1) Open a NEW editor — slot is :new with a blank draft, overlay renders.
await dispatch(kwExpr("set-editor", "open-new"));
await sleep(200);
const s1 = await slot();
assert(s1?.mode === "new", `open-new → mode :new (got ${s1?.mode})`);
assert(s1?.draft?.name === "", "new draft name is blank");
assert(Array.isArray(s1?.draft?.["tune-ids"]) && s1.draft["tune-ids"].length === 0,
       "new draft has no tunes");
const overlay = await evalJs(`document.querySelector(".se-overlay") != null`);
assert(overlay === true, "the .se-overlay full-screen editor renders when open");
await shot(`${OUT}/01-new-empty.png`);

// 2) Name the set.
await dispatch(kwExpr("set-editor", "update-draft"),
               vecExpr([kwExpr("name"), JSON.stringify("Friday Night Set")]));
await sleep(120);
const nm = JSON.parse(await evalJs(getInExpr(["set-editor", "draft", "name"])));
assert(nm === "Friday Night Set", `name updates the draft (got ${nm})`);

// Save is still gated — no tunes yet.
await dispatch(kwExpr("set-editor", "save"));
await sleep(150);
assert((await slot()) != null, "save is a no-op with zero tunes (editor stays open)");
assert((await setsCount()) === setsAtStart, "no set committed without tunes");

// 3) Add tunes via the picker (seeds: 1 Maggie/polka, 3 Rathlin/polka, 7 Ocean/jig).
await dispatch(kwExpr("set-editor", "start-pick"));
await sleep(120);
assert(JSON.parse(await evalJs(getInExpr(["set-editor", "picking?"]))) === true,
       "start-pick opens the picker");
await dispatch(kwExpr("set-editor", "add-tune"), vecExpr([1]));
await sleep(80);
await dispatch(kwExpr("set-editor", "add-tune"), vecExpr([7]));
await sleep(120);
assert(JSON.stringify(await tuneIds()) === "[1,7]",
       `picker adds in order (got ${JSON.stringify(await tuneIds())})`);

// dedup — adding an existing tune is a no-op.
await dispatch(kwExpr("set-editor", "add-tune"), vecExpr([1]));
await sleep(80);
assert(JSON.stringify(await tuneIds()) === "[1,7]", "add-tune dedups");
await shot(`${OUT}/02-tunes-added.png`);

// 4) Reorder — move index 0 to index 1 → [7,1].
await dispatch(kwExpr("set-editor", "reorder"), vecExpr([0, 1]));
await sleep(100);
assert(JSON.stringify(await tuneIds()) === "[7,1]",
       `reorder 0→1 (got ${JSON.stringify(await tuneIds())})`);

// 5) Remove a tune → [7].
await dispatch(kwExpr("set-editor", "remove-tune"), vecExpr([1]));
await sleep(100);
assert(JSON.stringify(await tuneIds()) === "[7]",
       `remove-tune (got ${JSON.stringify(await tuneIds())})`);

// rows in the DOM reflect the draft (1 row).
const rowCount = JSON.parse(await evalJs(
  `JSON.stringify(document.querySelectorAll(".se-tune-row").length)`));
assert(rowCount === 1, `one .se-tune-row renders (got ${rowCount})`);

// 6) Save — commits, closes, lands on the set detail.
await dispatch(kwExpr("set-editor", "save"));
await sleep(250);
assert((await slot()) == null, "save closes the editor (slot nil)");
assert((await setsCount()) === setsAtStart + 1, "exactly one new set committed");

const committed = JSON.parse(await evalJs(`
  (function () {
    var s = cljs.core.deref(ceol.web.state.app_state);
    var id = cljs.core.get.call(null, s, cljs.core.keyword.call(null, "active-set-id"));
    var set = cljs.core.get.call(null,
      cljs.core.get.call(null, s, cljs.core.keyword.call(null, "sets")), id);
    var mv = cljs.core.get.call(null, s, cljs.core.keyword.call(null, "main-view"));
    return JSON.stringify({ id: id, set: cljs.core.clj__GT_js(set), mainView: cljs.core.clj__GT_js(mv) });
  })()
`));
console.log("committed:", committed);
assert(committed.set?.name === "Friday Night Set", "committed name persists");
assert(JSON.stringify(committed.set?.["tune-ids"]) === "[7]", "committed tune-ids persist");
assert(committed.mainView === "set", "save lands on the set detail view");
const stored = await evalJs(`window.localStorage.getItem("ceol-sets") != null`);
assert(stored === true, "save writes through to localStorage");
await shot(`${OUT}/03-saved-detail.png`);

// 7) Open EDIT — seeds the draft from the committed set.
const newId = committed.id;
await dispatch(kwExpr("set-editor", "open-edit"), vecExpr([JSON.stringify(newId)]));
await sleep(200);
const s2 = await slot();
assert(s2?.mode === "edit", `open-edit → mode :edit (got ${s2?.mode})`);
assert(s2?.["set-id"] === newId, "edit carries the set-id");
assert(s2?.draft?.name === "Friday Night Set", "edit seeds the name");
assert(JSON.stringify(s2?.draft?.["tune-ids"]) === "[7]", "edit seeds the tune-ids");
await shot(`${OUT}/04-edit-seeded.png`);

// 8) Edit + save mutates the existing set (no new set created).
await dispatch(kwExpr("set-editor", "update-draft"),
               vecExpr([kwExpr("name"), JSON.stringify("Renamed Set")]));
await sleep(100);
await dispatch(kwExpr("set-editor", "add-tune"), vecExpr([3]));
await sleep(100);
await dispatch(kwExpr("set-editor", "save"));
await sleep(200);
assert((await setsCount()) === setsAtStart + 1, "edit-save does not create a new set");
const edited = JSON.parse(await evalJs(`
  (function () {
    var s = cljs.core.deref(ceol.web.state.app_state);
    var set = cljs.core.get.call(null,
      cljs.core.get.call(null, s, cljs.core.keyword.call(null, "sets")), ${JSON.stringify(newId)});
    return JSON.stringify(cljs.core.clj__GT_js(set));
  })()
`));
assert(edited?.name === "Renamed Set", "edit renames in place");
assert(JSON.stringify(edited?.["tune-ids"]) === "[7,3]", "edit appends the added tune");

// 9) Cancel discards a fresh edit.
await dispatch(kwExpr("set-editor", "open-edit"), vecExpr([JSON.stringify(newId)]));
await sleep(120);
await dispatch(kwExpr("set-editor", "update-draft"),
               vecExpr([kwExpr("name"), JSON.stringify("THROWN AWAY")]));
await sleep(100);
await dispatch(kwExpr("set-editor", "cancel"));
await sleep(150);
assert((await slot()) == null, "cancel closes the editor");
const afterCancel = JSON.parse(await evalJs(`
  (function () {
    var s = cljs.core.deref(ceol.web.state.app_state);
    var set = cljs.core.get.call(null,
      cljs.core.get.call(null, s, cljs.core.keyword.call(null, "sets")), ${JSON.stringify(newId)});
    return JSON.stringify(cljs.core.clj__GT_js(set));
  })()
`));
assert(afterCancel?.name === "Renamed Set", "cancel discards the unsaved edit");
await shot(`${OUT}/05-closed.png`);

// 10) Drag-to-reorder via synthetic touch on the grip handle. Exercises the
// gesture.cljs drag path end-to-end (on-drag-start/move/end → reorder). Pixel
// behaviour on real iOS still needs a device pass, but the wiring is covered.
await dispatch(kwExpr("set-editor", "open-new"));
await sleep(120);
await dispatch(kwExpr("set-editor", "update-draft"),
               vecExpr([kwExpr("name"), JSON.stringify("Drag Test")]));
await dispatch(kwExpr("set-editor", "start-pick"));
await sleep(80);
for (const id of [1, 7, 3]) {
  await dispatch(kwExpr("set-editor", "add-tune"), vecExpr([id]));
  await sleep(60);
}
await dispatch(kwExpr("set-editor", "stop-pick"));
await sleep(150);
assert(JSON.stringify(await tuneIds()) === "[1,7,3]", "drag setup: three tunes in order");

// Grip rect of the first row + the per-row slot height (row + 8px gap).
const geom = JSON.parse(await evalJs(`
  (function () {
    var row = document.querySelectorAll(".se-tune-row")[0];
    var grip = row.querySelector(".se-grip");
    var r = grip.getBoundingClientRect();
    return JSON.stringify({ x: r.x + r.width/2, y: r.y + r.height/2,
                            slot: row.offsetHeight + 8 });
  })()
`));
console.log("drag geom:", geom);

const downY = geom.y + Math.round(geom.slot * 1.2); // ~1 slot down → round → +1
await cdp("Input.dispatchTouchEvent", {
  type: "touchStart", touchPoints: [{ x: geom.x, y: geom.y, id: 0 }],
});
await sleep(40);
await cdp("Input.dispatchTouchEvent", {
  type: "touchMove", touchPoints: [{ x: geom.x, y: downY, id: 0 }],
});
await sleep(40);
const dragging = await evalJs(`document.querySelector(".se-tune-row.se-dragging") != null`);
assert(dragging === true, "row gets .se-dragging while held");
await cdp("Input.dispatchTouchEvent", { type: "touchEnd", touchPoints: [] });
await sleep(150);
assert(JSON.stringify(await tuneIds()) === "[7,1,3]",
       `grip drag row 0 → 1 reorders (got ${JSON.stringify(await tuneIds())})`);
const draggingCleared = await evalJs(`document.querySelector(".se-tune-row.se-dragging") == null`);
assert(draggingCleared === true, ".se-dragging clears on release");
await dispatch(kwExpr("set-editor", "cancel"));

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
