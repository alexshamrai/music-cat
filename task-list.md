# Claude Code Task List — Music Catalog App

Each task below is a self-contained prompt for Claude Code. Run them sequentially.
Replace `<YOUR_MUSIC_PATH>` with your actual music folder path in Task 0.

After each task, verify the "Done when" criteria before moving to the next.

---

## Task 0 — Scan Music Folder (Prerequisite) ✅ COMPLETED

> **Done when:** `catalog.json` exists with valid data, you've reviewed a sample.

```
I need you to scan my music folder and produce a catalog.json file. But first, INVESTIGATE the actual folder structure before writing any code.

## Step 1: Investigate Structure

Explore the folder at: <YOUR_MUSIC_PATH>

Do the following:
1. List the top-level directories (expected: genres like "Rock", "Jazz", etc.)
2. For 3-4 of those top-level dirs, list their subdirectories (expected: artist names)
3. For 3-4 of those artist dirs, list their subdirectories (expected: album names)
4. For 2-3 of those album dirs, list the files inside (expected: .mp3 files)
5. Check for any inconsistencies or deviations from the expected pattern:
   - Are there files (not folders) at unexpected levels?
   - Are there non-mp3 files mixed in (covers, .jpg, .m3u, .cue, .flac, etc.)?
   - Are there extra nesting levels (e.g. Genre/Subgenre/Artist/Album)?
   - Are there albums directly under genre (missing artist level)?
   - Are folder names consistent or do some have special formatting?
   - Are there hidden files/folders (starting with .)?
6. Pay special attention to album folder naming patterns. Check if year is embedded in album folder names and what format is used. Common patterns:
   - "Kind of Blue (1959)"
   - "1959 - Kind of Blue"
   - "(1959) Kind of Blue"
   - "Kind of Blue [1959]"
   - "Kind of Blue" (no year at all)
   Show me 10-15 real album folder names so we can identify the pattern(s) used.

Report your findings before proceeding. Show me the actual structure you found with examples.

## Step 2: Write and Run the Scanner

After investigating, write a Python script `music_scanner.py` that:

1. Walks the structure following the pattern you discovered in Step 1
2. Handles any edge cases and inconsistencies you found
3. Parses year from album folder names based on the pattern(s) you discovered in Step 1. Strip the year from the album title so the title is clean. Examples:
   - "Kind of Blue (1959)" → title: "Kind of Blue", year: 1959
   - "1959 - Kind of Blue" → title: "Kind of Blue", year: 1959
   - "Kind of Blue" → title: "Kind of Blue", year: null
   Adapt the regex to whatever pattern(s) you actually found in the folder names.
4. Skips hidden files/folders (starting with .)
5. Only collects .mp3 files as songs
6. Logs warnings for anything that doesn't fit the expected pattern (e.g. unexpected files, missing levels)

Output file: `catalog.json` in the project root.

Expected JSON structure:
{
  "scannedAt": "<ISO timestamp>",
  "rootPath": "<absolute path scanned>",
  "stats": {
    "totalGenres": <int>,
    "totalArtists": <int>,
    "totalAlbums": <int>,
    "totalTracks": <int>
  },
  "warnings": [
    "Skipped file at genre level: /path/to/desktop.ini",
    "Album with no mp3 files: Rock/Queen/Photos"
  ],
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
                "02 - Freddie Freeloader.mp3"
              ]
            }
          ]
        }
      ]
    }
  ]
}

Key rules:
- Songs are raw filenames (just the .mp3 file names, not full paths)
- Songs should be sorted naturally (so "2 - Track.mp3" comes before "10 - Track.mp3")
- Skip genres/artists that end up with zero albums after filtering
- Skip albums that have zero .mp3 files
- Collect ALL warnings about skipped items or structural oddities into the "warnings" array
- Print a summary at the end: genre count, artist count, album count, track count, warning count

## Step 3: Run It and Show Results

Execute the script against <YOUR_MUSIC_PATH>.
Show me:
1. The summary output (counts)
2. The warnings (if any)
3. The file size of catalog.json
4. A sample of 2-3 genres from the output to verify correctness

Do NOT proceed to Step 2 until you've shown me the Step 1 investigation results and I've confirmed.
```

---

## Task 1 — Project Skeleton & Entities ✅ COMPLETED

> **Done when:** `./gradlew bootRun` starts, H2 console at `/h2-console` shows all tables (artist, album, song, tag, artist_tags, album_tags).

