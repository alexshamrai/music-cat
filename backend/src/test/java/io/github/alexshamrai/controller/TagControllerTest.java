package io.github.alexshamrai.controller;

import io.github.alexshamrai.dto.TagDto;
import io.github.alexshamrai.exception.NotFoundException;
import io.github.alexshamrai.service.TagService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TagController.class)
@ActiveProfiles("test")
class TagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TagService tagService;

    // ==================== GET /api/tags ====================

    @Test
    void list_returns200WithTags() throws Exception {
        var tag1 = TagDto.builder().id(1L).name("rock").build();
        var tag2 = TagDto.builder().id(2L).name("jazz").build();
        when(tagService.findAll()).thenReturn(List.of(tag1, tag2));

        mockMvc.perform(get("/api/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].name", is("rock")));
    }

    @Test
    void list_emptyResult_returns200WithEmptyArray() throws Exception {
        when(tagService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ==================== POST /api/tags ====================

    @Test
    void create_validBody_returns201() throws Exception {
        var created = TagDto.builder().id(1L).name("chill").build();
        when(tagService.create("chill")).thenReturn(created);

        mockMvc.perform(post("/api/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "chill"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.name", is("chill")));
    }

    @Test
    void create_blankName_returns400() throws Exception {
        mockMvc.perform(post("/api/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": ""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_missingName_returns400() throws Exception {
        mockMvc.perform(post("/api/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ==================== DELETE /api/tags/{id} ====================

    @Test
    void delete_existingId_returns204() throws Exception {
        doNothing().when(tagService).delete(1L);

        mockMvc.perform(delete("/api/tags/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_nonExistentId_returns404() throws Exception {
        doThrow(new NotFoundException("Tag not found with id: 999")).when(tagService).delete(999L);

        mockMvc.perform(delete("/api/tags/999"))
                .andExpect(status().isNotFound());
    }
}
