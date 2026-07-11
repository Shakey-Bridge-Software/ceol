#!/usr/bin/env bash
# Drive a verification scenario against the running shadow-cljs dev server.
#
# Usage: ./verify.sh <scenario-name>
#   ./verify.sh b1         # runs verify/b1.mjs
#
# Pre-flight: shadow-cljs must already be serving the app at :8280 (run
# `./node_modules/.bin/shadow-cljs watch app` first). The script does NOT
# start the daemon — keeping it user-controlled avoids "already started"
# errors and lets you re-run scenarios against an in-flight build.
#
# Artifacts land in verify/out/<scenario>/ (gitignored).

set -euo pipefail

SCENARIO="${1:-}"
if [[ -z "$SCENARIO" ]]; then
  echo "usage: ./verify.sh <scenario>" >&2
  echo "available:" >&2
  ls verify/*.mjs 2>/dev/null | sed 's|verify/|  |;s|\.mjs$||' >&2 || echo "  (none — add verify/<name>.mjs)" >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

SCENARIO_FILE="verify/${SCENARIO}.mjs"
if [[ ! -f "$SCENARIO_FILE" ]]; then
  echo "no scenario at $SCENARIO_FILE" >&2
  exit 1
fi

# Confirm dev server is up
if ! curl -sf -o /dev/null http://localhost:8280/index.html; then
  echo "shadow-cljs not serving on :8280 — run ./node_modules/.bin/shadow-cljs watch app first" >&2
  exit 1
fi

# Chrome path is macOS-specific; adjust if you're on Linux.
CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
if [[ ! -x "$CHROME" ]]; then
  echo "Chrome not found at $CHROME — edit verify.sh" >&2
  exit 1
fi

PROFILE="verify/out/${SCENARIO}/.profile"
OUT_DIR="verify/out/${SCENARIO}"
mkdir -p "$OUT_DIR"

# Kill stale verification chromes (matches our user-data-dir path)
pkill -f "user-data-dir=.*/web/scripts/verify/out/" 2>/dev/null || true
sleep 0.5

# Fresh profile every run — deterministic localStorage
rm -rf "$PROFILE"
mkdir -p "$PROFILE"

# Launch headless on a fixed remote-debugging port
PORT=9222
"$CHROME" \
  --headless=new \
  --disable-gpu \
  --remote-debugging-port="$PORT" \
  --user-data-dir="$PROFILE" \
  about:blank >/dev/null 2>&1 &
CHROME_PID=$!

# Wait for CDP to come up
for _ in {1..20}; do
  if curl -sf "http://localhost:${PORT}/json/version" >/dev/null; then break; fi
  sleep 0.25
done

# Grab the page target's WS URL
PAGE_WS=$(curl -s "http://localhost:${PORT}/json" |
  python3 -c 'import sys,json; ts=json.load(sys.stdin); print([t["webSocketDebuggerUrl"] for t in ts if t.get("type")=="page"][0])')

# Hand off to the scenario (passes scenario-relative out dir on argv[3])
RC=0
SCENARIO_OUT="$OUT_DIR" node "$SCENARIO_FILE" "$PAGE_WS" "$OUT_DIR" || RC=$?

# Cleanup chrome
kill "$CHROME_PID" 2>/dev/null || true
wait "$CHROME_PID" 2>/dev/null || true

if [[ "$RC" -ne 0 ]]; then
  echo "scenario exited with code $RC" >&2
  exit "$RC"
fi

echo "✓ scenario '$SCENARIO' passed — artifacts in $OUT_DIR"
