// Scenario: B3 — mobile backup-status banner.
//
// The export/import feedback banner rendered only inside the desktop sidebar
// (display:none on mobile). B3 renders it as a fixed mobile toast too. Real
// export/import can't run headless (download / file picker), so we drive
// backup/set-status! directly — the same fn export!/import! call.
//
// Run via:  ./verify.sh b3

import {
  evalJs, dispatch, navigate, shot, sleep, kwExpr, getInExpr, shutdown,
} from "../cdp.mjs";

const OUT = process.env.SCENARIO_OUT ?? "verify/out/b3";

// Read the mobile toast's rendered banner (the one inside .mobile-backup-status).
const mobileBanner = async () => JSON.parse(await evalJs(`
  (function () {
    var el = document.querySelector(".mobile-backup-status .backup-status");
    if (!el) return JSON.stringify(null);
    return JSON.stringify({
      kind: el.className,
      icon: (el.querySelector(".backup-status-icon")||{}).textContent,
      msg:  (el.querySelector(".backup-status-msg")||{}).textContent
    });
  })()
`));
const setStatus = (kind, msg) => evalJs(
  `ceol.web.backup.set_status_BANG_(cljs.core.keyword.call(null, ${JSON.stringify(kind)}), ${JSON.stringify(msg)})`);

await navigate("http://localhost:8280/index.html");
await sleep(500);
await dispatch(kwExpr("onboarding", "dismiss"));
await sleep(150);

// No status → no toast in the DOM.
assert((await mobileBanner()) === null, "no toast before any export/import");

// Success status (as backup/import! would set on a good restore).
await setStatus("success", "Imported 3 tunes, 1 set.");
await sleep(150);
assert(JSON.parse(await evalJs(getInExpr(["backup-status"]))) != null,
       ":backup-status state is set");
const ok = await mobileBanner();
console.log("success toast:", ok);
assert(ok != null, "mobile toast renders the banner on success");
assert(ok.kind.includes("kind-success"), "success banner carries kind-success");
assert(ok.icon === "✓", "success icon is ✓");
assert(ok.msg === "Imported 3 tunes, 1 set.", "banner shows the message");
await shot(`${OUT}/01-success.png`);

// Error status (as a failed import would set).
await setStatus("error", "Invalid backup file.");
await sleep(150);
const err = await mobileBanner();
console.log("error toast:", err);
assert(err.kind.includes("kind-error"), "error banner carries kind-error");
assert(err.icon === "!", "error icon is !");
assert(err.msg === "Invalid backup file.", "error message shows");
await shot(`${OUT}/02-error.png`);

// The toast is fixed-position (a real overlay toast, not inline in the flow).
const positioned = await evalJs(
  `getComputedStyle(document.querySelector(".mobile-backup-status")).position === "fixed"`);
assert(positioned === true, "mobile toast is position:fixed");

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
