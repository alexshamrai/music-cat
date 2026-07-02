# Claude Code Task List — Music Catalog App

Each task below is a self-contained prompt for Claude Code. Run them sequentially.
After each task, verify ALL acceptance criteria before moving to the next.

**Goal of this phase:** take the finished local app (Tasks 0–8) live on Google Cloud Run's
free tier, with Google Sheets as the persistent source of truth.

**Architecture decisions (made 2026-06-11, after researching free-tier options):**

- **Host: Google Cloud Run**, region `europe-west1`, 1 vCPU / 1 GiB, `min-instances=0`,
  `max-instances=1`, startup CPU boost on. Scale-to-zero keeps a single-user app at $0
  indefinitely. The tradeoff: a cold start (~2–10s optimized) after ~15 min idle.
- **Persistent store: Google Sheets** (one spreadsheet, three tabs: Artists, Albums, Songs).
  H2 is a throwaway runtime cache rebuilt from Sheets on every boot. This is mandatory:
  Cloud Run's filesystem is in-memory tmpfs, wiped on every scale-to-zero. The sheet is
  also the human-editable interface — edit it in the browser, then pull-sync.
- **Write strategy: synchronous write-through.** Cloud Run (request-based billing)
  throttles CPU to ~0 between requests, so background `@Scheduled` flushes are unreliable.
  Mutations push to Sheets *during* the request (after commit). Artists+Albums sheets are
  small (~3,200 rows, one batch call); the Songs sheet (~31K rows) is rewritten only on
  structural changes.
- **Auth:** the `*.run.app` URL is public; a single-user HTTP Basic login (creds from env
  vars) protects UI and API from bots burning free-tier CPU.
- **GCP layout: two projects.** `music-cat-hosting` (billing attached; Cloud Run +
  Artifact Registry + Secret Manager) and `music-cat-sheets` (NO billing; Sheets API +
  service account) — an unbilled project cannot be charged when Workspace API overage
  billing ships later in 2026.
- **$0 guardrails:** budget alert at $1 (GCP has no hard spend cap), Artifact Registry
  cleanup policy (images are ~300–400 MB; >0.5 GB stored starts billing pennies),
  upgrade the billing account before trial day 90 (or the account auto-closes), keep
  Container Scanning disabled, stay on the free `*.run.app` URL (custom domain via load
  balancer costs ~$18/mo).

**Execution conventions for every task:**
- TDD where the task touches Java: write the failing test first, then implement.
- Run `./gradlew :backend:test` (from repo root — there is no backend/gradlew) before
  claiming a task done; all tests must pass.
- Commit at the end of each task (and at natural checkpoints inside large tasks).
- `year` is an H2 reserved word — keep it quoted in SQL/JPA.
- Genre is the `io.github.alexshamrai.domain.Genre` enum; sheets/exports store its
  `displayName` (e.g. "Hard Rock & Metal"); parse with `Genre.fromDisplayName(...)`.

---

## Completed — Tasks 0–8 ✅

Original prompts are in git history (`git show 0147f9d:task-list.md`). Summary:

| Task | Delivered |
|---|---|
| 0 | `music_scanner.py` → `catalog.json` (176 artists, 2,830 albums, ~31K tracks, 7 genres) |
| 1 | Monorepo, Spring Boot 4.0.2 / Java 25 backend, Flyway `V1__init_schema.sql`, JPA entities, repositories |
| 2 | `CatalogImportService` (JSON→DB), `CatalogAutoImporter` (auto-import on empty DB), `POST /api/catalog/import` |
| 3 | Artist CRUD API + favorite + tags, `GlobalExceptionHandler` |
| 4 | Album CRUD API + grade + favorite + tags, Tag CRUD API, `AlbumFilterParams` |
| 5 | Browse API (genres/tags/stats/favorites), Random pick API, `AlbumSpecs` JPA Specifications |
| 6 | React 19 + Vite 7 + TanStack Query shell, Sidebar routing, Dashboard with live stats |
| 7 | Shared components (StarRating, FavoriteToggle, TagBadge, FilterBar, AlbumCard, ArtistCard), Browse/Artists/Albums pages |
| 8 | Random Pick ("Surprise Me"), Favorites, Tags pages |

Existing facts the new tasks rely on:
- `WebConfig.java` exists in `io.github.alexshamrai.config` but only registers a
  String→Genre converter. SPA forwarding does NOT exist yet.
- `vite.config.ts` already builds into `backend/src/main/resources/static` (`emptyOutDir: true`).
- Root `build.gradle.kts` is minimal (`plugins { java }`) — no frontend build integration.
- `application.yml`: H2 file DB at `./data/music-cat`, `music-cat.catalog-path: ../catalog.json`.
- `CatalogAutoImporter` listens for `ApplicationReadyEvent`, imports `catalog.json` when
  `artistRepository.count() == 0`.

---

## Task 9 — Google Sheets Foundation (client, config, mappers)

> **Done when:** App compiles and all tests pass with `music-cat.sheets.enabled=false`
> (the default). Sheets beans exist only when enabled. Row↔entity mappers are fully
> unit-tested.

