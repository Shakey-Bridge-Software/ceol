#!/usr/bin/env bash
set -euo pipefail

# Build the production release, cache-bust (fingerprint) it, zip it into
# ceol.zip, then deploy to the server:
#   scp ceol.zip ->  ~/ on the server, unzip -> /var/www/html
#
# Deploy target comes from DEPLOY_HOST in .env (an SSH config alias, e.g. ceol-vm).
# Set DEPLOY_SKIP=1 to build+zip without deploying.
#
# Caching / cache-busting: the build is post-processed by fingerprint.sh so
# asset URLs (js/main.<hash>.js, css/style.<hash>.css) change on every release.
# Pair this with the nginx cache headers in deploy/nginx-cache.conf so browsers
# fetch fresh files automatically (no hard refresh needed).
ROOT="$(cd "$(dirname "$0")" && pwd)"
PUBLIC="$ROOT/web/resources/public"
ZIP="$ROOT/ceol.zip"

# Source deployment config from .env if present.
if [ -f "$ROOT/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  . "$ROOT/.env"
  set +a
fi

# 1. Fresh production build (writes to web/resources/public/)
"$ROOT/build.sh"

# 2. Cache-bust the build into a staging dir (leaves web/resources/public
#    untouched for the local dev/watch flow).
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT
"$ROOT/fingerprint.sh" "$PUBLIC" "$STAGE/ceol"

# 3. Zip the staged release so index.html sits at the zip root.
rm -f "$ZIP"
(cd "$STAGE/ceol" && zip -r -q "$ZIP" .)

echo "Release complete: $ZIP"

# 4. Deploy to the server.
if [ "${DEPLOY_SKIP:-}" = "1" ]; then
  echo "Deploy skipped (DEPLOY_SKIP=1)."
  exit 0
fi

if [ -z "${DEPLOY_HOST:-}" ]; then
  echo "error: DEPLOY_HOST is not set (add it to .env as an SSH config alias)." >&2
  exit 1
fi

echo "Deploying $ZIP to $DEPLOY_HOST:/var/www/html ..."
scp -q "$ZIP" "$DEPLOY_HOST:~/ceol.zip"
ssh "$DEPLOY_HOST" '
  set -e
  rm -rf /tmp/ceol-release
  mkdir -p /tmp/ceol-release
  unzip -q -o ~/ceol.zip -d /tmp/ceol-release
  sudo rsync -a --delete /tmp/ceol-release/ /var/www/html/
  rm -rf /tmp/ceol-release ~/ceol.zip
'
echo "Deploy complete: $DEPLOY_HOST:/var/www/html"


