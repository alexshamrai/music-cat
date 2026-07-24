# Sheets-Only Data Path + Local Fake Sheets + Layered Testing — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Google Sheets the only inbound data path (remove `catalog.json` and all file-import machinery), add a file-backed fake Sheets client for offline dev/testing seeded by a read-only prod snapshot, and add layered tests (fake-backed write path + `GoogleSheetsClient` via `MockHttpTransport`).

**Architecture:** A new `music-cat.sheets.mode` property swaps the `SheetsClient` implementation. `mode=google` keeps `GoogleSheetsClient`; `mode=fake` wires a `FakeSheetsClient` that reads/writes a local JSON file via `FakeSheetStore`. A read-only `SnapshotRunner` populates that file from the live sheet. The boot decision tree (`CatalogAutoImporter`) collapses to skip / restore-from-sheets / empty, with no `catalog.json` fallback.

**Tech Stack:** Java 25, Spring Boot 4.0.2, Spring Data JPA, H2, `google-api-client` 2.8.0 (transitively provides `com.google.api.client.testing.http.MockHttpTransport`), JUnit 5, Mockito, AssertJ.

## Global Constraints

- Gradle wrapper is at the **repo root** (multi-module). Run `./gradlew :backend:test`, `./gradlew :backend:build`, `./gradlew :backend:bootJar` **from the repo root** — never `cd backend`. Java 25 via sdkman `current`.
- Every state-changing HTTP request requires an `X-Requested-With` header (`RequireXhrHeaderFilter`). `@WebMvcTest`/MockMvc tests get it via `@io.github.alexshamrai.WithAuthenticatedUser`; `TestRestTemplate` tests add it with a request interceptor.
- Sheets beans are conditional. A missing/bad credentials file must fail at the **first real API call**, not at Spring context refresh — keep `GoogleSheetsClient`'s `ObjectProvider<Sheets>` indirection.
- The **`cloud` profile refuses to start if auth is still `admin`/`admin`**. Therefore local fake/snapshot runs use the **default** profile (with an in-memory H2 override), not `cloud`.
- `year` is an H2 reserved word — already quoted in entities/migrations; do not change.
- IDs are not stable across a pull; never cache an artist/album id — look up by name.
- New boolean DTO fields serialized to the frontend need `@JsonProperty("isFavorite")` (not applicable to any field in this plan, but hold the rule).
- SpEL note: in `@ConditionalOnExpression`, `${...}` placeholders resolve before SpEL runs, and SpEL `==` compares strings by value. Use exactly: `"${music-cat.sheets.enabled:false} and '${music-cat.sheets.mode:google}' == 'google'"` (or `== 'fake'`).

---

### Task 1: Extend `SheetsProperties` with `mode`, `fakeFile`, `snapshot`

**Files:**
- Modify: `backend/src/main/java/io/github/alexshamrai/config/SheetsProperties.java`

**Interfaces:**
- Produces: `SheetsProperties.mode()` (`String`, defaults `"google"`), `SheetsProperties.fakeFile()` (`String`, defaults `"./data/fake-sheets.json"`), `SheetsProperties.snapshot()` (`boolean`). A 3-arg convenience constructor `SheetsProperties(boolean, String, String)` is preserved so existing `new SheetsProperties(true, "creds.json", "sheet-123")` call sites still compile.

- [ ] **Step 1: Replace the record with the extended version**

```java
package io.github.alexshamrai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Google Sheets integration.
 * Bind with prefix "music-cat.sheets".
 *
 * <p>{@code mode} selects the SheetsClient implementation: "google" (default, real API) or
 * "fake" (local file-backed stand-in for offline dev/testing). {@code fakeFile} is where the
 * fake stores its data; {@code snapshot} activates the read-only prod → fake-file snapshot.
 */
@ConfigurationProperties(prefix = "music-cat.sheets")
public record SheetsProperties(
        boolean enabled,
        String credentialsPath,
        String spreadsheetId,
        String mode,
        String fakeFile,
        boolean snapshot
) {
    public SheetsProperties {
        if (mode == null || mode.isBlank()) {
            mode = "google";
        }
        if (fakeFile == null || fakeFile.isBlank()) {
            fakeFile = "./data/fake-sheets.json";
        }
    }

    /** Back-compat convenience for call sites (tests) that predate the mode/fake fields. */
    public SheetsProperties(boolean enabled, String credentialsPath, String spreadsheetId) {
        this(enabled, credentialsPath, spreadsheetId, "google", "./data/fake-sheets.json", false);
    }
}
```

- [ ] **Step 2: Verify existing Sheets tests still compile and pass**

Run: `./gradlew :backend:test --tests "io.github.alexshamrai.sheets.GoogleSheetsClientTest"`
Expected: PASS (the 3-arg `new SheetsProperties(...)` calls still resolve).

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/io/github/alexshamrai/config/SheetsProperties.java
git commit -m "feat(sheets): add mode/fakeFile/snapshot to SheetsProperties"
```

---

### Task 2: `FakeSheetStore` — file-backed tab storage

**Files:**
- Create: `backend/src/main/java/io/github/alexshamrai/sheets/FakeSheetStore.java`
- Test: `backend/src/test/java/io/github/alexshamrai/sheets/FakeSheetStoreTest.java`

**Interfaces:**
- Produces: `List<List<Object>> read(Path file, String tab)`, `void write(Path file, String tab, List<List<Object>> rows)`, `void writeAll(Path file, Map<String, List<List<Object>>> all)`, `Map<String, List<List<Object>>> readAll(Path file)`. Missing file → empty. Consumed by Tasks 3 and 5.

- [ ] **Step 1: Write the failing test**

```java
package io.github.alexshamrai.sheets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FakeSheetStoreTest {

    private final FakeSheetStore store = new FakeSheetStore();

    @Test
    void missingFile_readReturnsEmptyList(@TempDir Path dir) {
        Path file = dir.resolve("nope.json");
        assertThat(store.read(file, "Artists")).isEmpty();
    }

    @Test
    void write_thenRead_roundTripsRowsAndCreatesParentDir(@TempDir Path dir) {
        Path file = dir.resolve("nested/fake-sheets.json");
        List<List<Object>> rows = List.of(
                List.of("name", "genre", "subgenre", "favorite", "tags"),
                List.of("Pink Floyd", "Progressive Rock", "Psychedelic", "TRUE", "classic"));

        store.write(file, "Artists", rows);

        assertThat(store.read(file, "Artists"))
                .containsExactly(
                        List.of("name", "genre", "subgenre", "favorite", "tags"),
                        List.of("Pink Floyd", "Progressive Rock", "Psychedelic", "TRUE", "classic"));
    }

    @Test
    void write_secondTab_keepsFirstTab(@TempDir Path dir) {
        Path file = dir.resolve("fake-sheets.json");
        store.write(file, "Artists", List.of(List.of("name"), List.of("A")));
        store.write(file, "Albums", List.of(List.of("artist"), List.of("A")));

        assertThat(store.read(file, "Artists")).isNotEmpty();
        assertThat(store.read(file, "Albums")).isNotEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :backend:test --tests "io.github.alexshamrai.sheets.FakeSheetStoreTest"`
Expected: FAIL — `FakeSheetStore` does not exist (compilation error).

- [ ] **Step 3: Write the implementation**

```java
package io.github.alexshamrai.sheets;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * File-backed store for the fake Google Sheets. Persists a map of tab name → rows as JSON at
 * a caller-supplied path. Single source of the on-disk format, shared by {@link FakeSheetsClient}
 * (mode=fake) and {@link SnapshotRunner} (mode=google snapshot). Methods are synchronized; the
 * sync layer additionally serializes access via SheetsSyncLock.
 */
@Component
public class FakeSheetStore {

    private static final TypeReference<Map<String, List<List<Object>>>> TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper = new ObjectMapper();

    public synchronized Map<String, List<List<Object>>> readAll(Path file) {
        if (!Files.exists(file)) {
            return new LinkedHashMap<>();
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length == 0) {
                return new LinkedHashMap<>();
            }
            return objectMapper.readValue(bytes, TYPE);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read fake sheet file: " + file, e);
        }
    }

    public synchronized List<List<Object>> read(Path file, String tab) {
        List<List<Object>> rows = readAll(file).get(tab);
        return rows != null ? rows : List.of();
    }

    public synchronized void write(Path file, String tab, List<List<Object>> rows) {
        Map<String, List<List<Object>>> all = readAll(file);
        all.put(tab, rows);
        writeAll(file, all);
    }

    public synchronized void writeAll(Path file, Map<String, List<List<Object>>> all) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), all);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write fake sheet file: " + file, e);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :backend:test --tests "io.github.alexshamrai.sheets.FakeSheetStoreTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/io/github/alexshamrai/sheets/FakeSheetStore.java backend/src/test/java/io/github/alexshamrai/sheets/FakeSheetStoreTest.java
