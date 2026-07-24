#!/usr/bin/env bash
# Read-only snapshot of the LIVE Google Sheet into ./data/fake-sheets.json.
# Only READS the three tabs — it never writes to Google. Requires service-account
# credentials with (at least read) access to the prod spreadsheet.
#
#   SHEETS_SPREADSHEET_ID=<prod id> ./snapshot-prod-to-fake.sh
#
# Uses the DEFAULT profile (not cloud) because the cloud profile refuses to start with the
# default admin/admin credentials.
#
# All three MUSIC_CAT_SHEETS_* vars below are required together: ENABLED=true is what
# actually creates a SheetsClient bean (snapshot=true alone crashes at startup with
# NoSuchBeanDefinitionException — SnapshotRunner has nothing to inject), and MODE must stay
# "google" (not "fake", which would just snapshot the fake file into itself).
set -euo pipefail

JAR=$(ls backend/build/libs/music-cat-*.jar 2>/dev/null | head -1)
if [ -z "${JAR:-}" ]; then
  echo "Build the jar first: ./gradlew :backend:bootJar" >&2
  exit 1
fi

: "${SHEETS_SPREADSHEET_ID:?set SHEETS_SPREADSHEET_ID to the PROD spreadsheet id}"
: "${SHEETS_CREDENTIALS_PATH:=config/google-credentials.json}"

MUSIC_CAT_SHEETS_ENABLED=true \
MUSIC_CAT_SHEETS_MODE=google \
MUSIC_CAT_SHEETS_SNAPSHOT=true \
SHEETS_CREDENTIALS_PATH="$SHEETS_CREDENTIALS_PATH" \
SHEETS_SPREADSHEET_ID="$SHEETS_SPREADSHEET_ID" \
java -jar "$JAR" \
  --spring.main.web-application-type=none \
  --spring.datasource.url='jdbc:h2:mem:snapshot;DB_CLOSE_DELAY=-1' \
  --music-cat.sheets.fake-file=./data/fake-sheets.json

echo "Wrote ./data/fake-sheets.json"
