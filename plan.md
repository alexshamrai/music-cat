# Music Library App — Comprehensive Design & Implementation Plan

---

## 1. Overall Architecture

```
  PREREQUISITE (done once)              APPLICATION (long-lived)
┌─────────────────────────┐       ┌──────────────────────────────────┐
│ Claude runs scanner     │       │        Spring Boot App           │
│ against external drive  │       │                                  │
│         │               │       │  ┌──────────┐   ┌────────────┐  │
│         ▼               │       │  │ REST API │   │ H2 (file)  │  │
│   catalog.json ─────────────────>  │ /api/*   │──>│ data.mv.db │  │
│                         │       │  └──────────┘   └────────────┘  │
└─────────────────────────┘       │        │                        │
                                  │        ▼  (scheduled / manual)  │
                                  │  ┌──────────────────────┐       │
                                  │  │ Google Drive Backup   │       │
                                  │  │ → artists.csv         │       │
                                  │  │ → albums.csv          │       │
                                  │  │ → catalog_backup.json │       │
                                  │  └──────────────────────┘       │
                                  │                                  │
                                  │  localhost:8080                  │
                                  │  Swagger UI + (optional) SPA    │
                                  └──────────────────────────────────┘
```

---

## 2. Prerequisite: Scanning

**Done by Claude before app development begins.**

1. User provides the external drive path.
2. Claude runs `music_scanner.py` (see separate artifact).
3. Output: `catalog.json` — structured dump of Genre/Artist/Album/songs.
4. This file is committed alongside the app and used for initial import.

The scanner captures **only filesystem data** — no grades, tags, or favorites.
Those are application concerns added later through the API.

### catalog.json Structure

```json
{
  "scannedAt": "2026-02-22T14:30:00",
  "rootPath": "/Volumes/MyDrive/Music",
  "stats": {
    "totalGenres": 12,
    "totalArtists": 148,
    "totalAlbums": 523,
    "totalTracks": 6841
  },
  "catalog": [
    {
      "genre": "Jazz",
      "artists": [
        {
          "name": "Miles Davis",
          "albums": [
            {
              "title": "Kind of Blue",
              "year": 1959,
              "songs": [
                "01 - So What.mp3",
                "02 - Freddie Freeloader.mp3",
                "03 - Blue in Green.mp3",
                "04 - All Blues.mp3",
                "05 - Flamenco Sketches.mp3"
              ]
            },
            {
              "title": "Bitches Brew",
              "year": 1970,
              "songs": [
                "01 - Pharaoh's Dance.mp3",
                "02 - Bitches Brew.mp3"
              ]
            }
          ]
        }
      ]
    },
    {
      "genre": "Rock",
      "artists": [
        {
          "name": "Pink Floyd",
          "albums": [
            {
              "title": "The Dark Side of the Moon",
              "year": 1973,
              "songs": [
                "01 - Speak to Me.mp3",
                "02 - Breathe.mp3"
              ]
            }
          ]
        }
      ]
    }
  ]
}
```

**Key points:**
- Hierarchy mirrors the filesystem: Genre → Artist → Album → songs (filenames)
- Songs are raw filenames — title and trackNumber are parsed during app import, not at scan time
- `year` is parsed from album folder name at scan time (e.g. "Kind of Blue (1959)" → year: 1959); null if not present in folder name
- `title` is cleaned — year portion stripped (e.g. "Kind of Blue (1959)" → "Kind of Blue")
- No app-specific data (grades, tags, favorites) — purely a filesystem snapshot

---

## 3. Domain Model

### 3.1 Entity Relationship

```
┌──────────────────┐         ┌─────────────────────┐         ┌──────────────────┐
│     Artist        │ 1    N │      Album           │ 1    N │      Song        │
├──────────────────┤────────>├─────────────────────┤────────>├──────────────────┤
│ id        (PK)   │        │ id          (PK)     │        │ id        (PK)   │
│ name      (str)  │        │ title       (str)    │        │ title     (str)  │
│ genre     (str)  │        │ year        (int?)   │        │ trackNumber(int) │
│ subgenre  (str?) │        │ grade       (1-5?)   │        │ discNumber(int?) │
│ isFavorite(bool) │        │ isFavorite  (bool)   │        │ album_id  (FK)   │
│ createdAt (ts)   │        │ artist_id   (FK)     │        └──────────────────┘
│ updatedAt (ts)   │        │ createdAt   (ts)     │
│ updatedAt (ts)   │        │ createdAt   (ts)     │
└────────┬─────────┘        │ updatedAt   (ts)     │
         │                  └──────────┬────────────┘
         │ N:M                         │ N:M
         ▼                             ▼
   ┌────────────┐              ┌─────────────┐
   │artist_tags │              │ album_tags  │
   │ artist_id  │              │ album_id    │
   │ tag_id     │              │ tag_id      │
   └─────┬──────┘              └──────┬──────┘
         └────────────┬───────────────┘
                      ▼
              ┌──────────────┐
              │     Tag      │
              ├──────────────┤
              │ id     (PK)  │
              │ name (UNIQUE)│
              └──────────────┘
```