git commit -m "feat(sheets): add file-backed FakeSheetStore"
```

---

### Task 3: `FakeSheetsClient` (mode=fake)

**Files:**
- Create: `backend/src/main/java/io/github/alexshamrai/sheets/FakeSheetsClient.java`
- Test: `backend/src/test/java/io/github/alexshamrai/sheets/FakeSheetsClientTest.java`

**Interfaces:**
- Consumes: `FakeSheetStore` (Task 2), `SheetsProperties.fakeFile()` (Task 1).
- Produces: a `SheetsClient` bean active when `enabled=true AND mode=fake`, delegating `read`/`overwrite` to the file.

- [ ] **Step 1: Write the failing test**

```java
package io.github.alexshamrai.sheets;

import io.github.alexshamrai.config.SheetsProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FakeSheetsClientTest {

    @Test
    void overwrite_thenRead_roundTripsThroughFile(@TempDir Path dir) {
        Path file = dir.resolve("fake-sheets.json");
        SheetsProperties props = new SheetsProperties(true, null, null, "fake", file.toString(), false);
        FakeSheetsClient client = new FakeSheetsClient(new FakeSheetStore(), props);

        client.overwrite("Albums", List.of(
                List.of("artist", "title", "year", "grade", "favorite", "tags"),
                List.of("Pink Floyd", "The Dark Side of the Moon", "1973", "5", "TRUE", "classic")));

        assertThat(client.read("Albums")).hasSize(2);
        assertThat(client.read("Albums").get(1))
                .containsExactly("Pink Floyd", "The Dark Side of the Moon", "1973", "5", "TRUE", "classic");
    }

    @Test
    void read_unknownTab_returnsEmpty(@TempDir Path dir) {
        SheetsProperties props = new SheetsProperties(
                true, null, null, "fake", dir.resolve("fake.json").toString(), false);
        FakeSheetsClient client = new FakeSheetsClient(new FakeSheetStore(), props);

        assertThat(client.read("Songs")).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :backend:test --tests "io.github.alexshamrai.sheets.FakeSheetsClientTest"`
Expected: FAIL — `FakeSheetsClient` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package io.github.alexshamrai.sheets;

import io.github.alexshamrai.config.SheetsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

/**
 * Local, file-backed stand-in for Google Sheets (mode=fake). Reads/writes the three tabs to a
 * JSON file via {@link FakeSheetStore} — no network, no credentials, no connection to prod.
 */
@Service
@ConditionalOnExpression(
        "${music-cat.sheets.enabled:false} and '${music-cat.sheets.mode:google}' == 'fake'")
public class FakeSheetsClient implements SheetsClient {

    private static final Logger log = LoggerFactory.getLogger(FakeSheetsClient.class);

    private final FakeSheetStore store;
    private final Path file;

    public FakeSheetsClient(FakeSheetStore store, SheetsProperties props) {
        this.store = store;
        this.file = Path.of(props.fakeFile());
        log.warn("Sheets client: FAKE (file {}) — NOT connected to Google Sheets", file.toAbsolutePath());
    }

    @Override
    public List<List<Object>> read(String sheetName) {
        return store.read(file, sheetName);
    }

    @Override
    public void overwrite(String sheetName, List<List<Object>> rows) {
        store.write(file, sheetName, rows);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :backend:test --tests "io.github.alexshamrai.sheets.FakeSheetsClientTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/io/github/alexshamrai/sheets/FakeSheetsClient.java backend/src/test/java/io/github/alexshamrai/sheets/FakeSheetsClientTest.java
git commit -m "feat(sheets): add file-backed FakeSheetsClient (mode=fake)"
```

---

### Task 4: Gate `GoogleSheetsClient`/`GoogleSheetsConfig` on `mode=google` + wiring test

**Files:**
- Modify: `backend/src/main/java/io/github/alexshamrai/sheets/GoogleSheetsClient.java:18-19` (condition) and constructor (banner log)
- Modify: `backend/src/main/java/io/github/alexshamrai/config/GoogleSheetsConfig.java:18-19` (condition)
- Test: `backend/src/test/java/io/github/alexshamrai/sheets/FakeModeWiringTest.java`

**Interfaces:**
- Produces: exactly one `SheetsClient` bean per config — `GoogleSheetsClient` when `enabled=true AND mode=google` (default), `FakeSheetsClient` when `enabled=true AND mode=fake`, none when `enabled=false`.

- [ ] **Step 1: Write the failing wiring test**

```java
package io.github.alexshamrai.sheets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With sheets enabled + mode=fake, the SheetsClient bean must be the FakeSheetsClient and the
 * GoogleSheetsClient must be absent (so no credentials are ever required).
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "music-cat.sheets.enabled=true",
        "music-cat.sheets.mode=fake",
        "music-cat.sheets.fake-file=build/tmp/fake-mode-wiring.json"
})
class FakeModeWiringTest {

    @Autowired
    private ObjectProvider<SheetsClient> sheetsClientProvider;

    @Autowired
    private ObjectProvider<GoogleSheetsClient> googleClientProvider;

    @Test
    void fakeMode_wiresFakeClient_notGoogleClient() {
        assertThat(sheetsClientProvider.getIfAvailable()).isInstanceOf(FakeSheetsClient.class);
        assertThat(googleClientProvider.getIfAvailable()).isNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :backend:test --tests "io.github.alexshamrai.sheets.FakeModeWiringTest"`
Expected: FAIL — with the current `@ConditionalOnProperty(enabled=true)`, `GoogleSheetsClient` also wires and the context has two `SheetsClient` beans (or the wrong one), so the assertions fail (or startup fails on duplicate/absent bean).

- [ ] **Step 3: Change `GoogleSheetsClient`'s condition and add the banner**

In `backend/src/main/java/io/github/alexshamrai/sheets/GoogleSheetsClient.java`, replace the import and class annotation:

Replace:
```java
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
```
with:
```java
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
```

Replace:
```java
@Service
@ConditionalOnProperty(name = "music-cat.sheets.enabled", havingValue = "true")
public class GoogleSheetsClient implements SheetsClient {
```
with:
```java
@Service
@ConditionalOnExpression(
        "${music-cat.sheets.enabled:false} and '${music-cat.sheets.mode:google}' == 'google'")
public class GoogleSheetsClient implements SheetsClient {
```

And update the constructor to log which sheet it is bound to (replace the existing constructor body):
```java
    public GoogleSheetsClient(ObjectProvider<Sheets> sheetsProvider, SheetsProperties props) {
        this.sheetsProvider = sheetsProvider;
        this.spreadsheetId = props.spreadsheetId();
        String tail = (spreadsheetId == null || spreadsheetId.length() < 6)
                ? String.valueOf(spreadsheetId)
                : "…" + spreadsheetId.substring(spreadsheetId.length() - 6);
        log.info("Sheets client: GOOGLE (spreadsheet {})", tail);
    }
```

- [ ] **Step 4: Change `GoogleSheetsConfig`'s condition**

In `backend/src/main/java/io/github/alexshamrai/config/GoogleSheetsConfig.java`:

Replace:
```java
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
```
with:
```java
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
```

Replace:
```java
@Configuration
@ConditionalOnProperty(name = "music-cat.sheets.enabled", havingValue = "true")
public class GoogleSheetsConfig {
```
with:
```java
@Configuration
@ConditionalOnExpression(
        "${music-cat.sheets.enabled:false} and '${music-cat.sheets.mode:google}' == 'google'")
public class GoogleSheetsConfig {
```

- [ ] **Step 5: Run the wiring test AND the disabled-context test**

Run: `./gradlew :backend:test --tests "io.github.alexshamrai.sheets.FakeModeWiringTest" --tests "io.github.alexshamrai.sheets.SheetsDisabledTest"`
Expected: PASS — fake mode wires `FakeSheetsClient` only; disabled still wires no `SheetsClient`.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/io/github/alexshamrai/sheets/GoogleSheetsClient.java backend/src/main/java/io/github/alexshamrai/config/GoogleSheetsConfig.java backend/src/test/java/io/github/alexshamrai/sheets/FakeModeWiringTest.java
git commit -m "feat(sheets): select client impl by mode (google|fake)"
```

---

### Task 5: `SnapshotRunner` — read-only prod → fake-file

**Files:**
- Create: `backend/src/main/java/io/github/alexshamrai/sheets/SnapshotRunner.java`
- Test: `backend/src/test/java/io/github/alexshamrai/sheets/SnapshotRunnerTest.java`

**Interfaces:**
- Consumes: `SheetsClient` (Task 4 — resolves to `GoogleSheetsClient` under mode=google), `FakeSheetStore` (Task 2), `SheetsProperties.fakeFile()` (Task 1).
- Produces: `void doSnapshot()` (reads the three tabs and writes them to the fake file; unit-testable, no process exit). `run(...)` calls `doSnapshot()` then shuts the app down. Active only when `music-cat.sheets.snapshot=true`.

- [ ] **Step 1: Write the failing test**

```java
package io.github.alexshamrai.sheets;

import io.github.alexshamrai.config.SheetsProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SnapshotRunnerTest {

    @Test
    void doSnapshot_readsThreeTabs_writesThemToFakeFile_neverWritesToSheet(@TempDir Path dir) {
        Path file = dir.resolve("fake-sheets.json");
        SheetsClient sheetsClient = mock(SheetsClient.class);
        when(sheetsClient.read("Artists")).thenReturn(List.of(List.of("name"), List.of("Pink Floyd")));
        when(sheetsClient.read("Albums")).thenReturn(List.of(List.of("artist"), List.of("Pink Floyd")));
        when(sheetsClient.read("Songs")).thenReturn(List.of(List.of("artist"), List.of("Pink Floyd")));

        FakeSheetStore store = new FakeSheetStore();
        SheetsProperties props = new SheetsProperties(true, null, null, "google", file.toString(), true);

        SnapshotRunner runner = new SnapshotRunner(sheetsClient, store, props, null);
        runner.doSnapshot();

        assertThat(store.read(file, "Artists")).hasSize(2);
        assertThat(store.read(file, "Albums")).hasSize(2);
        assertThat(store.read(file, "Songs")).hasSize(2);
        // Read-only guarantee: the snapshot never writes back to the live sheet.
        verify(sheetsClient, never()).overwrite(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :backend:test --tests "io.github.alexshamrai.sheets.SnapshotRunnerTest"`
Expected: FAIL — `SnapshotRunner` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package io.github.alexshamrai.sheets;

import io.github.alexshamrai.config.SheetsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only snapshot: reads the three tabs from the LIVE sheet (via the real SheetsClient under
 * mode=google) and writes them verbatim into the fake-sheet file, then shuts the app down.
 * Activated only by {@code music-cat.sheets.snapshot=true}. It calls only {@code read()} — never
 * {@code overwrite()} — so it can never modify the live spreadsheet.
 */
@Component
@org.springframework.context.annotation.Lazy(false)
@ConditionalOnProperty(name = "music-cat.sheets.snapshot", havingValue = "true")
public class SnapshotRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SnapshotRunner.class);
    private static final List<String> TABS = List.of("Artists", "Albums", "Songs");

    private final SheetsClient sheetsClient;
    private final FakeSheetStore store;
    private final Path file;
    private final ConfigurableApplicationContext context;

    public SnapshotRunner(SheetsClient sheetsClient, FakeSheetStore store,
                          SheetsProperties props, ConfigurableApplicationContext context) {
        this.sheetsClient = sheetsClient;
        this.store = store;
        this.file = Path.of(props.fakeFile());
        this.context = context;
    }

    /** Reads the three tabs and writes them to the fake file. No process exit — unit-testable. */
    void doSnapshot() {
        log.info("Snapshotting live Google Sheets tabs {} into {}", TABS, file.toAbsolutePath());
        Map<String, List<List<Object>>> snapshot = new LinkedHashMap<>();
        for (String tab : TABS) {
            List<List<Object>> rows = sheetsClient.read(tab);
            snapshot.put(tab, rows);
            log.info("  {} — {} row(s)", tab, rows.size());
        }
        store.writeAll(file, snapshot);
        log.info("Snapshot complete → {}", file.toAbsolutePath());
    }

    @Override
    public void run(ApplicationArguments args) {
        doSnapshot();
        int code = SpringApplication.exit(context, () -> 0);
        System.exit(code);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :backend:test --tests "io.github.alexshamrai.sheets.SnapshotRunnerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/io/github/alexshamrai/sheets/SnapshotRunner.java backend/src/test/java/io/github/alexshamrai/sheets/SnapshotRunnerTest.java
git commit -m "feat(sheets): add read-only SnapshotRunner (prod -> fake file)"
```

---

### Task 6: Layer 1 — fake-mode write-path + restore integration test

**Files:**
- Test: `backend/src/test/java/io/github/alexshamrai/sheets/FakeSheetsIntegrationTest.java`

**Interfaces:**
- Consumes: the full app under `enabled=true, mode=fake`, `FakeSheetStore` (Task 2), the sync stack, `SheetsCatalogReader`, and the REST API (`POST /api/artists`, `POST /api/albums`, `PUT /api/albums/{id}/edit`).

This is a test-only task (no production code changes). It proves that a real HTTP mutation pushes to the fake file, and that the real file-backed client restores the graph.

- [ ] **Step 1: Write the test**

```java
package io.github.alexshamrai.sheets;

import io.github.alexshamrai.domain.Genre;
import io.github.alexshamrai.dto.AlbumCreateDto;
import io.github.alexshamrai.dto.AlbumDto;
import io.github.alexshamrai.dto.AlbumEditDto;
import io.github.alexshamrai.dto.AlbumSummaryDto;
import io.github.alexshamrai.dto.ArtistCreateDto;
import io.github.alexshamrai.dto.ArtistDto;
import io.github.alexshamrai.dto.SongEditInput;
import io.github.alexshamrai.repository.AlbumRepository;
import io.github.alexshamrai.repository.ArtistRepository;
import io.github.alexshamrai.repository.SongRepository;
import io.github.alexshamrai.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Layer 1: drives real HTTP mutations under mode=fake and asserts they are pushed to the fake
 * file (the write path through SheetSyncListener → SheetSyncService → SheetMapper →
 * FakeSheetsClient), then wipes the DB and restores from the fake file (the read path through
 * SheetsCatalogReader → FakeSheetsClient).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
class FakeSheetsIntegrationTest {

    static Path fakeFile;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        try {
            fakeFile = Files.createTempFile("fake-sheets-it", ".json");
            Files.deleteIfExists(fakeFile); // absent at boot → blank sheet → pushes resume
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        registry.add("music-cat.sheets.enabled", () -> "true");
        registry.add("music-cat.sheets.mode", () -> "fake");
        registry.add("music-cat.sheets.fake-file", () -> fakeFile.toString());
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:testdb-fake-it;DB_CLOSE_DELAY=-1");
    }

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private FakeSheetStore store;
    @Autowired
    private SheetsCatalogReader reader;
    @Autowired
    private ArtistRepository artistRepository;
    @Autowired
    private AlbumRepository albumRepository;
    @Autowired
    private SongRepository songRepository;
    @Autowired
    private TagRepository tagRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void authenticate() {
        restTemplate = restTemplate.withBasicAuth("admin", "admin");
        restTemplate.getRestTemplate().getInterceptors().add((request, body, execution) -> {
            request.getHeaders().add("X-Requested-With", "XMLHttpRequest");
            return execution.execute(request, body);
        });
    }

    @Test
    void mutationPushesToFakeFile_andRestoreRebuildsGraph() {
        // --- WRITE PATH: create artist + album, then add a song via /edit (structural push) ---
        ArtistCreateDto artistDto = new ArtistCreateDto("Fake Test Artist", Genre.JAZZ_AND_FUNK, null);
        var artistResp = restTemplate.postForEntity("/api/artists", artistDto, ArtistDto.class);
        assertThat(artistResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long artistId = artistResp.getBody().getId();

        var albumResp = restTemplate.postForEntity("/api/albums",
                new AlbumCreateDto("Fake Test Album", 1959, artistId), AlbumSummaryDto.class);
        assertThat(albumResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long albumId = albumResp.getBody().getId();

        var editResp = restTemplate.exchange("/api/albums/" + albumId + "/edit", HttpMethod.PUT,
                new HttpEntity<>(new AlbumEditDto("Fake Test Album", 1959,
                        List.of(new SongEditInput(null, "So What")))),
                AlbumDto.class);
        assertThat(editResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // The fake file now reflects the mutations (row 0 is the header).
        List<List<Object>> artistRows = store.read(fakeFile, "Artists");
        assertThat(artistRows).anyMatch(r -> "Fake Test Artist".equals(String.valueOf(r.get(0))));
        List<List<Object>> albumRows = store.read(fakeFile, "Albums");
        assertThat(albumRows).anyMatch(r -> "Fake Test Album".equals(String.valueOf(r.get(1))));
        List<List<Object>> songRows = store.read(fakeFile, "Songs");
        assertThat(songRows).anyMatch(r -> "So What".equals(String.valueOf(r.get(4))));

        // --- READ PATH: wipe the DB, restore from the fake file, assert the graph is rebuilt ---
        transactionTemplate.execute(status -> {
            songRepository.deleteAll();
            albumRepository.deleteAll();
            artistRepository.deleteAll();
            tagRepository.deleteAll();
            return null;
        });
        assertThat(artistRepository.count()).isZero();

        SheetsLoadResult result = reader.loadFromSheets();

        assertThat(result.artistCount()).isEqualTo(1);
        assertThat(result.albumCount()).isEqualTo(1);
        assertThat(result.songCount()).isEqualTo(1);
        assertThat(artistRepository.findAllForSync())
                .extracting(a -> a.getName()).containsExactly("Fake Test Artist");
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./gradlew :backend:test --tests "io.github.alexshamrai.sheets.FakeSheetsIntegrationTest"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/io/github/alexshamrai/sheets/FakeSheetsIntegrationTest.java
git commit -m "test(sheets): fake-mode write-path + restore integration test (Layer 1)"
```

---

### Task 7: Layer 2 — `GoogleSheetsClient` 429-retry + error handling via `MockHttpTransport`

**Files:**
- Test: `backend/src/test/java/io/github/alexshamrai/sheets/GoogleSheetsClientRetryTest.java`

**Interfaces:**
- Consumes: `GoogleSheetsClient` (Task 4) built on a `Sheets` client backed by `MockHttpTransport`.

The existing `GoogleSheetsClientTest` already covers chunking (`update` at A1 + `append` for later chunks). This task adds the retry/error path the deep-stub tests bypass. Note: the 429 test incurs ~1s from the real exponential backoff (`2^0 * 1000ms`).

- [ ] **Step 1: Write the test**

```java
package io.github.alexshamrai.sheets;

import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.sheets.v4.Sheets;
import io.github.alexshamrai.config.SheetsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Layer 2: exercises GoogleSheetsClient's real request/response handling and 429 retry via
 * Google's MockHttpTransport — the executeWithRetry path the deep-stub tests bypass.
 */
class GoogleSheetsClientRetryTest {

    private GoogleSheetsClient clientBackedBy(MockHttpTransport transport) {
        Sheets sheets = new Sheets.Builder(transport, GsonFactory.getDefaultInstance(), request -> {})
                .setApplicationName("music-cat-test")
                .build();
        @SuppressWarnings("unchecked")
        ObjectProvider<Sheets> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(sheets);
        return new GoogleSheetsClient(provider, new SheetsProperties(true, "creds.json", "sheet-123"));
    }

    @Test
    void read_retriesAfter429_thenSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        MockHttpTransport transport = new MockHttpTransport() {
            @Override
            public LowLevelHttpRequest buildRequest(String method, String url) {
                return new MockLowLevelHttpRequest() {
                    @Override
                    public LowLevelHttpResponse execute() {
                        if (calls.getAndIncrement() == 0) {
                            return new MockLowLevelHttpResponse()
                                    .setStatusCode(429)
                                    .setContentType("application/json")
                                    .setContent("{\"error\":{\"code\":429,\"message\":\"Rate Limit Exceeded\"}}");
                        }
                        return new MockLowLevelHttpResponse()
                                .setStatusCode(200)
                                .setContentType("application/json")
                                .setContent("{\"values\":[[\"name\"],[\"Pink Floyd\"]]}");
                    }
                };
            }
        };

        List<List<Object>> rows = clientBackedBy(transport).read("Artists");

        assertThat(calls.get()).isEqualTo(2); // one 429 + one success
        assertThat(rows).containsExactly(List.of("name"), List.of("Pink Floyd"));
    }

    @Test
    void read_non429Error_failsImmediatelyWithoutRetry() {
        AtomicInteger calls = new AtomicInteger();
        MockHttpTransport transport = new MockHttpTransport() {
            @Override
            public LowLevelHttpRequest buildRequest(String method, String url) {
                return new MockLowLevelHttpRequest() {
                    @Override
                    public LowLevelHttpResponse execute() {
                        calls.incrementAndGet();
                        return new MockLowLevelHttpResponse()
                                .setStatusCode(500)
                                .setContentType("application/json")
                                .setContent("{\"error\":{\"code\":500,\"message\":\"Backend Error\"}}");
                    }
                };
            }
        };

        assertThatThrownBy(() -> clientBackedBy(transport).read("Artists"))
                .isInstanceOf(RuntimeException.class);
        assertThat(calls.get()).isEqualTo(1); // no retry on a non-429 error
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./gradlew :backend:test --tests "io.github.alexshamrai.sheets.GoogleSheetsClientRetryTest"`
Expected: PASS (2 tests; ~1s for the retry test).

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/io/github/alexshamrai/sheets/GoogleSheetsClientRetryTest.java
git commit -m "test(sheets): GoogleSheetsClient 429-retry + error handling via MockHttpTransport (Layer 2)"
```

---

### Task 8: Simplify `CatalogAutoImporter` (drop `catalog.json` seed/fallback)

**Files:**
- Modify (full rewrite): `backend/src/main/java/io/github/alexshamrai/startup/CatalogAutoImporter.java`
- Modify (full rewrite): `backend/src/test/java/io/github/alexshamrai/startup/CatalogAutoImporterTest.java`

**Interfaces:**
- Produces: `CatalogAutoImporter(ArtistRepository, ObjectProvider<SheetsCatalogReader>, ObjectProvider<SheetSyncService>, ReadinessState)` — no `CatalogImportService`, no `catalogPath`.

- [ ] **Step 1: Rewrite the unit test (new boot tree, no catalog.json)**

```java
package io.github.alexshamrai.startup;

import io.github.alexshamrai.repository.ArtistRepository;
import io.github.alexshamrai.service.SheetSyncService;
import io.github.alexshamrai.service.SheetsCatalogReader;
import io.github.alexshamrai.service.SheetsLoadResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Boot decision tree (Sheets-only, no catalog.json):
 * <ol>
 *   <li>DB not empty → skip, resume event pushes</li>
 *   <li>DB empty + sheets disabled → empty DB, no sync interaction</li>
 *   <li>DB empty + sheets have data (clean) → restore, resume</li>
 *   <li>DB empty + restore with warnings / zero artists → empty/partial, suspend</li>
 *   <li>DB empty + sheets blank → empty DB, resume (trivially consistent)</li>
 *   <li>DB empty + restore throws → empty DB, suspend, still ready</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class CatalogAutoImporterTest {

    @Mock private ArtistRepository artistRepository;
    @Mock private ObjectProvider<SheetsCatalogReader> readerProvider;
    @Mock private ObjectProvider<SheetSyncService> syncProvider;
    @Mock private SheetsCatalogReader reader;
    @Mock private SheetSyncService syncService;

    private ReadinessState readinessState;

    private CatalogAutoImporter importer() {
        readinessState = new ReadinessState();
        return new CatalogAutoImporter(artistRepository, readerProvider, syncProvider, readinessState);
    }

    private static SheetsLoadResult cleanLoad() {
        return new SheetsLoadResult(176, 2830, 30876, List.of());
    }

    @Test
    void dbNotEmpty_skips_andResumesEventPushes() {
        when(artistRepository.count()).thenReturn(42L);
        when(syncProvider.getIfAvailable()).thenReturn(syncService);

        importer().onApplicationReady();

        verify(syncService).resumeEventPushes();
        verify(readerProvider, never()).getIfAvailable();
        assertThat(readinessState.isReady()).isTrue();
    }

    @Test
    void dbEmpty_sheetsDisabled_startsEmpty() {
        when(artistRepository.count()).thenReturn(0L);
        when(readerProvider.getIfAvailable()).thenReturn(null);
        when(syncProvider.getIfAvailable()).thenReturn(null);

        importer().onApplicationReady();

        assertThat(readinessState.isReady()).isTrue();
    }

    @Test
    void dbEmpty_sheetsHaveData_clean_restoresAndResumes() {
        when(artistRepository.count()).thenReturn(0L);
        when(readerProvider.getIfAvailable()).thenReturn(reader);
        when(syncProvider.getIfAvailable()).thenReturn(syncService);
        when(reader.sheetsHaveData()).thenReturn(true);
        when(reader.loadFromSheets()).thenReturn(cleanLoad());

        importer().onApplicationReady();

        verify(reader).loadFromSheets();
        verify(syncService).resumeEventPushes();
        verify(syncService, never()).suspendEventPushes(anyString());
    }

    @Test
    void dbEmpty_restoreWithWarnings_suspends() {
        when(artistRepository.count()).thenReturn(0L);
        when(readerProvider.getIfAvailable()).thenReturn(reader);
        when(syncProvider.getIfAvailable()).thenReturn(syncService);
        when(reader.sheetsHaveData()).thenReturn(true);
        when(reader.loadFromSheets()).thenReturn(
                new SheetsLoadResult(175, 2830, 30876, List.of("Artists row skipped — bad genre")));

        importer().onApplicationReady();

        verify(syncService).suspendEventPushes(anyString());
        verify(syncService, never()).resumeEventPushes();
    }

    @Test
    void dbEmpty_restoreProducesZeroArtists_startsEmptyAndSuspends() {
        when(artistRepository.count()).thenReturn(0L);
        when(readerProvider.getIfAvailable()).thenReturn(reader);
        when(syncProvider.getIfAvailable()).thenReturn(syncService);
        when(reader.sheetsHaveData()).thenReturn(true);
        when(reader.loadFromSheets()).thenReturn(new SheetsLoadResult(0, 0, 0, List.of()));

        importer().onApplicationReady();

        verify(syncService).suspendEventPushes(anyString());
        verify(syncService, never()).resumeEventPushes();
    }

    @Test
    void dbEmpty_sheetsBlank_resumesEventPushes() {
        when(artistRepository.count()).thenReturn(0L);
        when(readerProvider.getIfAvailable()).thenReturn(reader);
        when(syncProvider.getIfAvailable()).thenReturn(syncService);
        when(reader.sheetsHaveData()).thenReturn(false);

        importer().onApplicationReady();

        verify(syncService).resumeEventPushes();
    }

    @Test
    void dbEmpty_sheetsHaveDataThrows_startsEmptySuspendedAndReady() {
        when(artistRepository.count()).thenReturn(0L);
        when(readerProvider.getIfAvailable()).thenReturn(reader);
        when(syncProvider.getIfAvailable()).thenReturn(syncService);
        when(reader.sheetsHaveData()).thenThrow(new RuntimeException("Sheets API down"));

        importer().onApplicationReady();

        verify(syncService).suspendEventPushes(anyString());
        verify(syncService, never()).resumeEventPushes();
        verify(syncService, never()).pushCatalog(anyBoolean());
        assertThat(readinessState.isReady()).isTrue();
    }

    @Test
    void dbEmpty_loadFromSheetsThrows_startsEmptySuspended() {
        when(artistRepository.count()).thenReturn(0L);
        when(readerProvider.getIfAvailable()).thenReturn(reader);
        when(syncProvider.getIfAvailable()).thenReturn(syncService);
        when(reader.sheetsHaveData()).thenReturn(true);
        when(reader.loadFromSheets()).thenThrow(new RuntimeException("Malformed rows"));

        importer().onApplicationReady();

        verify(syncService).suspendEventPushes(anyString());
        verify(syncService, never()).resumeEventPushes();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :backend:test --tests "io.github.alexshamrai.startup.CatalogAutoImporterTest"`
Expected: FAIL — the old 6-arg constructor and `catalog.json` branches no longer match.

- [ ] **Step 3: Rewrite `CatalogAutoImporter`**

```java
package io.github.alexshamrai.startup;

import io.github.alexshamrai.repository.ArtistRepository;
import io.github.alexshamrai.service.SheetSyncService;
import io.github.alexshamrai.service.SheetsCatalogReader;
import io.github.alexshamrai.service.SheetsLoadResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Populates an empty database on boot from Google Sheets. Decision tree (Sheets-only):
 * <ol>
 *   <li>DB not empty → skip (resume event pushes)</li>
 *   <li>DB empty + sheets disabled → empty catalog (automated-test context only)</li>
 *   <li>DB empty + sheets have data → restore from Google Sheets</li>
 *   <li>DB empty + sheets blank → empty catalog (trivially consistent → resume pushes)</li>
 *   <li>DB empty + restore throws / produces 0 artists → empty catalog, pushes SUSPENDED</li>
 * </ol>
 *
 * <p>There is no local fallback: a Sheets outage on a cold start leaves the catalog empty until
 * Sheets recovers (repair connectivity, then POST /api/catalog/sync/pull). Event-driven pushes
 * start SUSPENDED (see {@link SheetSyncService}) and resume only when the DB provably mirrors the
 * sheet — a diverged/empty DB must never overwrite the spreadsheet.
 */
@Component
@org.springframework.context.annotation.Lazy(false)
@Slf4j
public class CatalogAutoImporter {

    private final ArtistRepository artistRepository;
    private final ObjectProvider<SheetsCatalogReader> sheetsCatalogReader;
    private final ObjectProvider<SheetSyncService> sheetSyncService;
    private final ReadinessState readinessState;

    public CatalogAutoImporter(ArtistRepository artistRepository,
                               ObjectProvider<SheetsCatalogReader> sheetsCatalogReader,
                               ObjectProvider<SheetSyncService> sheetSyncService,
                               ReadinessState readinessState) {
        this.artistRepository = artistRepository;
        this.sheetsCatalogReader = sheetsCatalogReader;
        this.sheetSyncService = sheetSyncService;
        this.readinessState = readinessState;
    }

    /**
     * Wraps the decision in try/finally so {@link ReadinessState#markReady()} always runs on exit
     * (including every early return), closing the {@code ReadinessGateFilter} window as soon as
     * the decision is made.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            doOnApplicationReady();
        } finally {
            readinessState.markReady();
        }
    }

    private void doOnApplicationReady() {
        SheetSyncService sync = sheetSyncService.getIfAvailable();

        if (artistRepository.count() > 0) {
            log.info("Database already contains data, skipping restore");
            if (sync != null) {
                sync.resumeEventPushes();
            }
            return;
        }

        SheetsCatalogReader reader = sheetsCatalogReader.getIfAvailable();
        if (reader == null) {
            log.info("Google Sheets disabled and DB is empty — starting with an empty catalog");
            return;
        }

        try {
            if (reader.sheetsHaveData()) {
                restoreFromSheets(reader, sync);
                return;
            }
        } catch (Exception e) {
            log.error("Sheets restore failed — starting with an empty catalog; pushes suspended so the "
                    + "spreadsheet stays untouched. Repair connectivity/sheet, then POST /api/catalog/sync/pull", e);
            if (sync != null) {
                sync.suspendEventPushes("Boot restore from Google Sheets failed: " + e.getMessage()
                        + " — running on an empty catalog; repair connectivity/sheet, then POST /api/catalog/sync/pull");
            }
            return;
        }

        // Sheets enabled but blank → empty DB and blank sheet are trivially consistent
        log.info("Google Sheets is blank and DB is empty — nothing to restore");
        if (sync != null) {
            sync.resumeEventPushes();
        }
    }

    private void restoreFromSheets(SheetsCatalogReader reader, SheetSyncService sync) {
        SheetsLoadResult result = reader.loadFromSheets();

        if (result.artistCount() == 0) {
            log.error("Sheets restore produced 0 artists although the spreadsheet has data — starting "
                    + "with an empty catalog; pushes suspended. Repair the sheet, then POST /api/catalog/sync/pull");
            if (sync != null) {
                sync.suspendEventPushes("Restore found data in the spreadsheet but produced 0 artists — "
                        + "running on an empty catalog; repair the sheet, then POST /api/catalog/sync/pull");
            }
            return;
        }

        log.info("Restored {} artists / {} albums / {} songs from Google Sheets",
                result.artistCount(), result.albumCount(), result.songCount());
        if (sync == null) {
            return;
        }
        if (result.clean()) {
            sync.resumeEventPushes();
        } else {
            sync.suspendEventPushes("Restore skipped " + result.warnings().size()
                    + " sheet row(s) — a push would erase them from the sheet. Repair the sheet, then "
                    + "POST /api/catalog/sync/pull. First warning: " + result.warnings().get(0));
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :backend:test --tests "io.github.alexshamrai.startup.CatalogAutoImporterTest"`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/io/github/alexshamrai/startup/CatalogAutoImporter.java backend/src/test/java/io/github/alexshamrai/startup/CatalogAutoImporterTest.java
git commit -m "refactor(startup): Sheets-only boot tree (drop catalog.json seed/fallback)"
```

---

### Task 9: Remove `POST /api/catalog/import` from `CatalogController`

**Files:**
- Modify: `backend/src/main/java/io/github/alexshamrai/controller/CatalogController.java`
- Modify: `backend/src/test/java/io/github/alexshamrai/controller/CatalogControllerTest.java`

**Interfaces:**
- Produces: `CatalogController` without the `/import` endpoint or `CatalogImportService` dependency; export + sync endpoints unchanged.

- [ ] **Step 1: Update the controller test — remove the three import tests + the mock**

In `CatalogControllerTest.java`:

Remove these imports:
```java
import io.github.alexshamrai.dto.ImportResult;
import io.github.alexshamrai.service.CatalogImportService;
import org.springframework.mock.web.MockMultipartFile;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
```

Remove the mocked bean:
```java
    @MockitoBean
    private CatalogImportService catalogImportService;
```

Remove the three tests `importCatalog_validFile_returns200WithResult`, `importCatalog_serviceThrowsIOException_returns500`, and `importCatalog_missingFilePart_returns500` (lines 48-82 in the current file), plus the now-unused `import java.io.IOException;`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :backend:test --tests "io.github.alexshamrai.controller.CatalogControllerTest"`
Expected: FAIL — `CatalogController` still declares `CatalogImportService`, so `@WebMvcTest` fails to satisfy the dependency now that its mock is gone (`UnsatisfiedDependencyException`).

- [ ] **Step 3: Remove the endpoint from the controller**

In `CatalogController.java`, remove these imports:
```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import io.github.alexshamrai.dto.ImportResult;
import io.github.alexshamrai.service.CatalogImportService;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
```

Remove the field:
```java
    private final CatalogImportService catalogImportService;
```

Remove the entire method (current lines 39-50):
```java
    @PostMapping("/import")
    public ResponseEntity<ImportResult> importCatalog(@RequestParam("file") MultipartFile file)
        throws IOException {
        Path tempFile = Files.createTempFile("catalog-import-", ".json");
        try {
            file.transferTo(tempFile);
            ImportResult result = catalogImportService.importFromJson(tempFile);
            return ResponseEntity.ok(result);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :backend:test --tests "io.github.alexshamrai.controller.CatalogControllerTest"`
Expected: PASS (export + sync tests only).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/io/github/alexshamrai/controller/CatalogController.java backend/src/test/java/io/github/alexshamrai/controller/CatalogControllerTest.java
git commit -m "refactor(catalog): remove POST /api/catalog/import (Sheets-only)"
```

---

### Task 10: Delete import machinery, `catalog.json`, and `catalog-path` config; move `Stats`

**Files:**
- Create: `backend/src/main/java/io/github/alexshamrai/dto/export/Stats.java`
- Delete: `backend/src/main/java/io/github/alexshamrai/dto/catalog/Stats.java`, `Catalog.java`, `GenreGroup.java`, `Artist.java`, `Album.java`
- Delete: `backend/src/main/java/io/github/alexshamrai/dto/ImportResult.java`
- Delete: `backend/src/main/java/io/github/alexshamrai/service/CatalogImportService.java`
- Delete: `backend/src/test/java/io/github/alexshamrai/service/CatalogImportServiceTest.java`, `backend/src/test/java/io/github/alexshamrai/service/CatalogImportIntegrationTest.java`
- Delete: `backend/src/test/resources/test-catalog.json`
- Delete: `catalog.json` (repo root)
- Modify: `backend/src/main/java/io/github/alexshamrai/dto/export/ExportCatalog.java` (import), `backend/src/main/java/io/github/alexshamrai/service/CatalogExportService.java` (import), `backend/src/test/java/io/github/alexshamrai/controller/CatalogControllerTest.java` (import)
- Modify: `Dockerfile` (remove COPY), `backend/src/main/resources/application.yml`, `application-cloud.yml`, `application-test.yml` (remove `catalog-path`)
- Modify: `backend/src/test/java/io/github/alexshamrai/startup/CatalogAutoImporterIntegrationTest.java` (drop the catalog-path comment)

- [ ] **Step 1: Create `Stats` in `dto/export`**

```java
package io.github.alexshamrai.dto.export;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Stats(int totalGenres, int totalArtists, int totalAlbums, int totalTracks) {}
```

- [ ] **Step 2: Repoint the three `Stats` importers**

In `ExportCatalog.java`, `CatalogExportService.java`, and `CatalogControllerTest.java`, replace:
```java
import io.github.alexshamrai.dto.catalog.Stats;
```
with:
```java
import io.github.alexshamrai.dto.export.Stats;
```

- [ ] **Step 3: Delete the import machinery, fixtures, and seed file**

```bash
git rm backend/src/main/java/io/github/alexshamrai/dto/catalog/Stats.java \
       backend/src/main/java/io/github/alexshamrai/dto/catalog/Catalog.java \
       backend/src/main/java/io/github/alexshamrai/dto/catalog/GenreGroup.java \
       backend/src/main/java/io/github/alexshamrai/dto/catalog/Artist.java \
       backend/src/main/java/io/github/alexshamrai/dto/catalog/Album.java \
       backend/src/main/java/io/github/alexshamrai/dto/ImportResult.java \
       backend/src/main/java/io/github/alexshamrai/service/CatalogImportService.java \
       backend/src/test/java/io/github/alexshamrai/service/CatalogImportServiceTest.java \
       backend/src/test/java/io/github/alexshamrai/service/CatalogImportIntegrationTest.java \
       backend/src/test/resources/test-catalog.json \
       catalog.json
```

(If any additional file in `dto/catalog/` remains, delete it too — the package must be empty and removed.)

- [ ] **Step 4: Remove the Dockerfile COPY line**

In `Dockerfile`, delete the line:
```dockerfile
COPY catalog.json /app/catalog.json
```

- [ ] **Step 5: Remove `catalog-path` from all three YAMLs**

In `backend/src/main/resources/application.yml`, remove:
```yaml
  catalog-path: ../catalog.json
```
In `backend/src/main/resources/application-cloud.yml`, remove:
```yaml
  catalog-path: /app/catalog.json
```
and the two comment lines above it referencing `catalog.json baked into the image`.

In `backend/src/test/resources/application-test.yml`, remove:
```yaml
  catalog-path: non-existent-catalog.json
```
(If `music-cat:` then has no remaining keys in the test YAML, delete the now-empty `music-cat:` block.)

- [ ] **Step 6: Update the boot integration test comment**

In `CatalogAutoImporterIntegrationTest.java`, replace the test body comment:
```java
        // The test profile has catalog-path=non-existent-catalog.json
        // Auto-importer should skip without error, DB should be empty
```
with:
```java
        // Sheets are disabled in the test profile → boot leaves the DB empty, no error.
```

- [ ] **Step 7: Verify the whole backend builds and all tests pass, and no dead references remain**

Run: `./gradlew :backend:build`
Expected: BUILD SUCCESSFUL.

Run: `grep -rn "catalog.json\|CatalogImportService\|dto.catalog\|ImportResult\|catalog-path" backend/src Dockerfile backend/src/main/resources 2>/dev/null`
Expected: no matches (empty output).

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "refactor: remove catalog.json + file-import machinery (Sheets-only)"
```

---

### Task 11: Scripts + documentation

**Files:**
- Create: `snapshot-prod-to-fake.sh` (repo root)
- Create: `run-fake.sh` (repo root)
- Modify: `CLAUDE.md`, `README.md`

- [ ] **Step 1: Create `snapshot-prod-to-fake.sh`**

```bash
#!/usr/bin/env bash
# Read-only snapshot of the LIVE Google Sheet into ./data/fake-sheets.json.
# Only READS the three tabs — it never writes to Google. Requires service-account
# credentials with (at least read) access to the prod spreadsheet.
#
#   SHEETS_SPREADSHEET_ID=<prod id> ./snapshot-prod-to-fake.sh
#
# Uses the DEFAULT profile (not cloud) because the cloud profile refuses to start with the
# default admin/admin credentials.
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
```

- [ ] **Step 2: Create `run-fake.sh`**

```bash
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
```

- [ ] **Step 3: Make the scripts executable**

Run: `chmod +x snapshot-prod-to-fake.sh run-fake.sh`
Expected: no output.

- [ ] **Step 4: Update `CLAUDE.md`**

Make these edits to `CLAUDE.md`:
- In the data-flow / boot-decision description, replace mentions of `catalog.json` seed/fallback with the Sheets-only tree: `DB non-empty → skip; DB empty + Sheets has data → restore; DB empty + Sheets blank/unreachable → empty catalog (recover via sync/pull)`. State the accepted trade-off: a Sheets outage on a cold start yields an empty app until recovery.
- Remove `/api/catalog/import` from the API list and remove `CatalogImportService`, `dto/catalog/*`, and `ImportResult` from the package/DTO lists.
- Add a "Local dev / staging" note: `MUSIC_CAT_SHEETS_MODE=fake` + a file-backed `FakeSheetsClient` (`./data/fake-sheets.json`), seeded by the read-only `./snapshot-prod-to-fake.sh`; run with `./run-fake.sh`. Note the three-layer test strategy (fake integration, `GoogleSheetsClient` via `MockHttpTransport`, and the documented Layer 3 real-spreadsheet follow-on).
- Add a Key Convention line: `music-cat.sheets.mode` (google|fake) selects the `SheetsClient` impl; fake mode needs no credentials.

- [ ] **Step 5: Update `README.md`**

Remove the `catalog.json` seed row/line (project-root `catalog.json` and the `music-cat.catalog-path` config row) and add a short "Local fake Sheets" subsection pointing at `snapshot-prod-to-fake.sh` and `run-fake.sh`.

- [ ] **Step 6: Full build + commit**

Run: `./gradlew :backend:build`
Expected: BUILD SUCCESSFUL.

```bash
git add -A
git commit -m "docs+scripts: fake-sheets snapshot/run scripts; document Sheets-only + fake mode"
```

---

## Self-Review

**Spec coverage:**
- Removals (Sheets-only): Tasks 8, 9, 10 (incl. Stats move, `catalog.json`, Dockerfile, `catalog-path`, tests). ✓
- `music-cat.sheets.mode`: Task 1 + wiring in Task 4. ✓
- `FakeSheetStore` / `FakeSheetsClient`: Tasks 2, 3. ✓
- Snapshot runner + read-only guarantee: Task 5. ✓
- Bean wiring (`ConditionalOnExpression`, sync stack unchanged, startup banner): Tasks 3, 4. ✓
- Simplified boot tree: Task 8. ✓
- Scripts (`snapshot-prod-to-fake.sh`, `run-fake.sh`): Task 11. ✓
- Layer 1 (fake write-path + restore): Task 6. ✓
- Layer 2 (`GoogleSheetsClient` via `MockHttpTransport`): Task 7. ✓
- Layer 3 documented as follow-on: recorded in the spec and CLAUDE.md note (Task 11); not implemented, as intended. ✓
- Consequence (outage → empty app) documented: Task 8 Javadoc + CLAUDE.md (Task 11). ✓

**Placeholder scan:** No TBD/TODO/"handle edge cases"/"similar to Task N" — every code step has complete code. ✓

**Type consistency:** `SheetsProperties(boolean,String,String,String,String,boolean)` + 3-arg convenience ctor used consistently (Tasks 3, 5, 7). `CatalogAutoImporter(ArtistRepository, ObjectProvider<SheetsCatalogReader>, ObjectProvider<SheetSyncService>, ReadinessState)` matches between Task 8's class and test. `FakeSheetStore` method names (`read`/`write`/`writeAll`/`readAll`) consistent across Tasks 2, 3, 5, 6. `SheetsLoadResult(artistCount, albumCount, songCount, warnings)` used as in the existing record. ✓

## Execution note on ordering

The build stays green after every task: `CatalogImportService` survives (unused) until Task 10, `Stats` is recreated in `dto/export` before its old copy is deleted (Task 10 steps 1-3), and the `/import` endpoint (Task 9) and boot-tree (Task 8) drop their `CatalogImportService` usages before it is deleted.