```
Set up the monorepo for a Music Library application with a Java Spring Boot backend and a React TypeScript frontend. Follow these instructions exactly.

## 1. Monorepo Structure

Create this directory layout:

music-cat/
├── backend/
├── frontend/
├── catalog.json       (already exists at project root from scanning step)
├── config/
├── build.gradle.kts   (root)
├── settings.gradle.kts
└── README.md

## 2. Root Gradle Setup

`settings.gradle.kts`:
- rootProject.name = "music-cat"
- include("backend")

`build.gradle.kts` (root): minimal, just declares the project.

## 3. Backend Module (Spring Boot 4.0.2, Java 25)

Initialize `backend/build.gradle.kts` with these dependencies:
- spring-boot-starter-web
- spring-boot-starter-data-jpa
- spring-boot-starter-validation
- h2 (runtimeOnly)
- spring-boot-h2console (Spring Boot 4.0 modularization)
- spring-boot-starter-flyway (Spring Boot 4.0 modularization)
- springdoc-openapi-starter-webmvc-ui (version 3.0.1)
- lombok 1.18.42 (compileOnly + annotationProcessor)
- spring-boot-starter-test (testImplementation)

## 4. Application Configuration

Create `backend/src/main/resources/application.yml`:

spring:
  datasource:
    url: jdbc:h2:file:./data/music-cat;AUTO_SERVER=TRUE
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  h2:
    console:
      enabled: true
      path: /h2-console
  flyway:
    enabled: true

music-cat:
  catalog-path: ../catalog.json

server:
  port: 8080

## 5. Flyway Migration

Create `backend/src/main/resources/db/migration/V1__init_schema.sql` with these tables:

**artist** table:
- id BIGINT AUTO_INCREMENT PRIMARY KEY
- name VARCHAR(255) NOT NULL
- genre VARCHAR(100) NOT NULL
- subgenre VARCHAR(100)
- is_favorite BOOLEAN DEFAULT FALSE
- created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
- updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP

**album** table:
- id BIGINT AUTO_INCREMENT PRIMARY KEY
- title VARCHAR(255) NOT NULL
- year INTEGER
- grade INTEGER (CHECK grade BETWEEN 1 AND 5 or NULL)
- is_favorite BOOLEAN DEFAULT FALSE
- artist_id BIGINT NOT NULL, FOREIGN KEY REFERENCES artist(id) ON DELETE CASCADE
- created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
- updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP

**song** table:
- id BIGINT AUTO_INCREMENT PRIMARY KEY
- title VARCHAR(255) NOT NULL
- track_number INTEGER NOT NULL
- disc_number INTEGER DEFAULT 1
- album_id BIGINT NOT NULL, FOREIGN KEY REFERENCES album(id) ON DELETE CASCADE

**tag** table:
- id BIGINT AUTO_INCREMENT PRIMARY KEY
- name VARCHAR(100) NOT NULL UNIQUE

**artist_tags** join table:
- artist_id BIGINT, FOREIGN KEY REFERENCES artist(id) ON DELETE CASCADE
- tag_id BIGINT, FOREIGN KEY REFERENCES tag(id) ON DELETE CASCADE
- PRIMARY KEY (artist_id, tag_id)

**album_tags** join table:
- album_id BIGINT, FOREIGN KEY REFERENCES album(id) ON DELETE CASCADE
- tag_id BIGINT, FOREIGN KEY REFERENCES tag(id) ON DELETE CASCADE
- PRIMARY KEY (album_id, tag_id)

Add indexes on: artist.genre, artist.name, album.artist_id, album.grade, song.album_id.

## 6. JPA Entities

Create entities in `io.github.alexshamrai.domain` package:

**ArtistEntity.java**: Maps to artist table. Has:
- @OneToMany(mappedBy = "artist", cascade = ALL, orphanRemoval = true) List<Album> albums
- @ManyToMany with Tag via artist_tags join table
- @PreUpdate method to set updatedAt

**AlbumEntity.java**: Maps to album table. Has:
- @ManyToOne(fetch = LAZY) ArtistEntity artist
- @OneToMany(mappedBy = "album", cascade = ALL, orphanRemoval = true) List<SongEntity> songs
- @ManyToMany with Tag via album_tags join table
- @PreUpdate method to set updatedAt

**SongEntity.java**: Maps to song table. Has:
- @ManyToOne(fetch = LAZY) AlbumEntity album

**TagEntity.java**: Maps to tag table. Has:
- @ManyToMany(mappedBy) for both artists and albums

Use Lombok (@Data, @NoArgsConstructor, @AllArgsConstructor, @Builder) on all entities.

## 7. Repositories

Create in `io.github.alexshamrai.repository`:
- ArtistRepository extends JpaRepository<ArtistEntity, Long>, JpaSpecificationExecutor<ArtistEntity>
- AlbumRepository extends JpaRepository<AlbumEntity, Long>, JpaSpecificationExecutor<AlbumEntity>
- SongRepository extends JpaRepository<SongEntity, Long>
- TagRepository extends JpaRepository<TagEntity, Long> with findByName(String name) and findByNameIn(Collection<String> names)

## 8. Main Application Class

Create `MusicLibraryApplication.java` with @SpringBootApplication in `io.github.alexshamrai`.

## 9. Verification

After creating everything, run the application and confirm:
- It starts without errors
- Flyway migration runs successfully
- H2 console is accessible (show me how to connect)
- All tables exist with correct columns
```

---

## Task 2 — Catalog Import ✅ COMPLETED

> **Done when:** App starts, auto-imports `catalog.json`, artists/albums/songs visible in H2 console.