### 3.2 Relationship Design: Song ↔ Album ↔ Artist

**Song → Album (Many-to-One, mandatory)**
Every song belongs to exactly one album. This is the primary relationship — songs are always accessed through their album. The album owns the song lifecycle (cascade delete).

**Song → Artist (indirect, via Album)**
No direct FK from Song to Artist. You always reach the artist through `song.album.artist`. This reflects your listening model (album-oriented) and keeps the schema clean. If you later want featured/guest artists per track, a `song_artists` join table could be added, but for now this is unnecessary complexity.

**Why a Song table instead of JSON array?**
- Proper ordering via `trackNumber` (and `discNumber` for multi-disc albums)
- Schema-enforced data: no malformed JSON, nullable fields handled by DB
- Queryable: `SELECT COUNT(*) FROM song WHERE album_id = ?` without JSON parsing
- Future-proof: easy to add duration, bitrate, or other metadata later
- Album-level queries remain fast — `@OneToMany(fetch = LAZY)` means songs load only when you ask for album detail

**Song properties:**
- `title` — derived from filename during scan (strip numbering + extension, e.g. "01 - So What.mp3" → "So What"), editable later
- `trackNumber` — parsed from filename prefix ("01 - ...", "02 - ...") or positional order as fallback
- `discNumber` — nullable, defaults to 1, useful for double/triple albums

### 3.3 Other Key Design Decisions

- **Tags as separate entity with join tables** — enables filtering like "give me a random album tagged `chill` + `instrumental`" efficiently.
- **grade is nullable** — unrated albums are allowed; you rate them over time.
- **year is nullable** — parsed from album folder name during scan if available (e.g. "Kind of Blue (1959)"); can also be set/corrected manually via the API.
- **No Song CRUD API** — songs are managed as part of album lifecycle (created on import, returned in album detail). No standalone song endpoints needed since you listen by album.

---

## 4. Technology Stack

| Layer | Technology | Rationale |
|---|---|---|
| Language | Java 25 (via sdkman) | Latest features |
| Framework | Spring Boot 4.0.2 | REST, JPA, embedded server, battle-tested |
| ORM | Spring Data JPA + Hibernate | Repository pattern, JPA Specifications for dynamic queries |
| Database | H2 (file-persisted mode) | Embedded, zero-config, single file, full SQL |
| Schema mgmt | Flyway | Versioned migrations, repeatable |
| Build (back) | Gradle 9.0 (Kotlin DSL) | Modern, flexible, orchestrates full-stack build |
| Validation | Jakarta Validation (`@Valid`) | `@Min(1) @Max(5)` for grade, etc. |
| API docs | SpringDoc OpenAPI 3.0.1 | Auto-generated Swagger UI at `/swagger-ui` |
| JSON | Jackson (built into Spring Boot) | Parsing catalog.json + DTO serialization |
| Google integration | Google API Client for Java + Sheets API | CSV backup to Google Drive/Sheets |
| Scheduling | Spring `@Scheduled` | Periodic auto-backup |
| Frontend | React 19 + TypeScript | Largest ecosystem, type safety |
| Build (front) | Vite 6 | Fast dev server, instant HMR, optimized builds |
| Routing | React Router 7 | Standard SPA routing |
| Data fetching | TanStack Query v5 | Caching, loading/error states, invalidation |
| Styling | Tailwind CSS 4 | Utility-first, clean UI with minimal effort |
| Icons | Lucide React | Lightweight, consistent icon set |

---

## 5. Persistence Strategy

### 5.1 H2 File Mode (Primary)

```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/music-library;AUTO_SERVER=TRUE
    driver-class-name: org.h2.Driver
    username: sa
    password:
  h2:
    console:
      enabled: true
      path: /h2-console
```

- Data stored in `./data/music-library.mv.db`
- Survives application restarts
- `AUTO_SERVER=TRUE` allows external tools to connect while app runs
- H2 Console at `http://localhost:8080/h2-console` for manual inspection

### 5.2 Google Drive CSV Backup (Secondary)

Two complementary approaches — choose one or both:

