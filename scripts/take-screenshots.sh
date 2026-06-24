#!/bin/bash
#
# Builds the app, starts the sample, runs a quick smoke test of the GUI, seeds some demo data so the
# screens are meaningful, captures screenshots of every GUI page, then stops the app.
#
# Usage: scripts/take-screenshots.sh
set -euo pipefail

ROOT="${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel)}"
cd "$ROOT"

PORT="${PORT:-8080}"
BASE="http://localhost:${PORT}/batch-admin"
API="${BASE}/api"
JAR="batch-admin-sample/target/spring-boot-batch-admin-sample-0.1.0-SNAPSHOT.jar"

if [ ! -f "$JAR" ]; then
  echo "[screenshots] Building the application…"
  mvn -q -B -DskipTests install
fi

echo "[screenshots] Ensuring Playwright + Chromium are available…"
( cd scripts && npm install >/tmp/screenshots-npm.log 2>&1 \
    && npx --yes playwright install chromium >/tmp/screenshots-pw.log 2>&1 ) \
  || echo "[screenshots] WARN: could not (re)install Playwright/Chromium; relying on existing install."

echo "[screenshots] Starting the sample application on port ${PORT}…"
java -jar "$JAR" --server.port="${PORT}" >/tmp/batch-admin-app.log 2>&1 &
APP_PID=$!
cleanup() { kill "$APP_PID" 2>/dev/null || true; }
trap cleanup EXIT

echo "[screenshots] Waiting for the application to be ready…"
for _ in $(seq 1 60); do
  if curl -sf "${API}/jobs" >/dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 1
done
if [ "${ready:-0}" != "1" ]; then
  echo "[screenshots] ERROR: application did not start. Last log lines:"
  tail -n 30 /tmp/batch-admin-app.log
  exit 1
fi

echo "[screenshots] Smoke-testing the GUI pages…"
for path in "" "/jobs" "/jobs/new" "/executions" "/schedules"; do
  code=$(curl -s -o /dev/null -w "%{http_code}" "${BASE}${path}")
  echo "  GET ${BASE}${path} -> ${code}"
  [ "$code" = "200" ] || { echo "[screenshots] ERROR: ${BASE}${path} returned ${code}"; exit 1; }
done

echo "[screenshots] Seeding demo data…"
ct='Content-Type: application/json'
curl -s -X POST "${API}/jobs" -H "$ct" -d '{
  "jobName":"nightlyExport","description":"Composed from building blocks",
  "steps":[{"name":"extract","type":"log","properties":{"message":"extracting"}},
           {"name":"wait","type":"sleep","properties":{"millis":300}},
           {"name":"notify","type":"log","properties":{"message":"done"}}]}' >/dev/null || true
curl -s -X POST "${API}/jobs/dailyReportJob/executions" -H "$ct" -d '{"parameters":{"region":"EU"}}' >/dev/null || true
curl -s -X POST "${API}/jobs/nightlyExport/executions" -H "$ct" -d '{}' >/dev/null || true
curl -s -X POST "${API}/jobs/housekeepingJob/executions" -H "$ct" -d '{}' >/dev/null || true
curl -s -X POST "${API}/schedules" -H "$ct" -d '{"jobName":"housekeepingJob","cron":"0 0 2 * * *","description":"nightly cleanup"}' >/dev/null || true
curl -s -X POST "${API}/schedules" -H "$ct" -d '{"jobName":"dailyReportJob","cron":"0 30 6 * * *","description":"daily report"}' >/dev/null || true
sleep 2

echo "[screenshots] Capturing screenshots…"
node scripts/capture-screenshots.mjs

echo "[screenshots] Done. See docs/screenshots/."
