# Sheets-Only Data Path + Local Fake Sheets + Layered Testing

**Date:** 2026-07-24
**Status:** Design — awaiting review

## Overview

Make Google Sheets the **only** way catalog data enters the application, remove
`catalog.json` and all file-import machinery, and replace them with a local,
file-backed **fake Google Sheets** for development and testing. The fake is
seeded by a **read-only snapshot** of the live sheet, so day-to-day testing runs
fully offline and can never write to production.

Add a layered testing strategy so the write/mutation path and the real Google
Sheets client internals are both covered without ever touching production during
the normal build.

## Motivation

- `catalog.json` is a frozen artifact of the original one-time scan (last content
  change pre-launch). It is never written back to, carries **no** curation
  (grades/favorites/tags), and diverged from Sheets the moment the first edit was
  made. Google Sheets is the real, live source of truth.
- Keeping `catalog.json` as a seed/fallback adds a stale, misleading data path and
  a boot decision tree that mostly never fires in production.
- We want to test app logic, the mutation→push flow, and the Google client
  behavior **without** risking the live sheet and **without** requiring network or
  credentials for the everyday test loop.

## Decisions (locked)

1. **Sheets-only.** Remove `catalog.json` and *all* file-import machinery. Google
   Sheets restore + `sync/pull` become the only inbound data paths.
2. **Local fake, file-backed.** A `FakeSheetsClient` reads/writes a local JSON
   file that mirrors the exact 3-tab sheet schema. Selected by a new
   `music-cat.sheets.mode=fake`. Run under the `cloud` profile so it mirrors prod
   (in-memory H2, restore-on-boot, push-on-edit).
3. **Read-only prod snapshot to seed the fake.** A dedicated command reads the
   three tabs from the live sheet once (read-only) and writes the fake file.
   Refreshable on demand; fully offline afterward. A hand-written or committed
   sample fixture is documented as a zero-prod-contact alternative.
4. **Layered testing (Layers 1 & 2 in this spec):**
   - **Layer 1** — fake-backed integration tests covering app logic, the
     mutation→push write path, boot-restore round-trip, and suspend/resume.
   - **Layer 2** — `GoogleSheetsClient` unit tests using Google's
     `MockHttpTransport` (chunking, append-vs-update, 429 backoff, parsing,
     errors).
   - **Layer 3** (real dedicated spreadsheet) — documented as an opt-in follow-on,
     not implemented here.

## Non-goals

- No UI "fake mode" banner (the startup log line is the indicator).
- No curated-import extension (the removed import path only ever read the skeleton
  shape anyway).
- No Layer 3 real-spreadsheet integration test in this spec (documented only).
- No change to the sync orchestration semantics (suspend/resume, bad-row skipping,
  chunked writes) — only the client implementation is made swappable.

## Detailed Design

### A. Removals (Sheets-only)

| Removed | Notes |
|---|---|
| `catalog.json` (repo root) | the frozen scan artifact |
| `Dockerfile` line `COPY catalog.json /app/catalog.json` | build stops shipping it |
| `service/CatalogImportService.java` | + `CatalogImportServiceTest`, `CatalogImportIntegrationTest` |
| `POST /api/catalog/import` in `CatalogController` | + its `CatalogControllerTest` case; export + sync endpoints stay |
| `dto/catalog/Catalog.java`, `GenreGroup.java`, `Artist.java`, `Album.java` | import-only records |
| `dto/ImportResult.java` | only used by the import path (verified: `CatalogImportService`, import endpoint, `CatalogAutoImporter`) |
| `music-cat.catalog-path` | remove from `application.yml`, `application-cloud.yml`, `application-test.yml` |
| `backend/src/test/resources/test-catalog.json` | import test fixture |
| `importCatalogJson()` + its branches in `CatalogAutoImporter` | boot tree simplifies (§C) |

**Coupling to preserve:** `dto/catalog/Stats` is imported by `dto/export/ExportCatalog`
and `service/CatalogExportService`. **Move `Stats` into `dto/export/`** (its only
remaining consumers) and delete the now-empty `dto/catalog` package. Do **not**
delete `Stats`.

### B. New components

- **`music-cat.sheets.mode`** — new property on `SheetsProperties`
  (`google` | `fake`, default `google`).
- **`FakeSheetStore`** — a plain (non-conditional) component that reads/writes a
  `Map<String, List<List<Object>>>` (tab name → rows) as JSON at
  `music-cat.sheets.fake-file` (default `./data/fake-sheets.json`). Single source
  of the on-disk format. Read of a missing file → empty map (each tab → empty
  list). Writes are whole-file. Access is synchronized (belt-and-suspenders; the
  sync layer already serializes via `SheetsSyncLock`).
