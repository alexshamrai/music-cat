#!/usr/bin/env bash
# Run the app locally against the LOCAL fake sheet (./data/fake-sheets.json).
# No Google, no credentials. In-memory H2 mirrors prod's rebuild-on-boot behaviour.
# Uses the DEFAULT profile (not cloud) so the default admin/admin credentials are allowed.
set -euo pipefail

JAR=$(ls backend/build/libs/music-cat-*.jar 2>/dev/null | head -1)
if [ -z "${JAR:-}" ]; then
  echo "Build the jar first: ./gradlew :backend:bootJar" >&2
  exit 1
fi

if [ ! -f ./data/fake-sheets.json ]; then
  echo "WARNING: ./data/fake-sheets.json not found — the app will boot with an EMPTY catalog." >&2
  echo "Seed it first: SHEETS_SPREADSHEET_ID=<id> ./snapshot-prod-to-fake.sh (or hand-write the file)." >&2
fi

MUSIC_CAT_SHEETS_ENABLED=true \
MUSIC_CAT_SHEETS_MODE=fake \
MUSIC_CAT_USER="${MUSIC_CAT_USER:-admin}" \
MUSIC_CAT_PASSWORD="${MUSIC_CAT_PASSWORD:-admin}" \
java -jar "$JAR" \
  --spring.datasource.url='jdbc:h2:mem:music-cat-fake;DB_CLOSE_DELAY=-1' \
  --music-cat.sheets.fake-file=./data/fake-sheets.json
