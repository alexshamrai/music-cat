package io.github.alexshamrai.service;

import io.github.alexshamrai.domain.Genre;
import io.github.alexshamrai.dto.ImportResult;
import io.github.alexshamrai.repository.AlbumRepository;
import io.github.alexshamrai.repository.ArtistRepository;
import io.github.alexshamrai.repository.SongRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.net.URL;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CatalogImportIntegrationTest {

    @Autowired
    private CatalogImportService catalogImportService;

    @Autowired
    private ArtistRepository artistRepository;

    @Autowired
    private AlbumRepository albumRepository;

    @Autowired
    private SongRepository songRepository;

    @Test
    void importFromJson_fullCatalog_persistsAllEntities() throws Exception {
        Path catalogPath = getTestCatalogPath();

        ImportResult result = catalogImportService.importFromJson(catalogPath);

        assertThat(result.artistCount()).isEqualTo(2);
        assertThat(result.albumCount()).isEqualTo(3);
        assertThat(result.songCount()).isEqualTo(7);

        assertThat(artistRepository.count()).isEqualTo(2);
        assertThat(albumRepository.count()).isEqualTo(3);
        assertThat(songRepository.count()).isEqualTo(7);
    }

    @Test
    void importFromJson_duplicateImport_skipsExistingAlbums() throws Exception {
        Path catalogPath = getTestCatalogPath();

        ImportResult first = catalogImportService.importFromJson(catalogPath);
        ImportResult second = catalogImportService.importFromJson(catalogPath);

        // Second import should skip all albums (they already exist)
        assertThat(second.albumCount()).isEqualTo(0);
        assertThat(second.songCount()).isEqualTo(0);

        // DB should not have duplicates
        assertThat(albumRepository.count()).isEqualTo(3);
        assertThat(songRepository.count()).isEqualTo(7);
    }

    @Test
    void importFromJson_songsParsedCorrectly() throws Exception {
        Path catalogPath = getTestCatalogPath();
        catalogImportService.importFromJson(catalogPath);

        var songs = songRepository.findAll();

        // "01 - Song One.mp3" → title "Song One", track 1
        var songOne = songs.stream().filter(s -> s.getTitle().equals("Song One")).findFirst();
        assertThat(songOne).isPresent();
        assertThat(songOne.get().getTrackNumber()).isEqualTo(1);
        assertThat(songOne.get().getDiscNumber()).isEqualTo(1);

        // "03. Third Song.mp3" → title "Third Song", track 3
        var thirdSong = songs.stream().filter(s -> s.getTitle().equals("Third Song")).findFirst();
        assertThat(thirdSong).isPresent();
        assertThat(thirdSong.get().getTrackNumber()).isEqualTo(3);

        // "01_Smooth_Jazz.mp3" → title "Smooth Jazz", track 1
        var smoothJazz = songs.stream().filter(s -> s.getTitle().equals("Smooth Jazz")).findFirst();
        assertThat(smoothJazz).isPresent();
        assertThat(smoothJazz.get().getTrackNumber()).isEqualTo(1);

        // "101 - Disc One Track One.mp3" → disc 1, track 1
        var discOne = songs.stream().filter(s -> s.getTitle().equals("Disc One Track One")).findFirst();
        assertThat(discOne).isPresent();
        assertThat(discOne.get().getTrackNumber()).isEqualTo(1);
        assertThat(discOne.get().getDiscNumber()).isEqualTo(1);

        // "201 - Disc Two Track One.mp3" → disc 2, track 1
        var discTwo = songs.stream().filter(s -> s.getTitle().equals("Disc Two Track One")).findFirst();
        assertThat(discTwo).isPresent();
        assertThat(discTwo.get().getTrackNumber()).isEqualTo(1);
        assertThat(discTwo.get().getDiscNumber()).isEqualTo(2);

        // "Some Track Without Number.mp3" → positional index as track number
        var noNumber = songs.stream().filter(s -> s.getTitle().equals("Some Track Without Number")).findFirst();
        assertThat(noNumber).isPresent();
        assertThat(noNumber.get().getTrackNumber()).isEqualTo(2); // positional index (2nd song)
    }

    @Test
    void importFromJson_entityRelationshipsCorrect() throws Exception {
        Path catalogPath = getTestCatalogPath();
        catalogImportService.importFromJson(catalogPath);

        var rockArtist = artistRepository.findByNameAndGenre("Test Artist", Genre.PROGRESSIVE_ROCK);
        assertThat(rockArtist).isPresent();

        var albums = albumRepository.findAll().stream()
                .filter(a -> a.getArtist().getId().equals(rockArtist.get().getId()))
                .toList();
        assertThat(albums).hasSize(2);
        assertThat(albums).extracting("title").containsExactlyInAnyOrder("First Album", "Second Album");

        var firstAlbum = albums.stream().filter(a -> a.getTitle().equals("First Album")).findFirst();
        assertThat(firstAlbum).isPresent();
        assertThat(firstAlbum.get().getYear()).isEqualTo(2020);
    }

    private Path getTestCatalogPath() throws Exception {
        URL resource = getClass().getClassLoader().getResource("test-catalog.json");
        assertThat(resource).isNotNull();
        return Path.of(resource.toURI());
    }
}