```
Implement the catalog import feature for the Music Library app. This reads catalog.json (produced by the scanner) and populates the H2 database.

The project skeleton with entities and repositories already exists in the backend/ module.

## 1. DTOs for catalog.json

Create in `io.github.alexshamrai.dto.catalog` package (implemented as Java records):

**Catalog**: scannedAt (String), rootPath (String), stats (Stats), warnings (List<String>), catalog (List<Genre>)
**Genre**: genre (String), artists (List<Artist>)
**Artist**: name (String), albums (List<Album>)
**Album**: title (String), year (Integer, nullable), songs (List<String>)
**Stats**: totalGenres, totalArtists, totalAlbums, totalTracks (all int)
**ImportResult**: artistCount, albumCount, songCount (all int)

Use @JsonIgnoreProperties(ignoreUnknown = true) on records.

## 2. CatalogImportService

Create `io.github.alexshamrai.service.CatalogImportService`:

Method: `ImportResult importFromJson(Path catalogFile)`

Logic:
1. Read and deserialize catalog.json using ObjectMapper
2. For each genre entry in catalog:
   a. For each artist in the genre:
      - Find existing artist by name+genre, or create new Artist(name, genre)
      - Save the artist
   b. For each album under the artist:
      - Skip if album already exists for this artist (by title match)
      - Create Album(title, year from JSON which may be null, artist)
      - Save the album
   c. For each song filename in the album:
      - Parse the song title from filename: strip track number prefix and .mp3 extension
        Examples: "01 - So What.mp3" → title: "So What", trackNumber: 1
                  "02. Freddie Freeloader.mp3" → title: "Freddie Freeloader", trackNumber: 2
                  "Track 3 - Blue in Green.mp3" → title: "Blue in Green", trackNumber: 3
                  "Some Song.mp3" → title: "Some Song", trackNumber: <positional index>
      - Handle various separator patterns: " - ", ". ", "_ ", etc.
      - If track number can't be parsed from filename, use the positional index (1-based)
      - Create Song(title, trackNumber, discNumber=1, album)
      - Save the song
3. Return ImportResult(artistCount, albumCount, songCount)

Use @Transactional on the import method.

## 3. CatalogAutoImporter

Create `io.github.alexshamrai.startup.CatalogAutoImporter`:

- Listens for ApplicationReadyEvent
- Checks if the database is empty (artistRepository.count() == 0)
- If empty, reads the catalog file from the path configured in application.yml (music-cat.catalog-path)
- Calls CatalogImportService.importFromJson()
- Logs the result: "Imported X artists, Y albums, Z songs"
- If catalog file doesn't exist, logs a warning and skips

## 4. Manual Import Endpoint

Create `io.github.alexshamrai.controller.CatalogController`:

POST /api/catalog/import
- Accepts multipart file upload of catalog.json
- Calls CatalogImportService
- Returns ImportResult as JSON

## 5. Verification

After implementing:
1. Delete the H2 data file if it exists (./data/music-cat.mv.db)
2. Start the app — it should auto-import from catalog.json
3. Show me the log output with import counts
4. Query H2 console: count of artists, albums, songs
5. Show a sample artist with their albums and songs to verify correctness
6. Show that song titles are properly parsed (no track numbers, no .mp3 extension)
```

---

## Task 3 — Artist CRUD API ✅ COMPLETED

> **Done when:** All artist endpoints work in Swagger UI — create, read, update, delete, toggle favorite, manage tags.

```
Implement the Artist CRUD REST API for the Music Library app. Entities, repositories, and import are already working.

## 1. DTOs

Create in `io.github.alexshamrai.dto`:

**ArtistDto** (response): id, name, genre, subgenre, isFavorite, tags (List<String>), albumCount (int)
**ArtistCreateDto** (request for POST): name (required), genre (required), subgenre (optional)
**ArtistUpdateDto** (request for PUT): name, genre, subgenre — all optional, only non-null fields update

Use Lombok @Data. Add Jakarta validation: @NotBlank on required fields.

## 2. ArtistService

Create `io.github.alexshamrai.service.ArtistService`:

- List<ArtistDto> findAll(String genre, String subgenre, Boolean favorite, String tag) — apply filters if non-null
- ArtistDto findById(Long id) — throw NotFoundException if not found
- ArtistDto create(ArtistCreateDto dto)
- ArtistDto update(Long id, ArtistUpdateDto dto) — partial update, only set non-null fields
- void delete(Long id)
- ArtistDto toggleFavorite(Long id) — flip isFavorite boolean
- ArtistDto setTags(Long id, List<String> tagNames) — replace all tags; create Tag entities if they don't exist

Map entities to DTOs. albumCount = artist.getAlbums().size().

## 3. ArtistController

Create `io.github.alexshamrai.controller.ArtistController` with @RestController @RequestMapping("/api/artists"):

GET    /api/artists              — list all, optional query params: genre, subgenre, favorite, tag
GET    /api/artists/{id}         — get by id
POST   /api/artists              — create, @Valid @RequestBody ArtistCreateDto
PUT    /api/artists/{id}         — update, @RequestBody ArtistUpdateDto
DELETE /api/artists/{id}         — delete (cascades albums and songs)
PATCH  /api/artists/{id}/favorite — toggle favorite
PUT    /api/artists/{id}/tags    — set tags, @RequestBody List<String>

All endpoints return ArtistDto (or void for delete). Use ResponseEntity with proper HTTP status codes:
- 200 for success
- 201 for create
- 204 for delete
- 404 for not found

## 4. Exception Handling

Create `io.github.alexshamrai.exception` package:
- NotFoundException extends RuntimeException
- GlobalExceptionHandler with @ControllerAdvice:
  - Handle NotFoundException → 404 with message
  - Handle MethodArgumentNotValidException → 400 with field errors
  - Handle generic Exception → 500

Response format for errors: { "status": 404, "message": "Artist not found with id: 42" }

## 5. Verification

Start the app and test via Swagger UI (http://localhost:8080/swagger-ui):
1. GET /api/artists — should return imported artists with albumCount
2. GET /api/artists?genre=Rock — filtered list
3. GET /api/artists/{id} — single artist detail
4. POST /api/artists — create a new artist, verify 201 response
5. PUT /api/artists/{id} — update genre, verify only that field changed
6. PATCH /api/artists/{id}/favorite — toggle, verify isFavorite flipped
7. PUT /api/artists/{id}/tags — set ["rock", "classic"], verify tags returned
8. DELETE /api/artists/{id} — verify gone, verify cascade deleted albums
9. POST with missing name — verify 400 validation error

Show me the Swagger UI and example responses for each.
```

