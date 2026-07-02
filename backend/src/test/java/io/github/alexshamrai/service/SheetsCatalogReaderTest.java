package io.github.alexshamrai.service;

import com.google.api.services.sheets.v4.Sheets;
import io.github.alexshamrai.domain.AlbumEntity;
import io.github.alexshamrai.domain.ArtistEntity;
import io.github.alexshamrai.domain.Genre;
import io.github.alexshamrai.domain.SongEntity;
import io.github.alexshamrai.repository.AlbumRepository;
import io.github.alexshamrai.repository.ArtistRepository;
import io.github.alexshamrai.repository.SongRepository;
import io.github.alexshamrai.repository.TagRepository;
import io.github.alexshamrai.sheets.SheetsClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static io.github.alexshamrai.TestDataFactory.artist;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Integration tests for SheetsCatalogReader with a mocked SheetsClient.
 * Verifies the full read path: sheet rows → parsed records → persisted entity graph.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "music-cat.sheets.enabled=true",
        "spring.datasource.url=jdbc:h2:mem:testdb-reader;DB_CLOSE_DELAY=-1"
})
@Transactional
class SheetsCatalogReaderTest {

    private static final List<Object> ARTISTS_HEADER = List.of("name", "genre", "subgenre", "favorite", "tags");
    private static final List<Object> ALBUMS_HEADER = List.of("artist", "title", "year", "grade", "favorite", "tags");
    private static final List<Object> SONGS_HEADER = List.of("artist", "album", "disc", "track", "title");

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

    @MockitoBean
    private SheetsClient sheetsClient;

    // Prevent GoogleSheetsConfig from loading real credentials
    @MockitoBean
    private Sheets googleSheetsApi;

    private void stubTabs(List<List<Object>> artists, List<List<Object>> albums, List<List<Object>> songs) {
        when(sheetsClient.read("Artists")).thenReturn(artists);
        when(sheetsClient.read("Albums")).thenReturn(albums);
        when(sheetsClient.read("Songs")).thenReturn(songs);
    }

    private void stubFullCatalog() {
        stubTabs(
                List.of(
                        ARTISTS_HEADER,
                        List.of("Miles Davis", "Jazz & Funk", "", "TRUE", "jazz, legend"),
                        List.of("Pink Floyd", "Progressive Rock", "Psychedelic", "FALSE", "classic")),
                List.of(
                        ALBUMS_HEADER,
                        List.of("Miles Davis", "Kind of Blue", "1959", "5", "TRUE", "masterpiece, classic"),
                        List.of("Miles Davis", "Bitches Brew", "1970.0", "", "FALSE", ""),
                        List.of("Pink Floyd", "The Dark Side of the Moon", "1973", "4", "TRUE", "classic")),
                List.of(
                        SONGS_HEADER,
                        List.of("Miles Davis", "Kind of Blue", "1", "1", "So What"),
                        List.of("Miles Davis", "Kind of Blue", "1.0", "2.0", "Freddie Freeloader"),
                        List.of("Miles Davis", "Bitches Brew", "1", "1", "Pharaoh's Dance"),
                        List.of("Pink Floyd", "The Dark Side of the Moon", "1", "1", "Speak to Me"),
                        List.of("Pink Floyd", "The Dark Side of the Moon", "2", "1", "Us and Them")));
    }

    @Test
    void loadFromSheets_populatesFullGraphFromTabs() {
        stubFullCatalog();

        SheetsLoadResult result = reader.loadFromSheets();

        assertThat(result.artistCount()).isEqualTo(2);
        assertThat(result.albumCount()).isEqualTo(3);
        assertThat(result.songCount()).isEqualTo(5);

        ArtistEntity miles = findArtist("Miles Davis");
        assertThat(miles.getGenre()).isEqualTo(Genre.JAZZ_AND_FUNK);
        assertThat(miles.getSubgenre()).isNull();
        assertThat(miles.isFavorite()).isTrue();
        assertThat(tagNames(miles)).containsExactlyInAnyOrder("jazz", "legend");

        ArtistEntity pink = findArtist("Pink Floyd");
        assertThat(pink.getGenre()).isEqualTo(Genre.PROGRESSIVE_ROCK);
        assertThat(pink.getSubgenre()).isEqualTo("Psychedelic");
        assertThat(pink.isFavorite()).isFalse();
        assertThat(tagNames(pink)).containsExactly("classic");

        AlbumEntity kindOfBlue = findAlbum("Kind of Blue");
        assertThat(kindOfBlue.getArtist().getName()).isEqualTo("Miles Davis");
        assertThat(kindOfBlue.getYear()).isEqualTo(1959);
        assertThat(kindOfBlue.getGrade()).isEqualTo(5);
        assertThat(kindOfBlue.isFavorite()).isTrue();
        assertThat(tagNames(kindOfBlue)).containsExactlyInAnyOrder("masterpiece", "classic");

        AlbumEntity bitchesBrew = findAlbum("Bitches Brew");
        assertThat(bitchesBrew.getYear()).isEqualTo(1970);
        assertThat(bitchesBrew.getGrade()).isNull();
        assertThat(bitchesBrew.isFavorite()).isFalse();
        assertThat(bitchesBrew.getTags()).isEmpty();

        AlbumEntity darkSide = findAlbum("The Dark Side of the Moon");
        assertThat(darkSide.getYear()).isEqualTo(1973);
        assertThat(darkSide.getGrade()).isEqualTo(4);

        // Songs attached to the right albums, disc/track preserved
        List<SongEntity> songs = songRepository.findAllForSync();
        assertThat(songs).hasSize(5);

        assertThat(songsOf(songs, "Kind of Blue"))
                .extracting(SongEntity::getDiscNumber, SongEntity::getTrackNumber, SongEntity::getTitle)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(1, 1, "So What"),
                        org.assertj.core.groups.Tuple.tuple(1, 2, "Freddie Freeloader"));

