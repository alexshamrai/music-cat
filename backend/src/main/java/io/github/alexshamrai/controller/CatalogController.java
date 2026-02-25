package io.github.alexshamrai.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.alexshamrai.dto.ImportResult;
import io.github.alexshamrai.service.CatalogImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogImportService catalogImportService;

    @PostMapping("/import")
    public ResponseEntity<ImportResult> importCatalog(@RequestParam("file") MultipartFile file)
        throws IOException {
        Path tempFile = Files.createTempFile("catalog-import-", ".json");
        try {
            file.transferTo(tempFile);
            ImportResult result = catalogImportService.importFromJson(tempFile);
            return ResponseEntity.ok(result);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