---

## Task 4 — Album CRUD API

> **Done when:** All album endpoints work — create, read, update, delete, grade, favorite, tags. Album detail returns song list.

```
Implement the Album CRUD REST API for the Music Library app. Artist API is already working.

## 1. DTOs

Create in `io.github.alexshamrai.dto`:

**SongDto** (response): id, title, trackNumber, discNumber
**AlbumDto** (response): id, title, year, grade, isFavorite, artist (object with id, name, genre), tags (List<String>), songs (List<SongDto> ordered by discNumber then trackNumber)
**AlbumSummaryDto** (for list responses, without songs): id, title, year, grade, isFavorite, artistName, genre, tags (List<String>), songCount (int)
**AlbumCreateDto** (request): title (required), year, artistId (required)
**AlbumUpdateDto** (request): title, year — optional fields
**GradeDto** (request): grade (int, @Min(1) @Max(5))

## 2. AlbumService

Create `io.github.alexshamrai.service.AlbumService`:

- List<AlbumSummaryDto> findAll(AlbumFilterParams filters) — see filter params below
- AlbumDto findById(Long id) — full detail with songs, throw NotFoundException
- AlbumSummaryDto create(AlbumCreateDto dto) — validates artistId exists
- AlbumSummaryDto update(Long id, AlbumUpdateDto dto) — partial update
- void delete(Long id)
- AlbumSummaryDto setGrade(Long id, int grade)
- AlbumSummaryDto toggleFavorite(Long id)
- AlbumSummaryDto setTags(Long id, List<String> tagNames) — replace all tags; create Tags if needed

## 3. AlbumFilterParams

Create `io.github.alexshamrai.dto.AlbumFilterParams`:
Fields (all optional): genre (String), subgenre (String), artistId (Long), artistName (String), tag (List<String>), minGrade (Integer), maxGrade (Integer), favorite (Boolean), unrated (Boolean)

Bind from query parameters. If unrated=true, filter where grade IS NULL.

## 4. AlbumController

Create `io.github.alexshamrai.controller.AlbumController` with @RestController @RequestMapping("/api/albums"):

GET    /api/albums               — list all with filters as query params
GET    /api/albums/{id}          — detail with songs
POST   /api/albums               — create, @Valid @RequestBody AlbumCreateDto, return 201
PUT    /api/albums/{id}          — update
DELETE /api/albums/{id}          — delete, return 204
PATCH  /api/albums/{id}/grade    — set grade, @Valid @RequestBody GradeDto
PATCH  /api/albums/{id}/favorite — toggle favorite
PUT    /api/albums/{id}/tags     — set tags, @RequestBody List<String>

## 5. Tag Endpoints

Create `io.github.alexshamrai.controller.TagController` with @RequestMapping("/api/tags"):

GET    /api/tags                 — list all tags
POST   /api/tags                 — create tag, body: { "name": "chill" }
DELETE /api/tags/{id}            — delete tag (removes from all associations)

## 6. Verification

Test via Swagger UI:
1. GET /api/albums — list with albumCount
2. GET /api/albums?genre=Jazz&minGrade=3 — filtered
3. GET /api/albums?unrated=true — albums with no grade
4. GET /api/albums/{id} — verify songs are included, ordered by disc/track
5. PATCH /api/albums/{id}/grade with {"grade": 4} — verify grade set
6. PATCH /api/albums/{id}/grade with {"grade": 6} — verify 400 validation error
7. PATCH /api/albums/{id}/favorite — toggle
8. PUT /api/albums/{id}/tags with ["chill", "instrumental"] — verify tags
9. POST /api/albums with new album for existing artist — verify 201
10. DELETE /api/albums/{id} — verify songs also deleted

Show me example responses for album list (summary) and album detail (with songs).
```

---

## Task 5 — Browse & Random Album API

> **Done when:** Browse endpoints return genre/tag summaries. `GET /api/random/album?genre=Jazz` returns a random album that changes on repeated calls.

