package io.github.alexshamrai.service;

import io.github.alexshamrai.domain.AlbumEntity;
import io.github.alexshamrai.domain.ArtistEntity;
import io.github.alexshamrai.domain.Genre;
import io.github.alexshamrai.domain.SongEntity;
import io.github.alexshamrai.dto.SyncResultDto;
import io.github.alexshamrai.repository.AlbumRepository;
import io.github.alexshamrai.repository.ArtistRepository;
import io.github.alexshamrai.repository.SongRepository;
import io.github.alexshamrai.sheets.SheetsClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static io.github.alexshamrai.TestDataFactory.albumWithId;
import static io.github.alexshamrai.TestDataFactory.artistWithId;
import static io.github.alexshamrai.TestDataFactory.songWithId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SheetSyncServiceTest {

    @Mock
    private SheetsClient sheetsClient;

    @Mock
    private ArtistRepository artistRepository;

    @Mock
    private AlbumRepository albumRepository;

    @Mock
    private SongRepository songRepository;

    @InjectMocks
    private SheetSyncService sheetSyncService;

    private ArtistEntity artist;
    private AlbumEntity album;
    private SongEntity song;

    @BeforeEach
    void setUp() {
        artist = artistWithId(1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        album = albumWithId(1L, "Kind of Blue", 1959, artist);
        song = songWithId(1L, "So What", 1, 1, album);
        artist.getAlbums().add(album);
        album.getSongs().add(song);
    }

    // ==================== pushCatalog(false) — non-structural ====================

    @Test
    void pushCatalog_nonStructural_writesArtistsAndAlbumsButNotSongs() {
        when(artistRepository.findAllForSync()).thenReturn(List.of(artist));
        when(albumRepository.findAllForSync()).thenReturn(List.of(album));

        sheetSyncService.pushCatalog(false);

        verify(sheetsClient).overwrite(eq("Artists"), any());
        verify(sheetsClient).overwrite(eq("Albums"), any());
        verify(sheetsClient, never()).overwrite(eq("Songs"), any());
    }

    @Test
    void pushCatalog_nonStructural_artistRowsHaveHeaderFirst() {
        when(artistRepository.findAllForSync()).thenReturn(List.of(artist));
        when(albumRepository.findAllForSync()).thenReturn(List.of(album));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<List<Object>>> captor = ArgumentCaptor.forClass(List.class);

        sheetSyncService.pushCatalog(false);

        verify(sheetsClient).overwrite(eq("Artists"), captor.capture());
        List<List<Object>> rows = captor.getValue();

        assertThat(rows).hasSize(2); // 1 header + 1 data row
        assertThat(rows.get(0)).containsExactly("name", "genre", "subgenre", "favorite", "tags");
    }

    @Test
    void pushCatalog_nonStructural_albumRowsHaveHeaderFirst() {
        when(artistRepository.findAllForSync()).thenReturn(List.of(artist));
        when(albumRepository.findAllForSync()).thenReturn(List.of(album));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<List<Object>>> captor = ArgumentCaptor.forClass(List.class);

        sheetSyncService.pushCatalog(false);

        verify(sheetsClient).overwrite(eq("Albums"), captor.capture());
        List<List<Object>> rows = captor.getValue();

        assertThat(rows).hasSize(2); // 1 header + 1 data row
        assertThat(rows.get(0)).containsExactly("artist", "title", "year", "grade", "favorite", "tags");
    }

    // ==================== pushCatalog(true) — structural ====================

    @Test
    void pushCatalog_structural_writesArtistsAlbumsAndSongs() {
        when(artistRepository.findAllForSync()).thenReturn(List.of(artist));
        when(albumRepository.findAllForSync()).thenReturn(List.of(album));
        when(songRepository.findAllForSync()).thenReturn(List.of(song));

        sheetSyncService.pushCatalog(true);

        verify(sheetsClient).overwrite(eq("Artists"), any());
        verify(sheetsClient).overwrite(eq("Albums"), any());
        verify(sheetsClient).overwrite(eq("Songs"), any());
    }

    @Test
    void pushCatalog_structural_songRowsHaveHeaderFirst() {
        when(artistRepository.findAllForSync()).thenReturn(List.of(artist));
        when(albumRepository.findAllForSync()).thenReturn(List.of(album));
        when(songRepository.findAllForSync()).thenReturn(List.of(song));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<List<Object>>> captor = ArgumentCaptor.forClass(List.class);

        sheetSyncService.pushCatalog(true);

        verify(sheetsClient).overwrite(eq("Songs"), captor.capture());
        List<List<Object>> rows = captor.getValue();

        assertThat(rows).hasSize(2); // 1 header + 1 data row
        assertThat(rows.get(0)).containsExactly("artist", "album", "disc", "track", "title");
    }

    @Test
    void pushCatalog_structural_songCountInResult() {
        when(artistRepository.findAllForSync()).thenReturn(List.of(artist));
        when(albumRepository.findAllForSync()).thenReturn(List.of(album));
        when(songRepository.findAllForSync()).thenReturn(List.of(song));

        SyncResultDto result = sheetSyncService.pushCatalog(true);

        assertThat(result.songCount()).isEqualTo(1);
        assertThat(result.artistCount()).isEqualTo(1);
        assertThat(result.albumCount()).isEqualTo(1);
        assertThat(result.syncedAt()).isNotNull();
    }

    // ==================== Sorting ====================

    @Test
    void pushCatalog_artistsSortedByGenreDisplayNameThenName() {
        var jazzArtist = artistWithId(1L, "B-Artist", Genre.JAZZ_AND_FUNK);
        var bluesArtist1 = artistWithId(2L, "C-Artist", Genre.BLUES);
        var bluesArtist2 = artistWithId(3L, "A-Artist", Genre.BLUES);
        // Blues (displayName "Blues") sorts before Jazz & Funk, within Blues: A before C

        when(artistRepository.findAllForSync()).thenReturn(List.of(jazzArtist, bluesArtist1, bluesArtist2));
        when(albumRepository.findAllForSync()).thenReturn(List.of());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<List<Object>>> captor = ArgumentCaptor.forClass(List.class);

        sheetSyncService.pushCatalog(false);

        verify(sheetsClient).overwrite(eq("Artists"), captor.capture());
        List<List<Object>> rows = captor.getValue();

        // row 0 = header, then data rows
        // Blues < Jazz & Funk; within Blues: A-Artist < C-Artist
        assertThat(rows.get(1).get(0)).isEqualTo("A-Artist");
        assertThat(rows.get(2).get(0)).isEqualTo("C-Artist");
        assertThat(rows.get(3).get(0)).isEqualTo("B-Artist");
    }

    @Test
    void pushCatalog_albumsSortedByArtistNameThenYearNullLastThenTitle() {
        var artist1 = artistWithId(1L, "A-Artist", Genre.BLUES);
        var artist2 = artistWithId(2L, "B-Artist", Genre.JAZZ_AND_FUNK);
        var album1 = albumWithId(1L, "Z-Album", null, artist1);       // null year = last
        var album2 = albumWithId(2L, "Early Album", 1960, artist1);   // artist1, year 1960
        var album3 = albumWithId(3L, "Late Album", 1970, artist1);    // artist1, year 1970
        var album4 = albumWithId(4L, "Another Album", 1980, artist2); // artist2

        when(artistRepository.findAllForSync()).thenReturn(List.of(artist1, artist2));
        when(albumRepository.findAllForSync()).thenReturn(List.of(album1, album3, album4, album2));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<List<Object>>> captor = ArgumentCaptor.forClass(List.class);

        sheetSyncService.pushCatalog(false);

        verify(sheetsClient).overwrite(eq("Albums"), captor.capture());
        List<List<Object>> rows = captor.getValue();

        // Skip header at index 0
        // Expected order: A-Artist/1960, A-Artist/1970, A-Artist/null, B-Artist/1980
        assertThat(rows.get(1).get(1)).isEqualTo("Early Album");
        assertThat(rows.get(2).get(1)).isEqualTo("Late Album");
        assertThat(rows.get(3).get(1)).isEqualTo("Z-Album");
        assertThat(rows.get(4).get(1)).isEqualTo("Another Album");
    }

    // ==================== Failure and self-heal ====================

    @Test
    void pushCatalog_sheetsClientThrows_setsLastError() {
        when(artistRepository.findAllForSync()).thenReturn(List.of(artist));
        when(albumRepository.findAllForSync()).thenThrow(new RuntimeException("Sheets API error"));

        assertThatThrownBy(() -> sheetSyncService.pushCatalog(false))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Sheets API error");

        assertThat(sheetSyncService.getStatus().lastError()).isEqualTo("Sheets API error");
    }

    @Test
    void pushCatalog_structuralFailure_marksSongsDirty() {
        when(artistRepository.findAllForSync()).thenReturn(List.of(artist));
        when(albumRepository.findAllForSync()).thenReturn(List.of(album));
        when(songRepository.findAllForSync()).thenThrow(new RuntimeException("Sheets songs error"));

        assertThatThrownBy(() -> sheetSyncService.pushCatalog(true))
                .isInstanceOf(RuntimeException.class);

        assertThat(sheetSyncService.getStatus().dirty()).isTrue();
    }

    @Test
    void pushCatalog_afterStructuralFailure_nonStructuralPushSelfHealsWithSongs() {
        when(artistRepository.findAllForSync()).thenReturn(List.of(artist));
        when(albumRepository.findAllForSync()).thenReturn(List.of(album));
        // First call (structural push) throws on Songs; second call (non-structural self-heal) succeeds
        when(songRepository.findAllForSync())
                .thenThrow(new RuntimeException("Sheets songs error"))
                .thenReturn(List.of(song));

        // First push: structural, fails on Songs
        assertThatThrownBy(() -> sheetSyncService.pushCatalog(true));

        // Second push: non-structural, but songsDirty=true → should still write Songs
        sheetSyncService.pushCatalog(false);

        verify(sheetsClient, times(1)).overwrite(eq("Songs"), any());
        assertThat(sheetSyncService.getStatus().dirty()).isFalse();
    }

    @Test
    void pushCatalog_success_clearsLastError() {
        when(artistRepository.findAllForSync()).thenReturn(List.of(artist));
        // First call throws, second call succeeds (chained stubbing)
        when(albumRepository.findAllForSync())
                .thenThrow(new RuntimeException("temporary error"))
                .thenReturn(List.of(album));

        assertThatThrownBy(() -> sheetSyncService.pushCatalog(false));
        assertThat(sheetSyncService.getStatus().lastError()).isNotNull();

        sheetSyncService.pushCatalog(false);

        assertThat(sheetSyncService.getStatus().lastError()).isNull();
        assertThat(sheetSyncService.getStatus().lastPushAt()).isNotNull();
    }
}
