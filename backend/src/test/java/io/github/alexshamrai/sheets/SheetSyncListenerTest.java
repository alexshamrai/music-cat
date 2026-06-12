package io.github.alexshamrai.sheets;

import com.google.api.services.sheets.v4.Sheets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for SheetSyncListener.
 *
 * <p>Enables Sheets with a mocked SheetsClient to verify the event-driven push behavior.
 * Tests confirm: correct sheet tabs are written per mutation type, and that a Sheets
 * failure does NOT cause the HTTP request to fail.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "music-cat.sheets.enabled=true",
        "spring.datasource.url=jdbc:h2:mem:testdb-sheets;DB_CLOSE_DELAY=-1"
})
class SheetSyncListenerTest {

    @Autowired
    private MockMvc mockMvc;

    // Mock the SheetsClient interface — replaces GoogleSheetsClient in the context
    @MockitoBean
    private SheetsClient sheetsClient;

    // Mock the Google Sheets API client — prevents GoogleSheetsConfig from trying to load credentials
    @MockitoBean
    private Sheets googleSheetsApi;

    @Test
    void gradeChange_nonStructural_doesNotWriteSongsTab() throws Exception {
        // Create artist and album in DB first
        var artistResponse = mockMvc.perform(post("/api/artists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "SyncTest Artist", "genre": "Blues"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String artistBody = artistResponse.getResponse().getContentAsString();
        Long artistId = extractId(artistBody);

        var albumResponse = mockMvc.perform(post("/api/albums")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "SyncTest Album", "artistId": %d}
                                """.formatted(artistId)))
                .andExpect(status().isCreated())
                .andReturn();

        String albumBody = albumResponse.getResponse().getContentAsString();
        Long albumId = extractId(albumBody);

        // Reset invocations from the create calls
        org.mockito.Mockito.reset(sheetsClient);

        // Set grade — non-structural
        mockMvc.perform(patch("/api/albums/" + albumId + "/grade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"grade": 4}
                                """))
                .andExpect(status().isOk());

        // Should write Artists and Albums, but NOT Songs
        verify(sheetsClient, atLeastOnce()).overwrite(eq("Artists"), any());
        verify(sheetsClient, atLeastOnce()).overwrite(eq("Albums"), any());
        verify(sheetsClient, never()).overwrite(eq("Songs"), any());
    }

    @Test
    void createArtist_structural_writesSongsTab() throws Exception {
        // sheetsClient.overwrite is void — no stubbing needed, default (no-op) is fine

        mockMvc.perform(post("/api/artists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "SyncTest Structural Artist", "genre": "Blues"}
                                """))
                .andExpect(status().isCreated());

        // Structural → should write Songs tab too
        verify(sheetsClient, atLeastOnce()).overwrite(eq("Artists"), any());
        verify(sheetsClient, atLeastOnce()).overwrite(eq("Albums"), any());
        verify(sheetsClient, atLeastOnce()).overwrite(eq("Songs"), any());
    }

    @Test
    void sheetsFailure_doesNotBreakGradeRequest() throws Exception {
        // Create artist and album
        var artistResponse = mockMvc.perform(post("/api/artists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "SyncFail Artist", "genre": "Blues"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        Long artistId = extractId(artistResponse.getResponse().getContentAsString());

        var albumResponse = mockMvc.perform(post("/api/albums")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "SyncFail Album", "artistId": %d}
                                """.formatted(artistId)))
                .andExpect(status().isCreated())
                .andReturn();
        Long albumId = extractId(albumResponse.getResponse().getContentAsString());

        // Make SheetsClient throw on every call
        doThrow(new RuntimeException("Sheets down")).when(sheetsClient).overwrite(any(), any());

        // Grade change should still succeed despite Sheets being down
        mockMvc.perform(patch("/api/albums/" + albumId + "/grade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"grade": 3}
                                """))
                .andExpect(status().isOk());
    }

    // Quick JSON id extractor — avoids ObjectMapper dependency overhead in test
    private Long extractId(String json) {
        int start = json.indexOf("\"id\":") + 5;
        int end = json.indexOf(',', start);
        if (end == -1) {
            end = json.indexOf('}', start);
        }
        return Long.parseLong(json.substring(start, end).trim());
    }
}
