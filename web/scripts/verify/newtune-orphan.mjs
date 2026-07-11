// Regression: a re-used tune id must NOT inherit a deleted tune's note or
// learned flag.
//
// Root cause it guards: delete! used to scrub only :custom-tunes, orphaning
// :tune-notes and :learned-tune-ids; and next-tune-id ignored those keyspaces.
// So delete-then-create re-allocated the id with the stranger's data attached.
// Fix: delete! scrubs every per-tune keyspace AND next-tune-id allocates above
// all of them.
//
// Run via:  ./verify.sh newtune-orphan

import {
  evalJs, dispatch, navigate, shot, sleep, kwExpr, vecExpr, getInExpr, shutdown,
} from "../cdp.mjs";

const OUT = process.env.SCENARIO_OUT ?? "verify/out/newtune-orphan";
const noteFor = (id) =>
  evalJs(`(function(){var v=cljs.core.get_in.call(null, cljs.core.deref.call(null, ceol.web.state.app_state), cljs.core.PersistentVector.fromArray([cljs.core.keyword.call(null,"tune-notes"), ${id}], true)); return v==null?"NIL":String(v);})()`);
const learned = (id) =>
  evalJs(`ceol.web.state.learned_QMARK_.call(null, cljs.core.deref.call(null, ceol.web.state.app_state), ${id}) === true`);

async function newTune(name) {
  await dispatch(kwExpr("tune-editor", "open-new"));
  await sleep(120);
  await dispatch(kwExpr("tune-editor", "update-draft"),
                 vecExpr([kwExpr("name"), JSON.stringify(name)]));
  await sleep(100);
  await dispatch(kwExpr("tune-editor", "save"));
  await sleep(300);
  return JSON.parse(await evalJs(getInExpr(["selected-tune-id"])));
}

await navigate("http://localhost:8280/index.html");
await sleep(600);
await dispatch(kwExpr("onboarding", "dismiss"));
await sleep(150);

// 1. Create a tune, give it a note + mark it learned.
const id1 = await newTune("Ghost Tune");
assert(id1 > 56, `first new tune clears the catalog keyspace (got ${id1})`);
await dispatch(kwExpr("notes", "update"), vecExpr([String(id1), JSON.stringify("secret practice note")]));
await sleep(120);
await dispatch(kwExpr("learned", "toggle"), vecExpr([String(id1)]));
await sleep(120);
assert((await noteFor(id1)) === "secret practice note", "note attached to the tune");
assert((await learned(id1)) === true, "tune marked learned");
await shot(`${OUT}/01-noted-and-learned.png`);

// 2. Delete it — every per-tune keyspace must be scrubbed.
await dispatch(kwExpr("tune", "delete"), vecExpr([String(id1)]));
await sleep(250);
assert((await noteFor(id1)) === "NIL", "note scrubbed on delete");
assert((await learned(id1)) === false, "learned flag scrubbed on delete");

// 3. Create another tune. Even if the id is re-used, it must be clean.
const id2 = await newTune("Fresh Tune");
assert((await noteFor(id2)) === "NIL", `re-used id ${id2} inherits no orphan note`);
assert((await learned(id2)) === false, `re-used id ${id2} inherits no orphan learned flag`);
await shot(`${OUT}/02-fresh-tune-clean.png`);

console.log(`PASS — delete scrubs all keyspaces; re-used id ${id2} stays clean (was ${id1})`);
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
