package io.github.alexshamrai.controller;

import io.github.alexshamrai.domain.Genre;
import io.github.alexshamrai.dto.AlbumSummaryDto;
import io.github.alexshamrai.dto.ArtistDto;
import io.github.alexshamrai.dto.BrowseFavoritesDto;
import io.github.alexshamrai.dto.BrowseGenreDto;
import io.github.alexshamrai.dto.BrowseStatsDto;
import io.github.alexshamrai.dto.BrowseTagDto;
import io.github.alexshamrai.service.BrowseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static io.github.alexshamrai.TestDataFactory.albumSummaryDto;
import static io.github.alexshamrai.TestDataFactory.artistDto;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BrowseController.class)
@ActiveProfiles("test")
@io.github.alexshamrai.WithAuthenticatedUser
class BrowseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BrowseService browseService;

    // ==================== GET /api/browse/genres ====================

    @Test
    void getGenres_returnsGenreList() throws Exception {
        var genre1 = BrowseGenreDto.builder()
                .genre(Genre.JAZZ_AND_FUNK).artistCount(5).albumCount(20).build();
        var genre2 = BrowseGenreDto.builder()
                .genre(Genre.PROGRESSIVE_ROCK).artistCount(10).albumCount(30).build();
        when(browseService.getGenres()).thenReturn(List.of(genre1, genre2));

        mockMvc.perform(get("/api/browse/genres"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].genre", is("Jazz & Funk")))
                .andExpect(jsonPath("$[0].artistCount", is(5)))
                .andExpect(jsonPath("$[0].albumCount", is(20)))
                .andExpect(jsonPath("$[1].genre", is("Progressive Rock")));
    }

    @Test
    void getGenres_emptyResult_returnsEmptyArray() throws Exception {
        when(browseService.getGenres()).thenReturn(List.of());

        mockMvc.perform(get("/api/browse/genres"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ==================== GET /api/browse/genres/{genre} ====================

    @Test
    void getArtistsByGenre_returnsArtistList() throws Exception {
        var artist = artistDto(1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        when(browseService.getArtistsByGenre(Genre.JAZZ_AND_FUNK)).thenReturn(List.of(artist));

        mockMvc.perform(get("/api/browse/genres/Jazz & Funk"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Miles Davis")))
                .andExpect(jsonPath("$[0].genre", is("Jazz & Funk")));
    }

    @Test
    void getArtistsByGenre_invalidGenre_returns400() throws Exception {
        mockMvc.perform(get("/api/browse/genres/InvalidGenre"))
                .andExpect(status().isBadRequest());
    }

    // ==================== GET /api/browse/genres/{genre}/artists/{artistId} ====================

    @Test
    void getAlbumsByArtist_returnsAlbumList() throws Exception {
        var album = albumSummaryDto(1L, "Kind of Blue", 1959, "Miles Davis", Genre.JAZZ_AND_FUNK);
        when(browseService.getAlbumsByArtist(1L)).thenReturn(List.of(album));

        mockMvc.perform(get("/api/browse/genres/Jazz & Funk/artists/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title", is("Kind of Blue")))
                .andExpect(jsonPath("$[0].artistName", is("Miles Davis")));
    }

    // ==================== GET /api/browse/tags ====================

    @Test
    void getTags_returnsTagList() throws Exception {
        var tag1 = BrowseTagDto.builder().tag("rock").artistCount(5).albumCount(10).build();
        var tag2 = BrowseTagDto.builder().tag("chill").artistCount(2).albumCount(3).build();
        when(browseService.getTags()).thenReturn(List.of(tag1, tag2));

        mockMvc.perform(get("/api/browse/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].tag", is("rock")))
                .andExpect(jsonPath("$[0].artistCount", is(5)))
                .andExpect(jsonPath("$[0].albumCount", is(10)));
    }

    @Test
    void getTags_emptyResult_returnsEmptyArray() throws Exception {
        when(browseService.getTags()).thenReturn(List.of());

        mockMvc.perform(get("/api/browse/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ==================== GET /api/browse/favorites ====================

    @Test
    void getFavorites_returnsFavoritesDto() throws Exception {
        var artist = artistDto(1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        artist.setFavorite(true);
        var album = albumSummaryDto(1L, "Kind of Blue", 1959, "Miles Davis", Genre.JAZZ_AND_FUNK);
        album.setFavorite(true);

        var favorites = BrowseFavoritesDto.builder()
                .favoriteArtists(List.of(artist))
                .favoriteAlbums(List.of(album))
                .build();
        when(browseService.getFavorites()).thenReturn(favorites);

        mockMvc.perform(get("/api/browse/favorites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favoriteArtists", hasSize(1)))
                .andExpect(jsonPath("$.favoriteArtists[0].name", is("Miles Davis")))
                .andExpect(jsonPath("$.favoriteAlbums", hasSize(1)))
                .andExpect(jsonPath("$.favoriteAlbums[0].title", is("Kind of Blue")));
    }

    @Test
    void getFavorites_noFavorites_returnsEmptyLists() throws Exception {
        var favorites = BrowseFavoritesDto.builder()
                .favoriteArtists(List.of())
                .favoriteAlbums(List.of())
                .build();
        when(browseService.getFavorites()).thenReturn(favorites);

        mockMvc.perform(get("/api/browse/favorites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favoriteArtists", hasSize(0)))
                .andExpect(jsonPath("$.favoriteAlbums", hasSize(0)));
    }

    // ==================== GET /api/browse/stats ====================

    @Test
    void getStats_returnsStatsDto() throws Exception {
        var stats = BrowseStatsDto.builder()
                .totalArtists(100)
                .totalAlbums(500)
                .totalSongs(5000)
                .totalTags(10)
                .totalGenres(7)
                .favoriteArtists(15)
                .favoriteAlbums(30)
                .ratedAlbums(200)
                .unratedAlbums(300)
                .gradeDistribution(Map.of("1", 10L, "2", 30L, "3", 60L, "4", 70L, "5", 30L))
                .build();
        when(browseService.getStats()).thenReturn(stats);

        mockMvc.perform(get("/api/browse/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalArtists", is(100)))
                .andExpect(jsonPath("$.totalAlbums", is(500)))
                .andExpect(jsonPath("$.totalSongs", is(5000)))
                .andExpect(jsonPath("$.totalTags", is(10)))
                .andExpect(jsonPath("$.totalGenres", is(7)))
                .andExpect(jsonPath("$.favoriteArtists", is(15)))
                .andExpect(jsonPath("$.favoriteAlbums", is(30)))
                .andExpect(jsonPath("$.ratedAlbums", is(200)))
                .andExpect(jsonPath("$.unratedAlbums", is(300)))
                .andExpect(jsonPath("$.gradeDistribution.5", is(30)));
    }
}