#### Option A: Google Sheets API (write directly to a spreadsheet)

The app writes/updates two sheets inside one Google Spreadsheet:
- **Sheet "Artists"** — id, name, genre, subgenre, isFavorite, tags, albumCount
- **Sheet "Albums"** — id, title, artistName, genre, year, grade, isFavorite, tags, trackCount, songs

**Pros:** Instantly viewable/editable in Google Sheets, no file management.  
**Cons:** Requires Sheets API, slightly more complex auth.

#### Option B: Google Drive API (upload CSV files)

The app generates `artists.csv` and `albums.csv` locally, then uploads/overwrites them on Google Drive.

**Pros:** Simpler API, just file upload. Files still open in Google Sheets.  
**Cons:** Overwrites rather than updates; no live editing roundtrip.

#### Recommendation: **Option A (Google Sheets API)**

Writing directly to a Spreadsheet is cleaner — you get a living, browsable backup that updates in place. One spreadsheet, two sheets, always current.

#### Authentication: Service Account (no login flow)

1. Create a Google Cloud project (free).
2. Enable Google Sheets API.
3. Create a Service Account → download `credentials.json`.
4. Share the target spreadsheet with the service account email.
5. App reads `credentials.json` at startup — no browser OAuth flow, no tokens to refresh manually.

```
credentials.json  →  Service Account  →  Sheets API  →  Your Spreadsheet
(stored locally)     (no user login)     (free tier)     (shared with SA)
```

**Free tier limits:** 300 requests/min — more than enough for periodic backup.

---

## 6. API Design

### 6.1 Catalog Import / Export

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/catalog/import` | Import catalog.json (multipart upload) |
| `GET` | `/api/catalog/export/json` | Export full enriched catalog as JSON |
| `GET` | `/api/catalog/export/csv` | Download zipped artists.csv + albums.csv |
| `POST` | `/api/catalog/backup/gdrive` | Trigger manual Google Sheets backup |

Auto-import: on first startup, if DB is empty and `catalog.json` exists at configured path, import automatically.

### 6.2 Artist CRUD

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/artists` | List all. Filters: `?genre=`, `?subgenre=`, `?favorite=true`, `?tag=` |
| `GET` | `/api/artists/{id}` | Get artist with albums list |
| `POST` | `/api/artists` | Create new artist |
| `PUT` | `/api/artists/{id}` | Update artist (name, genre, subgenre) |
| `DELETE` | `/api/artists/{id}` | Delete artist + cascade albums |
| `PATCH` | `/api/artists/{id}/favorite` | Toggle isFavorite |
| `PUT` | `/api/artists/{id}/tags` | Replace artist tags. Body: `["rock", "classic"]` |

### 6.3 Album CRUD

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/albums` | List all. Filters below. |
| `GET` | `/api/albums/{id}` | Get album detail with songs |
| `POST` | `/api/albums` | Create album |
| `PUT` | `/api/albums/{id}` | Update album (title, year) |
| `DELETE` | `/api/albums/{id}` | Delete album |
| `PATCH` | `/api/albums/{id}/grade` | Set grade 1-5. Body: `{"grade": 4}` |
| `PATCH` | `/api/albums/{id}/favorite` | Toggle isFavorite |
| `PUT` | `/api/albums/{id}/tags` | Replace album tags. Body: `["instrumental", "chill"]` |

**Album list filters** (all optional, combinable via query params):

| Param | Type | Example |
|---|---|---|
| `genre` | string | `?genre=Jazz` |
| `subgenre` | string | `?subgenre=Bebop` |
| `artistId` | long | `?artistId=42` |
| `artistName` | string | `?artistName=Miles` (partial match) |
| `tag` | string | `?tag=chill` (can repeat: `?tag=chill&tag=90s`) |
| `minGrade` | int 1-5 | `?minGrade=3` |
| `maxGrade` | int 1-5 | `?maxGrade=4` |
| `favorite` | boolean | `?favorite=true` |
| `unrated` | boolean | `?unrated=true` (grade IS NULL) |

### 6.4 Browse / Navigate

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/browse/genres` | All genres with artist count and album count |
| `GET` | `/api/browse/genres/{genre}` | Artists in that genre |
| `GET` | `/api/browse/genres/{genre}/artists/{artistId}` | Albums by artist |
| `GET` | `/api/browse/tags` | All tags with usage counts |
| `GET` | `/api/browse/favorites` | All favorite artists and albums |
| `GET` | `/api/browse/stats` | Total counts, grade distribution, etc. |