```
Implement the Google Sheets integration foundation for the Music Library app. No sync
logic yet — just the client, configuration, and row mapping. Everything must be inert
when disabled (the default), so local dev and CI never need credentials.

## 1. Dependencies

Add to backend/build.gradle.kts:
- com.google.api-client:google-api-client:2.7.2
- com.google.apis:google-api-services-sheets:v4-rev20231023-2.0.0
- com.google.auth:google-auth-library-oauth2-http:1.23.0

Before adding, check Maven Central for newer versions of each and use the latest stable
(the Sheets artifact rev string changes frequently; any v4 rev works).

## 2. Configuration properties

Add to backend/src/main/resources/application.yml:

music-cat:
  catalog-path: ../catalog.json        # (existing line, keep)
  sheets:
    enabled: false
    credentials-path: ${SHEETS_CREDENTIALS_PATH:../config/google-credentials.json}
    spreadsheet-id: ${SHEETS_SPREADSHEET_ID:}

Create io.github.alexshamrai.config.SheetsProperties — a @ConfigurationProperties(prefix
= "music-cat.sheets") record: enabled (boolean), credentialsPath (String), spreadsheetId
(String). Register with @EnableConfigurationProperties or @ConfigurationPropertiesScan.

## 3. GoogleSheetsConfig

Create io.github.alexshamrai.config.GoogleSheetsConfig:
- @Configuration, @ConditionalOnProperty(name = "music-cat.sheets.enabled", havingValue = "true")
- One @Bean Sheets sheets(SheetsProperties props):

    GoogleCredentials credentials = GoogleCredentials
        .fromStream(new FileInputStream(props.credentialsPath()))
        .createScoped(List.of(SheetsScopes.SPREADSHEETS));
    return new Sheets.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            new HttpCredentialsAdapter(credentials))
        .setApplicationName("music-cat")
        .build();

## 4. SheetsClient (thin testable wrapper)

Create io.github.alexshamrai.sheets.SheetsClient as an INTERFACE so sync services can be
unit-tested with a mock:

    public interface SheetsClient {
        List<List<Object>> read(String sheetName);              // whole tab, no header logic
        void overwrite(String sheetName, List<List<Object>> rows); // clear tab, write rows at A1
    }

Create io.github.alexshamrai.sheets.GoogleSheetsClient implements SheetsClient:
- @Service, same @ConditionalOnProperty as the config
- read: sheets.spreadsheets().values().get(spreadsheetId, sheetName).execute().getValues();
  return empty list when getValues() is null
- overwrite: values().clear(spreadsheetId, sheetName, new ClearValuesRequest()).execute(),
  then write rows with valueInputOption=RAW. Chunk writes at 10,000 rows per call (the
  Songs tab has ~31K rows; one giant payload risks the per-request size limit): first
  chunk updates at "<sheetName>!A1", subsequent chunks at "<sheetName>!A<offset>"
- On any GoogleJsonResponseException with status 429, retry up to 3 times with exponential
  backoff (1s, 2s, 4s) before rethrowing

## 5. Sheet schema + mappers

The spreadsheet has three tabs. Row 1 of each is a header. Natural keys, no DB ids
(H2 ids regenerate on every boot, so they must not leak into the persistent store):

Artists: name | genre | subgenre | favorite | tags
Albums:  artist | title | year | grade | favorite | tags
Songs:   artist | album | disc | track | title

Formats: genre = Genre displayName; favorite = TRUE/FALSE; tags = comma-separated
("rock, classic"); null year/grade/subgenre = empty string.
Known limitation (document in javadoc): artists are keyed by name — two artists with the
same name in different genres are unsupported by the sync and must be renamed.

Create io.github.alexshamrai.sheets.SheetMapper — pure static methods, no Spring:
- List<Object> toArtistRow(ArtistEntity), List<Object> toAlbumRow(AlbumEntity),
  List<Object> toSongRow(SongEntity) (song rows need artist name + album title from the
  song's album)
- ArtistRow parseArtistRow(List<Object>), AlbumRow parseAlbumRow(List<Object>),
  SongRow parseSongRow(List<Object>) — where ArtistRow/AlbumRow/SongRow are small records
  in the same package holding parsed values (e.g. AlbumRow(String artistName, String
  title, Integer year, Integer grade, boolean favorite, List<String> tags))
- Parsing must tolerate: short rows (missing trailing cells), numeric cells returned as
  "1959" or "1959.0" strings, stray whitespace, empty tag entries ("rock, , 90s")

## 6. Tests (write these FIRST)

backend/src/test/java/io/github/alexshamrai/sheets/SheetMapperTest.java:
- round-trip: entity → row → parsed record preserves every field
- null year, null grade, null subgenre → empty cells → parsed back as nulls
- tags "rock, classic, 90s" round-trip; empty tags cell → empty list
- "1959.0" parses to year 1959; short row (5 cells when 6 expected) doesn't throw
- unknown genre display name → IllegalArgumentException listing valid genres

Context-load test: extend an existing @SpringBootTest (or add SheetsDisabledTest)
asserting the context starts with sheets.enabled=false and contains NO SheetsClient bean
(use @Autowired(required = false) / ObjectProvider and assert null/empty).

## 7. Acceptance criteria

- [x] `./gradlew :backend:test` — green, including new SheetMapperTest (222 tests, 0 failures)
- [x] `./gradlew :backend:bootRun` starts cleanly with sheets disabled; log contains no Sheets noise (started in 2.4s, stats endpoint verified)
- [x] No bean of type SheetsClient exists when music-cat.sheets.enabled=false (SheetsDisabledTest proves it)
- [x] SheetMapper covers: round-trip, nulls, tag lists, numeric-string years, short rows, whitespace-trimmed keys
- [x] No manual setup or credentials were needed anywhere in this task
- [x] Committed (111e9ae + review fixes 0fbd9b1)
```

---

## Task 10 — Write Path: Push Catalog to Sheets

> **Done when:** Any mutation (grade, favorite, tags, CRUD, import) synchronously pushes
> Artists+Albums to the spreadsheet after commit; structural changes also push Songs.
> `POST /api/catalog/sync/push` forces a full push. All proven by tests with a mocked
> SheetsClient.

