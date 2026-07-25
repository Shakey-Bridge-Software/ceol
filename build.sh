#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/web"
./node_modules/.bin/shadow-cljs release app
echo "Build complete: web/resources/public/"