```
Implement Browse and Random Album endpoints for the Music Library app. All CRUD APIs are working.

## 1. JPA Specifications

Create `io.github.alexshamrai.specification.AlbumSpecs`:

Static methods that return Specification<AlbumEntity>:
- artistGenreEquals(String genre) — join to artist, where artist.genre = genre
- artistSubgenreEquals(String subgenre)
- byArtist(Long artistId) — where album.artist.id = artistId
- artistNameContains(String name) — case-insensitive LIKE
- hasTag(String tagName) — join to tags, where tag.name = tagName
- hasAnyTag(List<String> tagNames) — join to tags, where tag.name IN (tagNames)
- gradeGte(int min) — where grade >= min
- gradeLte(int max) — where grade <= max
- isFavorite() — where isFavorite = true
- isUnrated() — where grade IS NULL

These should be composable via spec.and(otherSpec).

If findAll in AlbumService is not already using these Specifications, refactor it to use them. The AlbumFilterParams should be converted to a combined Specification.

## 2. BrowseController

Create `io.github.alexshamrai.controller.BrowseController` with @RequestMapping("/api/browse"):

**GET /api/browse/genres**
Returns list of: { genre, artistCount, albumCount }
Query: SELECT genre, COUNT(DISTINCT artist.id), COUNT(album.id) GROUP BY genre
Sort alphabetically by genre.

**GET /api/browse/genres/{genre}**
Returns list of ArtistDto for that genre (reuse ArtistService with genre filter).

**GET /api/browse/genres/{genre}/artists/{artistId}**
Returns list of AlbumSummaryDto for that artist.

**GET /api/browse/tags**
Returns list of: { tag, artistCount, albumCount }
For each tag, count how many artists and albums use it.
Sort by total usage descending.

**GET /api/browse/favorites**
Returns: { favoriteArtists: List<ArtistDto>, favoriteAlbums: List<AlbumSummaryDto> }

**GET /api/browse/stats**
Returns:
{
  totalArtists: int,
  totalAlbums: int,
  totalSongs: int,
  totalTags: int,
  totalGenres: int,
  favoriteArtists: int,
  favoriteAlbums: int,
  ratedAlbums: int,
  unratedAlbums: int,
  gradeDistribution: { "1": count, "2": count, "3": count, "4": count, "5": count }
}

## 3. RandomPickService

Create `io.github.alexshamrai.service.RandomPickService`:

**randomAlbum(AlbumFilterParams filters):**
1. Build Specification<AlbumEntity> from filters (reuse AlbumSpecs)
2. Count matching albums
3. If 0, throw NoMatchException("No albums match the given filters")
4. Generate random offset (0 to count-1)
5. Use albumRepository.findAll(spec, PageRequest.of(randomOffset, 1)).getContent().get(0)
6. Return as AlbumDto (with songs)

**randomAlbums(AlbumFilterParams filters, int count):**
1. Build spec, find all matching
2. Shuffle the list
3. Return first N as AlbumDto list
4. If fewer than N exist, return all of them

## 4. RandomController

Create `io.github.alexshamrai.controller.RandomController` with @RequestMapping("/api/random"):

**GET /api/random/album**
Query params: same as AlbumFilterParams (genre, minGrade, tag, favorite, etc.)
Returns: AlbumDto (full detail with songs)
On no match: 404 with message "No albums match the given filters"

**GET /api/random/albums?count=5**
Same filters + count param (default 5, max 20)
Returns: List<AlbumDto>

## 5. NoMatchException

Create `io.github.alexshamrai.exception.NoMatchException` and handle in GlobalExceptionHandler → 404.

## 6. Verification

Test these scenarios:
1. GET /api/browse/genres — verify all genres with correct counts
2. GET /api/browse/tags — verify tags with usage counts
3. GET /api/browse/stats — verify all stats numbers make sense
4. GET /api/random/album — call 5 times, show that results vary
5. GET /api/random/album?genre=Jazz — only Jazz albums returned
6. GET /api/random/album?minGrade=4&favorite=true — combined filters
7. GET /api/random/album?genre=NonExistentGenre — verify 404
8. GET /api/random/albums?count=3&genre=Rock — verify 3 random Rock albums
9. GET /api/browse/favorites — verify only favorited items returned

Show me the responses.
```

---

## Task 6 — Frontend Shell, Routing & Dashboard

> **Done when:** `npm run dev` shows the app with sidebar navigation. Dashboard page displays real stats from the API. Clicking sidebar links navigates between pages (pages can be placeholder content for now).