```
Implement the Sheets write path. Design constraint: on Cloud Run with request-based
billing, CPU is throttled to ~0 between requests — background @Scheduled flushes are
unreliable. Therefore pushes happen synchronously inside the mutating request, after the
DB transaction commits.

## 1. CatalogChangedEvent

Create io.github.alexshamrai.event.CatalogChangedEvent — a record:
    public record CatalogChangedEvent(boolean structural) {}
structural=true → artists/albums/songs were created/deleted/imported (Songs tab must be
rewritten). structural=false → only grades/favorites/tags changed.

Publish it (via ApplicationEventPublisher) at the end of every mutating service method:
- ArtistService: create, update, delete, toggleFavorite (non-structural), setTags
  (non-structural). create/update/delete are structural.
- AlbumService: create/update/delete structural; setGrade/toggleFavorite/setTags
  non-structural.
- TagService: create/delete → non-structural (tag links live in artist/album rows).
- CatalogImportService.importFromJson → structural (publish once, after import).

Read-only services (BrowseService, RandomPickService) publish nothing.

## 2. SheetSyncService

Create io.github.alexshamrai.service.SheetSyncService:
- @Service, @ConditionalOnProperty(name = "music-cat.sheets.enabled", havingValue = "true")
- Dependencies: SheetsClient, ArtistRepository, AlbumRepository, SongRepository, SheetMapper (static)
- State: AtomicBoolean songsDirty, volatile Instant lastPushAt, volatile String lastError

pushCatalog(boolean includeSongs):
1. Load all artists (sorted by genre displayName, then name) → header row + mapped rows →
   sheetsClient.overwrite("Artists", rows)
2. Load all albums (sorted by artist name, then year null-last, then title) → overwrite("Albums", rows)
3. If includeSongs OR songsDirty: load all songs (sorted by artist, album, disc, track) →
   overwrite("Songs", rows); clear songsDirty on success
4. Set lastPushAt; clear lastError
5. On exception: set lastError, keep/set songsDirty if songs were pending, rethrow

Header rows are written as row 1 of each tab (the literal column names from the Task 9
schema). Reads in Task 11 skip row 1.

## 3. SheetSyncListener

Create io.github.alexshamrai.sheets.SheetSyncListener:
- Same @ConditionalOnProperty
- @TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)
  public void onCatalogChanged(CatalogChangedEvent event)
- Calls sheetSyncService.pushCatalog(event.structural())
- Catches and LOGS exceptions (a Sheets outage must not turn a successful rating into a
  500 — the DB write already committed). On failure set songsDirty so the next successful
  push self-heals, and lastError is visible via the status endpoint.

## 4. Sync endpoints

Extend CatalogController (io.github.alexshamrai.controller.CatalogController):

POST /api/catalog/sync/push
- 200 with SyncResultDto { artistCount, albumCount, songCount, syncedAt } on success
- 503 { "status": 503, "message": "Google Sheets sync is not configured" } when disabled
  (inject SheetSyncService via ObjectProvider<SheetSyncService> and check availability)

GET /api/catalog/sync/status
- 200 always: { enabled, lastPushAt, lastPullAt, dirty, lastError } (lastPullAt arrives
  in Task 11 — return null for now). When disabled: { "enabled": false, rest null }.

Create io.github.alexshamrai.dto.SyncResultDto and SyncStatusDto as records.

## 5. Tests (FIRST, with mocked SheetsClient)

SheetSyncServiceTest:
- pushCatalog(false) calls overwrite for Artists and Albums but NOT Songs
- pushCatalog(true) also overwrites Songs with header + one row per song
- rows are sorted as specified; header row is first
- SheetsClient failure → lastError set, songsDirty true, exception propagates
- after a failure, next pushCatalog(false) DOES rewrite Songs (dirty self-heal)

SheetSyncListenerTest (or integration test with sheets enabled + SheetsClient @MockitoBean):
- PATCH /api/albums/{id}/grade triggers exactly one push with includeSongs=false
- POST /api/artists triggers a push with includeSongs=true
- SheetsClient throwing does NOT fail the HTTP request (grade still returns 200)

CatalogControllerTest additions:
- sync/push returns 503 when disabled; sync/status returns enabled=false when disabled

## 6. Acceptance criteria

- [x] `./gradlew test` green (240 tests, 0 failures)
- [x] Every mutating endpoint publishes CatalogChangedEvent exactly once — ArtistService
      (create/update/delete structural; toggleFavorite/setTags non-structural), AlbumService
      (create/update/delete structural; setGrade/toggleFavorite/setTags non-structural),
      TagService (create/delete non-structural), CatalogImportService.importFromJson (structural)
- [x] A Sheets failure never breaks a user mutation (sheetsFailure_doesNotBreakGradeRequest)
- [x] Songs tab is NOT rewritten for a grade change (gradeChange_nonStructural_doesNotWriteSongsTab)
- [x] POST /api/catalog/sync/push → 503 when disabled; GET sync/status → enabled=false (CatalogControllerTest)
- [x] App still boots and all OLD tests pass with sheets disabled (SheetsDisabledTest)
- [x] Committed (1b87bfb + review fixes 9ec1981)
```

---

## Task 11 — Read Path: Boot From Sheets + Pull Endpoint

> **Done when:** On an empty DB the app rebuilds itself from the spreadsheet; on a blank
> spreadsheet it seeds from catalog.json and pushes everything up. `POST
> /api/catalog/sync/pull` wipes the DB and reloads from Sheets. All proven by tests with
> a mocked SheetsClient.

