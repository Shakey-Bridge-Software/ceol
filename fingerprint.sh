#!/usr/bin/env bash
set -euo pipefail

# ---------------------------------------------------------------------------
# fingerprint.sh — cache-bust a shadow-cljs production build for static deploy.
#
# Takes the shadow-cljs `release` output (web/resources/public) and produces a
# deployment directory whose asset URLs change on every release, so browsers
# and intermediaries are guaranteed to fetch fresh code without a hard refresh:
#
#   * js/main.js        -> js/main.<hash16>.js
#   * css/modules/*     -> merged into a single css/style.<hash16>.css
#   * index.html        -> rewritten to reference the hashed files above
#   * js/cljs-runtime/  -> dropped (dev-only; not referenced by the release bundle)
#   * *.map sourcemaps  -> dropped
#
# The source directory is left untouched, so the local dev / watch flow
# (which expects the stable js/main.js + css/style.css + @import'd modules)
# keeps working.
#
#   usage: fingerprint.sh <source-public-dir> <dest-dir>
# ---------------------------------------------------------------------------
usage() { echo "usage: $0 <source-public-dir> <dest-dir>" >&2; exit 1; }
[ $# -eq 2 ] || usage

SRC="$1"
DST="$2"

[ -d "$SRC" ] || { echo "error: source dir not found: $SRC" >&2; exit 1; }

rm -rf "$DST"
mkdir -p "$DST"

# Stage the build, excluding dev-only artifacts (not referenced by the bundle).
rsync -a \
  --exclude 'js/cljs-runtime' \
  --exclude '*.map' \
  "$SRC/" "$DST/"

JS_FILE="$DST/js/main.js"
CSS_FILE="$DST/css/style.css"

[ -f "$JS_FILE" ] || { echo "error: no js/main.js in build output (did the release build run?)" >&2; exit 1; }
[ -f "$CSS_FILE" ] || { echo "error: no css/style.css in build output" >&2; exit 1; }

# -- JS fingerprint on the compiled bundle -------------------------------
js_hash=$(shasum -a 256 "$JS_FILE" | awk '{print $1}' | cut -c1-16)
mv "$JS_FILE" "$DST/js/main.${js_hash}.js"
rm -f "$DST/js/manifest.edn"   # shadow-cljs build bookkeeping; not needed at runtime

# -- CSS: merge the entry + modules (in @import order) into one file ------
{
  # Drop the @import lines themselves; keep any inline rules in style.css.
  sed '/^@import[[:space:]]/d' "$CSS_FILE"
  # Replay the module files in the exact @import order (CSS is order-sensitive).
  while IFS= read -r mod; do
    [ -n "$mod" ] && cat "$DST/css/$mod"
  done < <(grep -oE "modules/[A-Za-z0-9_.-]+\.css" "$CSS_FILE")
} > "$DST/css/style.concat.css"

css_hash=$(shasum -a 256 "$DST/css/style.concat.css" | awk '{print $1}' | cut -c1-16)
mv "$DST/css/style.concat.css" "$DST/css/style.${css_hash}.css"
rm -f "$CSS_FILE"
rm -rf "$DST/css/modules"

# -- Rewrite index.html to reference the hashed assets --------------------
sed -i.bak \
  -e "s#href=\"/css/style.css\"#href=\"/css/style.${css_hash}.css\"#" \
  -e "s#src=\"/js/main.js\"#src=\"/js/main.${js_hash}.js\"#" \
  "$DST/index.html"
rm -f "$DST/index.html.bak"

echo "fingerprinted build -> $DST"
echo "  js:  /js/main.${js_hash}.js"
echo "  css: /css/style.${css_hash}.css"