- **`FakeSheetsClient implements SheetsClient`** — active when
  `enabled=true AND mode=fake`. Delegates `read`/`overwrite` to `FakeSheetStore`.
- **Snapshot runner** — a `CommandLineRunner`/`ApplicationRunner` active only when
  `music-cat.sheets.snapshot=true`, running in `mode=google`. It calls **only**
  `read("Artists")`, `read("Albums")`, `read("Songs")` on the real client and
  writes them via `FakeSheetStore`, then triggers `SpringApplication.exit(...)`.
  Read-only against the live sheet by construction (it never calls `overwrite`).

### B1. Bean wiring

- `GoogleSheetsClient` and `GoogleSheetsConfig` (the credentials/`Sheets` bean):
  active when `enabled=true AND mode=google` (mode `matchIfMissing=true`). In
  `mode=fake`, **no credentials are loaded and no Google client exists** in the
  process.
- `FakeSheetsClient`: active when `enabled=true AND mode=fake`.
- Sync stack (`SheetSyncService`, `SheetSyncListener`, `SheetsCatalogReader`,
  `SheetsSyncLock`): **unchanged** — still gated on `enabled=true` only; they
  consume whichever single `SheetsClient` bean is present.
- Mechanism: `@ConditionalOnExpression` combining `enabled` and `mode`
  (two-property condition), e.g.
  `@ConditionalOnExpression("${music-cat.sheets.enabled:false} and '${music-cat.sheets.mode:google}' == 'google'")`
  and the `== 'fake'` counterpart. Exact annotation finalized in the plan.
- **Startup banner:** one log line at boot naming the active client + backing
  store, e.g. `Sheets client: FAKE (file ./data/fake-sheets.json)` or
  `Sheets client: GOOGLE (spreadsheet ...<last6>)`.

### C. Simplified boot decision tree (`CatalogAutoImporter`)

`CatalogAutoImporter` drops its `CatalogImportService`, `ImportResult`, and
`catalogPath` dependencies and its `importCatalogJson()` helper. New tree:

```
DB not empty              → skip (resume event pushes)
DB empty + sheet has data → restore from sheet         (google OR fake)
DB empty + sheet blank    → empty DB, trivially consistent → resume pushes
DB empty + restore throws → empty DB, SUSPEND pushes, surface via sync/status
DB empty + sheets disabled→ empty DB (automated-test context only)
```

No fallback data exists anywhere. The `finally { readinessState.markReady() }`
behavior is preserved so the readiness gate always closes.

### D. Scripts

- **`snapshot-prod-to-fake.sh`** (repo root) — runs the jar with
  `MUSIC_CAT_SHEETS_ENABLED=true`, `MUSIC_CAT_SHEETS_MODE=google`,
  `MUSIC_CAT_SHEETS_SNAPSHOT=true`, real (read-only) credentials, and the prod
  spreadsheet id. Writes `./data/fake-sheets.json` and exits. Run once / to refresh.
- **`run-fake.sh`** (repo root) — runs the jar with
  `--spring.profiles.active=cloud`, `MUSIC_CAT_SHEETS_ENABLED=true`,
  `MUSIC_CAT_SHEETS_MODE=fake`, no credentials. Warns if `./data/fake-sheets.json`
  is missing (suggests running the snapshot or providing a sample fixture).

### E. On-disk fake format (reference)

Mirrors the schema in `SheetMapper` / `SheetSyncService` headers exactly. Row 1 of
each tab is the header.

```json
{
  "Artists": [
    ["name","genre","subgenre","favorite","tags"],
    ["Pink Floyd","Rock","Progressive Rock","TRUE","classic, psychedelic"]
  ],
  "Albums": [
    ["artist","title","year","grade","favorite","tags"],
    ["Pink Floyd","The Dark Side of the Moon","1973","5","TRUE","classic"]
  ],
  "Songs": [
    ["artist","album","disc","track","title"],
    ["Pink Floyd","The Dark Side of the Moon","1","1","Speak to Me"]
  ]
}
```

## Testing Strategy

### Layer 1 — Fake-backed integration tests (offline, every build)

Boot a Spring test context with `enabled=true`, `mode=fake`, and a temp
`fake-file`. Drive real HTTP endpoints via MockMvc and assert both the DB result
**and** the fake store contents.