```
Set up the React TypeScript frontend for the Music Library app. The backend API is fully working at localhost:8080.

## 1. Initialize Frontend

In the `frontend/` directory:
- Initialize with Vite: React + TypeScript template
- Install dependencies:
  - react, react-dom (v19)
  - react-router (v7)
  - @tanstack/react-query (v5)
  - axios
  - lucide-react
  - tailwindcss (v4)

## 2. Vite Configuration

`vite.config.ts`:
- Proxy /api/* to http://localhost:8080
- Output build to ../backend/src/main/resources/static

## 3. Tailwind Setup

Configure Tailwind CSS 4. Keep it simple — we're going for functional & clean, not flashy.

## 4. TypeScript Types

Create `src/types/index.ts` with interfaces matching the backend DTOs:

Artist: id, name, genre, subgenre?, isFavorite, tags (string[]), albumCount
AlbumSummary: id, title, year?, grade?, isFavorite, artistName, genre, tags (string[]), songCount
Album (full detail): id, title, year?, grade?, isFavorite, artist ({id, name, genre}), tags (string[]), songs (Song[])
Song: id, title, trackNumber, discNumber?
Tag: id, name
GenreBrowse: genre, artistCount, albumCount
TagBrowse: tag, artistCount, albumCount
Stats: totalArtists, totalAlbums, totalSongs, totalTags, totalGenres, favoriteArtists, favoriteAlbums, ratedAlbums, unratedAlbums, gradeDistribution (Record<string, number>)

## 5. API Client

Create `src/api/client.ts`:
- Create an axios instance with baseURL "/api"
- Export typed functions for each endpoint:
  - fetchArtists(filters?), fetchArtist(id), createArtist(data), updateArtist(id, data), deleteArtist(id), toggleArtistFavorite(id), setArtistTags(id, tags)
  - fetchAlbums(filters?), fetchAlbum(id), createAlbum(data), updateAlbum(id, data), deleteAlbum(id), setAlbumGrade(id, grade), toggleAlbumFavorite(id), setAlbumTags(id, tags)
  - fetchGenres(), fetchArtistsByGenre(genre), fetchAlbumsByArtist(genre, artistId)
  - fetchTags(), fetchTagStats()
  - fetchFavorites(), fetchStats()
  - fetchRandomAlbum(filters?), fetchRandomAlbums(filters?, count?)

## 6. TanStack Query Hooks

Create `src/hooks/` with custom hooks wrapping the API client:
- useArtists(filters), useArtist(id)
- useAlbums(filters), useAlbum(id)
- useBrowseGenres(), useBrowseTags(), useBrowseStats(), useFavorites()
- useRandomAlbum(filters)
- Mutation hooks: useToggleFavorite(), useSetGrade(), useSetTags(), etc.

Each query hook should use appropriate queryKeys for caching and invalidation.

## 7. Layout Components

**Layout.tsx**: Full page layout with sidebar on the left (fixed width ~240px) and content area on the right.
**Sidebar.tsx**: Navigation links with icons (use lucide-react):
- 🏠 Dashboard → /
- 📁 Browse → /browse
- 🎤 Artists → /artists
- 💿 Albums → /albums
- 🎲 Random Pick → /random
- ❤️ Favorites → /favorites
- 🏷️ Tags → /tags

Highlight the active route. Keep styling minimal — neutral colors, clean typography.

## 8. Routing

In `App.tsx` set up React Router with:
- / → DashboardPage
- /browse → BrowsePage
- /artists → ArtistListPage
- /artists/:id → ArtistDetailPage
- /albums → AlbumListPage (placeholder for now)
- /albums/:id → AlbumDetailPage (placeholder for now)
- /random → RandomPickPage (placeholder for now)
- /favorites → FavoritesPage (placeholder for now)
- /tags → TagsPage (placeholder for now)

Wrap everything in QueryClientProvider and BrowserRouter.

## 9. DashboardPage

Build `src/pages/DashboardPage.tsx`:
- Fetch stats from /api/browse/stats using the useBrowseStats hook
- Display in a clean grid of stat cards:
  - Total Artists, Total Albums, Total Songs
  - Total Genres, Total Tags
  - Favorite Artists, Favorite Albums
  - Rated Albums, Unrated Albums
- Show grade distribution as a simple bar chart or list (e.g., "★★★★★: 12 albums")
- Show loading state while fetching
- Keep it simple and informative

## 10. Placeholder Pages

For all other pages, create simple components that show the page title and a "Coming soon" message. We'll build them in the next tasks.

## 11. Verification

1. Run `cd frontend && npm run dev`
2. Open http://localhost:5173
3. Verify sidebar renders with all navigation links
4. Click each link — verify URL changes and correct page shows
5. Dashboard shows real stats from the API (not placeholder data)
6. Browser console has no errors
7. Show me a screenshot or describe what you see
```

---

## Task 7 — Frontend: Browse, Artists & Album Detail Pages

> **Done when:** You can navigate Genre → Artist → Album drill-down. Artist page shows albums. Album detail shows songs with clickable star rating, favorite toggle, and tag management.

```
Build the Browse, Artist, and Album detail pages for the Music Library frontend. The shell, routing, API hooks, and Dashboard are already working.

## 1. Shared Components

Build these reusable components first:

**StarRating.tsx**: Displays 1-5 stars. Props: grade (number | null), onChange (grade: number) => void, readonly (boolean).
- Show 5 star icons (filled for active, outline for inactive)
- Clickable when not readonly — calls onChange with new grade
- Show "Unrated" text if grade is null and readonly

**FavoriteToggle.tsx**: Heart icon. Props: isFavorite (boolean), onToggle () => void.
- Filled heart when favorited, outline when not
- Clickable, calls onToggle

**TagBadge.tsx**: Small pill/badge. Props: tag (string), onRemove? () => void, onClick? () => void.
- Show tag name in a rounded pill
- If onRemove provided, show small X button
- If onClick provided, make it clickable (for filtering)

**FilterBar.tsx**: Reusable filter controls. Props: filters (AlbumFilterParams), onChange.
- Genre dropdown (populated from browse/genres endpoint)
- Min grade dropdown (1-5)
- Favorite only checkbox
- Tag input (text input that adds tags)
- Clear all filters button

**AlbumCard.tsx**: Card showing album summary. Props: album (AlbumSummary).
- Album title (clickable link to /albums/:id)
- Artist name, year, song count
- Star rating (read-only display)
- Favorite heart
- Tag badges
- Compact — should work in a grid layout

**ArtistCard.tsx**: Card for artist. Props: artist (Artist).
- Name (link to /artists/:id), genre, subgenre
- Favorite heart, album count, tag badges

## 2. BrowsePage

`src/pages/BrowsePage.tsx`:
- Fetch genres from /api/browse/genres
- Display as a list/grid of genre cards showing: genre name, artist count, album count
- Click a genre → expand or navigate to show artists in that genre (fetch from /api/browse/genres/{genre})
- Click an artist → show their albums inline or navigate to /artists/:id
- This should feel like drilling down: Genres → Artists → Albums

## 3. ArtistListPage

`src/pages/ArtistListPage.tsx`:
- Fetch all artists from /api/artists
- Filter controls: genre dropdown, favorites only, tag filter
- Display as a list or grid of ArtistCards
- Each card links to artist detail

## 4. ArtistDetailPage

`src/pages/ArtistDetailPage.tsx`:
- Fetch artist from /api/artists/:id
- Show artist name, genre, subgenre (editable inline or via edit button)
- FavoriteToggle — calls toggleArtistFavorite mutation
- Tag management — show existing tags as TagBadges with remove, plus input to add new tags
- Album list — grid of AlbumCards for this artist's albums
- All mutations should invalidate relevant queries so UI updates immediately

## 5. AlbumDetailPage

`src/pages/AlbumDetailPage.tsx`:
- Fetch album from /api/albums/:id (includes songs)
- Show: title, year, artist name (link to artist), genre
- StarRating — clickable, calls setAlbumGrade mutation
- FavoriteToggle — calls toggleAlbumFavorite mutation
- Tag management — same pattern as artist detail
- Song list: ordered table/list showing trackNumber, title, discNumber (if > 1)
- Back button or breadcrumb navigation

## 6. AlbumListPage

`src/pages/AlbumListPage.tsx`:
- Fetch all albums from /api/albums
- Full FilterBar at the top (genre, min grade, tags, favorites, unrated)
- Display as grid of AlbumCards
- Filters update the query params and refetch

## 7. Verification

Test the full flow:
1. BrowsePage → click a genre → see artists → click artist → see albums
2. ArtistListPage → filter by genre → click artist → ArtistDetailPage
3. On ArtistDetailPage: toggle favorite (verify heart changes), add a tag, remove a tag
4. Click an album → AlbumDetailPage
5. On AlbumDetailPage: click stars to rate, toggle favorite, add/remove tags, see song list
6. AlbumListPage → apply filters → verify filtered results
7. Navigation works: sidebar links, back button, breadcrumbs
8. No console errors, mutations update UI immediately
```

