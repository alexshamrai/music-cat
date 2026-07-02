package io.github.alexshamrai.service;

import com.google.api.services.sheets.v4.Sheets;
import io.github.alexshamrai.domain.AlbumEntity;
import io.github.alexshamrai.domain.ArtistEntity;
import io.github.alexshamrai.domain.Genre;
import io.github.alexshamrai.domain.SongEntity;
import io.github.alexshamrai.repository.AlbumRepository;
import io.github.alexshamrai.repository.ArtistRepository;
import io.github.alexshamrai.repository.SongRepository;
import io.github.alexshamrai.sheets.SheetsClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Integration test for SheetSyncService that verifies the songs traversal
 * (song.getAlbum().getArtist()) works correctly from a non-request thread
 * without OSIV holding the session open.
 *
 * <p>This test MUST NOT be annotated with @Transactional — the whole point is
 * to prove that pushCatalog owns its session via @Transactional(readOnly=true)
 * rather than borrowing one from the caller or OSIV.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "music-cat.sheets.enabled=true",
        "spring.datasource.url=jdbc:h2:mem:testdb-sync-it;DB_CLOSE_DELAY=-1"
})
class SheetSyncServiceIntegrationTest {

    @Autowired
    private SheetSyncService sheetSyncService;

    @Autowired
    private ArtistRepository artistRepository;

    @Autowired
    private AlbumRepository albumRepository;

    @Autowired
    private SongRepository songRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private SheetsClient sheetsClient;

    // Prevent GoogleSheetsConfig from loading real credentials
    @MockitoBean
    private Sheets googleSheetsApi;

    /**
     * Calls pushCatalog(true) from a plain background thread (no OSIV, no caller @Transactional).
     * Without @Transactional(readOnly=true) on pushCatalog and fetch-joined queries,
     * this throws LazyInitializationException when traversing song.getAlbum().getArtist().
     */
    @Test
    void pushCatalog_fromNonRequestThread_traversesSongAlbumArtistWithoutLazyInit() throws Exception {
        // Seed artist + album + song inside a transaction so they are committed before pushCatalog runs
        final long[] ids = new long[3];
        transactionTemplate.execute(status -> {
            ArtistEntity artist = ArtistEntity.builder()
                    .name("Non-OSIV Artist")
                    .genre(Genre.JAZZ_AND_FUNK)
                    .build();
            artistRepository.save(artist);

            AlbumEntity album = AlbumEntity.builder()
                    .title("Non-OSIV Album")
                    .artist(artist)
                    .build();
            albumRepository.save(album);

            SongEntity song = SongEntity.builder()
                    .title("Non-OSIV Song")
                    .trackNumber(1)
                    .discNumber(1)
                    .album(album)
                    .build();
            songRepository.save(song);

            ids[0] = artist.getId();
            ids[1] = album.getId();
            ids[2] = song.getId();
            return null;
        });

        // Call pushCatalog from a background thread — NO OSIV, no caller @Transactional
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> future = executor.submit((Callable<Void>) () -> {
                sheetSyncService.pushCatalog(true);
                return null;
            });
            future.get(); // propagates any exception as ExecutionException
        } finally {
            executor.shutdown();
        }

        // Verify Songs tab was written and contains a row with the seeded artist/album names
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<List<Object>>> captor = ArgumentCaptor.forClass(List.class);
        verify(sheetsClient).overwrite(eq("Songs"), captor.capture());

        List<List<Object>> songRows = captor.getValue();
        // Row 0 is the header; at least one data row must exist
        assertThat(songRows).hasSizeGreaterThanOrEqualTo(2);

        // Find the row for our seeded song (artist=col0, album=col1, title=col4)
        boolean found = songRows.stream()
                .skip(1) // skip header
                .anyMatch(row ->
                        "Non-OSIV Artist".equals(row.get(0)) &&
                        "Non-OSIV Album".equals(row.get(1)) &&
                        "Non-OSIV Song".equals(row.get(4)));
        assertThat(found)
                .as("Expected a Songs row with artist='Non-OSIV Artist', album='Non-OSIV Album', title='Non-OSIV Song'")
                .isTrue();
    }
}