```
Implement the Sheets read path: this is what makes Cloud Run's ephemeral filesystem safe.

## 1. SheetsCatalogReader

Create io.github.alexshamrai.service.SheetsCatalogReader:
- @Service, @ConditionalOnProperty(name = "music-cat.sheets.enabled", havingValue = "true")
- Dependencies: SheetsClient, ArtistRepository, AlbumRepository, SongRepository,
  TagRepository

@Transactional ImportResult loadFromSheets():
1. Read all three tabs via sheetsClient.read(...); skip header row of each
2. Parse rows with SheetMapper.parse*Row
3. Build ArtistEntity per Artists row (genre via Genre.fromDisplayName; tags resolved or
   created via TagRepository.findByName / new TagEntity — reuse the exact tag-resolution
   pattern from ArtistService.setTags)
4. Build AlbumEntity per Albums row, attached to its artist by name. Unknown artist name
   → collect a warning, skip the row (do not abort the whole load)
5. Build SongEntity per Songs row, attached by (artist name + album title); unknown album
   → warning, skip
6. Save all; log warnings + counts; return ImportResult(artistCount, albumCount, songCount)

@Transactional ImportResult replaceFromSheets():
- Deletes everything first (songRepository.deleteAll(), albumRepository.deleteAll(),
  artistRepository.deleteAll(), tagRepository.deleteAll() — in that order), then runs the
  same load. Used by the pull endpoint.

boolean sheetsHaveData(): Artists tab has >1 row (more than just a header) — used to
decide seed vs. restore.

## 2. Boot orchestration — extend CatalogAutoImporter

Modify io.github.alexshamrai.startup.CatalogAutoImporter.onApplicationReady() to this
decision tree (inject ObjectProvider<SheetsCatalogReader> and
ObjectProvider<SheetSyncService> so the class still works with sheets disabled):

1. DB not empty → log "Database already contains data, skipping auto-import" (unchanged)
2. DB empty + sheets ENABLED + sheetsHaveData() → loadFromSheets(); log "Restored N
   artists / N albums / N songs from Google Sheets"
3. DB empty + sheets ENABLED + sheets blank → importFromJson(catalog.json), then
   sheetSyncService.pushCatalog(true); log "Seeded from catalog.json and pushed initial
   state to Google Sheets"
4. DB empty + sheets DISABLED → importFromJson(catalog.json) (today's behavior)

If a sheets restore THROWS (network down, malformed rows): log the error and fall back to
case 3's catalog.json import WITHOUT the push (so a transient Sheets outage can't silently
fork two diverging states — leave the sheet untouched, surface lastError in sync/status).

## 3. Pull endpoint

Extend CatalogController:

POST /api/catalog/sync/pull
- Calls sheetsCatalogReader.replaceFromSheets()
- 200 with SyncResultDto { artistCount, albumCount, songCount, syncedAt } (same record as push)
- 503 when sheets disabled (same shape as push)
- Record lastPullAt in SheetSyncService state; expose it in GET sync/status (replace the
  null from Task 10)

## 4. Tests (FIRST, mocked SheetsClient)

SheetsCatalogReaderTest:
- three tabs with 2 artists / 3 albums / 5 songs → DB contains exactly those, with
  grades/favorites/tags/subgenre/year intact, songs ordered by disc+track
- album row referencing unknown artist → skipped with warning, rest imported
- tag reuse: two rows sharing tag "classic" → one TagEntity
- replaceFromSheets on a populated DB → old data gone, sheet data present
- sheetsHaveData(): header-only tab → false; header+1 row → true

CatalogAutoImporter tests (extend CatalogAutoImporterIntegrationTest):
- sheets enabled + data in sheets → loadFromSheets called, catalog.json NOT imported
- sheets enabled + blank sheets → catalog.json imported, pushCatalog(true) called
- sheets enabled + reader throws → catalog.json imported, pushCatalog NOT called
- sheets disabled → existing behavior unchanged (existing tests stay green)

## 5. Round-trip invariant test (the money test)

With mocked SheetsClient backed by in-memory Map<String, List<List<Object>>> (overwrite
stores, read returns): seed DB via TestDataFactory → pushCatalog(true) → wipe DB →
loadFromSheets() → assert the full object graph (artists, albums, songs, tags, grades,
favorites, years, subgenres) is identical to the original. This single test guarantees no
field silently falls out of the persistence loop.

## 6. Acceptance criteria

- [x] `./gradlew test` green (259 tests, 0 failures), including SheetsRoundTripInvariantTest
- [x] All four boot scenarios covered by tests (sheets data / blank sheets / reader error /
      disabled) — CatalogAutoImporterTest (8 decision-tree tests)
- [x] POST /api/catalog/sync/pull replaces DB content from sheets (SyncPullIntegrationTest
      proves old rows gone)
- [x] GET /api/catalog/sync/status now reports lastPullAt
- [x] A Sheets outage at boot falls back to catalog.json and does NOT push (auto-import
      suppresses CatalogChangedEvent via importFromJson(path, false); boot pushes happen
      only explicitly in the blank-sheets seed case)
- [x] Committed
```

---

## Task 12 — Export Endpoints (JSON + CSV)

> **Done when:** `GET /api/catalog/export/json` downloads the enriched catalog;
> `GET /api/catalog/export/csv` downloads a ZIP with artists.csv + albums.csv.

