package io.github.alexshamrai.service;

import com.google.api.services.sheets.v4.Sheets;
import io.github.alexshamrai.domain.AlbumEntity;
import io.github.alexshamrai.domain.ArtistEntity;
import io.github.alexshamrai.domain.Genre;
import io.github.alexshamrai.domain.SongEntity;
import io.github.alexshamrai.domain.TagEntity;
import io.github.alexshamrai.repository.AlbumRepository;
import io.github.alexshamrai.repository.ArtistRepository;
import io.github.alexshamrai.repository.SongRepository;
import io.github.alexshamrai.repository.TagRepository;
import io.github.alexshamrai.sheets.SheetsClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * The round-trip invariant test: seed DB → pushCatalog(true) → wipe DB → loadFromSheets()
 * → the full object graph (artists, albums, songs, tags, grades, favorites, years,
 * subgenres) must be identical to the original.
 *
 * <p>The SheetsClient mock is backed by an in-memory map, so whatever the write path
 * produces is exactly what the read path consumes. This single test guarantees no field
 * silently falls out of the persistence loop.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "music-cat.sheets.enabled=true",
        "spring.datasource.url=jdbc:h2:mem:testdb-roundtrip;DB_CLOSE_DELAY=-1"
})
class SheetsRoundTripInvariantTest {

    @Autowired
    private SheetSyncService sheetSyncService;

    @Autowired
    private SheetsCatalogReader sheetsCatalogReader;

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

    @MockitoBean
    private SheetsClient sheetsClient;

    // Prevent GoogleSheetsConfig from loading real credentials
    @MockitoBean
    private Sheets googleSheetsApi;

    private final Map<String, List<List<Object>>> sheetStore = new HashMap<>();

    @BeforeEach
    void wireInMemorySheets() {
        sheetStore.clear();
        doAnswer(inv -> {
            sheetStore.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(sheetsClient).overwrite(anyString(), anyList());
        when(sheetsClient.read(anyString()))
                .thenAnswer(inv -> sheetStore.getOrDefault(inv.getArgument(0), List.of()));
    }

    @Test
    void fullObjectGraph_survivesPushWipeLoad() {
        seedCatalog();
        List<String> before = snapshotGraph();
        long tagCountBefore = tagRepository.count();
        assertThat(before).isNotEmpty();

        sheetSyncService.pushCatalog(true);

        transactionTemplate.execute(status -> {
            songRepository.deleteAll();
            albumRepository.deleteAll();
            artistRepository.deleteAll();
            tagRepository.deleteAll();
            return null;
        });
        assertThat(snapshotGraph()).isEmpty();

        sheetsCatalogReader.loadFromSheets();

        List<String> after = snapshotGraph();
        assertThat(after).containsExactlyElementsOf(before);
        assertThat(tagRepository.count()).isEqualTo(tagCountBefore);
    }

    /**
     * Seeds a graph exercising every synced field: favorites, grades, null grade,
     * null year, subgenre, shared tags, multi-disc songs.
     */
    private void seedCatalog() {
        transactionTemplate.execute(status -> {
            TagEntity classic = tagRepository.save(tag("classic"));
            TagEntity jazz = tagRepository.save(tag("jazz"));
            TagEntity masterpiece = tagRepository.save(tag("masterpiece"));

            ArtistEntity miles = ArtistEntity.builder()
                    .name("Miles Davis")
                    .genre(Genre.JAZZ_AND_FUNK)
                    .isFavorite(true)
                    .tags(mutableSet(jazz, classic))
                    .build();
            artistRepository.save(miles);

            ArtistEntity pink = ArtistEntity.builder()
                    .name("Pink Floyd")
                    .genre(Genre.PROGRESSIVE_ROCK)
                    .subgenre("Psychedelic")
                    .tags(mutableSet(classic))
                    .build();
            artistRepository.save(pink);

            AlbumEntity kindOfBlue = AlbumEntity.builder()
                    .title("Kind of Blue")
                    .year(1959)
                    .grade(5)
                    .isFavorite(true)
                    .artist(miles)
                    .tags(mutableSet(masterpiece, classic))
                    .build();
            albumRepository.save(kindOfBlue);

            AlbumEntity noYear = AlbumEntity.builder()
                    .title("Unknown Year Album")
                    .artist(miles)
                    .build();
            albumRepository.save(noYear);

            AlbumEntity darkSide = AlbumEntity.builder()
                    .title("The Dark Side of the Moon")
                    .year(1973)
                    .grade(4)
                    .artist(pink)
                    .build();
            albumRepository.save(darkSide);

            songRepository.save(song("So What", 1, 1, kindOfBlue));
            songRepository.save(song("Freddie Freeloader", 2, 1, kindOfBlue));
            songRepository.save(song("Mystery Track", 1, 1, noYear));
            songRepository.save(song("Speak to Me", 1, 1, darkSide));
            songRepository.save(song("Us and Them", 1, 2, darkSide));
            return null;
        });
    }

    /** Canonical, sorted, line-per-entity representation of the whole graph. */
    private List<String> snapshotGraph() {
        return transactionTemplate.execute(status -> {
            List<String> lines = new ArrayList<>();
            artistRepository.findAllForSync().forEach(a -> lines.add(
                    "ARTIST|" + a.getName() + "|" + a.getGenre().getDisplayName() + "|"
                            + a.getSubgenre() + "|" + a.isFavorite() + "|" + tagNames(a.getTags())));
            albumRepository.findAllForSync().forEach(al -> lines.add(
                    "ALBUM|" + al.getArtist().getName() + "|" + al.getTitle() + "|" + al.getYear()
                            + "|" + al.getGrade() + "|" + al.isFavorite() + "|" + tagNames(al.getTags())));
            songRepository.findAllForSync().forEach(s -> lines.add(
                    "SONG|" + s.getAlbum().getArtist().getName() + "|" + s.getAlbum().getTitle()
                            + "|" + s.getDiscNumber() + "|" + s.getTrackNumber() + "|" + s.getTitle()));
            Collections.sort(lines);
            return lines;
        });
    }

    private static String tagNames(Set<TagEntity> tags) {
        return tags.stream().map(TagEntity::getName).sorted().collect(Collectors.joining(","));
    }

    private static Set<TagEntity> mutableSet(TagEntity... tags) {
        return new HashSet<>(List.of(tags));
    }

    private static TagEntity tag(String name) {
        return TagEntity.builder().name(name).build();
    }

    private static SongEntity song(String title, int track, int disc, AlbumEntity album) {
        return SongEntity.builder().title(title).trackNumber(track).discNumber(disc).album(album).build();
    }
}
