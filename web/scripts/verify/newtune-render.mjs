// Regression: creating a new tune must NOT inherit a stale catalog ABC body.
//
// Root cause it guards: :abc-data / :abc-edits carry the full bundled catalog
// (ids 1..56) while :tunes holds only the 3 B5 seeds (1,3,7). next-tune-id
// allocated off :tunes alone returned 8 — which already had a leftover ABC
// body in :abc-data — so the new tune rendered a stranger's notation. The fix
// allocates above every tune-id keyspace.
//
// Run via:  ./verify.sh newtune-render

import {
  evalJs, dispatch, navigate, shot, sleep, kwExpr, vecExpr, getInExpr, shutdown,
} from "../cdp.mjs";

const OUT = process.env.SCENARIO_OUT ?? "verify/out/newtune-render";
const svgCount = (sel) =>
  evalJs(`(function(){var e=document.querySelector(${JSON.stringify(sel)});return e?e.querySelectorAll("svg").length:-1;})()`);
const present = (sel) => evalJs(`document.querySelector(${JSON.stringify(sel)}) != null`);
const editedAbc = (id) =>
  evalJs(`(function(){var v=ceol.web.state.edited_abc_for_tune.call(null, cljs.core.deref.call(null, ceol.web.state.app_state), ${id}); return v==null?"NIL":"STR";})()`);

await navigate("http://localhost:8280/index.html");
await sleep(600);
await dispatch(kwExpr("onboarding", "dismiss"));
await sleep(150);

// 1. Select a seed tune that HAS ABC (id 7 = "Out on the Ocean") and let abc.js render.
await dispatch(kwExpr("tune", "select"), vecExpr(["7"]));
await sleep(500);
assert((await present("#sheet-music")) === true, "#sheet-music container present for tune with ABC");
assert((await svgCount("#sheet-music")) >= 1, `abc.js rendered an SVG (got ${await svgCount("#sheet-music")})`);
await shot(`${OUT}/01-tune-with-abc.png`);

// 2. Open the new-tune editor, name it, save.
await dispatch(kwExpr("tune-editor", "open-new"));
await sleep(150);
await dispatch(kwExpr("tune-editor", "update-draft"),
               vecExpr([kwExpr("name"), JSON.stringify("Brand New Tune")]));
await sleep(100);
await dispatch(kwExpr("tune-editor", "save"));
await sleep(500);

const selId = JSON.parse(await evalJs(getInExpr(["selected-tune-id"])));
// New id must clear the whole catalog keyspace (ids 1..56), so > 56.
assert(selId > 56, `new tune id clears the catalog keyspace (got ${selId})`);
assert((await editedAbc(selId)) === "NIL", "new tune has no inherited ABC body");

// 3. Empty-state shows; NO stale SVG remains anywhere in .sheet-area.
assert((await present(".sheet-empty")) === true, "empty-state message shows for tune without ABC");
const stale = await svgCount(".sheet-area");
assert(stale === 0, `no stale sheet-music SVG after new-tune save (found ${stale})`);
await shot(`${OUT}/02-new-tune-clean.png`);

// 4. Round-trip: selecting a tune WITH ABC must re-mount #sheet-music and
//    render fresh (the keyed empty node is destroyed, a new container mounts).
await dispatch(kwExpr("tune", "select"), vecExpr(["1"]));
await sleep(500);
assert((await present(".sheet-empty")) === false, "empty-state gone after selecting a tune with ABC");
assert((await present("#sheet-music")) === true, "#sheet-music re-mounts for the new selection");
assert((await svgCount("#sheet-music")) === 1, `exactly one fresh SVG renders (got ${await svgCount("#sheet-music")})`);
assert((await svgCount(".sheet-area")) === 1, "no leftover SVG from the empty round-trip");
await shot(`${OUT}/03-reselect-with-abc.png`);

console.log("PASS — new tune shows empty sheet, no inherited notation, round-trip clean");
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