```
Add offline export endpoints (independent backup layer beside Sheets).

## 1. CatalogExportService

Create io.github.alexshamrai.service.CatalogExportService:

exportJson(): returns the full enriched catalog as the existing dto.catalog records
EXTENDED with curation fields. Create io.github.alexshamrai.dto.export package with
records: ExportCatalog(exportedAt, stats, genres), ExportGenre(genre, artists),
ExportArtist(name, subgenre, isFavorite, tags, albums), ExportAlbum(title, year, grade,
isFavorite, tags, songs), ExportSong(title, trackNumber, discNumber). Group by genre
displayName, sort genres/artists/albums alphabetically (albums by year then title).

exportCsvZip(): returns byte[] — a ZIP (java.util.zip.ZipOutputStream) containing:
- artists.csv: name,genre,subgenre,isFavorite,tags,albumCount
- albums.csv: artistName,genre,title,year,grade,isFavorite,tags,songCount
CSV rules: comma-separated; quote any field containing comma/quote/newline; double
embedded quotes; tags joined with ", " inside one quoted field; UTF-8; header row first.
(Artist names like "Crosby, Stills & Nash" and albums like "What's the \"Story\"" must
survive — test exactly these.)

## 2. Endpoints

Extend CatalogController:
GET /api/catalog/export/json → application/json,
  Content-Disposition: attachment; filename="music-cat-export.json"
GET /api/catalog/export/csv → application/zip,
  Content-Disposition: attachment; filename="music-cat-export.zip"

## 3. Tests (FIRST)

CatalogExportServiceTest: grouping/sorting; null year/grade → null in JSON, empty in CSV;
CSV quoting cases above; ZIP contains exactly artists.csv and albums.csv with expected
line counts. Controller test: both endpoints return 200 with correct Content-Type and
Content-Disposition headers.

## 4. Acceptance criteria

- [ ] `./gradlew test` green
- [ ] Manual check: bootRun, curl both endpoints, open the files — JSON has grades/tags,
      CSVs open correctly in a spreadsheet app with quoted names intact
- [ ] Committed
```

---

## Task 13 — Production Build: SPA Forwarding + Gradle Frontend Integration

> **Done when:** `./gradlew :backend:bootJar` (from a clean checkout, no local node
> required) produces ONE jar; `java -jar` serves UI + API; deep links like
> /albums/42 work.

```
Wire the frontend build into Gradle and add SPA route forwarding.

## 1. SPA forwarding

Create io.github.alexshamrai.config.SpaForwardingController (@Controller, NOT @RestController):

    @GetMapping({"/browse", "/artists", "/artists/{id:[0-9]+}", "/albums",
                 "/albums/{id:[0-9]+}", "/random", "/favorites", "/tags"})
    public String forwardSpaRoutes() {
        return "forward:/index.html";
    }

This is the explicit list of React Router routes from frontend/src/App.tsx — verify
against App.tsx and include every route that exists there. Leave WebConfig.java untouched
(it holds the Genre converter). Add a comment: "new frontend routes must be added here".

## 2. Gradle frontend integration (node downloaded by Gradle — required for Docker)

In frontend/: change vite.config.ts build.outDir to "dist" (emptyOutDir: true). The copy
into backend resources becomes Gradle's job (keeps `npm run dev` and plain `vite build`
self-contained inside frontend/).

Root settings.gradle.kts: include("frontend").
Create frontend/build.gradle.kts using the node plugin:

    plugins { id("com.github.node-gradle.node") version "7.1.0" }
    node {
        version = "22.12.0"
        download = true
    }
    val npmBuild = tasks.register<com.github.gradle.node.npm.task.NpmTask>("npmBuild") {
        dependsOn(tasks.npmInstall)
        args = listOf("run", "build")
        inputs.dir("src"); inputs.files("package.json", "package-lock.json",
            "vite.config.ts", "index.html", "tsconfig.json", "tsconfig.app.json")
        outputs.dir("dist")
    }

(Check the plugin's latest version and exact NpmTask import path against its docs if 7.1.0
fails to resolve.)

backend/build.gradle.kts: make processResources depend on the frontend build and copy
dist/ into static/:

    tasks.processResources {
        dependsOn(":frontend:npmBuild")
        from(project(":frontend").layout.projectDirectory.dir("dist")) {
            into("static")
        }
    }

## 3. Hygiene

- Add to .gitignore: backend/src/main/resources/static/ (stale artifact location — also
  `git rm -r --cached` it if committed), frontend/dist/, frontend/.gradle/, config/*.json,
  data/
- Verify dev flow still works: backend bootRun + `cd frontend && npm run dev` (proxy
  unchanged).

## 4. README.md (root)

Rewrite README.md: one-paragraph description; prerequisites (Java 25 via sdkman, no local
node needed for the jar build); dev mode (two terminals); production build
(`./gradlew :backend:bootJar` → `java -jar backend/build/libs/music-cat-0.0.1-SNAPSHOT.jar`);
configuration reference (music-cat.* properties incl. sheets); link to /swagger-ui.

## 5. Acceptance criteria

- [ ] `./gradlew clean :backend:bootJar` succeeds (Gradle downloads node itself)
- [ ] `java -jar backend/build/libs/music-cat-*.jar` → http://localhost:8080 loads the app
- [ ] Direct browser hits on /browse, /albums/1, /random return the SPA (no Whitelabel 404)
- [ ] /swagger-ui and /api/browse/stats still work
- [ ] `npm run dev` flow unchanged
- [ ] backend/src/main/resources/static is gitignored and absent from `git status` after build
- [ ] `./gradlew test` green
- [ ] Committed
```

---

## Task 14 — Single-User Auth (HTTP Basic)

> **Done when:** Every request without credentials gets 401; with the env-configured
> user/password everything works, including the React UI and Swagger.