### 6.5 Random Album 🎲

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/random/album` | Get one random album matching filters |
| `GET` | `/api/random/albums?count=5` | Get N random albums matching filters |

Accepts **all the same filter params** as `/api/albums`:

```
GET /api/random/album?genre=Jazz&minGrade=3&tag=chill&favorite=true
```

**Implementation — JPA Specification pattern:**

```java
@Service
@RequiredArgsConstructor
public class RandomPickService {
    private final AlbumRepository albumRepo;

    public Album randomAlbum(AlbumFilterParams filters) {
        Specification<Album> spec = buildSpec(filters);
        long count = albumRepo.count(spec);
        if (count == 0) throw new NoMatchException("No albums match filters");

        int randomOffset = ThreadLocalRandom.current().nextInt((int) count);

        return albumRepo.findAll(spec, PageRequest.of(randomOffset, 1))
            .getContent()
            .get(0);
    }

    public List<Album> randomAlbums(AlbumFilterParams filters, int count) {
        Specification<Album> spec = buildSpec(filters);
        List<Album> candidates = albumRepo.findAll(spec);
        Collections.shuffle(candidates);
        return candidates.stream().limit(count).toList();
    }

    private Specification<Album> buildSpec(AlbumFilterParams f) {
        Specification<Album> spec = Specification.where(null);
        if (f.getGenre() != null)
            spec = spec.and(AlbumSpecs.artistGenreEquals(f.getGenre()));
        if (f.getMinGrade() != null)
            spec = spec.and(AlbumSpecs.gradeGte(f.getMinGrade()));
        if (f.getTags() != null && !f.getTags().isEmpty())
            spec = spec.and(AlbumSpecs.hasAnyTag(f.getTags()));
        if (Boolean.TRUE.equals(f.getFavorite()))
            spec = spec.and(AlbumSpecs.isFavorite());
        if (f.getArtistId() != null)
            spec = spec.and(AlbumSpecs.byArtist(f.getArtistId()));
        // ... other filters
        return spec;
    }
}
```

### 6.6 Tags

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/tags` | List all tags |
| `POST` | `/api/tags` | Create tag |
| `DELETE` | `/api/tags/{id}` | Delete tag (removes from all associations) |

Tags are also created on-the-fly when setting tags on an artist/album.

---

## 7. Google Sheets Backup — Detail Design

### 7.1 Spreadsheet Layout

One Google Spreadsheet with two sheets:

**Sheet: "Artists"**

| id | name | genre | subgenre | isFavorite | tags | albumCount | updatedAt |
|---|---|---|---|---|---|---|---|
| 1 | Miles Davis | Jazz | | true | jazz, legend | 5 | 2026-02-21 |
| 2 | Pink Floyd | Rock | Progressive | true | psychedelic, classic | 3 | 2026-02-21 |

**Sheet: "Albums"**

| id | title | artistName | genre | year | grade | isFavorite | tags | trackCount | updatedAt |
|---|---|---|---|---|---|---|---|---|---|
| 1 | Kind of Blue | Miles Davis | Jazz | 1959 | 5 | true | masterpiece | 5 | 2026-02-21 |

### 7.2 Backup Service

```java
@Service
@RequiredArgsConstructor
public class GoogleSheetsBackupService {
    private final ArtistRepository artistRepo;
    private final AlbumRepository albumRepo;
    private final Sheets sheetsService;  // Google Sheets API client

    @Value("${music-library.gdrive.spreadsheet-id}")
    private String spreadsheetId;

    public BackupResult backup() {
        // 1. Clear existing data (keep headers)
        // 2. Write artists sheet
        // 3. Write albums sheet
        // 4. Return stats

        List<Artist> artists = artistRepo.findAll();
        List<Album> albums = albumRepo.findAll();

        List<List<Object>> artistRows = artists.stream()
            .map(a -> List.<Object>of(
                a.getId(), a.getName(), a.getGenre(),
                Optional.ofNullable(a.getSubgenre()).orElse(""),
                a.isFavorite(),
                a.getTags().stream().map(Tag::getName).collect(joining(", ")),
                a.getAlbums().size(),
                a.getUpdatedAt().toString()
            )).toList();

        List<List<Object>> albumRows = albums.stream()
            .map(a -> List.<Object>of(
                a.getId(), a.getTitle(), a.getArtist().getName(),
                a.getArtist().getGenre(),
                Optional.ofNullable(a.getYear()).map(Object::toString).orElse(""),
                Optional.ofNullable(a.getGrade()).map(Object::toString).orElse(""),
                a.isFavorite(),
                a.getTags().stream().map(Tag::getName).collect(joining(", ")),
                a.getSongs().size(),
                a.getUpdatedAt().toString()
            )).toList();

        clearAndWrite("Artists", ARTIST_HEADERS, artistRows);
        clearAndWrite("Albums", ALBUM_HEADERS, albumRows);

        return new BackupResult(artists.size(), albums.size(), Instant.now());
    }

    // Can be triggered manually or on schedule
    @Scheduled(cron = "${music-library.gdrive.backup-cron:0 0 2 * * SUN}")  // weekly
    public void scheduledBackup() {
        backup();
    }
}
```