---

## Task 8 — Frontend: Random Pick, Favorites & Tags Pages

> **Done when:** "Surprise Me" returns a random album with filters working. Favorites and Tags pages display correctly.

```
Build the Random Pick, Favorites, and Tags pages for the Music Library frontend. All other pages are working.

## 1. RandomPickPage

`src/pages/RandomPickPage.tsx` — this is the core feature of the app.

Layout:
- Top section: FilterBar with genre, min grade, tags, favorites only
- Big "🎲 Surprise Me" button (prominent, centered)
- Result area below

Behavior:
- Click "Surprise Me" → calls fetchRandomAlbum with current filters
- Show loading spinner while fetching
- Display result as a large AlbumCard or expanded album view:
  - Album title, artist, year, genre
  - Star rating (clickable to rate immediately)
  - Favorite toggle
  - Song list
  - Tags
- "🎲 Roll Again" button to get another random album (same filters)
- "Clear Filters" to reset
- If no albums match filters, show a friendly message: "No albums match your filters. Try broadening your search."

Make it feel fun — this is the page you'll use most. The reveal should feel satisfying.

## 2. FavoritesPage

`src/pages/FavoritesPage.tsx`:
- Fetch from /api/browse/favorites
- Two sections: "Favorite Artists" and "Favorite Albums"
- Display as grids of ArtistCards and AlbumCards
- Favorite toggles work inline (unfavoriting removes from the list with query invalidation)
- Show counts: "12 favorite artists, 34 favorite albums"
- If no favorites: "No favorites yet. Browse your library and heart the ones you love."

## 3. TagsPage

`src/pages/TagsPage.tsx`:
- Fetch tag stats from /api/browse/tags
- Display as a tag cloud or list: tag name with artist count and album count
- Click a tag → show all artists and albums with that tag (use existing list endpoints with tag filter)
- Ability to create new tags
- Ability to delete unused tags (confirm dialog)
- Sort options: by name, by usage count

## 4. Verification

Test:
1. RandomPickPage: click Surprise Me with no filters → get a random album
2. Apply genre filter → click again → verify album matches genre
3. Apply minGrade=4 → verify returned album has grade >= 4
4. Set filters that match nothing → verify friendly "no matches" message
5. Rate the random album from the result → stars update
6. Click Roll Again → different album appears
7. FavoritesPage: shows favorited items, unfavoriting removes from view
8. TagsPage: shows tags with counts, clicking a tag shows filtered content
```

---

## Task 9 — Google Sheets Backup

> **Done when:** `POST /api/catalog/backup/gdrive` writes current data to a Google Spreadsheet. Verify data visible in Google Sheets.

