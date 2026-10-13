#!/bin/sh
# Serve the flick plugin repo on http://127.0.0.1:8090 so the app can subscribe to it.
# Edit a plugin, reload the app's repo, swipe. No build in the loop.
set -e

PORT="${1:-8090}"
REPO="$(cd "$(dirname "$0")/../repo" && pwd)"

echo "serving $REPO on http://127.0.0.1:$PORT/repo.json"
cd "$REPO"
exec python3 -m http.server "$PORT"
