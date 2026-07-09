package io.github.alexshamrai.service;

import io.github.alexshamrai.domain.ArtistEntity;
import io.github.alexshamrai.domain.Genre;
import io.github.alexshamrai.domain.TagEntity;
import io.github.alexshamrai.dto.ArtistCreateDto;
import io.github.alexshamrai.dto.ArtistDto;
import io.github.alexshamrai.dto.ArtistUpdateDto;
import io.github.alexshamrai.exception.NotFoundException;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArtistServiceTest {

    @Mock
    private ArtistRepository artistRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ArtistService artistService;

    // ==================== findAll tests ====================

    @Test
    void findAll_noFilters_returnsAllArtists() {
        var artist1 = artistWithId(1L, "Artist One", Genre.PROGRESSIVE_ROCK);
        var artist2 = artistWithId(2L, "Artist Two", Genre.JAZZ_AND_FUNK);
        when(artistRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(artist1, artist2));

        List<ArtistDto> result = artistService.findAll(null, null, null, null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Artist One");
        assertThat(result.get(1).getName()).isEqualTo("Artist Two");
    }

    @Test
    void findAll_emptyResult_returnsEmptyList() {
        when(artistRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of());

        List<ArtistDto> result = artistService.findAll(null, null, null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void findAll_withGenreFilter_callsRepository() {
        when(artistRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of());

        artistService.findAll(Genre.PROGRESSIVE_ROCK, null, null, null);

        verify(artistRepository).findAll(any(org.springframework.data.jpa.domain.Specification.class));
    }

    @Test
    void findAll_withFavoriteFalse_doesNotApplyFavoriteFilter() {
        when(artistRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of());

        // Boolean.FALSE should not trigger the isFavorite filter
        artistService.findAll(null, null, false, null);

        verify(artistRepository).findAll(any(org.springframework.data.jpa.domain.Specification.class));
    }

    // ==================== findById tests ====================

    @Test
    void findById_existingId_returnsArtistDto() {
        var tags = new HashSet<>(Set.of(tagWithId(1L, "zebra"), tagWithId(2L, "alpha")));
        var albums = new ArrayList<>(List.of(album("Album1", 2020, null)));
        var artist = ArtistEntity.builder()
                .id(1L)
                .name("Test Artist")
                .genre(Genre.PROGRESSIVE_ROCK)
                .subgenre("Indie")
                .isFavorite(true)
                .tags(tags)
                .albums(albums)
                .build();
        when(artistRepository.findById(1L)).thenReturn(Optional.of(artist));

        ArtistDto result = artistService.findById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Test Artist");
        assertThat(result.getGenre()).isEqualTo(Genre.PROGRESSIVE_ROCK);
        assertThat(result.getSubgenre()).isEqualTo("Indie");
        assertThat(result.isFavorite()).isTrue();
        assertThat(result.getTags()).containsExactly("alpha", "zebra"); // sorted
        assertThat(result.getAlbumCount()).isEqualTo(1);
    }

    @Test
    void findById_nonExistentId_throwsNotFoundException() {
        when(artistRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> artistService.findById(999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("999");
    }

    // ==================== create tests ====================

    @Test
    void create_validDto_savesAndReturnsDto() {
        ArtistCreateDto dto = createDto("New Band", Genre.PROGRESSIVE_ROCK, "Indie");

        ArgumentCaptor<ArtistEntity> captor = ArgumentCaptor.forClass(ArtistEntity.class);
        when(artistRepository.save(captor.capture())).thenAnswer(inv -> {
            ArtistEntity saved = inv.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        ArtistDto result = artistService.create(dto);

        assertThat(result.getName()).isEqualTo("New Band");
        assertThat(result.getGenre()).isEqualTo(Genre.PROGRESSIVE_ROCK);
        assertThat(result.getSubgenre()).isEqualTo("Indie");

        ArtistEntity captured = captor.getValue();
        assertThat(captured.getName()).isEqualTo("New Band");
        assertThat(captured.getGenre()).isEqualTo(Genre.PROGRESSIVE_ROCK);
        assertThat(captured.getSubgenre()).isEqualTo("Indie");
    }

    // ==================== update tests ====================

    @Test
    void update_onlyName_updatesOnlyName() {
        var artist = artistWithId(1L, "Old Name", Genre.PROGRESSIVE_ROCK);
        artist.setSubgenre("Indie");
        when(artistRepository.findById(1L)).thenReturn(Optional.of(artist));
        when(artistRepository.save(any(ArtistEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ArtistDto result = artistService.update(1L, updateDto("New Name", null, null));

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getGenre()).isEqualTo(Genre.PROGRESSIVE_ROCK); // unchanged
        assertThat(result.getSubgenre()).isEqualTo("Indie"); // unchanged
    }

    @Test
    void update_allFields_updatesAll() {
        var artist = artistWithId(1L, "Old", Genre.PROGRESSIVE_ROCK);
        when(artistRepository.findById(1L)).thenReturn(Optional.of(artist));
        when(artistRepository.save(any(ArtistEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ArtistDto result = artistService.update(1L, updateDto("New", Genre.JAZZ_AND_FUNK, "Bebop"));

        assertThat(result.getName()).isEqualTo("New");
        assertThat(result.getGenre()).isEqualTo(Genre.JAZZ_AND_FUNK);
        assertThat(result.getSubgenre()).isEqualTo("Bebop");
    }

    @Test
    void update_nonExistentId_throwsNotFoundException() {
        when(artistRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> artistService.update(999L, updateDto("X", null, null)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("999");
    }

    // ==================== uniqueness guard tests ====================

    @Test
    void create_duplicateName_throwsIllegalArgument() {
        when(artistRepository.existsByName("New Band")).thenReturn(true);

        assertThatThrownBy(() -> artistService.create(createDto("New Band", Genre.PROGRESSIVE_ROCK, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("New Band");

        verify(artistRepository, never()).save(any(ArtistEntity.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void update_renameToExistingName_throwsIllegalArgument() {
        var artist = artistWithId(1L, "Old Name", Genre.PROGRESSIVE_ROCK);
        when(artistRepository.findById(1L)).thenReturn(Optional.of(artist));
        when(artistRepository.existsByName("Taken")).thenReturn(true);

        assertThatThrownBy(() -> artistService.update(1L, updateDto("Taken", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Taken");

        verify(artistRepository, never()).save(any(ArtistEntity.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void update_nameUnchanged_skipsNameCollisionCheck() {
        var artist = artistWithId(1L, "Same Name", Genre.PROGRESSIVE_ROCK);
        when(artistRepository.findById(1L)).thenReturn(Optional.of(artist));
        when(artistRepository.save(any(ArtistEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ArtistDto result = artistService.update(1L, updateDto("Same Name", Genre.JAZZ_AND_FUNK, null));

        assertThat(result.getGenre()).isEqualTo(Genre.JAZZ_AND_FUNK);
        verify(artistRepository, never()).existsByName(any());
    }

    // ==================== delete tests ====================

    @Test
    void delete_existingId_deletesSuccessfully() {
        var artist = artistWithId(1L, "Band", Genre.PROGRESSIVE_ROCK);
        when(artistRepository.findById(1L)).thenReturn(Optional.of(artist));

        artistService.delete(1L);

        verify(artistRepository).delete(artist);
    }

    @Test
    void delete_nonExistentId_throwsNotFoundException() {
        when(artistRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> artistService.delete(999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("999");

        verify(artistRepository, never()).delete(any(ArtistEntity.class));
    }

    // ==================== toggleFavorite tests ====================

    @Test
    void toggleFavorite_currentlyFalse_setsTrue() {
        var artist = artistWithId(1L, "Band", Genre.PROGRESSIVE_ROCK);
        artist.setFavorite(false);
        when(artistRepository.findById(1L)).thenReturn(Optional.of(artist));
        when(artistRepository.save(any(ArtistEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ArtistDto result = artistService.toggleFavorite(1L);

        assertThat(result.isFavorite()).isTrue();
    }

    @Test
    void toggleFavorite_currentlyTrue_setsFalse() {
        var artist = artistWithId(1L, "Band", Genre.PROGRESSIVE_ROCK);
        artist.setFavorite(true);
        when(artistRepository.findById(1L)).thenReturn(Optional.of(artist));
        when(artistRepository.save(any(ArtistEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ArtistDto result = artistService.toggleFavorite(1L);

        assertThat(result.isFavorite()).isFalse();
    }

    @Test
    void toggleFavorite_nonExistentId_throwsNotFoundException() {
        when(artistRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> artistService.toggleFavorite(999L))
                .isInstanceOf(NotFoundException.class);
    }

    // ==================== setTags tests ====================

    @Test
    void setTags_existingTags_assignsWithoutCreating() {
        var artist = artistWithId(1L, "Band", Genre.PROGRESSIVE_ROCK);
        var rockTag = tagWithId(10L, "rock");
        var classicTag = tagWithId(11L, "classic");

        when(artistRepository.findById(1L)).thenReturn(Optional.of(artist));
        when(tagRepository.findByName("rock")).thenReturn(Optional.of(rockTag));
        when(tagRepository.findByName("classic")).thenReturn(Optional.of(classicTag));
        when(artistRepository.save(any(ArtistEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ArtistDto result = artistService.setTags(1L, List.of("rock", "classic"));

        assertThat(result.getTags()).containsExactly("classic", "rock"); // sorted
        verify(tagRepository, never()).save(any(TagEntity.class));
    }

    @Test
    void setTags_newTags_createsAndAssigns() {
        var artist = artistWithId(1L, "Band", Genre.PROGRESSIVE_ROCK);

        when(artistRepository.findById(1L)).thenReturn(Optional.of(artist));
        when(tagRepository.findByName("newtag")).thenReturn(Optional.empty());
        when(tagRepository.save(any(TagEntity.class))).thenAnswer(inv -> {
            TagEntity tag = inv.getArgument(0);
            tag.setId(100L);
            return tag;
        });
        when(artistRepository.save(any(ArtistEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ArtistDto result = artistService.setTags(1L, List.of("newtag"));

        assertThat(result.getTags()).containsExactly("newtag");
        verify(tagRepository).save(any(TagEntity.class));
    }

    @Test
    void setTags_emptyList_clearsAllTags() {
        var artist = artistWithTags(1L, "Band", Genre.PROGRESSIVE_ROCK,
                new HashSet<>(Set.of(tagWithId(1L, "old"))));

        when(artistRepository.findById(1L)).thenReturn(Optional.of(artist));
        when(artistRepository.save(any(ArtistEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ArtistDto result = artistService.setTags(1L, List.of());

        assertThat(result.getTags()).isEmpty();
    }

    @Test
    void setTags_nonExistentArtist_throwsNotFoundException() {
        when(artistRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> artistService.setTags(999L, List.of("tag")))
                .isInstanceOf(NotFoundException.class);
    }

    // ==================== DTO mapping tests ====================

    @Test
    void toDto_zeroAlbums_albumCountIsZero() {
        var artist = artistWithId(1L, "Band", Genre.PROGRESSIVE_ROCK);
        when(artistRepository.findById(1L)).thenReturn(Optional.of(artist));

        ArtistDto result = artistService.findById(1L);

        assertThat(result.getAlbumCount()).isEqualTo(0);
    }

    @Test
    void toDto_tagsSortedAlphabetically() {
        var artist = artistWithTags(1L, "Band", Genre.PROGRESSIVE_ROCK,
                new HashSet<>(Set.of(tagWithId(1L, "zebra"), tagWithId(2L, "alpha"), tagWithId(3L, "middle"))));
        when(artistRepository.findById(1L)).thenReturn(Optional.of(artist));

        ArtistDto result = artistService.findById(1L);

        assertThat(result.getTags()).containsExactly("alpha", "middle", "zebra");
    }
}