```
Implement Google Sheets backup for the Music Library app. This writes the current database state to a Google Spreadsheet as CSV-like data.

## Prerequisites (manual steps — I'll do these myself):
1. Create Google Cloud project, enable Google Sheets API
2. Create Service Account, download credentials JSON → save as config/google-credentials.json
3. Create a Google Spreadsheet with two sheets named "Artists" and "Albums"
4. Share the spreadsheet with the service account email

## 1. Add Dependencies

Add to backend/build.gradle.kts:
- com.google.api-client:google-api-client:2.2.0
- com.google.apis:google-api-services-sheets:v4-rev20231023-2.0.0
- com.google.auth:google-auth-library-oauth2-http:1.20.0

## 2. Configuration

Add to application.yml:

music-cat:
  gdrive:
    enabled: false                    # disabled by default, enable when credentials exist
    credentials-path: ../config/google-credentials.json
    spreadsheet-id: ""                # user fills this in
    backup-cron: "0 0 2 * * SUN"     # weekly Sunday 2am

## 3. GoogleSheetsConfig

Create `io.github.alexshamrai.config.GoogleSheetsConfig`:
- @Configuration, @ConditionalOnProperty(name = "music-cat.gdrive.enabled", havingValue = "true")
- Read credentials from the configured path
- Build GoogleCredentials with Sheets scope
- Create and expose a Sheets service bean

## 4. GoogleSheetsBackupService

Create `io.github.alexshamrai.service.GoogleSheetsBackupService`:
- @ConditionalOnProperty same as config

**backup() method:**
1. Fetch all artists with tags and album counts
2. Fetch all albums with artist name, tags, song counts
3. Clear "Artists" sheet (keep row 1 as header)
4. Write header row: id | name | genre | subgenre | isFavorite | tags | albumCount | updatedAt
5. Write all artist rows
6. Clear "Albums" sheet (keep row 1 as header)
7. Write header row: id | title | artistName | genre | year | grade | isFavorite | tags | trackCount | updatedAt
8. Write all album rows
9. Return BackupResultDto with counts and timestamp

**Tags format in cells:** comma-separated string, e.g. "rock, classic, 90s"
**Boolean format:** TRUE/FALSE (Google Sheets native)
**Null values:** empty string

## 5. Scheduled Backup

Add @Scheduled(cron = "${music-cat.gdrive.backup-cron}") on a scheduledBackup() method that calls backup() and logs the result.

## 6. Endpoint

Add to CatalogController:

POST /api/catalog/backup/gdrive — trigger manual backup
- Returns BackupResultDto: { artistCount, albumCount, backedUpAt }
- If gdrive not enabled, return 503 with message "Google Sheets backup is not configured"

## 7. Verification

For now, just verify:
1. App starts fine with gdrive.enabled=false (no errors, no bean creation)
2. POST /api/catalog/backup/gdrive returns 503 when disabled
3. Code compiles and is ready — actual Google Sheets testing happens after I set up credentials

Show me the code and confirm it compiles.
```

---

## Task 10 — Export, Build Integration & Polish

> **Done when:** `./gradlew bootJar` produces a single JAR. `java -jar music-cat.jar` serves both API and React UI on port 8080.

```
Final task: add export endpoints, integrate the frontend build into the Gradle build, add SPA routing support, and verify the full production build.

## 1. Export Endpoints

Add to CatalogController:

**GET /api/catalog/export/json**
- Export the full enriched catalog as JSON
- Format: same structure as catalog.json but with added fields (grade, isFavorite, tags, subgenre, parsed song titles)
- Set Content-Disposition header: attachment; filename="music-cat-export.json"

**GET /api/catalog/export/csv**
- Generate two CSV files: artists.csv and albums.csv
- Zip them together
- Return as application/zip with Content-Disposition: attachment; filename="music-cat-export.zip"

artists.csv columns: id, name, genre, subgenre, isFavorite, tags, albumCount
albums.csv columns: id, title, artistName, genre, year, grade, isFavorite, tags, songCount

## 2. SPA Routing Support

Create `io.github.alexshamrai.config.WebConfig`:
- Forward all non-API, non-static paths to index.html so React Router works
- Paths starting with /api/ should NOT be forwarded
- Static resources (.js, .css, .html, images) should NOT be forwarded
- Everything else (like /browse, /artists/42, /random) forwards to index.html

## 3. Gradle Build Integration

Update root `build.gradle.kts` to:

1. Register a task `buildFrontend` that:
   - Runs `npm install` in frontend/
   - Runs `npm run build` in frontend/
2. Register a task `copyFrontend` that:
   - Depends on buildFrontend
   - Copies frontend/dist/* to backend/src/main/resources/static/
3. Make the backend's `processResources` task depend on copyFrontend

So `./gradlew bootJar` does: npm install → npm build → copy dist → compile Java → package JAR.

## 4. README

Create a README.md at the project root with:
- Project description (one paragraph)
- Prerequisites: Java 17+, Node 18+, catalog.json from scanner
- Quick start (development mode with two terminals)
- Production build and run
- Configuration reference (application.yml key settings)
- Google Sheets backup setup instructions
- API overview (link to /swagger-ui when running)

## 5. Verification

Run the full production build:
1. ./gradlew clean bootJar — should complete without errors
2. java -jar backend/build/libs/music-cat-*.jar
3. Open http://localhost:8080 — React app loads
4. Navigate to /browse, /artists, /random — SPA routing works (no 404)
5. Open http://localhost:8080/swagger-ui — API docs work
6. Open http://localhost:8080/api/browse/stats — API returns data
7. GET /api/catalog/export/json — downloads JSON file
8. GET /api/catalog/export/csv — downloads ZIP file
9. Verify export files have correct content

Show me the build output and confirm everything works from the single JAR.
```

---

## Summary

| Task | Focus | Estimated Time |
|---|---|---|
| **0** | Scan music folder → catalog.json | 30 min |
| **1** | Monorepo, Spring Boot, entities, Flyway | 1-2 hours |
| **2** | Catalog import (JSON → DB) | 1 hour |
| **3** | Artist CRUD API | 1-2 hours |
| **4** | Album CRUD + Tags API | 1-2 hours |
| **5** | Browse + Random Album API | 1-2 hours |
| **6** | Frontend shell, routing, dashboard | 2-3 hours |
| **7** | Browse, artist, album detail pages | 3-4 hours |
| **8** | Random pick, favorites, tags pages | 2-3 hours |
| **9** | Google Sheets backup | 1-2 hours |
| **10** | Export, build integration, polish | 1-2 hours |
| | **Total** | **~15-22 hours** |