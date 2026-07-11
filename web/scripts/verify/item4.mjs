// Scenario: Item #4 — mobile set action sheet (design kihYP).
//
// Opens the set-detail ⋮ sheet and exercises each row by clicking the real
// .as-row DOM buttons (so the view→dispatch wiring is covered end-to-end):
// Edit set → editor, Duplicate → :set/duplicate, Delete → B2 confirm → delete.
// Play is asserted as present only (clicking it would start audio).
//
// Run via:  ./verify.sh item4

import {
  evalJs, dispatch, navigate, shot, sleep, kwExpr,
  vecExpr, getInExpr, shutdown,
} from "../cdp.mjs";

const OUT = process.env.SCENARIO_OUT ?? "verify/out/item4";

const setsCount = async () =>
  JSON.parse(await evalJs(`JSON.stringify(cljs.core.count(
    cljs.core.get.call(null, cljs.core.deref(ceol.web.state.app_state),
      cljs.core.keyword.call(null, "sets"))))`));
const menuSetId = async () =>
  JSON.parse(await evalJs(getInExpr(["context-menu-set-id"])));
// Click an .as-row button by its visible label (exercises real dispatch).
const clickRow = (label) => evalJs(
  `(function(){var b=[...document.querySelectorAll(".as-row")]
     .find(x=>x.textContent.includes(${JSON.stringify(label)}));
     if(!b) return false; b.click(); return true;})()`);

await navigate("http://localhost:8280/index.html");
await sleep(600);
await dispatch(kwExpr("onboarding", "dismiss"));
await sleep(150);

const startCount = await setsCount();
console.log("sets at start:", startCount); // seeded from default-sets.edn (20 sets); set-17 = "Polkas Set 1"

// Open a real seed set's detail, then its action sheet via ⋮ wiring.
await dispatch(kwExpr("set", "toggle"), vecExpr([JSON.stringify("set-17")]));
await sleep(150);
const moreClicked = await evalJs(
  `(function(){var b=document.querySelector(".set-detail-more");
     if(!b) return false; b.click(); return true;})()`);
assert(moreClicked === true, "set-detail ⋮ button exists and is clickable");
await sleep(200);
assert((await menuSetId()) === "set-17", "⋮ opens the sheet for the set");
const overlay = await evalJs(`document.querySelector(".as-overlay") != null`);
assert(overlay === true, ".as-overlay set action sheet renders");

// Four rows, in design order, with the right labels.
const labels = JSON.parse(await evalJs(
  `JSON.stringify([...document.querySelectorAll(".as-row .as-row-label")].map(s=>s.textContent))`));
console.log("rows:", labels);
assert(JSON.stringify(labels) === JSON.stringify(["Play set", "Edit set", "Duplicate", "Delete"]),
       "rows are Play set / Edit set / Duplicate / Delete");
const dangerCount = JSON.parse(await evalJs(
  `JSON.stringify(document.querySelectorAll(".as-row.as-row-danger").length)`));
assert(dangerCount === 1, "only the Delete row is danger-styled");
await shot(`${OUT}/01-sheet-open.png`);

// Edit set → opens the Item #3 editor seeded from this set; sheet closes.
assert((await clickRow("Edit set")) === true, "Edit set row is clickable");
await sleep(200);
const te = JSON.parse(await evalJs(getInExpr(["set-editor"])));
assert(te?.mode === "edit" && te?.["set-id"] === "set-17",
       "Edit set opens the editor in :edit mode for this set");
assert((await menuSetId()) === null, "sheet closes when a row is chosen");
await shot(`${OUT}/02-edit-from-sheet.png`);
await dispatch(kwExpr("set-editor", "cancel"));
await sleep(120);

// Duplicate → clones the set with a " (copy)" name, selects it.
await dispatch(kwExpr("set-menu", "open"), vecExpr([JSON.stringify("set-17")]));
await sleep(150);
assert((await clickRow("Duplicate")) === true, "Duplicate row is clickable");
await sleep(200);
assert((await setsCount()) === startCount + 1, "Duplicate adds one set");
const dup = JSON.parse(await evalJs(`
  (function () {
    var s = cljs.core.deref(ceol.web.state.app_state);
    var id = cljs.core.get.call(null, s, cljs.core.keyword.call(null, "active-set-id"));
    var set = cljs.core.get.call(null,
      cljs.core.get.call(null, s, cljs.core.keyword.call(null, "sets")), id);
    return JSON.stringify(cljs.core.clj__GT_js(set));
  })()
`));
console.log("duplicate:", dup);
assert(dup?.name === "Polkas Set 1 (copy)", `copy name (got ${dup?.name})`);
assert(JSON.stringify(dup?.["tune-ids"]) === "[1,2]", "copy carries the tune-ids");
assert((await menuSetId()) === null, "sheet closes after Duplicate");

// Duplicate again → " (copy 2)" collision bump.
await dispatch(kwExpr("set-menu", "open"), vecExpr([JSON.stringify("set-17")]));
await sleep(120);
await clickRow("Duplicate");
await sleep(200);
const dup2 = JSON.parse(await evalJs(`
  (function () {
    var s = cljs.core.deref(ceol.web.state.app_state);
    var id = cljs.core.get.call(null, s, cljs.core.keyword.call(null, "active-set-id"));
    return JSON.stringify(cljs.core.clj__GT_js(
      cljs.core.get.call(null, cljs.core.get.call(null, s,
        cljs.core.keyword.call(null, "sets")), id)));
  })()
`));
assert(/\(copy 2\)$/.test(dup2?.name), `second copy bumps to '(copy 2)' (got ${dup2?.name})`);

// Delete → routes through the B2 confirm modal (no immediate delete).
const beforeDelete = await setsCount();
await dispatch(kwExpr("set-menu", "open"), vecExpr([JSON.stringify("set-17")]));
await sleep(120);
assert((await clickRow("Delete")) === true, "Delete row is clickable");
await sleep(180);
const confirm = JSON.parse(await evalJs(getInExpr(["confirm"])));
assert(confirm?.title === "Delete set?", "Delete opens the B2 confirm modal");
assert((await setsCount()) === beforeDelete, "nothing deleted until confirmed");
assert((await menuSetId()) === null, "sheet closes when the confirm opens");
await shot(`${OUT}/03-delete-confirm.png`);

// Confirm via the modal's destructive button (runs on-confirm + closes).
const confirmClicked = await evalJs(
  `(function(){var b=document.querySelector(".modal-destructive");
     if(!b) return false; b.click(); return true;})()`);
assert(confirmClicked === true, "confirm modal destructive button exists");
await sleep(200);
assert((await setsCount()) === beforeDelete - 1, "confirming deletes the set");
assert((await evalJs(getInExpr(["confirm"]))) == null, "confirm modal closes after delete");
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