### 7.3 Configuration

```yaml
music-library:
  catalog-path: ../catalog.json

  gdrive:
    enabled: true
    credentials-path: ./config/google-credentials.json
    spreadsheet-id: "1aBcDeFgHiJkLmNoPqRsTuVwXyZ"     # from spreadsheet URL
    backup-cron: "0 0 2 * * SUN"                        # weekly Sunday 2am
```

### 7.4 Google Setup (One-Time)

```
1. Go to console.cloud.google.com
2. Create project "music-library"
3. Enable "Google Sheets API"
4. Create Service Account → download JSON key → save as ./config/google-credentials.json
5. Create a Google Spreadsheet, add two sheets: "Artists", "Albums"
6. Share the spreadsheet with the service account email (xxxx@project.iam.gserviceaccount.com)
7. Copy spreadsheet ID from URL → paste into application.yml
```

---

## 8. Monorepo Structure

Single repository, two modules — frontend builds into backend's static resources.

```
music-library/
│
├── backend/
│   ├── src/main/java/io/github/alexshamrai/
│   │   ├── MusicLibraryApplication.java
│   │   │
│   │   ├── config/
│   │   │   ├── AppConfig.java                    # General app properties
│   │   │   ├── GoogleSheetsConfig.java           # Sheets API client bean
│   │   │   └── WebConfig.java                    # SPA routing: forward non-API to index.html
│   │   │
│   │   ├── domain/
│   │   │   ├── ArtistEntity.java                 # @Entity
│   │   │   ├── AlbumEntity.java                  # @Entity
│   │   │   ├── SongEntity.java                   # @Entity
│   │   │   └── TagEntity.java                    # @Entity
│   │   │
│   │   ├── repository/
│   │   │   ├── ArtistRepository.java             # JpaSpecificationExecutor
│   │   │   ├── AlbumRepository.java              # JpaSpecificationExecutor
│   │   │   ├── SongRepository.java
│   │   │   └── TagRepository.java
│   │   │
│   │   ├── specification/
│   │   │   ├── ArtistSpecs.java                  # Dynamic query filters
│   │   │   └── AlbumSpecs.java
│   │   │
│   │   ├── service/
│   │   │   ├── ArtistService.java
│   │   │   ├── AlbumService.java
│   │   │   ├── TagService.java
│   │   │   ├── CatalogImportService.java         # catalog.json → DB
│   │   │   ├── CatalogExportService.java         # DB → JSON/CSV download
│   │   │   ├── RandomPickService.java            # Random album with filters
│   │   │   └── GoogleSheetsBackupService.java    # DB → Google Sheets
│   │   │
│   │   ├── controller/
│   │   │   ├── ArtistController.java             # /api/artists
│   │   │   ├── AlbumController.java              # /api/albums
│   │   │   ├── TagController.java                # /api/tags
│   │   │   ├── BrowseController.java             # /api/browse
│   │   │   ├── RandomController.java             # /api/random
│   │   │   └── CatalogController.java            # /api/catalog (import/export/backup)
│   │   │
│   │   ├── dto/
│   │   │   ├── ArtistDto.java / ArtistCreateDto.java
│   │   │   ├── AlbumDto.java / AlbumCreateDto.java
│   │   │   ├── AlbumFilterParams.java            # Query param binding
│   │   │   ├── catalog/                          # Java records mapping catalog.json
│   │   │   │   ├── Catalog.java, Genre.java, Artist.java, Album.java
│   │   │   │   ├── Stats.java, ImportResult.java
│   │   │   ├── BrowseGenreDto.java
│   │   │   ├── StatsDto.java
│   │   │   └── BackupResultDto.java
│   │   │
│   │   ├── exception/
│   │   │   ├── GlobalExceptionHandler.java       # @ControllerAdvice
│   │   │   ├── NotFoundException.java
│   │   │   └── NoMatchException.java
│   │   │
│   │   └── startup/
│   │       └── CatalogAutoImporter.java          # @EventListener(ApplicationReadyEvent)
│   │
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── static/                               # ← frontend build output lands here
│   │   └── db/migration/
│   │       └── V1__init_schema.sql
│   │
│   └── build.gradle.kts                          # Backend build + task to copy frontend dist
│
├── frontend/
│   ├── src/
│   │   ├── main.tsx                              # Entry point
│   │   ├── App.tsx                               # Router setup
│   │   ├── api/
│   │   │   └── client.ts                         # Axios/fetch wrapper, typed API calls
│   │   ├── hooks/
│   │   │   ├── useArtists.ts                     # TanStack Query hooks
│   │   │   ├── useAlbums.ts
│   │   │   ├── useRandomAlbum.ts
│   │   │   └── useBrowse.ts
│   │   ├── pages/
│   │   │   ├── DashboardPage.tsx                 # Stats overview, quick actions
│   │   │   ├── BrowsePage.tsx                    # Genre → Artist → Album drill-down
│   │   │   ├── ArtistListPage.tsx                # Filterable artist table
│   │   │   ├── ArtistDetailPage.tsx              # Artist info + album list
│   │   │   ├── AlbumDetailPage.tsx               # Album info + song list + grade/tags
│   │   │   ├── RandomPickPage.tsx                # 🎲 Filter form + random result
│   │   │   ├── FavoritesPage.tsx                 # Favorite artists & albums
│   │   │   └── TagsPage.tsx                      # Tag management + browse by tag
│   │   ├── components/
│   │   │   ├── Layout.tsx                        # Shell: sidebar + content area
│   │   │   ├── Sidebar.tsx                       # Navigation links
│   │   │   ├── AlbumCard.tsx                     # Album display with grade stars
│   │   │   ├── ArtistCard.tsx
│   │   │   ├── StarRating.tsx                    # Clickable 1-5 star grade
│   │   │   ├── TagBadge.tsx                      # Tag pill with click-to-filter
│   │   │   ├── FavoriteToggle.tsx                # Heart icon toggle
│   │   │   ├── FilterBar.tsx                     # Reusable filter controls
│   │   │   └── RandomAlbumResult.tsx             # Album reveal with "roll again"
│   │   └── types/
│   │       └── index.ts                          # TypeScript interfaces matching backend DTOs
│   │
│   ├── index.html
│   ├── package.json
│   ├── tsconfig.json
│   ├── vite.config.ts                            # Proxy /api → localhost:8080 in dev
│   └── tailwind.config.js
│
├── catalog.json                                  # Scanner output (prerequisite, at project root)
├── config/
│   └── google-credentials.json                   # Service account key
├── data/                                         # H2 file (auto-created)
├── build.gradle.kts                              # Root build orchestrates both modules
├── settings.gradle.kts                           # include("backend", "frontend")
└── README.md
```

