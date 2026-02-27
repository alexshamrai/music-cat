package io.github.alexshamrai.service;

import io.github.alexshamrai.domain.ArtistEntity;
import io.github.alexshamrai.domain.Genre;
import io.github.alexshamrai.domain.TagEntity;
import io.github.alexshamrai.dto.AlbumFilterParams;
import io.github.alexshamrai.dto.AlbumSummaryDto;
import io.github.alexshamrai.dto.ArtistDto;
import io.github.alexshamrai.dto.BrowseFavoritesDto;
import io.github.alexshamrai.dto.BrowseGenreDto;
import io.github.alexshamrai.dto.BrowseStatsDto;
import io.github.alexshamrai.dto.BrowseTagDto;
import io.github.alexshamrai.repository.AlbumRepository;
import io.github.alexshamrai.repository.ArtistRepository;
import io.github.alexshamrai.repository.SongRepository;
import io.github.alexshamrai.repository.TagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.github.alexshamrai.TestDataFactory.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrowseServiceTest {

    @Mock
    private ArtistRepository artistRepository;

    @Mock
    private AlbumRepository albumRepository;

    @Mock
    private SongRepository songRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private ArtistService artistService;

    @Mock
    private AlbumService albumService;

    @InjectMocks
    private BrowseService browseService;

    // ==================== getGenres tests ====================

    @Test
    void getGenres_withArtists_returnsGenreWithCounts() {
        var artist1 = artistWithId(1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        var album1 = albumWithId(1L, "Kind of Blue", 1959, artist1);
        var album2 = albumWithId(2L, "Bitches Brew", 1970, artist1);
        artist1.setAlbums(new ArrayList<>(List.of(album1, album2)));

        // Return artists for every genre query (same mock for all specs)
        when(artistRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(artist1));

        List<BrowseGenreDto> result = browseService.getGenres();

        // All genres will have 1 artist and 2 albums (same mock for all)
        assertThat(result).isNotEmpty();
        assertThat(result.getFirst().getArtistCount()).isEqualTo(1);
        assertThat(result.getFirst().getAlbumCount()).isEqualTo(2);
    }

    @Test
    void getGenres_skipsEmptyGenres() {
        when(artistRepository.findAll(any(Specification.class)))
                .thenReturn(List.of());

        List<BrowseGenreDto> result = browseService.getGenres();

        assertThat(result).isEmpty();
    }

    @Test
    void getGenres_sortedAlphabetically() {
        var artistRock = artistWithId(1L, "Pink Floyd", Genre.PROGRESSIVE_ROCK);
        artistRock.setAlbums(new ArrayList<>(List.of(albumWithId(1L, "DSOTM", 1973, artistRock))));

        var artistJazz = artistWithId(2L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        artistJazz.setAlbums(new ArrayList<>(List.of(albumWithId(2L, "KOB", 1959, artistJazz))));

        // Return the same non-empty list for all genre queries
        when(artistRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(artistRock));

        List<BrowseGenreDto> result = browseService.getGenres();

        // All genres get results from the same mock, verify they are sorted by display name
        assertThat(result).isNotEmpty();
        for (int i = 1; i < result.size(); i++) {
            assertThat(result.get(i).getGenre().getDisplayName())
                    .isGreaterThanOrEqualTo(result.get(i - 1).getGenre().getDisplayName());
        }
    }

    // ==================== getArtistsByGenre tests ====================

    @Test
    void getArtistsByGenre_delegatesToArtistService() {
        var artist = artistDto(1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        when(artistService.findAll(Genre.JAZZ_AND_FUNK, null, null, null))
                .thenReturn(List.of(artist));

        List<ArtistDto> result = browseService.getArtistsByGenre(Genre.JAZZ_AND_FUNK);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getName()).isEqualTo("Miles Davis");
    }

    @Test
    void getArtistsByGenre_emptyResult_returnsEmptyList() {
        when(artistService.findAll(Genre.CLASSICAL, null, null, null))
                .thenReturn(List.of());

        List<ArtistDto> result = browseService.getArtistsByGenre(Genre.CLASSICAL);

        assertThat(result).isEmpty();
    }

    // ==================== getAlbumsByArtist tests ====================

    @Test
    void getAlbumsByArtist_delegatesToAlbumService() {
        var album = albumSummaryDto(1L, "Kind of Blue", 1959, "Miles Davis", Genre.JAZZ_AND_FUNK);
        when(albumService.findAll(any(AlbumFilterParams.class))).thenReturn(List.of(album));

        List<AlbumSummaryDto> result = browseService.getAlbumsByArtist(1L);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getTitle()).isEqualTo("Kind of Blue");
    }

    @Test
    void getAlbumsByArtist_emptyResult_returnsEmptyList() {
        when(albumService.findAll(any(AlbumFilterParams.class))).thenReturn(List.of());

        List<AlbumSummaryDto> result = browseService.getAlbumsByArtist(999L);

        assertThat(result).isEmpty();
    }

    // ==================== getTags tests ====================

    @Test
    void getTags_returnsTagsWithCounts() {
        var tag1 = tagWithId(1L, "rock");
        tag1.setArtists(new HashSet<>(Set.of(artistWithId(1L, "A", Genre.PROGRESSIVE_ROCK))));
        tag1.setAlbums(new HashSet<>());

        var tag2 = tagWithId(2L, "chill");
        tag2.setArtists(new HashSet<>());
        tag2.setAlbums(new HashSet<>());

        when(tagRepository.findAll()).thenReturn(List.of(tag1, tag2));

        List<BrowseTagDto> result = browseService.getTags();

        assertThat(result).hasSize(2);
        // Sorted by total usage descending: "rock" has 1 usage, "chill" has 0
        assertThat(result.get(0).getTag()).isEqualTo("rock");
        assertThat(result.get(0).getArtistCount()).isEqualTo(1);
        assertThat(result.get(0).getAlbumCount()).isEqualTo(0);
        assertThat(result.get(1).getTag()).isEqualTo("chill");
    }

    @Test
    void getTags_sortedByUsageDescending() {
        var tag1 = tagWithId(1L, "rare");
        tag1.setArtists(new HashSet<>());
        tag1.setAlbums(new HashSet<>());

        var tag2 = tagWithId(2L, "popular");
        tag2.setArtists(new HashSet<>(Set.of(
                artistWithId(1L, "A", Genre.PROGRESSIVE_ROCK),
                artistWithId(2L, "B", Genre.BLUES))));
        tag2.setAlbums(new HashSet<>());

        when(tagRepository.findAll()).thenReturn(List.of(tag1, tag2));

        List<BrowseTagDto> result = browseService.getTags();

        assertThat(result.get(0).getTag()).isEqualTo("popular");
        assertThat(result.get(1).getTag()).isEqualTo("rare");
    }

    @Test
    void getTags_emptyResult_returnsEmptyList() {
        when(tagRepository.findAll()).thenReturn(List.of());

        List<BrowseTagDto> result = browseService.getTags();

        assertThat(result).isEmpty();
    }

    // ==================== getFavorites tests ====================

    @Test
    void getFavorites_returnsFavoriteArtistsAndAlbums() {
        var artist = artistDto(1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        artist.setFavorite(true);
        var album = albumSummaryDto(1L, "Kind of Blue", 1959, "Miles Davis", Genre.JAZZ_AND_FUNK);
        album.setFavorite(true);

        when(artistService.findAll(isNull(), isNull(), eq(true), isNull()))
                .thenReturn(List.of(artist));
        when(albumService.findAll(any(AlbumFilterParams.class))).thenReturn(List.of(album));

        BrowseFavoritesDto result = browseService.getFavorites();

        assertThat(result.getFavoriteArtists()).hasSize(1);
        assertThat(result.getFavoriteArtists().getFirst().getName()).isEqualTo("Miles Davis");
        assertThat(result.getFavoriteAlbums()).hasSize(1);
        assertThat(result.getFavoriteAlbums().getFirst().getTitle()).isEqualTo("Kind of Blue");
    }

    @Test
    void getFavorites_noFavorites_returnsEmptyLists() {
        when(artistService.findAll(isNull(), isNull(), eq(true), isNull()))
                .thenReturn(List.of());
        when(albumService.findAll(any(AlbumFilterParams.class))).thenReturn(List.of());

        BrowseFavoritesDto result = browseService.getFavorites();

        assertThat(result.getFavoriteArtists()).isEmpty();
        assertThat(result.getFavoriteAlbums()).isEmpty();
    }

    // ==================== getStats tests ====================

    @Test
    void getStats_returnsAllCounts() {
        when(artistRepository.count()).thenReturn(100L);
        when(albumRepository.count()).thenReturn(500L);
        when(songRepository.count()).thenReturn(5000L);
        when(tagRepository.count()).thenReturn(10L);

        // Genre counts (at least one genre has artists)
        when(artistRepository.count(any(Specification.class))).thenReturn(0L);

        // Favorite and grade counts
        when(albumRepository.count(any(Specification.class))).thenReturn(0L);

        BrowseStatsDto result = browseService.getStats();

        assertThat(result.getTotalArtists()).isEqualTo(100);
        assertThat(result.getTotalAlbums()).isEqualTo(500);
        assertThat(result.getTotalSongs()).isEqualTo(5000);
        assertThat(result.getTotalTags()).isEqualTo(10);
        assertThat(result.getGradeDistribution()).hasSize(5);
        assertThat(result.getGradeDistribution()).containsKeys("1", "2", "3", "4", "5");
    }

    @Test
    void getStats_gradeDistributionHasAllGrades() {
        when(artistRepository.count()).thenReturn(0L);
        when(albumRepository.count()).thenReturn(0L);
        when(songRepository.count()).thenReturn(0L);
        when(tagRepository.count()).thenReturn(0L);
        when(artistRepository.count(any(Specification.class))).thenReturn(0L);
        when(albumRepository.count(any(Specification.class))).thenReturn(0L);

        BrowseStatsDto result = browseService.getStats();

        assertThat(result.getGradeDistribution()).hasSize(5);
        for (int i = 1; i <= 5; i++) {
            assertThat(result.getGradeDistribution()).containsKey(String.valueOf(i));
        }
    }
}
