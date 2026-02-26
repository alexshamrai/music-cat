# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Personal music catalog app for browsing, rating, tagging, and randomly picking albums. The core feature is the "Surprise Me" random album picker with filters. Google Sheets serves as the persistent data store (read and write); H2 is used as a runtime cache/database. The music library data was scanned once from an external drive via a Python script — the scanner is a one-time prerequisite, not part of the running application.

## Current State

Tasks 0–2 from `task-list.md` are complete. Next up: Task 3 (Artist CRUD API).

**Done:**
- `music_scanner.py` — One-time Python scanner that produced `catalog.json` (prerequisite, already run)
- `catalog.json` — Scanned library (~176 artists, ~2830 albums, ~31K tracks across 7 genres) at project root
- `plan.md` — Full architecture and design document
- `task-list.md` — Sequential implementation tasks (Tasks 0-10)
- **Backend skeleton** — Spring Boot app with Gradle build, Flyway migration, H2 database, application.yml
- **Domain entities** — ArtistEntity, AlbumEntity, SongEntity, TagEntity with JPA mappings
- **Repositories** — ArtistRepository, AlbumRepository, SongRepository, TagRepository
- **Catalog import** — CatalogImportService (JSON → DB), CatalogAutoImporter (auto-imports on first startup if DB empty), POST /api/catalog/import endpoint
- **Catalog DTOs** — Java records in `dto.catalog` package: Catalog, Genre, Artist, Album, Stats, ImportResult

**Not started:**
- Frontend (React) — `frontend/` directory is empty
- REST APIs for artists, albums, browse, random, tags
- Google Sheets backup, export endpoints
- Exception handling, JPA Specifications

## Architecture

Monorepo with two modules that build into a single deployable JAR:

- **backend/** — Java 25, Spring Boot 4.0.2, Spring Data JPA, H2 (file-persisted), Flyway migrations, SpringDoc OpenAPI 3.0.1
- **frontend/** — React 19, TypeScript, Vite 6, TanStack Query v5, React Router 7, Tailwind CSS 4, Lucide icons (not started)
- **catalog.json** — Scanner output at project root (one-time initial seed)
- **config/** — Google Sheets service account credentials

Data flow: Google Sheets ↔ App (read/write persistent store) ↔ H2 DB (runtime cache) ↔ REST API ↔ React UI

Initial seed: `catalog.json` → imported into H2 on first boot → synced to Google Sheets

## Key Commands

```bash
# Development (two terminals)
cd backend && ./gradlew bootRun          # Backend on :8080
cd frontend && npm run dev               # Frontend on :5173 (proxies /api to :8080)

# Production build (single JAR)
./gradlew bootJar
java -jar backend/build/libs/music-library-*.jar   # Serves both API and UI on :8080
```

## Domain Model

```
Artist (1) ---> (N) Album (1) ---> (N) Song
   |  N:M                |  N:M
   +-------> Tag <-------+
```

- Songs accessed through albums only (no direct Song API)
- Tags are shared between artists and albums via join tables
- `grade` (1-5, nullable) and `isFavorite` are on both Artist and Album
- `year` parsed from album folder names during scan; nullable

## API Structure

All endpoints under `/api/`:
- `/api/artists` — Artist CRUD + favorite toggle + tag management
- `/api/albums` — Album CRUD + grade + favorite + tags; supports rich filtering (genre, minGrade, tags, favorite, unrated)
- `/api/browse/genres`, `/api/browse/tags`, `/api/browse/stats`, `/api/browse/favorites` — Navigation/discovery
- `/api/random/album`, `/api/random/albums` — Random pick with same filters as album list
- `/api/tags` — Tag CRUD
- `/api/catalog/import`, `/api/catalog/export/*`, `/api/catalog/backup/gdrive` — Import/export/backup

## Scanner (Prerequisite — Already Complete)

`music_scanner.py` is a one-time script that was run against an external drive to produce `catalog.json`. It is not part of the running application. The external drive is no longer needed.

## Backend Packages

Base package: `io.github.alexshamrai`

**Implemented:**
- `domain/` — JPA entities: ArtistEntity, AlbumEntity, SongEntity, TagEntity
- `repository/` — Spring Data JPA repos with JpaSpecificationExecutor
- `service/` — CatalogImportService (catalog.json → DB)
- `controller/` — CatalogController (POST /api/catalog/import)
- `dto/catalog/` — Java records mapping catalog.json: Catalog, Genre, Artist, Album, Stats, ImportResult
- `startup/` — CatalogAutoImporter (imports on first run if DB empty)

**Planned (not yet created):**
- `specification/` — AlbumSpecs/ArtistSpecs for dynamic query filters
- `service/` — ArtistService, AlbumService, TagService, RandomPickService, CatalogExportService, GoogleSheetsBackupService
- `controller/` — ArtistController, AlbumController, TagController, BrowseController, RandomController
- `dto/` — Request/response DTOs (ArtistDto, AlbumDto, etc.)
- `exception/` — NotFoundException, NoMatchException, GlobalExceptionHandler
- `config/` — WebConfig (SPA routing), GoogleSheetsConfig

## Important Conventions

- **Google Sheets is the persistent store** — the app reads from and writes to Google Sheets; H2 is a runtime database/cache
- Album filtering uses JPA Specifications (composable via `.and()`)
- Google Sheets integration is `@ConditionalOnProperty` — disabled by default, no errors when credentials missing
- Frontend uses Vite proxy in dev; production build outputs to `backend/src/main/resources/static/`
- SPA routing: Spring forwards non-API/non-static paths to `index.html`
- Schema managed by Flyway (`db/migration/V1__init_schema.sql`), JPA set to `validate` mode
- `year` is a reserved word in H2 — must be quoted (`"year"`) in SQL migrations and JPA `@Column(name = "\"year\"")`
- Java 25 (via sdkman), Gradle 9.0 wrapper, Spring Boot 4.0.2, Lombok 1.18.42
- Spring Boot 4.0 modularization: Flyway requires `spring-boot-starter-flyway`, H2 console requires `spring-boot-h2console`