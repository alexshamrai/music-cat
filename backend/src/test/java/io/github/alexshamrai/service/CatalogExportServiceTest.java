package io.github.alexshamrai.service;

import io.github.alexshamrai.domain.AlbumEntity;
import io.github.alexshamrai.domain.ArtistEntity;
import io.github.alexshamrai.domain.Genre;
import io.github.alexshamrai.domain.SongEntity;
import io.github.alexshamrai.dto.export.ExportAlbum;
import io.github.alexshamrai.dto.export.ExportArtist;
import io.github.alexshamrai.dto.export.ExportCatalog;
import io.github.alexshamrai.dto.export.ExportGenre;
import io.github.alexshamrai.repository.AlbumRepository;
import io.github.alexshamrai.repository.ArtistRepository;
import io.github.alexshamrai.repository.SongRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static io.github.alexshamrai.TestDataFactory.albumWithId;
import static io.github.alexshamrai.TestDataFactory.artistWithId;
import static io.github.alexshamrai.TestDataFactory.artistWithTags;
import static io.github.alexshamrai.TestDataFactory.songWithId;
import static io.github.alexshamrai.TestDataFactory.tagWithId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CatalogExportServiceTest {

    @Mock
    private ArtistRepository artistRepository;

    @Mock
    private AlbumRepository albumRepository;

    @Mock
    private SongRepository songRepository;

    @InjectMocks
    private CatalogExportService service;

    private ArtistEntity bbKing;
    private ArtistEntity crosby;
    private ArtistEntity aerosmith;
    private AlbumEntity liveAtTheRegal;
    private AlbumEntity storyAlbum;
    private AlbumEntity dejaVu;

    @BeforeEach
    void setUp() {
        bbKing = artistWithTags(1L, "B.B. King", Genre.BLUES, Set.of(tagWithId(1L, "legend")));
        bbKing.setFavorite(true);

        crosby = artistWithId(2L, "Crosby, Stills & Nash", Genre.POP_AND_ROCK);
        crosby.setSubgenre("Folk Rock");

        aerosmith = artistWithId(3L, "Aerosmith", Genre.POP_AND_ROCK);

        liveAtTheRegal = albumWithId(10L, "Live at the Regal", 1965, bbKing);
        liveAtTheRegal.setGrade(5);
        liveAtTheRegal.setFavorite(true);
        liveAtTheRegal.setTags(Set.of(tagWithId(2L, "live"), tagWithId(3L, "classic")));

        storyAlbum = albumWithId(11L, "What's the \"Story\"", null, crosby);

        dejaVu = albumWithId(12L, "Déjà Vu", 1970, crosby);

        when(artistRepository.findAllForSync()).thenReturn(List.of(crosby, bbKing, aerosmith));
        when(albumRepository.findAllForSync()).thenReturn(List.of(storyAlbum, liveAtTheRegal, dejaVu));
        when(songRepository.findAllForSync()).thenReturn(List.of(
                songWithId(100L, "Every Day I Have the Blues", 1, 1, liveAtTheRegal),
                songWithId(103L, "Bonus Disc Song", 1, 2, liveAtTheRegal),
                songWithId(101L, "Sweet Little Angel", 2, 1, liveAtTheRegal),
                songWithId(102L, "Carry On", 1, 1, dejaVu)));
    }

    // ==================== JSON export ====================

    @Test
    void exportJson_groupsByGenre_sortedAlphabetically() {
        ExportCatalog catalog = service.exportJson();

        assertThat(catalog.exportedAt()).isNotNull();
        assertThat(catalog.genres())
                .extracting(ExportGenre::genre)
                .containsExactly("Blues", "Pop & Rock");
    }

    @Test
    void exportJson_artistsSortedByName_albumsByYearNullLastThenTitle() {
        ExportCatalog catalog = service.exportJson();

        ExportGenre popRock = catalog.genres().get(1);
        assertThat(popRock.artists())
                .extracting(ExportArtist::name)
                .containsExactly("Aerosmith", "Crosby, Stills & Nash");

        ExportArtist crosbyExport = popRock.artists().get(1);
        assertThat(crosbyExport.albums())
                .extracting(ExportAlbum::title)
                .containsExactly("Déjà Vu", "What's the \"Story\"");
    }

    @Test
    void exportJson_curationFieldsPreserved_nullsStayNull() {
        ExportCatalog catalog = service.exportJson();

        ExportArtist king = catalog.genres().get(0).artists().get(0);
        assertThat(king.name()).isEqualTo("B.B. King");
        assertThat(king.subgenre()).isNull();
        assertThat(king.isFavorite()).isTrue();
        assertThat(king.tags()).containsExactly("legend");

        ExportAlbum regal = king.albums().get(0);
        assertThat(regal.year()).isEqualTo(1965);
        assertThat(regal.grade()).isEqualTo(5);
        assertThat(regal.isFavorite()).isTrue();
        assertThat(regal.tags()).containsExactly("classic", "live");

        // Songs ordered by disc, then track
        assertThat(regal.songs())
                .extracting(s -> s.title())
                .containsExactly("Every Day I Have the Blues", "Sweet Little Angel", "Bonus Disc Song");

        ExportArtist crosbyExport = catalog.genres().get(1).artists().get(1);
        ExportAlbum story = crosbyExport.albums().get(1);
        assertThat(story.year()).isNull();
        assertThat(story.grade()).isNull();
        assertThat(story.songs()).isEmpty();
    }

    @Test
    void exportJson_statsCountEverything() {
        ExportCatalog catalog = service.exportJson();

        assertThat(catalog.stats().totalGenres()).isEqualTo(2);
        assertThat(catalog.stats().totalArtists()).isEqualTo(3);
        assertThat(catalog.stats().totalAlbums()).isEqualTo(3);
        assertThat(catalog.stats().totalTracks()).isEqualTo(4);
    }

    // ==================== CSV export ====================

    @Test
    void exportCsvZip_containsExactlyArtistsAndAlbumsCsv() throws IOException {
        Map<String, String> entries = unzip(service.exportCsvZip());

        assertThat(entries.keySet()).containsExactlyInAnyOrder("artists.csv", "albums.csv");
        // header + 3 artists / header + 3 albums
        assertThat(lines(entries.get("artists.csv"))).hasSize(4);
        assertThat(lines(entries.get("albums.csv"))).hasSize(4);
    }

    @Test
    void exportCsvZip_headers() throws IOException {
        Map<String, String> entries = unzip(service.exportCsvZip());

        assertThat(lines(entries.get("artists.csv")).get(0))
                .isEqualTo("name,genre,subgenre,isFavorite,tags,albumCount");
        assertThat(lines(entries.get("albums.csv")).get(0))
                .isEqualTo("artistName,genre,title,year,grade,isFavorite,tags,songCount");
    }

    @Test
    void exportCsvZip_commaInArtistName_isQuoted() throws IOException {
        Map<String, String> entries = unzip(service.exportCsvZip());

        assertThat(lines(entries.get("artists.csv")))
                .contains("\"Crosby, Stills & Nash\",Pop & Rock,Folk Rock,false,,2");
    }

    @Test
    void exportCsvZip_embeddedQuotesDoubled_nullYearGradeEmpty() throws IOException {
        Map<String, String> entries = unzip(service.exportCsvZip());

        assertThat(lines(entries.get("albums.csv")))
                .contains("\"Crosby, Stills & Nash\",Pop & Rock,\"What's the \"\"Story\"\"\",,,false,,0");
    }

    @Test
    void exportCsvZip_multiTagField_quotedAndJoined() throws IOException {
        Map<String, String> entries = unzip(service.exportCsvZip());

        assertThat(lines(entries.get("albums.csv")))
                .contains("B.B. King,Blues,Live at the Regal,1965,5,true,\"classic, live\",3");
    }

    @Test
    void exportCsvZip_formulaLikeTitle_isNeutralized() throws IOException {
        ArtistEntity evil = artistWithId(4L, "=HYPERLINK(\"http://evil\",\"x\")", Genre.POP_AND_ROCK);
        when(artistRepository.findAllForSync()).thenReturn(List.of(evil));
        when(albumRepository.findAllForSync()).thenReturn(List.of());
        when(songRepository.findAllForSync()).thenReturn(List.of());

        Map<String, String> entries = unzip(service.exportCsvZip());

        assertThat(lines(entries.get("artists.csv")))
                .contains("\"'=HYPERLINK(\"\"http://evil\"\",\"\"x\"\")\",Pop & Rock,,false,,0");
    }

    // ==================== helpers ====================

    private static Map<String, String> unzip(byte[] zipBytes) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }

    private static List<String> lines(String content) {
        return content.strip().lines().toList();
    }
}
