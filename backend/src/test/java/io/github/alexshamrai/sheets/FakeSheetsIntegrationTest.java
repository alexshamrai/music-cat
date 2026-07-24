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
import io.github.alexshamrai.service.SheetsCatalogReader;
import io.github.alexshamrai.service.SheetsLoadResult;
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
