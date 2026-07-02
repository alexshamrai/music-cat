# music-cat

Personal music catalog for browsing, rating, tagging, and randomly picking albums
("Surprise Me"). Spring Boot backend + React frontend, packaged as a single jar.
Google Sheets serves as the persistent, human-editable data store; the embedded H2
database is a runtime cache rebuilt from Sheets on boot.

## Prerequisites

- **Java 25** (e.g. via [sdkman](https://sdkman.io): `sdk install java 25-open`)
- No local Node.js needed for the jar build — Gradle downloads its own Node
- `catalog.json` at the project root (one-time scanner output, already committed)

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
| `music-cat.catalog-path` | `../catalog.json` | Scanner output imported on first boot (empty DB) |
| `music-cat.sheets.enabled` | `false` | Master switch for the Google Sheets sync |
| `music-cat.sheets.credentials-path` | `../config/google-credentials.json` (env `SHEETS_CREDENTIALS_PATH`) | Service-account JSON key |
| `music-cat.sheets.spreadsheet-id` | — (env `SHEETS_SPREADSHEET_ID`) | Spreadsheet with tabs `Artists`, `Albums`, `Songs` |

With Sheets enabled, every mutation pushes the catalog to the spreadsheet after the
DB commit, and an empty database restores itself from the spreadsheet on boot.
Sync endpoints: `POST /api/catalog/sync/push`, `POST /api/catalog/sync/pull`,
`GET /api/catalog/sync/status`.

Offline backups: `GET /api/catalog/export/json` (enriched catalog),
`GET /api/catalog/export/csv` (ZIP with artists.csv + albums.csv).
