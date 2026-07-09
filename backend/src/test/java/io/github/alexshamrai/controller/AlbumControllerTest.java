package io.github.alexshamrai.controller;

import io.github.alexshamrai.domain.Genre;
import io.github.alexshamrai.dto.AlbumDto;
import io.github.alexshamrai.dto.AlbumSummaryDto;
import io.github.alexshamrai.dto.SongDto;
import io.github.alexshamrai.exception.NotFoundException;
import io.github.alexshamrai.service.AlbumService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static io.github.alexshamrai.TestDataFactory.albumDto;
import static io.github.alexshamrai.TestDataFactory.albumSummaryDto;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AlbumController.class)
@ActiveProfiles("test")
@io.github.alexshamrai.WithAuthenticatedUser
class AlbumControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AlbumService albumService;

    // ==================== GET /api/albums ====================

    @Test
    void list_noFilters_returns200WithList() throws Exception {
        var album1 = albumSummaryDto(1L, "Kind of Blue", 1959, "Miles Davis", Genre.JAZZ_AND_FUNK);
        var album2 = albumSummaryDto(2L, "Dark Side of the Moon", 1973, "Pink Floyd", Genre.PROGRESSIVE_ROCK);
        when(albumService.findAll(any())).thenReturn(List.of(album1, album2));

        mockMvc.perform(get("/api/albums"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title", is("Kind of Blue")))
                .andExpect(jsonPath("$[0].artistName", is("Miles Davis")))
                .andExpect(jsonPath("$[1].title", is("Dark Side of the Moon")));
    }

    @Test
    void list_withGenreFilter_passes() throws Exception {
        when(albumService.findAll(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/albums").param("genre", "Jazz & Funk"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void list_withMultipleFilters_passes() throws Exception {
        when(albumService.findAll(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/albums")
                        .param("genre", "Jazz & Funk")
                        .param("minGrade", "3")
                        .param("favorite", "true")
                        .param("tag", "chill"))
                .andExpect(status().isOk());
    }

    @Test
    void list_emptyResult_returns200WithEmptyArray() throws Exception {
        when(albumService.findAll(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/albums"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ==================== GET /api/albums/{id} ====================

    @Test
    void getById_existingId_returns200WithAlbumDetail() throws Exception {
        var album = AlbumDto.builder()
                .id(1L)
                .title("Kind of Blue")
                .year(1959)
                .grade(5)
                .favorite(true)
                .artist(AlbumDto.ArtistSummaryDto.builder()
                        .id(1L).name("Miles Davis").genre(Genre.JAZZ_AND_FUNK).build())
                .tags(List.of("classic", "masterpiece"))
                .songs(List.of(
                        SongDto.builder().id(1L).title("So What").trackNumber(1).discNumber(1).build(),
                        SongDto.builder().id(2L).title("Freddie Freeloader").trackNumber(2).discNumber(1).build()))
                .build();
        when(albumService.findById(1L)).thenReturn(album);

        mockMvc.perform(get("/api/albums/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.title", is("Kind of Blue")))
                .andExpect(jsonPath("$.year", is(1959)))
                .andExpect(jsonPath("$.grade", is(5)))
                .andExpect(jsonPath("$.favorite", is(true)))
                .andExpect(jsonPath("$.artist.id", is(1)))
                .andExpect(jsonPath("$.artist.name", is("Miles Davis")))
                .andExpect(jsonPath("$.artist.genre", is("Jazz & Funk")))
                .andExpect(jsonPath("$.tags", hasSize(2)))
                .andExpect(jsonPath("$.songs", hasSize(2)))
                .andExpect(jsonPath("$.songs[0].title", is("So What")))
                .andExpect(jsonPath("$.songs[0].trackNumber", is(1)));
    }

    @Test
    void getById_nonExistentId_returns404() throws Exception {
        when(albumService.findById(999L)).thenThrow(new NotFoundException("Album not found with id: 999"));

        mockMvc.perform(get("/api/albums/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", is("Album not found with id: 999")));
    }

    // ==================== POST /api/albums ====================

    @Test
    void create_validBody_returns201() throws Exception {
        var created = albumSummaryDto(1L, "New Album", 2020, "Miles Davis", Genre.JAZZ_AND_FUNK);
        when(albumService.create(any())).thenReturn(created);

        mockMvc.perform(post("/api/albums")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "New Album", "year": 2020, "artistId": 1}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.title", is("New Album")));
    }

    @Test
    void create_missingTitle_returns400() throws Exception {
        mockMvc.perform(post("/api/albums")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"year": 2020, "artistId": 1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists());
    }

    @Test
    void create_missingArtistId_returns400() throws Exception {
        mockMvc.perform(post("/api/albums")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Album"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.artistId").exists());
    }

    @Test
    void create_blankTitle_returns400() throws Exception {
        mockMvc.perform(post("/api/albums")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "", "artistId": 1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists());
    }

    @Test
    void create_nonExistentArtist_returns404() throws Exception {
        when(albumService.create(any()))
                .thenThrow(new NotFoundException("Artist not found with id: 999"));

        mockMvc.perform(post("/api/albums")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Album", "artistId": 999}
                                """))
                .andExpect(status().isNotFound());
    }

    // ==================== PUT /api/albums/{id} ====================

    @Test
    void update_existingId_returns200() throws Exception {
        var updated = albumSummaryDto(1L, "Updated Title", 1970, "Miles Davis", Genre.JAZZ_AND_FUNK);
        when(albumService.update(eq(1L), any())).thenReturn(updated);

        mockMvc.perform(put("/api/albums/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Updated Title", "year": 1970}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Updated Title")));
    }

    @Test
    void update_emptyTitle_returns400() throws Exception {
        mockMvc.perform(put("/api/albums/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists());
    }

    @Test
    void update_nonExistentId_returns404() throws Exception {
        when(albumService.update(eq(999L), any()))
                .thenThrow(new NotFoundException("Album not found with id: 999"));

        mockMvc.perform(put("/api/albums/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "X"}
                                """))
                .andExpect(status().isNotFound());
    }

    // ==================== PUT /api/albums/{id}/edit ====================

    @Test
    void edit_validBody_returns200WithReconciledSongs() throws Exception {
        var album = AlbumDto.builder()
                .id(1L)
                .title("Renamed Album")
                .year(1970)
                .favorite(false)
                .artist(AlbumDto.ArtistSummaryDto.builder()
                        .id(1L).name("Miles Davis").genre(Genre.JAZZ_AND_FUNK).build())
                .tags(List.of())
                .songs(List.of(
                        SongDto.builder().id(1L).title("So What (Take 1)").trackNumber(1).discNumber(1).build(),
                        SongDto.builder().id(null).title("Bonus").trackNumber(2).discNumber(1).build()))
                .build();
        when(albumService.edit(eq(1L), any())).thenReturn(album);

        mockMvc.perform(put("/api/albums/1/edit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Renamed Album", "year": 1970,
                                 "songs": [{"id": 1, "title": "So What (Take 1)"}, {"id": null, "title": "Bonus"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Renamed Album")))
                .andExpect(jsonPath("$.songs", hasSize(2)))
                .andExpect(jsonPath("$.songs[0].title", is("So What (Take 1)")));
    }

    @Test
    void edit_blankTitle_returns400() throws Exception {
        mockMvc.perform(put("/api/albums/1/edit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "", "songs": []}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists());
    }

    @Test
    void edit_missingSongsList_returns400() throws Exception {
        mockMvc.perform(put("/api/albums/1/edit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Album"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.songs").exists());
    }

    @Test
    void edit_blankSongTitle_returns400() throws Exception {
        mockMvc.perform(put("/api/albums/1/edit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Album", "songs": [{"id": null, "title": ""}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    void edit_titleCollision_returns400() throws Exception {
        when(albumService.edit(eq(1L), any()))
                .thenThrow(new IllegalArgumentException("Another album titled 'Taken' already exists for this artist"));

        mockMvc.perform(put("/api/albums/1/edit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Taken", "songs": []}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Another album titled 'Taken' already exists for this artist")));
    }

    @Test
    void edit_nonExistentId_returns404() throws Exception {
        when(albumService.edit(eq(999L), any()))
                .thenThrow(new NotFoundException("Album not found with id: 999"));

        mockMvc.perform(put("/api/albums/999/edit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "X", "songs": []}
                                """))
                .andExpect(status().isNotFound());
    }

    // ==================== DELETE /api/albums/{id} ====================

    @Test
    void delete_existingId_returns204() throws Exception {
        doNothing().when(albumService).delete(1L);

        mockMvc.perform(delete("/api/albums/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_nonExistentId_returns404() throws Exception {
        doThrow(new NotFoundException("Album not found with id: 999")).when(albumService).delete(999L);

        mockMvc.perform(delete("/api/albums/999"))
                .andExpect(status().isNotFound());
    }

    // ==================== PATCH /api/albums/{id}/grade ====================

    @Test
    void setGrade_validGrade_returns200() throws Exception {
        var graded = albumSummaryDto(1L, "Kind of Blue", 1959, "Miles Davis", Genre.JAZZ_AND_FUNK);
        graded.setGrade(4);
        when(albumService.setGrade(1L, 4)).thenReturn(graded);

        mockMvc.perform(patch("/api/albums/1/grade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"grade": 4}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grade", is(4)));
    }

    @Test
    void setGrade_gradeTooHigh_returns400() throws Exception {
        mockMvc.perform(patch("/api/albums/1/grade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"grade": 6}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void setGrade_gradeTooLow_returns400() throws Exception {
        mockMvc.perform(patch("/api/albums/1/grade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"grade": 0}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void setGrade_nonExistentId_returns404() throws Exception {
        when(albumService.setGrade(999L, 3))
                .thenThrow(new NotFoundException("Album not found with id: 999"));

        mockMvc.perform(patch("/api/albums/999/grade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"grade": 3}
                                """))
                .andExpect(status().isNotFound());
    }

    // ==================== PATCH /api/albums/{id}/favorite ====================

    @Test
    void toggleFavorite_existingId_returns200() throws Exception {
        var toggled = albumSummaryDto(1L, "Kind of Blue", 1959, "Miles Davis", Genre.JAZZ_AND_FUNK);
        toggled.setFavorite(true);
        when(albumService.toggleFavorite(1L)).thenReturn(toggled);

        mockMvc.perform(patch("/api/albums/1/favorite"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorite", is(true)));
    }

    @Test
    void toggleFavorite_nonExistentId_returns404() throws Exception {
        when(albumService.toggleFavorite(999L))
                .thenThrow(new NotFoundException("Album not found with id: 999"));

        mockMvc.perform(patch("/api/albums/999/favorite"))
                .andExpect(status().isNotFound());
    }

    // ==================== PUT /api/albums/{id}/tags ====================

    @Test
    void setTags_validBody_returns200() throws Exception {
        var withTags = albumSummaryDto(1L, "Kind of Blue", 1959, "Miles Davis", Genre.JAZZ_AND_FUNK);
        withTags.setTags(List.of("chill", "jazz"));
        when(albumService.setTags(eq(1L), any())).thenReturn(withTags);

        mockMvc.perform(put("/api/albums/1/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                ["jazz", "chill"]
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags", hasSize(2)));
    }

    @Test
    void setTags_emptyList_returns200() throws Exception {
        var noTags = albumSummaryDto(1L, "Kind of Blue", 1959, "Miles Davis", Genre.JAZZ_AND_FUNK);
        when(albumService.setTags(eq(1L), any())).thenReturn(noTags);

        mockMvc.perform(put("/api/albums/1/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags", hasSize(0)));
    }

    @Test
    void setTags_nonExistentId_returns404() throws Exception {
        when(albumService.setTags(eq(999L), any()))
                .thenThrow(new NotFoundException("Album not found with id: 999"));

        mockMvc.perform(put("/api/albums/999/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                ["tag"]
                                """))
                .andExpect(status().isNotFound());
    }
}