### 8.1 Frontend Tech Stack

| Concern | Choice | Why |
|---|---|---|
| Framework | React 19 + TypeScript | Largest ecosystem, best tooling, TS for type safety |
| Build | Vite 6 | Fast dev server, instant HMR, optimized production builds |
| Routing | React Router 7 | Standard, file-based-like routing |
| Data fetching | TanStack Query (React Query) v5 | Caching, invalidation, loading/error states for free |
| HTTP client | Axios or `fetch` wrapper | Typed API calls matching backend DTOs |
| Styling | Tailwind CSS 4 | Utility-first, functional & clean with minimal effort |
| Icons | Lucide React | Lightweight, clean icon set |

### 8.2 Pages & Navigation

```
Sidebar                        Content Area
┌─────────────────┐           ┌──────────────────────────────┐
│ 🏠 Dashboard     │ ───────> │ Stats, recent activity       │
│ 📁 Browse        │ ───────> │ Genre → Artist → Album tree  │
│ 🎤 Artists       │ ───────> │ Filterable artist list       │
│ 💿 Albums        │ ───────> │ Filterable album grid/list   │
│ 🎲 Random Pick   │ ───────> │ Filter form + random result  │
│ ❤️ Favorites     │ ───────> │ Favorite artists & albums    │
│ 🏷️ Tags          │ ───────> │ Tag cloud + browse by tag    │
└─────────────────┘           └──────────────────────────────┘
```

**Key interactions:**
- **Browse**: click Genre → see artists → click artist → see albums → click album → see detail with songs
- **Random Pick**: select filters (genre dropdown, min grade, tags, favorites only) → click "🎲 Surprise Me" → album card reveals with "Roll Again" button
- **Album Detail**: view songs, click stars to set grade, toggle favorite heart, add/remove tag pills
- **Artist Detail**: view albums grid, edit genre/subgenre, toggle favorite, manage tags
- **Inline editing**: grade stars, favorite hearts, and tag pills are clickable directly in list views — no need to open detail page for quick actions

