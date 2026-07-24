package io.github.alexshamrai.controller;

import io.github.alexshamrai.dto.export.Stats;
import io.github.alexshamrai.dto.export.ExportCatalog;
import io.github.alexshamrai.service.CatalogExportService;
import io.github.alexshamrai.service.SheetSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CatalogController.class)
@ActiveProfiles("test")
@io.github.alexshamrai.WithAuthenticatedUser
class CatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogExportService catalogExportService;

    // SheetSyncService is @ConditionalOnProperty — by default NOT present in the test context.
    // No @MockitoBean needed; CatalogController uses ObjectProvider so it handles absent bean.

    // ==================== Export endpoints ====================

    @Test
    void exportJson_returnsAttachmentWithJsonContentType() throws Exception {
        when(catalogExportService.exportJson()).thenReturn(
                new ExportCatalog(java.time.Instant.now(), new Stats(1, 1, 1, 1), java.util.List.of()));

        mockMvc.perform(get("/api/catalog/export/json"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("application/json")))
                .andExpect(header().string("Content-Disposition",
                        is("attachment; filename=\"music-cat-export.json\"")))
                .andExpect(jsonPath("$.stats.totalArtists", is(1)));
    }

    @Test
    void exportCsv_returnsZipAttachment() throws Exception {
        when(catalogExportService.exportCsvZip()).thenReturn(new byte[]{0x50, 0x4B, 0x05, 0x06});

        mockMvc.perform(get("/api/catalog/export/csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", is("application/zip")))
                .andExpect(header().string("Content-Disposition",
                        is("attachment; filename=\"music-cat-export.zip\"")));
    }

    // ==================== Sync endpoints — sheets disabled ====================

    @Test
    void syncPush_sheetsDisabled_returns503() throws Exception {
        mockMvc.perform(post("/api/catalog/sync/push"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status", is(503)))
                .andExpect(jsonPath("$.message", is("Google Sheets sync is not configured")));
    }

    @Test
    void syncPull_sheetsDisabled_returns503() throws Exception {
        mockMvc.perform(post("/api/catalog/sync/pull"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status", is(503)))
                .andExpect(jsonPath("$.message", is("Google Sheets sync is not configured")));
    }

    @Test
    void syncStatus_sheetsDisabled_returnsEnabledFalse() throws Exception {
        mockMvc.perform(get("/api/catalog/sync/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled", is(false)))
                .andExpect(jsonPath("$.lastPushAt", nullValue()))
                .andExpect(jsonPath("$.lastPullAt", nullValue()));
    }
}
