package io.github.alexshamrai.controller;

import io.github.alexshamrai.dto.ArtistDto;
import io.github.alexshamrai.exception.NotFoundException;
import io.github.alexshamrai.service.ArtistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static io.github.alexshamrai.TestDataFactory.artistDto;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ArtistController.class)
@ActiveProfiles("test")
class ArtistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ArtistService artistService;

    // ==================== GET /api/artists ====================

    @Test
    void list_noFilters_returns200WithList() throws Exception {
        var artist1 = artistDto(1L, "Band One", "Rock");
        var artist2 = artistDto(2L, "Band Two", "Jazz");
        when(artistService.findAll(null, null, null, null)).thenReturn(List.of(artist1, artist2));

        mockMvc.perform(get("/api/artists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name", is("Band One")))
                .andExpect(jsonPath("$[1].name", is("Band Two")));
    }

    @Test
    void list_withGenreFilter_passesGenreToService() throws Exception {
        when(artistService.findAll("Rock", null, null, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/artists").param("genre", "Rock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        verify(artistService).findAll("Rock", null, null, null);
    }

    @Test
    void list_withAllFilters_passesAllToService() throws Exception {
        when(artistService.findAll("Rock", "Indie", true, "chill")).thenReturn(List.of());

        mockMvc.perform(get("/api/artists")
                        .param("genre", "Rock")
                        .param("subgenre", "Indie")
                        .param("favorite", "true")
                        .param("tag", "chill"))
                .andExpect(status().isOk());

        verify(artistService).findAll("Rock", "Indie", true, "chill");
    }

    @Test
    void list_emptyResult_returns200WithEmptyArray() throws Exception {
        when(artistService.findAll(null, null, null, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/artists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ==================== GET /api/artists/{id} ====================

    @Test
    void getById_existingId_returns200WithArtist() throws Exception {
        var artist = ArtistDto.builder()
                .id(1L)
                .name("Genesis")
                .genre("Rock")
                .subgenre("Progressive")
                .favorite(true)
                .tags(List.of("classic", "prog"))
                .albumCount(5)
                .build();
        when(artistService.findById(1L)).thenReturn(artist);

        mockMvc.perform(get("/api/artists/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.name", is("Genesis")))
                .andExpect(jsonPath("$.genre", is("Rock")))
                .andExpect(jsonPath("$.subgenre", is("Progressive")))
                .andExpect(jsonPath("$.favorite", is(true)))
                .andExpect(jsonPath("$.tags", hasSize(2)))
                .andExpect(jsonPath("$.albumCount", is(5)));
    }

    @Test
    void getById_nonExistentId_returns404() throws Exception {
        when(artistService.findById(999L)).thenThrow(new NotFoundException("Artist not found with id: 999"));

        mockMvc.perform(get("/api/artists/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", is("Artist not found with id: 999")));
    }

    // ==================== POST /api/artists ====================

    @Test
    void create_validBody_returns201() throws Exception {
        var created = artistDto(1L, "New Band", "Rock");
        when(artistService.create(any())).thenReturn(created);

        mockMvc.perform(post("/api/artists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "New Band", "genre": "Rock"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.name", is("New Band")));
    }

    @Test
    void create_missingName_returns400() throws Exception {
        mockMvc.perform(post("/api/artists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"genre": "Rock"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void create_missingGenre_returns400() throws Exception {
        mockMvc.perform(post("/api/artists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Band"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.genre").exists());
    }

    @Test
    void create_blankName_returns400() throws Exception {
        mockMvc.perform(post("/api/artists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "genre": "Rock"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void create_emptyBody_returns400() throws Exception {
        mockMvc.perform(post("/api/artists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.genre").exists());
    }

    // ==================== PUT /api/artists/{id} ====================

    @Test
    void update_existingId_returns200() throws Exception {
        var updated = artistDto(1L, "Updated", "Jazz");
        when(artistService.update(eq(1L), any())).thenReturn(updated);

        mockMvc.perform(put("/api/artists/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Updated", "genre": "Jazz"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Updated")));
    }

    @Test
    void update_emptyName_returns400() throws Exception {
        mockMvc.perform(put("/api/artists/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void partialUpdate_existingId_returns200() throws Exception {
        var updated = artistDto(1L, "Updated", "Jazz");
        when(artistService.update(eq(1L), any())).thenReturn(updated);

        mockMvc.perform(patch("/api/artists/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Updated", "genre": "Jazz"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Updated")));
    }

    @Test
    void update_nonExistentId_returns404() throws Exception {
        when(artistService.update(eq(999L), any()))
                .thenThrow(new NotFoundException("Artist not found with id: 999"));

        mockMvc.perform(put("/api/artists/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "X"}
                                """))
                .andExpect(status().isNotFound());
    }

    // ==================== DELETE /api/artists/{id} ====================

    @Test
    void delete_existingId_returns204() throws Exception {
        doNothing().when(artistService).delete(1L);

        mockMvc.perform(delete("/api/artists/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_nonExistentId_returns404() throws Exception {
        doThrow(new NotFoundException("Artist not found with id: 999")).when(artistService).delete(999L);

        mockMvc.perform(delete("/api/artists/999"))
                .andExpect(status().isNotFound());
    }

    // ==================== PATCH /api/artists/{id}/favorite ====================

    @Test
    void toggleFavorite_existingId_returns200() throws Exception {
        var toggled = ArtistDto.builder()
                .id(1L).name("Band").genre("Rock").favorite(true).tags(List.of()).albumCount(0).build();
        when(artistService.toggleFavorite(1L)).thenReturn(toggled);

        mockMvc.perform(patch("/api/artists/1/favorite"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorite", is(true)));
    }

    @Test
    void toggleFavorite_nonExistentId_returns404() throws Exception {
        when(artistService.toggleFavorite(999L))
                .thenThrow(new NotFoundException("Artist not found with id: 999"));

        mockMvc.perform(patch("/api/artists/999/favorite"))
                .andExpect(status().isNotFound());
    }

    // ==================== PUT /api/artists/{id}/tags ====================

    @Test
    void setTags_validBody_returns200() throws Exception {
        var withTags = ArtistDto.builder()
                .id(1L).name("Band").genre("Rock").favorite(false)
                .tags(List.of("chill", "rock")).albumCount(0).build();
        when(artistService.setTags(eq(1L), any())).thenReturn(withTags);

        mockMvc.perform(put("/api/artists/1/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                ["rock", "chill"]
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags", hasSize(2)));
    }

    @Test
    void setTags_emptyList_returns200() throws Exception {
        var noTags = artistDto(1L, "Band", "Rock");
        when(artistService.setTags(eq(1L), any())).thenReturn(noTags);

        mockMvc.perform(put("/api/artists/1/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags", hasSize(0)));
    }

    @Test
    void setTags_nonExistentId_returns404() throws Exception {
        when(artistService.setTags(eq(999L), any()))
                .thenThrow(new NotFoundException("Artist not found with id: 999"));

        mockMvc.perform(put("/api/artists/999/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                ["tag"]
                                """))
                .andExpect(status().isNotFound());
    }
}