        assertThat(songsOf(songs, "The Dark Side of the Moon"))
                .extracting(SongEntity::getDiscNumber, SongEntity::getTrackNumber, SongEntity::getTitle)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(1, 1, "Speak to Me"),
                        org.assertj.core.groups.Tuple.tuple(2, 1, "Us and Them"));
    }

    @Test
    void loadFromSheets_albumRowWithUnknownArtist_isSkippedRestImported() {
        stubTabs(
                List.of(
                        ARTISTS_HEADER,
                        List.of("Miles Davis", "Jazz & Funk", "", "FALSE", "")),
                List.of(
                        ALBUMS_HEADER,
                        List.of("Miles Davis", "Kind of Blue", "1959", "", "FALSE", ""),
                        List.of("Ghost Artist", "Phantom Album", "2000", "", "FALSE", "")),
                List.of(SONGS_HEADER));

        SheetsLoadResult result = reader.loadFromSheets();

        assertThat(result.artistCount()).isEqualTo(1);
        assertThat(result.albumCount()).isEqualTo(1);
        assertThat(albumRepository.findAllForSync())
                .extracting(AlbumEntity::getTitle)
                .containsExactly("Kind of Blue");
    }

    @Test
    void loadFromSheets_songRowWithUnknownAlbum_isSkippedRestImported() {
        stubTabs(
                List.of(
                        ARTISTS_HEADER,
                        List.of("Miles Davis", "Jazz & Funk", "", "FALSE", "")),
                List.of(
                        ALBUMS_HEADER,
                        List.of("Miles Davis", "Kind of Blue", "1959", "", "FALSE", "")),
                List.of(
                        SONGS_HEADER,
                        List.of("Miles Davis", "Kind of Blue", "1", "1", "So What"),
                        List.of("Miles Davis", "Unknown Album", "1", "1", "Lost Song")));

        SheetsLoadResult result = reader.loadFromSheets();

        assertThat(result.songCount()).isEqualTo(1);
        assertThat(songRepository.findAllForSync())
                .extracting(SongEntity::getTitle)
                .containsExactly("So What");
    }

    @Test
    void loadFromSheets_sharedTag_resolvesToSingleTagEntity() {
        stubFullCatalog();

        reader.loadFromSheets();

        // "classic" appears on Pink Floyd (artist), Kind of Blue and Dark Side (albums)
        assertThat(tagRepository.findAll())
                .extracting(t -> t.getName())
                .containsExactlyInAnyOrder("jazz", "legend", "classic", "masterpiece");
    }

    @Test
    void replaceFromSheets_populatedDb_oldDataGoneSheetDataPresent() {
        artistRepository.save(artist("Old Artist", Genre.BLUES));
        tagRepository.save(io.github.alexshamrai.TestDataFactory.tag("obsolete"));

        stubTabs(
                List.of(
                        ARTISTS_HEADER,
                        List.of("Miles Davis", "Jazz & Funk", "", "FALSE", "fresh")),
                List.of(ALBUMS_HEADER),
                List.of(SONGS_HEADER));

        SheetsLoadResult result = reader.replaceFromSheets();

        assertThat(result.artistCount()).isEqualTo(1);
        assertThat(artistRepository.findAll())
                .extracting(ArtistEntity::getName)
                .containsExactly("Miles Davis");
        assertThat(tagRepository.findAll())
                .extracting(t -> t.getName())
                .containsExactly("fresh");
    }

    @Test
    void sheetsHaveData_headerOnly_returnsFalse() {
        when(sheetsClient.read("Artists")).thenReturn(List.of(ARTISTS_HEADER));

        assertThat(reader.sheetsHaveData()).isFalse();
    }

    @Test
    void sheetsHaveData_headerPlusOneRow_returnsTrue() {
        when(sheetsClient.read("Artists")).thenReturn(List.of(
                ARTISTS_HEADER,
                List.of("Miles Davis", "Jazz & Funk", "", "FALSE", "")));

        assertThat(reader.sheetsHaveData()).isTrue();
    }

    @Test
    void sheetsHaveData_emptyTab_returnsFalse() {
        when(sheetsClient.read("Artists")).thenReturn(List.of());

        assertThat(reader.sheetsHaveData()).isFalse();
    }

    @Test
    void sheetsHaveData_artistsBlankButAlbumsHaveRows_returnsTrue() {
        // A partially-failed overwrite can leave Artists cleared while Albums survive —
        // that must NOT look like a blank spreadsheet (seed+push would wipe Albums)
        when(sheetsClient.read("Artists")).thenReturn(List.of(ARTISTS_HEADER));
        when(sheetsClient.read("Albums")).thenReturn(List.of(
                ALBUMS_HEADER,
                List.of("Miles Davis", "Kind of Blue", "1959", "5", "TRUE", "")));

        assertThat(reader.sheetsHaveData()).isTrue();
    }

    // ==================== row resilience ====================

    @Test
    void loadFromSheets_fullyBlankRow_skippedSilentlyWithoutWarning() {
        stubTabs(
                List.of(
                        ARTISTS_HEADER,
                        List.of("", "", "", "", ""),
                        List.of("Miles Davis", "Jazz & Funk", "", "FALSE", "")),
                List.of(ALBUMS_HEADER),
                List.of(SONGS_HEADER));

        SheetsLoadResult result = reader.loadFromSheets();

        assertThat(result.artistCount()).isEqualTo(1);
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void loadFromSheets_unknownGenreRow_skippedWithWarningRestImported() {
        stubTabs(
                List.of(
                        ARTISTS_HEADER,
                        List.of("Typo Artist", "Jazzz", "", "FALSE", ""),
                        List.of("Miles Davis", "Jazz & Funk", "", "FALSE", "")),
                List.of(ALBUMS_HEADER),
                List.of(SONGS_HEADER));

        SheetsLoadResult result = reader.loadFromSheets();

        assertThat(result.artistCount()).isEqualTo(1);
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().get(0)).contains("Typo Artist").contains("Unknown genre");
        assertThat(artistRepository.findAll())
                .extracting(ArtistEntity::getName)
                .containsExactly("Miles Davis");
    }

    @Test
    void loadFromSheets_duplicateArtistName_firstWinsSecondSkippedWithWarning() {
        stubTabs(
                List.of(
                        ARTISTS_HEADER,
                        List.of("Miles Davis", "Jazz & Funk", "", "TRUE", ""),
                        List.of("Miles Davis", "Blues", "", "FALSE", "")),
                List.of(ALBUMS_HEADER),
                List.of(SONGS_HEADER));

        SheetsLoadResult result = reader.loadFromSheets();

        assertThat(result.artistCount()).isEqualTo(1);
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().get(0)).contains("duplicate artist");
        List<ArtistEntity> artists = artistRepository.findAll();
        assertThat(artists).hasSize(1);
        assertThat(artists.get(0).getGenre()).isEqualTo(Genre.JAZZ_AND_FUNK);
    }

    @Test
    void loadFromSheets_duplicateAlbumKey_firstWinsSecondSkippedWithWarning() {
        stubTabs(
                List.of(
                        ARTISTS_HEADER,
                        List.of("Miles Davis", "Jazz & Funk", "", "FALSE", "")),
                List.of(
                        ALBUMS_HEADER,
                        List.of("Miles Davis", "Kind of Blue", "1959", "5", "TRUE", ""),
                        List.of("Miles Davis", "Kind of Blue", "1960", "1", "FALSE", "")),
                List.of(SONGS_HEADER));

        SheetsLoadResult result = reader.loadFromSheets();

        assertThat(result.albumCount()).isEqualTo(1);
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().get(0)).contains("duplicate album");
        List<AlbumEntity> albums = albumRepository.findAllForSync();
        assertThat(albums).hasSize(1);
        assertThat(albums.get(0).getYear()).isEqualTo(1959);
        assertThat(albums.get(0).getGrade()).isEqualTo(5);
    }

    // ==================== helpers ====================

    private ArtistEntity findArtist(String name) {
        return artistRepository.findAllForSync().stream()
                .filter(a -> a.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Artist not found: " + name));
    }

    private AlbumEntity findAlbum(String title) {
        return albumRepository.findAllForSync().stream()
                .filter(a -> a.getTitle().equals(title))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Album not found: " + title));
    }

    private List<SongEntity> songsOf(List<SongEntity> songs, String albumTitle) {
        return songs.stream()
                .filter(s -> s.getAlbum().getTitle().equals(albumTitle))
                .toList();
    }

    private List<String> tagNames(ArtistEntity artist) {
        return artist.getTags().stream().map(t -> t.getName()).toList();
    }

    private List<String> tagNames(AlbumEntity album) {
        return album.getTags().stream().map(t -> t.getName()).toList();
    }
}
