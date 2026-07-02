package io.github.alexshamrai.controller;

import com.google.api.services.sheets.v4.Sheets;
import io.github.alexshamrai.domain.ArtistEntity;
import io.github.alexshamrai.domain.Genre;
import io.github.alexshamrai.repository.ArtistRepository;
import io.github.alexshamrai.sheets.SheetsClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static io.github.alexshamrai.TestDataFactory.artist;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for POST /api/catalog/sync/pull with sheets enabled and a mocked
 * SheetsClient: the pull must replace DB content with sheet content and record
 * lastPullAt in the sync status.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "music-cat.sheets.enabled=true",
        "spring.datasource.url=jdbc:h2:mem:testdb-pull;DB_CLOSE_DELAY=-1"
})
class SyncPullIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ArtistRepository artistRepository;

    @MockitoBean
    private SheetsClient sheetsClient;

    // Prevent GoogleSheetsConfig from loading real credentials
    @MockitoBean
    private Sheets googleSheetsApi;

    @Test
    void syncPull_replacesDbFromSheets_andRecordsLastPullAt() throws Exception {
        artistRepository.save(artist("Stale Local Artist", Genre.BLUES));

        when(sheetsClient.read("Artists")).thenReturn(List.of(
                List.of("name", "genre", "subgenre", "favorite", "tags"),
                List.of("Sheet Artist", "Jazz & Funk", "", "TRUE", "")));
        when(sheetsClient.read("Albums")).thenReturn(List.of(
                List.of("artist", "title", "year", "grade", "favorite", "tags")));
        when(sheetsClient.read("Songs")).thenReturn(List.of(
                List.of("artist", "album", "disc", "track", "title")));

        mockMvc.perform(post("/api/catalog/sync/pull"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artistCount", is(1)))
                .andExpect(jsonPath("$.albumCount", is(0)))
                .andExpect(jsonPath("$.songCount", is(0)))
                .andExpect(jsonPath("$.syncedAt", notNullValue()));

        assertThat(artistRepository.findAll())
                .extracting(ArtistEntity::getName)
                .containsExactly("Sheet Artist");

        mockMvc.perform(get("/api/catalog/sync/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled", is(true)))
                .andExpect(jsonPath("$.lastPullAt", notNullValue()))
                .andExpect(jsonPath("$.suspended", is(false)));
    }

    @Test
    void syncPull_withSkippedRows_suspendsEventPushes() throws Exception {
        when(sheetsClient.read("Artists")).thenReturn(List.of(
                List.of("name", "genre", "subgenre", "favorite", "tags"),
                List.of("Sheet Artist", "Jazz & Funk", "", "TRUE", "")));
        // Album row referencing an artist missing from the Artists tab → skipped with warning
        when(sheetsClient.read("Albums")).thenReturn(List.of(
                List.of("artist", "title", "year", "grade", "favorite", "tags"),
                List.of("Ghost Artist", "Phantom Album", "2000", "", "FALSE", "")));
        when(sheetsClient.read("Songs")).thenReturn(List.of(
                List.of("artist", "album", "disc", "track", "title")));

        mockMvc.perform(post("/api/catalog/sync/pull"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.albumCount", is(0)));

        // The DB is missing a sheet row — pushes must be suspended so it isn't erased
        mockMvc.perform(get("/api/catalog/sync/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suspended", is(true)))
                .andExpect(jsonPath("$.lastError", notNullValue()));
    }
}
