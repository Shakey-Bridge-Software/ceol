// Scenario: Wave 1 E — settings Export/Import as list rows (design ddeLd).
//
// The BACKUP section's Export/Import were label + brown action-button rows. E
// restyles them as tappable icon + text + chevron list rows (leading download/
// upload icon, title + subtitle, trailing chevron), same handlers. Import still
// routes through the B2 confirm modal.
//
// Run via:  ./verify.sh waveE

import {
  evalJs, dispatch, navigate, shot, sleep, kwExpr, shutdown,
} from "../cdp.mjs";

const OUT = process.env.SCENARIO_OUT ?? "verify/out/waveE";
const present = (sel) => evalJs(`document.querySelector(${JSON.stringify(sel)}) != null`);
const count = (sel) => evalJs(`document.querySelectorAll(${JSON.stringify(sel)}).length`);
const rowPart = (i, part) => evalJs(`(function(){
  var r=document.querySelectorAll(".settings-list-row")[${i}];
  if(!r) return null;
  var e=r.querySelector(${JSON.stringify(part)});
  return e?e.textContent.trim():null;
})()`);
const rowHas = (i, sel) => evalJs(`(function(){
  var r=document.querySelectorAll(".settings-list-row")[${i}];
  return !!(r && r.querySelector(${JSON.stringify(sel)}));
})()`);

await navigate("http://localhost:8280/index.html");
await sleep(600);
await dispatch(kwExpr("onboarding", "dismiss"));
await sleep(150);
await dispatch(kwExpr("settings", "open"));
await sleep(200);

assert((await present(".settings-view")) === true, "settings view opens");
assert((await count(".settings-list-row")) === 2, `BACKUP has two list rows (got ${await count(".settings-list-row")})`);
assert((await present(".settings-list-divider")) === true, "rows are separated by a divider");

// Export row: download icon + title + subtitle + chevron.
assert((await rowPart(0, ".settings-list-title")) === "Export backup",
       `export row title (got ${await rowPart(0, ".settings-list-title")})`);
assert((await rowPart(0, ".settings-list-sub")) === "Save all data as a .edn file", "export row subtitle");
assert((await rowHas(0, ".settings-list-icon svg")) === true, "export row has a leading icon");
assert((await rowHas(0, ".settings-list-chevron")) === true, "export row has a trailing chevron");

// Import row.
assert((await rowPart(1, ".settings-list-title")) === "Import backup",
       `import row title (got ${await rowPart(1, ".settings-list-title")})`);
assert((await rowPart(1, ".settings-list-sub")) === "Restore from a .edn file", "import row subtitle");
assert((await rowHas(1, ".settings-list-icon svg")) === true, "import row has a leading icon");
await shot(`${OUT}/01-rows.png`);

// Tapping the Import row routes through the B2 confirm modal (wiring intact).
await evalJs(`document.querySelectorAll(".settings-list-row")[1].click()`);
await sleep(200);
assert((await present(".modal")) === true, "import opens the confirm modal");
assert((await evalJs(`document.querySelector(".modal-title").textContent.trim()`)) === "Replace all data?",
       "confirm modal warns before replacing data");
await shot(`${OUT}/02-import-confirm.png`);
await dispatch(kwExpr("confirm", "cancel"));
await sleep(120);
assert((await present(".modal")) === false, "cancel dismisses the modal");

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