### 8.3 Build Integration

**Development** (two terminals):
```bash
# Terminal 1: backend on :8080
cd backend && ./gradlew bootRun

# Terminal 2: frontend on :5173 with API proxy
cd frontend && npm run dev
```

`vite.config.ts` proxies `/api/**` to `localhost:8080` so the frontend dev server talks to the real backend.

**Production** (single command):
```bash
./gradlew bootJar
# 1. Runs `npm install && npm run build` in frontend/
# 2. Copies frontend/dist/* → backend/src/main/resources/static/
# 3. Builds the Spring Boot JAR
# Result: java -jar backend/build/libs/music-library.jar
#         serves BOTH API and UI on localhost:8080
```

### 8.4 SPA Routing Support

Spring Boot needs to forward non-API, non-static routes to `index.html` so React Router works:

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Forward SPA routes to index.html (React Router handles them)
        registry.addViewController("/{path:[^\\.]*}")
            .setViewName("forward:/index.html");
    }
}
```

### 8.5 TypeScript Types (matching backend DTOs)

```typescript
// frontend/src/types/index.ts

export interface Artist {
    id: number;
    name: string;
    genre: string;
    subgenre?: string;
    isFavorite: boolean;
    tags: Tag[];
    albumCount: number;
}

export interface Album {
    id: number;
    title: string;
    year?: number;
    grade?: number;          // 1-5
    isFavorite: boolean;
    artist: ArtistSummary;
    tags: Tag[];
    songs: Song[];           // ordered by discNumber, trackNumber
}

export interface Song {
    id: number;
    title: string;
    trackNumber: number;
    discNumber?: number;
}

export interface Tag {
    id: number;
    name: string;
}

export interface ArtistSummary {
    id: number;
    name: string;
    genre: string;
}

export interface GenreBrowse {
    genre: string;
    artistCount: number;
    albumCount: number;
}

export interface Stats {
    totalArtists: number;
    totalAlbums: number;
    totalSongs: number;
    totalTags: number;
    gradeDistribution: Record<number, number>;  // { 1: 5, 2: 12, 3: 45, ... }
    favoriteArtists: number;
    favoriteAlbums: number;
}
```
```

---

## 9. Dependencies

### 9.1 Backend (build.gradle.kts)

```kotlin
dependencies {
    // Core
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Database (Spring Boot 4.0 modularization)
    runtimeOnly("com.h2database:h2")
    implementation("org.springframework.boot:spring-boot-h2console")
    implementation("org.springframework.boot:spring-boot-starter-flyway")

    // API docs
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.1")

    // Google Sheets (to be added in Task 9)
    // implementation("com.google.api-client:google-api-client:2.2.0")
    // implementation("com.google.apis:google-api-services-sheets:v4-rev20231023-2.0.0")
    // implementation("com.google.auth:google-auth-library-oauth2-http:1.20.0")

    // Utilities
    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
```

### 9.2 Frontend (package.json)

```json
{
  "dependencies": {
    "react": "^19.0.0",
    "react-dom": "^19.0.0",
    "react-router": "^7.0.0",
    "@tanstack/react-query": "^5.0.0",
    "axios": "^1.7.0",
    "lucide-react": "^0.460.0"
  },
  "devDependencies": {
    "typescript": "^5.7.0",
    "vite": "^6.0.0",
    "@vitejs/plugin-react": "^4.0.0",
    "tailwindcss": "^4.0.0",
    "@types/react": "^19.0.0",
    "@types/react-dom": "^19.0.0"
  }
}
```

---

## 10. Data Lifecycle & Backup Strategy

```
SCAN (once)          IMPORT (once)           LIVE                      BACKUP
─────────────       ──────────────       ─────────────          ──────────────────
External Drive      catalog.json         H2 Database            Google Sheets
     │                   │                    │                 (artists + albums)
     ▼                   ▼                    │                       ▲
 Scanner ──> catalog.json ──> Auto-import ──> │ ── CRUD / grade ──>   │
             (archive)       on 1st run       │    tag / favorite     │
                                              │                       │
                                              ├── Manual: POST /api/catalog/backup/gdrive
                                              ├── Scheduled: weekly cron
                                              ├── Local JSON: GET /api/catalog/export/json
                                              └── Local CSV:  GET /api/catalog/export/csv
```

**Four backup layers:**
1. **catalog.json** — immutable original scan, your "factory reset" snapshot
2. **Google Sheets** — live, human-readable CSV backup with grades/tags/favorites
3. **JSON export** — full enriched catalog, downloadable anytime
4. **H2 file** — copyable binary backup (`./data/music-library.mv.db`)

