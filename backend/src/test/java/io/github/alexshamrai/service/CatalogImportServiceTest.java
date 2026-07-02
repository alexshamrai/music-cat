package io.github.alexshamrai.service;

import io.github.alexshamrai.domain.AlbumEntity;
import io.github.alexshamrai.domain.ArtistEntity;
import io.github.alexshamrai.domain.Genre;
import io.github.alexshamrai.domain.SongEntity;
import io.github.alexshamrai.repository.AlbumRepository;
import io.github.alexshamrai.repository.ArtistRepository;
import io.github.alexshamrai.repository.SongRepository;
import io.github.alexshamrai.dto.ImportResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static io.github.alexshamrai.TestDataFactory.artistWithId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogImportServiceTest {

    @Mock
    private ArtistRepository artistRepository;

    @Mock
    private AlbumRepository albumRepository;

    @Mock
    private SongRepository songRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private CatalogImportService catalogImportService;

    @TempDir
    Path tempDir;

    // ==================== parseSongFilename tests ====================

    @Test
    void parseSongFilename_numDashPattern_parsesCorrectly() {
        var result = catalogImportService.parseSongFilename("01 - Song Title.mp3", 1);

        assertThat(result.title()).isEqualTo("Song Title");
        assertThat(result.trackNumber()).isEqualTo(1);
        assertThat(result.discNumber()).isEqualTo(1);
    }

    @Test
    void parseSongFilename_numDashWithExtraSpaces_parsesCorrectly() {
        var result = catalogImportService.parseSongFilename("03  -  Another Song.mp3", 1);

        assertThat(result.title()).isEqualTo("Another Song");
        assertThat(result.trackNumber()).isEqualTo(3);
        assertThat(result.discNumber()).isEqualTo(1);
    }

    @Test
    void parseSongFilename_numDotPattern_parsesCorrectly() {
        var result = catalogImportService.parseSongFilename("05. Fifth Song.mp3", 1);

        assertThat(result.title()).isEqualTo("Fifth Song");
        assertThat(result.trackNumber()).isEqualTo(5);
        assertThat(result.discNumber()).isEqualTo(1);
    }

    @Test
    void parseSongFilename_numDotNoSpace_parsesCorrectly() {
        var result = catalogImportService.parseSongFilename("05.Fifth Song.mp3", 1);

        assertThat(result.title()).isEqualTo("Fifth Song");
        assertThat(result.trackNumber()).isEqualTo(5);
        assertThat(result.discNumber()).isEqualTo(1);
    }

    @Test
    void parseSongFilename_numUnderscorePattern_parsesCorrectly() {
        var result = catalogImportService.parseSongFilename("07_Seventh_Song.mp3", 1);

        assertThat(result.title()).isEqualTo("Seventh Song");
        assertThat(result.trackNumber()).isEqualTo(7);
        assertThat(result.discNumber()).isEqualTo(1);
    }

    @Test
    void parseSongFilename_numSpacePattern_parsesCorrectly() {
        var result = catalogImportService.parseSongFilename("09 Ninth Song.mp3", 1);

        assertThat(result.title()).isEqualTo("Ninth Song");
        assertThat(result.trackNumber()).isEqualTo(9);
        assertThat(result.discNumber()).isEqualTo(1);
    }

    @Test
    void parseSongFilename_fallbackPattern_usesPositionalIndex() {
        var result = catalogImportService.parseSongFilename("Some Track Without Number.mp3", 5);

        assertThat(result.title()).isEqualTo("Some Track Without Number");
        assertThat(result.trackNumber()).isEqualTo(5);
        assertThat(result.discNumber()).isEqualTo(1);
    }

    @Test
    void parseSongFilename_noExtension_usesFilenameAsIs() {
        var result = catalogImportService.parseSongFilename("NoExtension", 3);

        assertThat(result.title()).isEqualTo("NoExtension");
        assertThat(result.trackNumber()).isEqualTo(3);
        assertThat(result.discNumber()).isEqualTo(1);
    }

    @Test
    void parseSongFilename_discTrack101_parsesDisc1Track1() {
        var result = catalogImportService.parseSongFilename("101 - First of Disc One.mp3", 1);

        assertThat(result.title()).isEqualTo("First of Disc One");
        assertThat(result.trackNumber()).isEqualTo(1);
        assertThat(result.discNumber()).isEqualTo(1);
    }

    @Test
    void parseSongFilename_discTrack201_parsesDisc2Track1() {
        var result = catalogImportService.parseSongFilename("201 - First of Disc Two.mp3", 1);

        assertThat(result.title()).isEqualTo("First of Disc Two");
        assertThat(result.trackNumber()).isEqualTo(1);
        assertThat(result.discNumber()).isEqualTo(2);
    }

    @Test
    void parseSongFilename_discTrack312_parsesDisc3Track12() {
        var result = catalogImportService.parseSongFilename("312 - Twelfth of Disc Three.mp3", 1);

        assertThat(result.title()).isEqualTo("Twelfth of Disc Three");
        assertThat(result.trackNumber()).isEqualTo(12);
        assertThat(result.discNumber()).isEqualTo(3);
    }

    @Test
    void parseSongFilename_artistPrefixRemoval_stripsLongArtistName() {
        var result = catalogImportService.parseSongFilename("01 - Some Artist Name - Song Title.mp3", 1);

        assertThat(result.title()).isEqualTo("Song Title");
        assertThat(result.trackNumber()).isEqualTo(1);
    }

    @Test
    void parseSongFilename_shortPrefixNotStripped_preservesDash() {
        // "A" has length 1, no space → should NOT be stripped
        var result = catalogImportService.parseSongFilename("01 - A - B.mp3", 1);

        assertThat(result.title()).isEqualTo("A - B");
    }

    @Test
    void parseSongFilename_caseInsensitiveMp3Extension() {
        var result = catalogImportService.parseSongFilename("01 - Song.MP3", 1);

        assertThat(result.title()).isEqualTo("Song");
        assertThat(result.trackNumber()).isEqualTo(1);
    }

    @Test
    void parseSongFilename_doubleDigitTrack_parsesCorrectly() {
        var result = catalogImportService.parseSongFilename("12 - Twelfth Track.mp3", 1);

        assertThat(result.title()).isEqualTo("Twelfth Track");
        assertThat(result.trackNumber()).isEqualTo(12);
        assertThat(result.discNumber()).isEqualTo(1);
    }

    // ==================== importFromJson tests ====================

    @Test
    void importFromJson_validCatalog_importsAllEntities() throws Exception {
        Path catalogFile = writeTempCatalog("""
                {
                  "scannedAt": "2026-01-01",
                  "rootPath": "/test",
                  "stats": {"totalGenres":1,"totalArtists":1,"totalAlbums":1,"totalTracks":2},
                  "warnings": [],
                  "catalog": [{
                    "genre": "Progressive Rock",
                    "artists": [{
                      "name": "TestBand",
                      "albums": [{
                        "title": "TestAlbum",
                        "year": 2020,
                        "songs": ["01 - First.mp3", "02 - Second.mp3"]
                      }]
                    }]
                  }]
                }
                """);

        var savedArtist = artistWithId(1L, "TestBand", Genre.PROGRESSIVE_ROCK);
        when(artistRepository.findByNameAndGenre("TestBand", Genre.PROGRESSIVE_ROCK)).thenReturn(Optional.empty());
        when(artistRepository.save(any(ArtistEntity.class))).thenReturn(savedArtist);
        when(albumRepository.existsByArtistIdAndTitle(anyLong(), anyString())).thenReturn(false);
        when(albumRepository.save(any(AlbumEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(songRepository.save(any(SongEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ImportResult result = catalogImportService.importFromJson(catalogFile);

        assertThat(result.artistCount()).isEqualTo(1);
        assertThat(result.albumCount()).isEqualTo(1);
        assertThat(result.songCount()).isEqualTo(2);

        verify(artistRepository).save(any(ArtistEntity.class));
        verify(albumRepository).save(any(AlbumEntity.class));
        verify(songRepository, times(2)).save(any(SongEntity.class));
    }

    @Test
    void importFromJson_duplicateAlbum_skipsExisting() throws Exception {
        Path catalogFile = writeTempCatalog("""
                {
                  "scannedAt": "2026-01-01",
                  "rootPath": "/test",
                  "stats": {"totalGenres":1,"totalArtists":1,"totalAlbums":1,"totalTracks":1},
                  "warnings": [],
                  "catalog": [{
                    "genre": "Progressive Rock",
                    "artists": [{
                      "name": "TestBand",
                      "albums": [{
                        "title": "ExistingAlbum",
                        "year": 2020,
                        "songs": ["01 - Song.mp3"]
                      }]
                    }]
                  }]
                }
                """);

        var savedArtist = artistWithId(1L, "TestBand", Genre.PROGRESSIVE_ROCK);
        when(artistRepository.findByNameAndGenre("TestBand", Genre.PROGRESSIVE_ROCK)).thenReturn(Optional.of(savedArtist));
        when(albumRepository.existsByArtistIdAndTitle(1L, "ExistingAlbum")).thenReturn(true);

        ImportResult result = catalogImportService.importFromJson(catalogFile);

        assertThat(result.albumCount()).isEqualTo(0);
        assertThat(result.songCount()).isEqualTo(0);
        verify(albumRepository, never()).save(any(AlbumEntity.class));
        verify(songRepository, never()).save(any(SongEntity.class));
    }

    @Test
    void importFromJson_existingArtist_reusesEntity() throws Exception {
        Path catalogFile = writeTempCatalog("""
                {
                  "scannedAt": "2026-01-01",
                  "rootPath": "/test",
                  "stats": {"totalGenres":1,"totalArtists":1,"totalAlbums":1,"totalTracks":1},
                  "warnings": [],
                  "catalog": [{
                    "genre": "Progressive Rock",
                    "artists": [{
                      "name": "ExistingBand",
                      "albums": [{
                        "title": "NewAlbum",
                        "year": 2023,
                        "songs": ["01 - Track.mp3"]
                      }]
                    }]
                  }]
                }
                """);

        var existingArtist = artistWithId(42L, "ExistingBand", Genre.PROGRESSIVE_ROCK);
        when(artistRepository.findByNameAndGenre("ExistingBand", Genre.PROGRESSIVE_ROCK)).thenReturn(Optional.of(existingArtist));
        when(albumRepository.existsByArtistIdAndTitle(42L, "NewAlbum")).thenReturn(false);
        when(albumRepository.save(any(AlbumEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(songRepository.save(any(SongEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        catalogImportService.importFromJson(catalogFile);

        // Artist should NOT be saved again (reused from DB)
        verify(artistRepository, never()).save(any(ArtistEntity.class));
    }

    @Test
    void importFromJson_albumWithNullYear_handlesGracefully() throws Exception {
        Path catalogFile = writeTempCatalog("""
                {
                  "scannedAt": "2026-01-01",
                  "rootPath": "/test",
                  "stats": {"totalGenres":1,"totalArtists":1,"totalAlbums":1,"totalTracks":1},
                  "warnings": [],
                  "catalog": [{
                    "genre": "Jazz & Funk",
                    "artists": [{
                      "name": "JazzMan",
                      "albums": [{
                        "title": "No Year Album",
                        "year": null,
                        "songs": ["01 - Track.mp3"]
                      }]
                    }]
                  }]
                }
                """);

        var savedArtist = artistWithId(1L, "JazzMan", Genre.JAZZ_AND_FUNK);
        when(artistRepository.findByNameAndGenre("JazzMan", Genre.JAZZ_AND_FUNK)).thenReturn(Optional.empty());
        when(artistRepository.save(any(ArtistEntity.class))).thenReturn(savedArtist);
        when(albumRepository.existsByArtistIdAndTitle(anyLong(), anyString())).thenReturn(false);

        ArgumentCaptor<AlbumEntity> albumCaptor = ArgumentCaptor.forClass(AlbumEntity.class);
        when(albumRepository.save(albumCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));
        when(songRepository.save(any(SongEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        catalogImportService.importFromJson(catalogFile);

        assertThat(albumCaptor.getValue().getYear()).isNull();
    }

    @Test
    void importFromJson_invalidJson_throwsException() {
        Path catalogFile = tempDir.resolve("invalid.json");
        try {
            Files.writeString(catalogFile, "not valid json {{{");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        assertThatThrownBy(() -> catalogImportService.importFromJson(catalogFile))
                .isInstanceOf(Exception.class);
    }

    @Test
    void importFromJson_songsHaveCorrectTrackAndTitle() throws Exception {
        Path catalogFile = writeTempCatalog("""
                {
                  "scannedAt": "2026-01-01",
                  "rootPath": "/test",
                  "stats": {"totalGenres":1,"totalArtists":1,"totalAlbums":1,"totalTracks":3},
                  "warnings": [],
                  "catalog": [{
                    "genre": "Progressive Rock",
                    "artists": [{
                      "name": "Band",
                      "albums": [{
                        "title": "Album",
                        "year": 2020,
                        "songs": [
                          "01 - First Song.mp3",
                          "02. Second Song.mp3",
                          "03_Third_Song.mp3"
                        ]
                      }]
                    }]
                  }]
                }
                """);

        var savedArtist = artistWithId(1L, "Band", Genre.PROGRESSIVE_ROCK);
        when(artistRepository.findByNameAndGenre("Band", Genre.PROGRESSIVE_ROCK)).thenReturn(Optional.empty());
        when(artistRepository.save(any(ArtistEntity.class))).thenReturn(savedArtist);
        when(albumRepository.existsByArtistIdAndTitle(anyLong(), anyString())).thenReturn(false);
        when(albumRepository.save(any(AlbumEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<SongEntity> songCaptor = ArgumentCaptor.forClass(SongEntity.class);
        when(songRepository.save(songCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        catalogImportService.importFromJson(catalogFile);

        var songs = songCaptor.getAllValues();
        assertThat(songs).hasSize(3);

        assertThat(songs.get(0).getTitle()).isEqualTo("First Song");
        assertThat(songs.get(0).getTrackNumber()).isEqualTo(1);

        assertThat(songs.get(1).getTitle()).isEqualTo("Second Song");
        assertThat(songs.get(1).getTrackNumber()).isEqualTo(2);

        assertThat(songs.get(2).getTitle()).isEqualTo("Third Song");
        assertThat(songs.get(2).getTrackNumber()).isEqualTo(3);
    }

    private Path writeTempCatalog(String json) {
        try {
            Path file = tempDir.resolve("catalog.json");
            Files.writeString(file, json);
            return file;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
