package io.github.alexshamrai.controller;

import io.github.alexshamrai.domain.Genre;
import io.github.alexshamrai.dto.AlbumDto;
import io.github.alexshamrai.dto.SongDto;
import io.github.alexshamrai.exception.NoMatchException;
import io.github.alexshamrai.service.RandomPickService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static io.github.alexshamrai.TestDataFactory.albumDto;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RandomController.class)
@ActiveProfiles("test")
class RandomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RandomPickService randomPickService;

    // ==================== GET /api/random/album ====================

    @Test
    void randomAlbum_noFilters_returns200WithAlbum() throws Exception {
        var album = albumDto(1L, "Kind of Blue", 1959, 1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        when(randomPickService.randomAlbum(any())).thenReturn(album);

        mockMvc.perform(get("/api/random/album"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.title", is("Kind of Blue")))
                .andExpect(jsonPath("$.artist.name", is("Miles Davis")));
    }

    @Test
    void randomAlbum_withGenreFilter_passes() throws Exception {
        var album = albumDto(1L, "Kind of Blue", 1959, 1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        when(randomPickService.randomAlbum(any())).thenReturn(album);

        mockMvc.perform(get("/api/random/album").param("genre", "Jazz & Funk"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Kind of Blue")));
    }

    @Test
    void randomAlbum_withMultipleFilters_passes() throws Exception {
        var album = albumDto(1L, "Kind of Blue", 1959, 1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        when(randomPickService.randomAlbum(any())).thenReturn(album);

        mockMvc.perform(get("/api/random/album")
                        .param("genre", "Jazz & Funk")
                        .param("minGrade", "3")
                        .param("favorite", "true")
                        .param("tag", "chill"))
                .andExpect(status().isOk());
    }

    @Test
    void randomAlbum_noMatchingAlbums_returns404() throws Exception {
        when(randomPickService.randomAlbum(any()))
                .thenThrow(new NoMatchException("No albums match the given filters"));

        mockMvc.perform(get("/api/random/album").param("genre", "Jazz & Funk"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", is("No albums match the given filters")));
    }

    @Test
    void randomAlbum_invalidGenre_returns400() throws Exception {
        mockMvc.perform(get("/api/random/album").param("genre", "FakeGenre"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void randomAlbum_withSongs_returnsSongsInResponse() throws Exception {
        var album = AlbumDto.builder()
                .id(1L)
                .title("Kind of Blue")
                .year(1959)
                .grade(5)
                .favorite(true)
                .artist(AlbumDto.ArtistSummaryDto.builder()
                        .id(1L).name("Miles Davis").genre(Genre.JAZZ_AND_FUNK).build())
                .tags(List.of("masterpiece"))
                .songs(List.of(
                        SongDto.builder().id(1L).title("So What").trackNumber(1).discNumber(1).build(),
                        SongDto.builder().id(2L).title("Freddie Freeloader").trackNumber(2).discNumber(1).build()))
                .build();
        when(randomPickService.randomAlbum(any())).thenReturn(album);

        mockMvc.perform(get("/api/random/album"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.songs", hasSize(2)))
                .andExpect(jsonPath("$.songs[0].title", is("So What")))
                .andExpect(jsonPath("$.tags", hasSize(1)))
                .andExpect(jsonPath("$.grade", is(5)));
    }

    // ==================== GET /api/random/albums ====================

    @Test
    void randomAlbums_defaultCount_returns200WithList() throws Exception {
        var album1 = albumDto(1L, "Kind of Blue", 1959, 1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        var album2 = albumDto(2L, "Bitches Brew", 1970, 1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        when(randomPickService.randomAlbums(any(), eq(5))).thenReturn(List.of(album1, album2));

        mockMvc.perform(get("/api/random/albums"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void randomAlbums_customCount_passes() throws Exception {
        var album = albumDto(1L, "Kind of Blue", 1959, 1L, "Miles Davis", Genre.JAZZ_AND_FUNK);
        when(randomPickService.randomAlbums(any(), eq(3))).thenReturn(List.of(album));

        mockMvc.perform(get("/api/random/albums").param("count", "3"))
                .andExpect(status().isOk());
    }

    @Test
    void randomAlbums_withGenreFilter_passes() throws Exception {
        when(randomPickService.randomAlbums(any(), eq(5))).thenReturn(List.of());

        mockMvc.perform(get("/api/random/albums").param("genre", "Jazz & Funk"))
                .andExpect(status().isOk());
    }

    @Test
    void randomAlbums_emptyResult_returnsEmptyArray() throws Exception {
        when(randomPickService.randomAlbums(any(), eq(5))).thenReturn(List.of());

        mockMvc.perform(get("/api/random/albums"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