```
Protect the app — the Cloud Run URL is public.

## 1. Dependency + config

backend/build.gradle.kts: add org.springframework.boot:spring-boot-starter-security.

application.yml:

music-cat:
  auth:
    username: ${MUSIC_CAT_USER:admin}
    password: ${MUSIC_CAT_PASSWORD:admin}

## 2. SecurityConfig

Create io.github.alexshamrai.config.SecurityConfig:
- @Configuration, @EnableWebSecurity
- SecurityFilterChain: authorizeHttpRequests → anyRequest().authenticated();
  httpBasic(Customizer.withDefaults()); csrf disabled (stateless Basic, no cookies);
  sessionManagement STATELESS
- InMemoryUserDetailsManager with one user built from the two properties, password
  encoded with PasswordEncoderFactories.createDelegatingPasswordEncoder()
- Spring Boot 4 / Spring Security 7 — use the lambda DSL; if an API moved, check the
  Spring Security 7 migration notes rather than guessing

Note: H2 console (dev only) needs frameOptions sameOrigin and its CSRF exemption if you
keep it reachable — simpler: leave it authenticated and accept the console only works in
dev where you can also just disable security via a 'local' profile if it gets annoying.
Decision: keep ALL paths authenticated; no exemptions. Cloud Run health checks use TCP,
not HTTP, so no health-path exemption is needed.

## 3. Existing tests

All controller tests will now hit 401. Fix once, centrally: add
@AutoConfigureMockMvc + @WithMockUser via a shared annotation or apply
SecurityMockMvcRequestPostProcessors.httpBasic(...) in the shared test setup —
whichever matches the existing test style (check ArtistControllerTest first). Do NOT
sprinkle permitAll into production code to make tests pass.

## 4. New tests

SecurityConfigTest:
- GET /api/browse/stats without auth → 401
- with correct Basic credentials → 200
- with wrong password → 401
- GET / (index.html) without auth → 401

## 5. Acceptance criteria

- [ ] `./gradlew test` green (old controller tests fixed via test infra, not permitAll)
- [ ] bootRun: browser prompts for credentials once, then the full UI works (TanStack
      Query requests reuse the browser's cached Basic credentials automatically)
- [ ] curl without credentials → 401; with -u admin:admin → 200
- [ ] Credentials come from env vars; defaults only apply locally
- [ ] Committed
```

---

## Task 15 — Cloud Profile, Cold-Start Tuning & Dockerfile

> **Done when:** `docker run` of the image (1 GiB memory cap) boots the full app with
> the `cloud` profile in under ~10s on the laptop, using in-memory H2 + baked-in
> catalog.json.

```
Containerize for Cloud Run. Filesystem is ephemeral there, so the cloud profile must be
fully self-contained: in-memory H2 + catalog.json baked into the image + Sheets restore.

## 1. application-cloud.yml

Create backend/src/main/resources/application-cloud.yml:

spring:
  datasource:
    url: jdbc:h2:mem:music-cat;DB_CLOSE_DELAY=-1
  h2:
    console:
      enabled: false
  main:
    lazy-initialization: true

music-cat:
  catalog-path: /app/catalog.json
  sheets:
    enabled: true
    credentials-path: ${SHEETS_CREDENTIALS_PATH:/secrets/google/credentials.json}

server:
  port: ${PORT:8080}

Lazy-init gotcha: @EventListener beans may not be instantiated before the event fires.
Annotate CatalogAutoImporter and SheetSyncListener with @Lazy(false). The container test
below is what proves boot rehydration still runs — do not skip it.

## 2. Dockerfile (project root)

# ---- build ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew --no-daemon :backend:bootJar -x test

# ---- runtime ----
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/backend/build/libs/*.jar app.jar
COPY catalog.json /app/catalog.json
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+TieredCompilation -XX:TieredStopAtLevel=1 -Xss256k"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

(The Gradle build downloads node via the Task 13 plugin, so no node stage is needed.
If image size or build time hurts, a follow-up can add layered-jar extraction and a
gradle dependency cache mount — do not gold-plate now.)

Create .dockerignore: .git, .gradle, **/build, **/node_modules, frontend/dist, data,
config, docs, *.md (keep catalog.json!).

## 3. Startup time measurement

Run locally and record the "Started MusicLibraryApplication in X seconds" line:
1. To allow running the cloud profile locally without credentials, define the enabled
   flag in application-cloud.yml as: enabled: ${MUSIC_CAT_SHEETS_ENABLED:true}
   Then: docker run --rm -m 1g -p 8080:8080 -e SPRING_PROFILES_ACTIVE=cloud
   -e MUSIC_CAT_SHEETS_ENABLED=false <image>
2. Target: under ~10s including the catalog.json import. If far over, check that lazy
   init is active and the import isn't doing per-row flushes.

## 4. Acceptance criteria

- [ ] docker build . succeeds from a clean clone (no local node/gradle needed beyond the wrapper)
- [ ] docker run with cloud profile + sheets disabled boots, imports catalog.json,
      http://localhost:8080 serves UI behind Basic auth, /api/browse/stats shows 176/2830 counts
- [ ] Startup line recorded in the completion report; lazy-init confirmed not to break
      CatalogAutoImporter (the import demonstrably ran)
- [ ] Image runs within -m 1g (no OOM kill during boot + a browse click-through)
- [ ] `./gradlew test` still green (cloud profile changes nothing by default)
- [ ] Dockerfile + .dockerignore + application-cloud.yml committed
```

---

## Task 16 — GCP Provisioning (manual steps + verified live Sheets run)

