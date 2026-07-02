package io.github.alexshamrai.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.alexshamrai.dto.ImportResult;
import io.github.alexshamrai.dto.SyncResultDto;
import io.github.alexshamrai.dto.SyncStatusDto;
import io.github.alexshamrai.dto.export.ExportCatalog;
import io.github.alexshamrai.service.CatalogExportService;
import io.github.alexshamrai.service.CatalogImportService;
import io.github.alexshamrai.service.SheetSyncService;
import io.github.alexshamrai.service.SheetsCatalogReader;
import io.github.alexshamrai.service.SheetsLoadResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogImportService catalogImportService;
    private final CatalogExportService catalogExportService;
    private final ObjectProvider<SheetSyncService> sheetSyncServiceProvider;
    private final ObjectProvider<SheetsCatalogReader> sheetsCatalogReaderProvider;

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

    @GetMapping("/export/json")
    public ResponseEntity<ExportCatalog> exportJson() {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"music-cat-export.json\"")
                .body(catalogExportService.exportJson());
    }

    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportCsv() {
        return ResponseEntity.ok()
                .header("Content-Type", "application/zip")
                .header("Content-Disposition", "attachment; filename=\"music-cat-export.zip\"")
                .body(catalogExportService.exportCsvZip());
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

    @PostMapping("/sync/pull")
    public ResponseEntity<?> pullSync() {
        SheetsCatalogReader reader = sheetsCatalogReaderProvider.getIfAvailable();
        SheetSyncService syncService = sheetSyncServiceProvider.getIfAvailable();
        if (reader == null || syncService == null) {
            return ResponseEntity.status(503)
                    .body(Map.of("status", 503, "message", "Google Sheets sync is not configured"));
        }
        SheetsLoadResult result = reader.replaceFromSheets();
        Instant syncedAt = Instant.now();
        syncService.recordPull(syncedAt);
        if (result.clean()) {
            // DB now mirrors the sheet exactly — event pushes are safe again
            syncService.resumeEventPushes();
        } else {
            syncService.suspendEventPushes("Pull skipped " + result.warnings().size()
                    + " sheet row(s) — a push would erase them from the sheet. First warning: "
                    + result.warnings().get(0));
        }
        return ResponseEntity.ok(new SyncResultDto(
                result.artistCount(), result.albumCount(), result.songCount(), syncedAt));
    }

    @GetMapping("/sync/status")
    public ResponseEntity<SyncStatusDto> syncStatus() {
        SheetSyncService syncService = sheetSyncServiceProvider.getIfAvailable();
        if (syncService == null) {
            return ResponseEntity.ok(new SyncStatusDto(false, null, null, false, false, null));
        }
        return ResponseEntity.ok(syncService.getStatus());
    }
}