---

## 11. Implementation Plan

### Phase 0 — Prerequisite: Scan (Day 0) ✅
- [x] Claude scans external drive
- [x] Produces catalog.json
- [x] User validates output, stores catalog.json with project

### Phase 1 — Project Skeleton (Day 1) ✅
- [x] Initialize Spring Boot 4.0.2 project with Gradle 9.0
- [x] Configure build.gradle.kts with all dependencies (Java 25, Lombok 1.18.42)
- [x] Set up application.yml (H2, Flyway, app properties)
- [x] Write Flyway migration `V1__init_schema.sql` (artist, album, song, tag, join tables)
- [x] Create JPA entities: ArtistEntity, AlbumEntity, SongEntity, TagEntity (with join tables)
- [x] Create repositories (extend JpaRepository + JpaSpecificationExecutor)
- [x] Verify: app starts, H2 console shows tables

### Phase 2 — Catalog Import (Day 2) ✅
- [x] Create DTOs mapping catalog.json structure (Java records: Catalog, Genre, Artist, Album, Stats, ImportResult)
- [x] Implement CatalogImportService (JSON → DB upsert)
- [x] Implement CatalogAutoImporter (imports on first startup if DB empty)
- [x] POST /api/catalog/import endpoint (manual re-import)
- [x] Verify: start app → catalog.json auto-loaded → data visible in H2 console

### Phase 3 — CRUD API (Day 3-4)
- [ ] DTO layer: ArtistDto, AlbumDto, create/update variants
- [ ] ArtistService + ArtistController (full CRUD + tags + favorite)
- [ ] AlbumService + AlbumController (full CRUD + grade + tags + favorite)
- [ ] TagService + TagController
- [ ] GlobalExceptionHandler (@ControllerAdvice)
- [ ] Validation: @Min/@Max on grade, @NotBlank on names, etc.
- [ ] Verify via Swagger UI: create, update, delete, tag, grade, favorite

### Phase 4 — Browse & Random (Day 5)
- [ ] BrowseController: genres, tags, favorites, stats endpoints
- [ ] AlbumSpecs / ArtistSpecs (JPA Specification classes for dynamic filters)
- [ ] AlbumFilterParams (query param binding class)
- [ ] RandomPickService (random with all filter combinations)
- [ ] RandomController: single random + multiple random
- [ ] Verify: `GET /api/random/album?genre=Jazz&minGrade=3`

### Phase 5 — Google Sheets Backup (Day 6)
- [ ] Google Cloud project setup + Sheets API enable + service account
- [ ] GoogleSheetsConfig: build Sheets API client from credentials.json
- [ ] GoogleSheetsBackupService: clear sheets, write artists + albums
- [ ] POST /api/catalog/backup/gdrive endpoint (manual trigger)
- [ ] @Scheduled cron for weekly auto-backup
- [ ] Verify: data appears in Google Spreadsheet

### Phase 6 — Export & Polish (Day 7)
- [ ] GET /api/catalog/export/json — full enriched catalog
- [ ] GET /api/catalog/export/csv — zipped CSV download
- [ ] SpringDoc OpenAPI configuration + Swagger UI polish
- [ ] Package as executable JAR: `./gradlew bootJar`
- [ ] README with setup instructions
- [ ] Final end-to-end test

---

## 12. Quick Start (After All Phases)

```bash
# 1. Prerequisites: catalog.json exists, Google credentials configured

# 2. Run
./gradlew bootRun
# First run: auto-imports catalog.json → H2

# 3. Use
open http://localhost:8080/swagger-ui    # Full interactive API
open http://localhost:8080/h2-console    # Database browser

# 4. Examples
curl "http://localhost:8080/api/random/album?genre=Jazz&minGrade=3"
curl "http://localhost:8080/api/browse/genres"
curl -X PATCH "http://localhost:8080/api/albums/42/grade" \
     -H "Content-Type: application/json" -d '{"grade": 5}'
curl -X POST "http://localhost:8080/api/catalog/backup/gdrive"
```

---

## 13. Future Enhancements

- **ID3 metadata parsing** — extract year, proper song titles from MP3 tags
- **Album art** — extract embedded cover art, serve via API, display in UI
- **Listening log** — track when you last played; "suggest unheard albums"
- **Smart playlists** — saved filter presets ("Jazz favorites 4+")
- **Full-text search** — search across all fields with instant results
- **Keyboard shortcuts** — quick navigation, spacebar to roll random
- **Dark mode** — Tailwind `dark:` variant toggle