> **Done when:** Both GCP projects exist, the spreadsheet is shared with the service
> account, and a LOCAL run with `sheets.enabled=true` does the initial seed: catalog.json
> → H2 → all three tabs visible in the spreadsheet in the browser.

```
Mostly manual (human-with-browser) steps; Claude prepares commands and verifies the
outcome. Use the user's PERSONAL Google account throughout.

## 1. [manual] Sheets project (NO billing — must stay unbilled)

1. console.cloud.google.com → create project "music-cat-sheets". Do NOT attach billing.
2. Enable "Google Sheets API" (APIs & Services → Library).
3. IAM → Service Accounts → create "music-cat-sync" (no roles needed — access comes from
   sheet sharing). Create a JSON key, download to config/google-credentials.json
   (verify .gitignore covers config/*.json BEFORE saving the file — it does after Task 13).
4. In Google Sheets (personal account — NOT created by the service account, so it lives
   in your own Drive and is browser-editable): create spreadsheet "music-cat" with three
   tabs named exactly: Artists, Albums, Songs.
5. Share the spreadsheet with the service account's client_email (from the JSON) as Editor.
6. Note the spreadsheet ID (the long token in the sheet URL between /d/ and /edit).

## 2. [manual] Hosting project (billing attached)

1. Create project "music-cat-hosting"; sign up for the free trial if new ($300/90 days;
   real credit/debit card required — it's identity verification, nothing is charged).
2. SET A CALENDAR REMINDER NOW: "Upgrade GCP billing account before trial day 90" — if
   the trial lapses un-upgraded the account auto-closes and resources are deleted after a
   30-day grace. Upgrading does NOT start charges while usage stays in the free tier.
3. Billing → Budgets & alerts → budget $1, alert at 50/90/100% (GCP has no hard cap;
   this is the tripwire).
4. Enable APIs: run.googleapis.com, artifactregistry.googleapis.com,
   secretmanager.googleapis.com. Do NOT enable Container Scanning / Artifact Analysis
   (it bills per pushed image).

## 3. CLI setup (Claude can run; auth via `! gcloud auth login` typed by the user)

gcloud config set project music-cat-hosting   # use the real project id (may have suffix)
gcloud artifacts repositories create music-cat --repository-format=docker \
    --location=europe-west1
gcloud auth configure-docker europe-west1-docker.pkg.dev

Cleanup policy (keeps stored images under the 0.5 GB free allowance — Spring Boot images
are ~300 MB each). Create cleanup-policy.json:

[
  {
    "name": "keep-2-recent",
    "action": { "type": "Keep" },
    "mostRecentVersions": { "keepCount": 2 }
  },
  {
    "name": "delete-older-than-30d",
    "action": { "type": "Delete" },
    "condition": { "olderThan": "2592000s" }
  }
]

gcloud artifacts repositories set-cleanup-policies music-cat \
    --location=europe-west1 --policy=cleanup-policy.json --no-dry-run

Secret with the service-account key (bytes live in the hosting project; that's fine):

gcloud secrets create sheets-sa-key --data-file=config/google-credentials.json

## 4. Live Sheets smoke test (local app, real spreadsheet)

Delete ./data/music-cat.mv.db (local H2 file) so the DB is empty, then:

SHEETS_SPREADSHEET_ID=<id> ./gradlew :backend:bootRun \
    --args='--music-cat.sheets.enabled=true'

Expected: log shows "Seeded from catalog.json and pushed initial state to Google Sheets".
Open the spreadsheet in the browser: Artists ≈ 177 rows (176 + header), Albums ≈ 2831
(2830 + header), Songs ≈ 30877 (30876 + header); counts must match /api/browse/stats.

Then prove the loop: stop the app, delete ./data/music-cat.mv.db again, restart the same
way → log shows "Restored ... from Google Sheets" and the UI works WITHOUT catalog.json
being re-imported. Rate one album in the UI → the grade appears in the Albums tab within
seconds (synchronous push).

## 5. Acceptance criteria

- [ ] music-cat-sheets project has NO billing account attached (verify in console)
- [ ] config/google-credentials.json exists locally and is NOT in `git status`
- [ ] Spreadsheet is owned by the personal account, shared with the SA, three tabs named exactly
- [ ] Budget alert exists; calendar reminder for billing upgrade is set
- [ ] Artifact Registry repo + cleanup policy active (gcloud artifacts repositories
      describe music-cat --location=europe-west1 shows the policy)
- [ ] Secret sheets-sa-key exists
- [ ] Initial seed verified in the browser; restore-from-sheets verified; live grade
      write-through verified
```

---

## Task 17 — Deploy to Cloud Run

> **Done when:** The app is live on its `*.run.app` URL behind Basic auth, restored from
> Sheets, inside the free tier, with a `deploy.sh` that repeats the process.

