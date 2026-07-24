# music-cat

Personal music catalog for browsing, rating, tagging, and randomly picking albums
("Surprise Me"). Spring Boot backend + React frontend, packaged as a single jar.
Google Sheets serves as the persistent, human-editable data store; the embedded H2
database is a runtime cache rebuilt from Sheets on boot.

## Prerequisites

- **Java 25** (e.g. via [sdkman](https://sdkman.io): `sdk install java 25-open`)
- No local Node.js needed for the jar build — Gradle downloads its own Node

## Development (two terminals)

```bash
# Terminal 1: backend on :8080
./gradlew :backend:bootRun

# Terminal 2: frontend on :5173 with hot reload (proxies /api to :8080)
cd frontend && npm run dev
```

- Swagger UI: http://localhost:8080/swagger-ui
- H2 console: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:file:./data/music-cat`)

## Production build (single jar)

```bash
./gradlew :backend:bootJar
java -jar backend/build/libs/music-cat-0.0.1-SNAPSHOT.jar
```

The build compiles the React app (Gradle downloads Node itself), copies it into the
jar's static resources, and serves UI + API together on http://localhost:8080.

## Configuration

All app settings live under the `music-cat.*` prefix in
`backend/src/main/resources/application.yml`:

| Property | Default | Purpose |
|---|---|---|
| `music-cat.sheets.enabled` | `false` | Master switch for the Google Sheets sync |
| `music-cat.sheets.mode` | `google` | `google` (real Sheets API) or `fake` (local file-backed `FakeSheetsClient`, no credentials) — see "Local fake Sheets" below |
| `music-cat.sheets.credentials-path` | `../config/google-credentials.json` (env `SHEETS_CREDENTIALS_PATH`) | Service-account JSON key (mode `google` only) |
| `music-cat.sheets.spreadsheet-id` | — (env `SHEETS_SPREADSHEET_ID`) | Spreadsheet with tabs `Artists`, `Albums`, `Songs` (mode `google` only) |
| `music-cat.auth.username` | `admin` (env `MUSIC_CAT_USER`) | HTTP Basic username protecting every path |
| `music-cat.auth.password` | `admin` (env `MUSIC_CAT_PASSWORD`) | HTTP Basic password — **must** be overridden for any non-local deployment; the app refuses to start under the `cloud` profile with the default |

With Sheets enabled, every mutation pushes the catalog to the spreadsheet after the
DB commit, and an empty database restores itself from the spreadsheet on boot.
Sync endpoints: `POST /api/catalog/sync/push`, `POST /api/catalog/sync/pull`,
`GET /api/catalog/sync/status`.

Offline backups: `GET /api/catalog/export/json` (enriched catalog),
`GET /api/catalog/export/csv` (ZIP with artists.csv + albums.csv).

State-changing requests (`POST`/`PUT`/`PATCH`/`DELETE`) require an
`X-Requested-With` header — the frontend sends it automatically; a plain HTML
form cannot, which is the point (blocks blind cross-site CSRF against a
Basic-auth-only API, since browsers cache and auto-attach Basic credentials
per-origin regardless of which page initiated the request).

## Local fake Sheets

The app has no local seed data of its own — Google Sheets is the only inbound data
path. For offline dev/testing, `music-cat.sheets.mode=fake` swaps in a file-backed
`FakeSheetsClient` (`./data/fake-sheets.json`) with no network calls and no
credentials, so it can never touch production:

```bash
# One-time (or refresh): read-only snapshot of the LIVE spreadsheet into the fake file
SHEETS_SPREADSHEET_ID=<prod id> ./snapshot-prod-to-fake.sh

# Run entirely offline against the fake file (default profile, admin/admin, in-memory H2)
./run-fake.sh
```

`snapshot-prod-to-fake.sh` only ever calls the Sheets API's read method — it cannot
write back to the live spreadsheet. Both scripts expect
`backend/build/libs/music-cat-*.jar` to already exist (`./gradlew :backend:bootJar`).

## Live deployment

The app runs on **Google Cloud Run** (`europe-west1`), scaled to zero when idle,
with Google Sheets as the persistent store — no database to manage, no server to
patch. Deploy with:

```bash
export MUSIC_CAT_USER=<username>
export MUSIC_CAT_PASSWORD=<strong-password>       # never admin/admin — the app refuses to boot with it
export SHEETS_SPREADSHEET_ID=<id>
./deploy.sh
```

`deploy.sh` cross-builds for `linux/amd64` (needed on an Apple Silicon Mac),
pushes to Artifact Registry, and deploys to Cloud Run with `--allow-unauthenticated`
at the platform level — the app's own HTTP Basic auth is the real access control,
not Cloud Run IAM.

**Automated deploys (GitHub Actions).** Pushing/merging to `master` triggers
`.github/workflows/deploy.yml`: it runs the backend tests, builds the image on an
amd64 runner (no cross-build needed), and deploys a new Cloud Run revision —
mirroring `deploy.sh`'s flags. Pull requests run `.github/workflows/ci.yml`
(backend tests + frontend build) as a merge gate. GCP auth is keyless via
Workload Identity Federation (no service-account key stored in GitHub); the live
`MUSIC_CAT_USER` / `MUSIC_CAT_PASSWORD` / `SHEETS_SPREADSHEET_ID` values come from
repository secrets. `deploy.sh` remains available for manual/emergency local deploys.

**Editing data by hand.** The spreadsheet is editable directly in the browser —
that's the point of using Sheets as the store. After a hand edit, call
`POST /api/catalog/sync/pull` to load it into the running app. **Mind the
direction**: `pull` overwrites the database from the sheet; `push` overwrites the
sheet from the database. Calling `push` right after a hand edit discards it.
Also note that `pull` fully reloads the database, so numeric IDs are not stable
across a pull — look albums/artists up by name if you scripted against an ID
before a pull. A tag with **zero** artist/album associations has no representation
in the sheet (tags only persist as a value on Artist/Album rows) and is silently
dropped on the next pull — attach it to something to make it durable.

**Cold starts.** With `min-instances=0`, the first request after ~15 minutes idle
triggers a cold start: container boot + a full restore from Sheets (~176 artists /
2830 albums / 30876 songs). Expect roughly 10-15 seconds. The app holds that first
request open (rather than failing fast) specifically so Cloud Run keeps full CPU
allocated to the container until the restore finishes — Cloud Run only allocates
CPU during active request processing, so a fail-fast response would have starved
the restore of CPU and made it dramatically slower.

**Free-tier guardrails** (all already configured, verify periodically):
- $1/month billing budget alert on `music-cat-hosting` (50/90/100% thresholds)
- Artifact Registry cleanup policy on the `music-cat` repo (keeps the 2 most
  recent images, deletes anything older than 30 days) — keeps storage under the
  0.5 GB free allowance
- Container/vulnerability scanning is **not** enabled (it bills per pushed image)
- The Cloud Run service account needs `roles/secretmanager.secretAccessor` on the
  `sheets-sa-key` secret (grant once; `deploy.sh` does not do this for you)
- GCP free-trial billing account must be upgraded before day 90, or resources get
  deleted after a 30-day grace period — upgrading does **not** start charges while
  usage stays in the free tier
