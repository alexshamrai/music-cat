package io.github.alexshamrai.controller;

import io.github.alexshamrai.dto.ImportResult;
import io.github.alexshamrai.service.CatalogImportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CatalogController.class)
@ActiveProfiles("test")
class CatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogImportService catalogImportService;

    @Test
    void importCatalog_validFile_returns200WithResult() throws Exception {
        when(catalogImportService.importFromJson(any())).thenReturn(new ImportResult(5, 10, 50));

        MockMultipartFile file = new MockMultipartFile(
                "file", "catalog.json", "application/json",
                """
                {"catalog": []}
                """.getBytes());

        mockMvc.perform(multipart("/api/catalog/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artistCount", is(5)))
                .andExpect(jsonPath("$.albumCount", is(10)))
                .andExpect(jsonPath("$.songCount", is(50)));
    }

    @Test
    void importCatalog_serviceThrowsIOException_returns500() throws Exception {
        when(catalogImportService.importFromJson(any())).thenThrow(new IOException("Parse error"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "catalog.json", "application/json",
                "invalid".getBytes());

        mockMvc.perform(multipart("/api/catalog/import").file(file))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status", is(500)));
    }

    @Test
    void importCatalog_missingFilePart_returns500() throws Exception {
        mockMvc.perform(multipart("/api/catalog/import"))
                .andExpect(status().isInternalServerError());
    }
}
