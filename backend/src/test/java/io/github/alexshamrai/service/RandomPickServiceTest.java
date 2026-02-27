package io.github.alexshamrai.service;

import io.github.alexshamrai.domain.AlbumEntity;
import io.github.alexshamrai.domain.Genre;
import io.github.alexshamrai.dto.AlbumDto;
import io.github.alexshamrai.dto.AlbumFilterParams;
import io.github.alexshamrai.exception.NoMatchException;
import io.github.alexshamrai.repository.AlbumRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static io.github.alexshamrai.TestDataFactory.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RandomPickServiceTest {

    @Mock
    private AlbumRepository albumRepository;

    @Mock
    private AlbumService albumService;

    @InjectMocks
    private RandomPickService randomPickService;

    // ==================== randomAlbum tests ====================

    @Test
    void randomAlbum_noFilters_returnsAlbum() {
        var artist = artistWithId(1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        var album = albumWithId(1L, "Kind of Blue", 1959, artist);
        var albumDto = albumDto(1L, "Kind of Blue", 1959, 1L, "Miles Davis", Genre.JAZZ_AND_FUNK);

        when(albumService.buildSpecification(any(AlbumFilterParams.class)))
                .thenReturn(Specification.where(
                        (Specification<AlbumEntity>) (root, query, cb) -> cb.conjunction()));
        when(albumRepository.count(any(Specification.class))).thenReturn(1L);
        when(albumRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(album)));
        when(albumService.findById(1L)).thenReturn(albumDto);

        AlbumDto result = randomPickService.randomAlbum(new AlbumFilterParams());

        assertThat(result.getTitle()).isEqualTo("Kind of Blue");
        assertThat(result.getArtist().getName()).isEqualTo("Miles Davis");
    }

    @Test
    void randomAlbum_noMatchingAlbums_throwsNoMatchException() {
        when(albumService.buildSpecification(any(AlbumFilterParams.class)))
                .thenReturn(Specification.where(
                        (Specification<AlbumEntity>) (root, query, cb) -> cb.conjunction()));
        when(albumRepository.count(any(Specification.class))).thenReturn(0L);

        assertThatThrownBy(() -> randomPickService.randomAlbum(new AlbumFilterParams()))
                .isInstanceOf(NoMatchException.class)
                .hasMessage("No albums match the given filters");
    }

    @Test
    void randomAlbum_withFilters_delegatesBuildSpecification() {
        var artist = artistWithId(1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        var album = albumWithId(1L, "Kind of Blue", 1959, artist);
        var albumDto = albumDto(1L, "Kind of Blue", 1959, 1L, "Miles Davis", Genre.JAZZ_AND_FUNK);

        var filters = AlbumFilterParams.builder()
                .genre(Genre.JAZZ_AND_FUNK)
                .minGrade(3)
                .build();

        when(albumService.buildSpecification(any(AlbumFilterParams.class)))
                .thenReturn(Specification.where(
                        (Specification<AlbumEntity>) (root, query, cb) -> cb.conjunction()));
        when(albumRepository.count(any(Specification.class))).thenReturn(1L);
        when(albumRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(album)));
        when(albumService.findById(1L)).thenReturn(albumDto);

        AlbumDto result = randomPickService.randomAlbum(filters);

        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Kind of Blue");
    }

    @Test
    void randomAlbum_multipleAlbums_returnsOne() {
        var artist = artistWithId(1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        var album1 = albumWithId(1L, "Kind of Blue", 1959, artist);
        var albumDto1 = albumDto(1L, "Kind of Blue", 1959, 1L, "Miles Davis", Genre.JAZZ_AND_FUNK);

        when(albumService.buildSpecification(any(AlbumFilterParams.class)))
                .thenReturn(Specification.where(
                        (Specification<AlbumEntity>) (root, query, cb) -> cb.conjunction()));
        when(albumRepository.count(any(Specification.class))).thenReturn(5L);
        when(albumRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(album1)));
        when(albumService.findById(1L)).thenReturn(albumDto1);

        AlbumDto result = randomPickService.randomAlbum(new AlbumFilterParams());

        assertThat(result).isNotNull();
    }

    // ==================== randomAlbums tests ====================

    @Test
    void randomAlbums_returnsRequestedCount() {
        var artist = artistWithId(1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        var album1 = albumWithId(1L, "Kind of Blue", 1959, artist);
        var album2 = albumWithId(2L, "Bitches Brew", 1970, artist);
        var album3 = albumWithId(3L, "Sketches of Spain", 1960, artist);

        var dto1 = albumDto(1L, "Kind of Blue", 1959, 1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        var dto2 = albumDto(2L, "Bitches Brew", 1970, 1L, "Miles Davis", Genre.JAZZ_AND_FUNK);

        when(albumService.buildSpecification(any(AlbumFilterParams.class)))
                .thenReturn(Specification.where(
                        (Specification<AlbumEntity>) (root, query, cb) -> cb.conjunction()));
        when(albumRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(album1, album2, album3));
        when(albumService.findById(any(Long.class))).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            if (id == 1L) return dto1;
            return dto2;
        });

        List<AlbumDto> result = randomPickService.randomAlbums(new AlbumFilterParams(), 2);

        assertThat(result).hasSize(2);
    }

    @Test
    void randomAlbums_fewerThanRequested_returnsAll() {
        var artist = artistWithId(1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        var album1 = albumWithId(1L, "Kind of Blue", 1959, artist);
        var dto1 = albumDto(1L, "Kind of Blue", 1959, 1L, "Miles Davis", Genre.JAZZ_AND_FUNK);

        when(albumService.buildSpecification(any(AlbumFilterParams.class)))
                .thenReturn(Specification.where(
                        (Specification<AlbumEntity>) (root, query, cb) -> cb.conjunction()));
        when(albumRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(album1));
        when(albumService.findById(1L)).thenReturn(dto1);

        List<AlbumDto> result = randomPickService.randomAlbums(new AlbumFilterParams(), 5);

        assertThat(result).hasSize(1);
    }

    @Test
    void randomAlbums_noMatchingAlbums_returnsEmptyList() {
        when(albumService.buildSpecification(any(AlbumFilterParams.class)))
                .thenReturn(Specification.where(
                        (Specification<AlbumEntity>) (root, query, cb) -> cb.conjunction()));
        when(albumRepository.findAll(any(Specification.class)))
                .thenReturn(List.of());

        List<AlbumDto> result = randomPickService.randomAlbums(new AlbumFilterParams(), 5);

        assertThat(result).isEmpty();
    }

    @Test
    void randomAlbums_withFilters_passesFiltersToSpec() {
        var filters = AlbumFilterParams.builder()
                .genre(Genre.PROGRESSIVE_ROCK)
                .favorite(true)
                .build();

        when(albumService.buildSpecification(any(AlbumFilterParams.class)))
                .thenReturn(Specification.where(
                        (Specification<AlbumEntity>) (root, query, cb) -> cb.conjunction()));
        when(albumRepository.findAll(any(Specification.class)))
                .thenReturn(List.of());

        List<AlbumDto> result = randomPickService.randomAlbums(filters, 3);

        assertThat(result).isEmpty();
    }
}
