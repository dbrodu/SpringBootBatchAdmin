#!/bin/bash
#
# SessionStart hook for Claude Code on the web.
#
# Prepares the environment so tests, builds and GUI screenshots work out of the box:
#   1. warms the Maven dependency cache and builds the modules (so `mvn test` is fast/offline);
#   2. installs Playwright + Chromium used by scripts/capture-screenshots.mjs.
#
# Runs synchronously so the session only starts once everything is ready (no race conditions).
set -euo pipefail

# Only run inside the remote (Claude Code on the web) environment.
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  echo "[session-start] Local environment detected; skipping remote setup."
  exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel)}"

echo "[session-start] Warming Maven cache and building (this populates the cached container layer)…"
# `install -DskipTests` downloads every dependency/plugin and installs the starter into the local
# repo so the sample module resolves it; subsequent `mvn test` runs are fast and offline.
mvn -q -B -DskipTests install

echo "[session-start] Installing screenshot tooling (Playwright)…"
# Best-effort: never fail the session if the browser cannot be provisioned.
if npm --prefix scripts install >/tmp/session-start-npm.log 2>&1; then
  # Reuse the Chromium pre-installed in the environment (PLAYWRIGHT_BROWSERS_PATH) when present;
  # only download from the Playwright CDN as a fallback (it may be blocked by the network policy).
  if ( cd scripts && node -e "const fs=require('fs');process.exit(fs.existsSync(require('playwright').chromium.executablePath())?0:1)" ) 2>/dev/null; then
    echo "[session-start] Using pre-installed Chromium at ${PLAYWRIGHT_BROWSERS_PATH:-default cache}."
  else
    ( cd scripts && npx --yes playwright install chromium ) >/tmp/session-start-playwright.log 2>&1 \
      || echo "[session-start] WARN: Chromium not available and download failed; screenshots may be unavailable (see /tmp/session-start-playwright.log)."
  fi
else
  echo "[session-start] WARN: npm install for scripts failed (see /tmp/session-start-npm.log)."
fi

echo "[session-start] Done."