- **Write/mutation path:** rate an album, toggle favorite, add/remove a tag, and
  run the batch album edit (rename + add/rename/delete songs). Assert the fake
  store's relevant tab rows changed correctly (proves
  endpoint → `SheetSyncListener` → `SheetSyncService` → `SheetMapper` →
  `overwrite`).
- **Round-trip:** after a mutation, run a fresh boot-restore from the same fake
  store and assert the DB is rebuilt to match.
- **Safety paths:** a malformed row in the fake store → restore skips it and pushes
  stay suspended; an induced push failure → pushes re-suspend; a blank fake store →
  empty DB + pushes resume (consistent).
- Rework `CatalogAutoImporterTest` / `CatalogAutoImporterIntegrationTest` to the new
  boot tree. Audit any other integration test that relied on `catalog.json`
  auto-import for data and reseed it via repositories or a fake fixture.

### Layer 2 — `GoogleSheetsClient` unit tests with `MockHttpTransport`

Google-recommended approach for their Java client libraries (no Sheets emulator
exists). Build a `Sheets` client on a `MockHttpTransport` returning canned
responses and test `GoogleSheetsClient` directly:

- `read()` parses a `ValueRange`; empty tab → empty list.
- `overwrite()` with `> CHUNK_SIZE` (10,000) rows issues a clear, a first
  `values.update` at A1, then `values.append` for later chunks — assert against the
  captured request URLs/methods/bodies (guards the grid-growth logic).
- A `429` response triggers retry/backoff and eventually succeeds; exhausting
  `MAX_RETRIES` throws.
- `GoogleJsonResponseException` / error handling behaves as expected.

`MockHttpTransport` ships in `com.google.api.client.testing.http` within
`google-http-client` (transitive via `google-api-client:2.8.0`); add an explicit
`testImplementation` on `google-http-client` only if it is not resolvable on the
test classpath.

### Layer 3 — Real dedicated spreadsheet (documented follow-on, NOT built here)

A small `@Tag`-gated integration test against a throwaway spreadsheet + service
account, run in a separate/opt-in CI job with credentials from a secret
(create → write → read-back → clean up a few rows). This is where the real API
semantics (auth, quotas, actual grid growth) get verified. Out of scope for this
spec; captured so it is not forgotten.

## Consequences & Trade-offs

- **Prod:** a Google Sheets outage during a Cloud Run cold start yields an **empty
  app** until Sheets recovers (H2 is in-memory; every cold start re-decides).
  Recover by fixing access and `POST /api/catalog/sync/pull`. Accepted.
- **Bootstrapping a blank/new sheet** now has no automatic seed. The current prod
  sheet is already populated; recreating from scratch would require manual entry or
  `sync/pull` from a populated sheet. Accepted (Sheets-only).
- **Coverage boundary:** Layers 1+2 together cover app logic, the mutation/write
  path, sync orchestration, and the Google client internals — all offline and
  deterministic. Only Layer 3 exercises the genuine Google service.

## File-by-file change list (for the plan)

**Delete:** `catalog.json`; `service/CatalogImportService.java`;
`dto/catalog/{Catalog,GenreGroup,Artist,Album}.java`; `dto/ImportResult.java`;
`test/.../CatalogImportServiceTest.java`; `test/.../CatalogImportIntegrationTest.java`;
`test/resources/test-catalog.json`.

**Move:** `dto/catalog/Stats.java` → `dto/export/Stats.java` (update imports in
`ExportCatalog`, `CatalogExportService`).

**Edit:** `Dockerfile` (drop COPY); `application.yml`, `application-cloud.yml`,
`application-test.yml` (drop `catalog-path`; document `mode`/`fake-file`/`snapshot`);
`CatalogController` (drop `/import`, `CatalogImportService`, `ImportResult`);
`CatalogAutoImporter` (simplified tree, drop import deps); `SheetsProperties`
(+`mode`, +`fakeFile`, +`snapshot` as needed); `CatalogControllerTest` (drop import
case); `CatalogAutoImporterTest` / `CatalogAutoImporterIntegrationTest` (new tree).

**Add:** `sheets/FakeSheetStore.java`; `sheets/FakeSheetsClient.java`;
`sheets/SnapshotRunner.java` (or `startup/`); conditionals on `GoogleSheetsClient`
+ `GoogleSheetsConfig`; startup banner log; `snapshot-prod-to-fake.sh`;
`run-fake.sh`; Layer 1 fake integration test(s); Layer 2 `GoogleSheetsClientTest`.

**Docs:** update `CLAUDE.md` (data flow, boot tree, removed import endpoint, new
fake/snapshot commands) and `README.md`.

## Open questions

None outstanding — all design decisions resolved during brainstorming.