```
Deploy. Mac is arm64 — Cloud Run needs linux/amd64, so every build must use buildx
--platform linux/amd64 (a plain `docker build` will produce an image that crashes on
deploy with an exec format error).

## 1. Build + push

REGION=europe-west1
PROJECT=<music-cat-hosting project id>
IMAGE=$REGION-docker.pkg.dev/$PROJECT/music-cat/app:$(git rev-parse --short HEAD)

docker buildx build --platform linux/amd64 -t $IMAGE --push .

## 2. Deploy

gcloud run deploy music-cat \
  --image=$IMAGE \
  --region=$REGION \
  --allow-unauthenticated \
  --min-instances=0 --max-instances=1 \
  --cpu=1 --memory=1Gi \
  --cpu-boost \
  --set-env-vars=SPRING_PROFILES_ACTIVE=cloud,SHEETS_SPREADSHEET_ID=<id>,MUSIC_CAT_USER=<user>,MUSIC_CAT_PASSWORD=<strong-password> \
  --set-secrets=/secrets/google/credentials.json=sheets-sa-key:latest

Notes:
- --allow-unauthenticated is correct: auth is the app's Basic login, not IAM.
- The secret mounts as a file at the exact path application-cloud.yml expects.
- If `gcloud run deploy` complains about --cpu-boost, the flag may have been renamed —
  check `gcloud run deploy --help` for the startup CPU boost option rather than dropping it.

## 3. deploy.sh

Create deploy.sh at the project root encapsulating steps 1–2 (REGION/PROJECT/SERVICE as
variables at the top, image tag from git short SHA, secrets/env NOT hardcoded — read
MUSIC_CAT_USER/MUSIC_CAT_PASSWORD/SHEETS_SPREADSHEET_ID from the caller's environment and
fail with a clear message if missing). chmod +x. Document it in README's deployment section.

## 4. Acceptance criteria

- [ ] Service URL opens in a browser, prompts Basic auth, dashboard shows the real stats
      (176 artists / 2830 albums)
- [ ] Logs (gcloud run services logs read music-cat --region=europe-west1) show
      "Restored ... from Google Sheets" — NOT the catalog.json seed path
- [ ] Startup CPU boost, min=0/max=1, 1 vCPU/1Gi visible in
      `gcloud run services describe music-cat --region=europe-west1`
- [ ] Cold-start measurement recorded: after >20 min idle, `time curl -u user:pass
      https://<url>/api/browse/stats` — report the number (expect ~3–12s; it's the
      reference figure for later tuning)
- [ ] deploy.sh committed; rerunning it deploys a new revision successfully
- [ ] Billing page still shows $0.00 forecast (check Artifact Registry storage is under
      0.5 GB after the cleanup policy)
```

---

## Task 18 — Live E2E Verification + Docs Refresh

> **Done when:** The full persistence loop is proven on the LIVE service across a cold
> start, and CLAUDE.md / README / MEMORY reflect the deployed reality.

```
Final verification of the whole point of this phase: curation data must survive scale-to-
zero, and hand-edits to the sheet must flow back into the app.

## 1. Live persistence loop (use curl -u or the browser against the live URL)

1. Pick one album; PATCH its grade to 5 via the live UI. Confirm the Albums tab in the
   spreadsheet shows grade 5 within seconds.
2. Force a fresh instance: gcloud run services update music-cat --region=europe-west1
   --update-env-vars=DEPLOY_BUMP=$(date +%s)   (new revision = guaranteed new instance).
3. After the new revision is serving, fetch the same album → grade is STILL 5 (it came
   back through Sheets, not memory). This is the make-or-break check.
4. Hand-edit test: in the browser, change that album's grade to 2 directly in the
   spreadsheet, then POST /api/catalog/sync/pull on the live service → UI now shows
   grade 2. Mind the direction: PULL overwrites the DB from the sheet; a PUSH right
   after a hand-edit would overwrite your hand-edit with the DB state. Document this
   loudly in README's "Editing data by hand" section.
5. Create a tag and favorite an artist in the live UI; verify both appear in the sheet;
   pull-sync; verify nothing is lost (round-trip sanity in production).

## 2. Failure-mode check

Temporarily revoke access (unshare the spreadsheet from the service account), restart the
service (env bump), and verify: app still comes up serving catalog.json data, GET
/api/catalog/sync/status shows the error, ratings still work locally (200) but
sync/status reports the failed push. Re-share the sheet, POST /api/catalog/sync/push,
verify the sheet catches back up. This proves a Sheets outage degrades gracefully instead
of bricking the app.

## 3. Documentation refresh

- README.md: add "Live deployment" section — URL placeholder, deploy.sh usage, the
  pull-vs-push direction warning, cold-start expectation, free-tier guardrails summary
  (budget alert, cleanup policy, day-90 upgrade, no Container Scanning).
- CLAUDE.md: update "Current State" (Tasks 9–18 done), Architecture (Sheets = source of
  truth is now IMPLEMENTED, Cloud Run deployment), Key Commands (deploy.sh), and the
  stale "Planned (not yet created)" package list.
- Verify task-list.md acceptance boxes for Tasks 9–18 are all checked.

## 4. Acceptance criteria

- [ ] Grade survives a forced new revision (step 3) — screenshot or curl output in report
- [ ] Hand-edit → pull → visible in UI, with the direction warning documented
- [ ] Failure mode verified: revoked sheet ≠ dead app; status endpoint surfaces the error;
      recovery via push works
- [ ] README + CLAUDE.md updated; no doc still claims Sheets backup is "not started"
- [ ] Final commit; consider tagging v1.0.0
```

---

## Summary

| Task | Focus | Estimated Time |
|---|---|---|
| 9 | Sheets client, config, mappers | 1–2 hours |
| 10 | Write path: event-driven push | 1–2 hours |
| 11 | Read path: boot restore + pull | 2 hours |
| 12 | JSON/CSV export | 1 hour |
| 13 | SPA forwarding + Gradle frontend build | 1–2 hours |
| 14 | HTTP Basic auth | 1 hour |
| 15 | Cloud profile + Dockerfile | 1–2 hours |
| 16 | GCP provisioning + live Sheets smoke test | 1–2 hours (mostly manual) |
| 17 | Cloud Run deploy + deploy.sh | 1 hour |
| 18 | Live E2E verification + docs | 1 hour |
| | **Total** | **~11–16 hours** |
