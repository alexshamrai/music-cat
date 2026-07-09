package io.github.alexshamrai.service;

import io.github.alexshamrai.domain.AlbumEntity;
import io.github.alexshamrai.domain.ArtistEntity;
import io.github.alexshamrai.domain.Genre;
import io.github.alexshamrai.domain.SongEntity;
import io.github.alexshamrai.domain.TagEntity;
import io.github.alexshamrai.dto.AlbumDto;
import io.github.alexshamrai.dto.AlbumEditDto;
import io.github.alexshamrai.dto.AlbumFilterParams;
import io.github.alexshamrai.dto.AlbumSummaryDto;
import io.github.alexshamrai.dto.SongDto;
import io.github.alexshamrai.dto.SongEditInput;
import io.github.alexshamrai.event.CatalogChangedEvent;
import io.github.alexshamrai.exception.NotFoundException;
import io.github.alexshamrai.repository.AlbumRepository;
import io.github.alexshamrai.repository.ArtistRepository;
import io.github.alexshamrai.repository.TagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static io.github.alexshamrai.TestDataFactory.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlbumServiceTest {

    @Mock
    private AlbumRepository albumRepository;

    @Mock
    private ArtistRepository artistRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AlbumService albumService;

    // ==================== findAll tests ====================

    @Test
    void findAll_noFilters_returnsAllAlbums() {
        var artist = artistWithId(1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        var album1 = albumWithId(1L, "Kind of Blue", 1959, artist);
        var album2 = albumWithId(2L, "Bitches Brew", 1970, artist);
        when(albumRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(album1, album2));

        List<AlbumSummaryDto> result = albumService.findAll(new AlbumFilterParams());

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTitle()).isEqualTo("Kind of Blue");
        assertThat(result.get(1).getTitle()).isEqualTo("Bitches Brew");
    }

    @Test
    void findAll_emptyResult_returnsEmptyList() {
        when(albumRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of());

        List<AlbumSummaryDto> result = albumService.findAll(new AlbumFilterParams());

        assertThat(result).isEmpty();
    }

    @Test
    void findAll_nullFilters_returnsAllAlbums() {
        when(albumRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of());

        List<AlbumSummaryDto> result = albumService.findAll(null);

        assertThat(result).isEmpty();
    }

    @Test
    void findAll_summaryIncludesArtistInfo() {
        var artist = artistWithId(1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        var album = albumWithId(1L, "Kind of Blue", 1959, artist);
        when(albumRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(album));

        List<AlbumSummaryDto> result = albumService.findAll(new AlbumFilterParams());

        assertThat(result.get(0).getArtistName()).isEqualTo("Miles Davis");
        assertThat(result.get(0).getGenre()).isEqualTo(Genre.JAZZ_AND_FUNK);
    }

    // ==================== findById tests ====================

    @Test
    void findById_existingId_returnsAlbumDtoWithSongs() {
        var artist = artistWithId(1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        var album = albumWithId(1L, "Kind of Blue", 1959, artist);
        album.setGrade(5);
        album.setFavorite(true);
        album.setTags(new HashSet<>(Set.of(tagWithId(1L, "masterpiece"))));

        var song1 = songWithId(1L, "So What", 1, 1, album);
        var song2 = songWithId(2L, "Freddie Freeloader", 2, 1, album);
        album.setSongs(new ArrayList<>(List.of(song2, song1))); // unsorted to verify sorting

        when(albumRepository.findById(1L)).thenReturn(Optional.of(album));

        AlbumDto result = albumService.findById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTitle()).isEqualTo("Kind of Blue");
        assertThat(result.getYear()).isEqualTo(1959);
        assertThat(result.getGrade()).isEqualTo(5);
        assertThat(result.isFavorite()).isTrue();
        assertThat(result.getArtist().getId()).isEqualTo(1L);
        assertThat(result.getArtist().getName()).isEqualTo("Miles Davis");
        assertThat(result.getArtist().getGenre()).isEqualTo(Genre.JAZZ_AND_FUNK);
        assertThat(result.getTags()).containsExactly("masterpiece");
        assertThat(result.getSongs()).hasSize(2);
        assertThat(result.getSongs().get(0).getTitle()).isEqualTo("So What"); // sorted by track
        assertThat(result.getSongs().get(1).getTitle()).isEqualTo("Freddie Freeloader");
    }

    @Test
    void findById_nonExistentId_throwsNotFoundException() {
        when(albumRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> albumService.findById(999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("999");
    }

    // ==================== create tests ====================

    @Test
    void create_validDto_savesAndReturnsSummary() {
        var artist = artistWithId(1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        when(artistRepository.findById(1L)).thenReturn(Optional.of(artist));

        ArgumentCaptor<AlbumEntity> captor = ArgumentCaptor.forClass(AlbumEntity.class);
        when(albumRepository.save(captor.capture())).thenAnswer(inv -> {
            AlbumEntity saved = inv.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        AlbumSummaryDto result = albumService.create(albumCreateDto("Kind of Blue", 1959, 1L));

        assertThat(result.getTitle()).isEqualTo("Kind of Blue");
        assertThat(result.getYear()).isEqualTo(1959);
        assertThat(result.getArtistName()).isEqualTo("Miles Davis");

        AlbumEntity captured = captor.getValue();
        assertThat(captured.getTitle()).isEqualTo("Kind of Blue");
        assertThat(captured.getYear()).isEqualTo(1959);
        assertThat(captured.getArtist().getId()).isEqualTo(1L);
    }

    @Test
    void create_nonExistentArtist_throwsNotFoundException() {
        when(artistRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> albumService.create(albumCreateDto("Album", 2020, 999L)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    void create_titleCollidesWithSiblingAlbum_throwsIllegalArgument() {
        var artist = artistWithId(1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        when(artistRepository.findById(1L)).thenReturn(Optional.of(artist));
        when(albumRepository.existsByArtistIdAndTitle(1L, "Kind of Blue")).thenReturn(true);

        assertThatThrownBy(() -> albumService.create(albumCreateDto("Kind of Blue", 1959, 1L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Kind of Blue");

        verify(albumRepository, never()).save(any(AlbumEntity.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void create_nullYear_savesWithNullYear() {
        var artist = artistWithId(1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        when(artistRepository.findById(1L)).thenReturn(Optional.of(artist));
        when(albumRepository.save(any(AlbumEntity.class))).thenAnswer(inv -> {
            AlbumEntity saved = inv.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        AlbumSummaryDto result = albumService.create(albumCreateDto("Unknown Album", null, 1L));

        assertThat(result.getYear()).isNull();
    }

    // ==================== update tests ====================

    @Test
    void update_onlyTitle_updatesOnlyTitle() {
        var artist = artistWithId(1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        var album = albumWithId(1L, "Old Title", 1959, artist);
        when(albumRepository.findById(1L)).thenReturn(Optional.of(album));
        when(albumRepository.save(any(AlbumEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AlbumSummaryDto result = albumService.update(1L, albumUpdateDto("New Title", null));

        assertThat(result.getTitle()).isEqualTo("New Title");
        assertThat(result.getYear()).isEqualTo(1959); // unchanged
    }

    @Test
    void update_allFields_updatesAll() {
        var artist = artistWithId(1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        var album = albumWithId(1L, "Old", 1959, artist);
        when(albumRepository.findById(1L)).thenReturn(Optional.of(album));
        when(albumRepository.save(any(AlbumEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AlbumSummaryDto result = albumService.update(1L, albumUpdateDto("New Title", 1970));

        assertThat(result.getTitle()).isEqualTo("New Title");
        assertThat(result.getYear()).isEqualTo(1970);
    }

    @Test
    void update_nonExistentId_throwsNotFoundException() {
        when(albumRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> albumService.update(999L, albumUpdateDto("X", null)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("999");
    }

    // ==================== update collision guard ====================

    @Test
    void update_titleCollidesWithSiblingAlbum_throwsIllegalArgument() {
        var artist = artistWithId(1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        var album = albumWithId(1L, "Old Title", 1959, artist);
        when(albumRepository.findById(1L)).thenReturn(Optional.of(album));
        when(albumRepository.existsByArtistIdAndTitle(1L, "Bitches Brew")).thenReturn(true);

        assertThatThrownBy(() -> albumService.update(1L, albumUpdateDto("Bitches Brew", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bitches Brew");

        verify(albumRepository, never()).save(any(AlbumEntity.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void update_titleUnchanged_skipsCollisionCheck() {
        var artist = artistWithId(1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        var album = albumWithId(1L, "Kind of Blue", 1959, artist);
        when(albumRepository.findById(1L)).thenReturn(Optional.of(album));
        when(albumRepository.save(any(AlbumEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        // same title, only year changes — collision check must not fire
        AlbumSummaryDto result = albumService.update(1L, albumUpdateDto("Kind of Blue", 1960));

        assertThat(result.getYear()).isEqualTo(1960);
        verify(albumRepository, never()).existsByArtistIdAndTitle(any(), any());
    }

    // ==================== edit (batch reconcile) tests ====================

    private static AlbumEditDto editDto(String title, Integer year, SongEditInput... songs) {
        return new AlbumEditDto(title, year, new ArrayList<>(List.of(songs)));
    }

    private static SongDto songByTitle(AlbumDto album, String title) {
        return album.getSongs().stream()
                .filter(s -> s.getTitle().equals(title))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no song titled '" + title + "'"));
    }

    @Test
    void edit_renamesExistingSong_preservingTrackAndDisc() {
        var artist = artistWithId(1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        var album = albumWithId(1L, "Kind of Blue", 1959, artist);
        var s1 = songWithId(1L, "So What", 1, 1, album);
        var s2 = songWithId(2L, "Flamenco Sketches", 5, 1, album);
        album.setSongs(new ArrayList<>(List.of(s1, s2)));
        when(albumRepository.findById(1L)).thenReturn(Optional.of(album));
        when(albumRepository.save(any(AlbumEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AlbumDto result = albumService.edit(1L, editDto("Kind of Blue", 1959,
                new SongEditInput(1L, "So What (Take 1)"),
                new SongEditInput(2L, "Flamenco Sketches")));

        assertThat(result.getSongs()).hasSize(2);
        assertThat(songByTitle(result, "So What (Take 1)").getTrackNumber()).isEqualTo(1);
        assertThat(s1.getTitle()).isEqualTo("So What (Take 1)");
        assertThat(s1.getTrackNumber()).isEqualTo(1);
        assertThat(s2.getTrackNumber()).isEqualTo(5); // untouched
        verify(eventPublisher).publishEvent(new CatalogChangedEvent(true));
    }

    @Test
    void edit_addsNewSong_autoNumberedAfterLastDiscOneTrack() {
        var artist = artistWithId(1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        var album = albumWithId(1L, "Kind of Blue", 1959, artist);
        var s1 = songWithId(1L, "So What", 1, 1, album);
        var s2 = songWithId(2L, "Blue in Green", 3, 1, album);
        album.setSongs(new ArrayList<>(List.of(s1, s2)));
        when(albumRepository.findById(1L)).thenReturn(Optional.of(album));
        when(albumRepository.save(any(AlbumEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AlbumDto result = albumService.edit(1L, editDto("Kind of Blue", 1959,
                new SongEditInput(1L, "So What"),
                new SongEditInput(2L, "Blue in Green"),
                new SongEditInput(null, "Bonus Track")));

        assertThat(result.getSongs()).hasSize(3);
        SongDto bonus = songByTitle(result, "Bonus Track");
        assertThat(bonus.getTrackNumber()).isEqualTo(4); // max disc-1 track (3) + 1
        assertThat(bonus.getDiscNumber()).isEqualTo(1);
        verify(eventPublisher).publishEvent(new CatalogChangedEvent(true));
    }

    @Test
    void edit_addToEmptyAlbum_getsTrackOne() {
        var artist = artistWithId(1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        var album = albumWithId(1L, "Empty", 1959, artist);
        when(albumRepository.findById(1L)).thenReturn(Optional.of(album));
        when(albumRepository.save(any(AlbumEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AlbumDto result = albumService.edit(1L, editDto("Empty", 1959,
                new SongEditInput(null, "First Song")));

        assertThat(result.getSongs()).hasSize(1);
        assertThat(result.getSongs().get(0).getTrackNumber()).isEqualTo(1);
        assertThat(result.getSongs().get(0).getDiscNumber()).isEqualTo(1);
    }

    @Test
    void edit_omittedSongIsDeleted() {
        var artist = artistWithId(1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        var album = albumWithId(1L, "Kind of Blue", 1959, artist);
        var s1 = songWithId(1L, "So What", 1, 1, album);
        var s2 = songWithId(2L, "Freddie Freeloader", 2, 1, album);
        album.setSongs(new ArrayList<>(List.of(s1, s2)));
        when(albumRepository.findById(1L)).thenReturn(Optional.of(album));
        when(albumRepository.save(any(AlbumEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        // send only s1 — s2 should be removed via orphanRemoval
        AlbumDto result = albumService.edit(1L, editDto("Kind of Blue", 1959,
                new SongEditInput(1L, "So What")));

        assertThat(result.getSongs()).hasSize(1);
        assertThat(result.getSongs().get(0).getTitle()).isEqualTo("So What");
        assertThat(album.getSongs()).extracting(s -> s.getId()).containsExactly(1L);
        verify(eventPublisher).publishEvent(new CatalogChangedEvent(true));
    }

    @Test
    void edit_updatesAlbumTitleAndYear() {
        var artist = artistWithId(1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        var album = albumWithId(1L, "Old Title", 1959, artist);
        when(albumRepository.findById(1L)).thenReturn(Optional.of(album));
        when(albumRepository.save(any(AlbumEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AlbumDto result = albumService.edit(1L, editDto("New Title", 1970));

        assertThat(result.getTitle()).isEqualTo("New Title");
        assertThat(result.getYear()).isEqualTo(1970);
    }

    @Test
    void edit_clearsYearWhenNull() {
        var artist = artistWithId(1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        var album = albumWithId(1L, "Kind of Blue", 1959, artist);
        when(albumRepository.findById(1L)).thenReturn(Optional.of(album));
        when(albumRepository.save(any(AlbumEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AlbumDto result = albumService.edit(1L, editDto("Kind of Blue", null));

        assertThat(result.getYear()).isNull();
    }

    @Test
    void edit_titleCollidesWithSiblingAlbum_throwsIllegalArgument() {
        var artist = artistWithId(1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        var album = albumWithId(1L, "Kind of Blue", 1959, artist);
        when(albumRepository.findById(1L)).thenReturn(Optional.of(album));
        when(albumRepository.existsByArtistIdAndTitle(1L, "Bitches Brew")).thenReturn(true);

        assertThatThrownBy(() -> albumService.edit(1L, editDto("Bitches Brew", 1959)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bitches Brew");

        verify(albumRepository, never()).save(any(AlbumEntity.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void edit_songIdNotBelongingToAlbum_throwsIllegalArgument() {
        var artist = artistWithId(1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        var album = albumWithId(1L, "Kind of Blue", 1959, artist);
        var s1 = songWithId(1L, "So What", 1, 1, album);
        album.setSongs(new ArrayList<>(List.of(s1)));
        when(albumRepository.findById(1L)).thenReturn(Optional.of(album));

        assertThatThrownBy(() -> albumService.edit(1L, editDto("Kind of Blue", 1959,
                new SongEditInput(99L, "Ghost Song"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");

        verify(albumRepository, never()).save(any(AlbumEntity.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void edit_nonExistentAlbum_throwsNotFound() {
        when(albumRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> albumService.edit(999L, editDto("X", null)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    void edit_publishesExactlyOneStructuralEvent() {
        var artist = artistWithId(1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        var album = albumWithId(1L, "Kind of Blue", 1959, artist);
        var s1 = songWithId(1L, "So What", 1, 1, album);
        album.setSongs(new ArrayList<>(List.of(s1)));
        when(albumRepository.findById(1L)).thenReturn(Optional.of(album));
        when(albumRepository.save(any(AlbumEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        // rename album + rename song + add + delete in one call
        albumService.edit(1L, editDto("Renamed", 1959,
                new SongEditInput(null, "Added")));

        verify(eventPublisher, times(1)).publishEvent(new CatalogChangedEvent(true));
    }

    // ==================== delete tests ====================

    @Test
    void delete_existingId_deletesSuccessfully() {
        var artist = artistWithId(1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        var album = albumWithId(1L, "Kind of Blue", 1959, artist);
        when(albumRepository.findById(1L)).thenReturn(Optional.of(album));

        albumService.delete(1L);

        verify(albumRepository).delete(album);
    }

    @Test
    void delete_nonExistentId_throwsNotFoundException() {
        when(albumRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> albumService.delete(999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("999");

        verify(albumRepository, never()).delete(any(AlbumEntity.class));
    }

    // ==================== setGrade tests ====================

    @Test
    void setGrade_validGrade_setsGrade() {
        var artist = artistWithId(1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        var album = albumWithId(1L, "Kind of Blue", 1959, artist);
        when(albumRepository.findById(1L)).thenReturn(Optional.of(album));
        when(albumRepository.save(any(AlbumEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AlbumSummaryDto result = albumService.setGrade(1L, 4);

        assertThat(result.getGrade()).isEqualTo(4);
    }

    @Test
    void setGrade_nonExistentId_throwsNotFoundException() {
        when(albumRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> albumService.setGrade(999L, 3))
                .isInstanceOf(NotFoundException.class);
    }

    // ==================== toggleFavorite tests ====================

    @Test
    void toggleFavorite_currentlyFalse_setsTrue() {
        var artist = artistWithId(1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        var album = albumWithId(1L, "Kind of Blue", 1959, artist);
        album.setFavorite(false);
        when(albumRepository.findById(1L)).thenReturn(Optional.of(album));
        when(albumRepository.save(any(AlbumEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AlbumSummaryDto result = albumService.toggleFavorite(1L);

        assertThat(result.isFavorite()).isTrue();
    }

    @Test
    void toggleFavorite_currentlyTrue_setsFalse() {
        var artist = artistWithId(1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        var album = albumWithId(1L, "Kind of Blue", 1959, artist);
        album.setFavorite(true);
        when(albumRepository.findById(1L)).thenReturn(Optional.of(album));
        when(albumRepository.save(any(AlbumEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AlbumSummaryDto result = albumService.toggleFavorite(1L);

        assertThat(result.isFavorite()).isFalse();
    }

    @Test
    void toggleFavorite_nonExistentId_throwsNotFoundException() {
        when(albumRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> albumService.toggleFavorite(999L))
                .isInstanceOf(NotFoundException.class);
    }

    // ==================== setTags tests ====================

    @Test
    void setTags_existingTags_assignsWithoutCreating() {
        var artist = artistWithId(1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        var album = albumWithId(1L, "Kind of Blue", 1959, artist);
        var jazzTag = tagWithId(10L, "jazz");
        var classicTag = tagWithId(11L, "classic");

        when(albumRepository.findById(1L)).thenReturn(Optional.of(album));
        when(tagRepository.findByName("jazz")).thenReturn(Optional.of(jazzTag));
        when(tagRepository.findByName("classic")).thenReturn(Optional.of(classicTag));
        when(albumRepository.save(any(AlbumEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AlbumSummaryDto result = albumService.setTags(1L, List.of("jazz", "classic"));

        assertThat(result.getTags()).containsExactly("classic", "jazz"); // sorted
        verify(tagRepository, never()).save(any(TagEntity.class));
    }

    @Test
    void setTags_newTags_createsAndAssigns() {
        var artist = artistWithId(1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        var album = albumWithId(1L, "Kind of Blue", 1959, artist);

        when(albumRepository.findById(1L)).thenReturn(Optional.of(album));
        when(tagRepository.findByName("newtag")).thenReturn(Optional.empty());
        when(tagRepository.save(any(TagEntity.class))).thenAnswer(inv -> {
            TagEntity tag = inv.getArgument(0);
            tag.setId(100L);
            return tag;
        });
        when(albumRepository.save(any(AlbumEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AlbumSummaryDto result = albumService.setTags(1L, List.of("newtag"));

        assertThat(result.getTags()).containsExactly("newtag");
        verify(tagRepository).save(any(TagEntity.class));
    }

    @Test
    void setTags_emptyList_clearsAllTags() {
        var artist = artistWithId(1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        var album = albumWithTags(1L, "Kind of Blue", 1959, artist,
                new HashSet<>(Set.of(tagWithId(1L, "old"))));

        when(albumRepository.findById(1L)).thenReturn(Optional.of(album));
        when(albumRepository.save(any(AlbumEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AlbumSummaryDto result = albumService.setTags(1L, List.of());

        assertThat(result.getTags()).isEmpty();
    }

    @Test
    void setTags_nonExistentAlbum_throwsNotFoundException() {
        when(albumRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> albumService.setTags(999L, List.of("tag")))
                .isInstanceOf(NotFoundException.class);
    }

    // ==================== DTO mapping tests ====================

    @Test
    void toSummaryDto_includesSongCount() {
        var artist = artistWithId(1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        var album = albumWithId(1L, "Kind of Blue", 1959, artist);
        album.getSongs().add(songWithId(1L, "So What", 1, 1, album));
        album.getSongs().add(songWithId(2L, "Freddie Freeloader", 2, 1, album));
        when(albumRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(album));

        List<AlbumSummaryDto> result = albumService.findAll(new AlbumFilterParams());

        assertThat(result.get(0).getSongCount()).isEqualTo(2);
    }

    @Test
    void toDto_songsSortedByDiscThenTrack() {
        var artist = artistWithId(1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        var album = albumWithId(1L, "Kind of Blue", 1959, artist);
        var song1 = songWithId(1L, "Track 1 Disc 2", 1, 2, album);
        var song2 = songWithId(2L, "Track 2 Disc 1", 2, 1, album);
        var song3 = songWithId(3L, "Track 1 Disc 1", 1, 1, album);
        album.setSongs(new ArrayList<>(List.of(song1, song2, song3)));

        when(albumRepository.findById(1L)).thenReturn(Optional.of(album));

        AlbumDto result = albumService.findById(1L);

        assertThat(result.getSongs()).hasSize(3);
        assertThat(result.getSongs().get(0).getTitle()).isEqualTo("Track 1 Disc 1");
        assertThat(result.getSongs().get(1).getTitle()).isEqualTo("Track 2 Disc 1");
        assertThat(result.getSongs().get(2).getTitle()).isEqualTo("Track 1 Disc 2");
    }

    @Test
    void toSummaryDto_tagsSortedAlphabetically() {
        var artist = artistWithId(1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        var album = albumWithTags(1L, "Kind of Blue", 1959, artist,
                new HashSet<>(Set.of(tagWithId(1L, "zebra"), tagWithId(2L, "alpha"), tagWithId(3L, "middle"))));
        when(albumRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(album));

        List<AlbumSummaryDto> result = albumService.findAll(new AlbumFilterParams());

        assertThat(result.get(0).getTags()).containsExactly("alpha", "middle", "zebra");
    }
}
