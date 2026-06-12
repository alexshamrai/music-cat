package io.github.alexshamrai.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.alexshamrai.dto.ImportResult;
import io.github.alexshamrai.dto.SyncResultDto;
import io.github.alexshamrai.dto.SyncStatusDto;
import io.github.alexshamrai.service.CatalogImportService;
import io.github.alexshamrai.service.SheetSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogImportService catalogImportService;
    private final ObjectProvider<SheetSyncService> sheetSyncServiceProvider;

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

    @PostMapping("/sync/push")
    public ResponseEntity<?> pushSync() {
        SheetSyncService syncService = sheetSyncServiceProvider.getIfAvailable();
        if (syncService == null) {
            return ResponseEntity.status(503)
                    .body(Map.of("status", 503, "message", "Google Sheets sync is not configured"));
        }
        SyncResultDto result = syncService.pushCatalog(true);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/sync/status")
    public ResponseEntity<SyncStatusDto> syncStatus() {
        SheetSyncService syncService = sheetSyncServiceProvider.getIfAvailable();
        if (syncService == null) {
            return ResponseEntity.ok(new SyncStatusDto(false, null, null, false, null));
        }
        return ResponseEntity.ok(syncService.getStatus());
    }
}
