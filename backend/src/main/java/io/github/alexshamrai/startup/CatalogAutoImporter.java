package io.github.alexshamrai.startup;

import java.nio.file.Files;
import java.nio.file.Path;

import io.github.alexshamrai.dto.ImportResult;
import io.github.alexshamrai.repository.ArtistRepository;
import io.github.alexshamrai.service.CatalogImportService;
import io.github.alexshamrai.service.SheetSyncService;
import io.github.alexshamrai.service.SheetsCatalogReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Populates an empty database on boot. Decision tree:
 * <ol>
 *   <li>DB not empty → skip</li>
 *   <li>DB empty + sheets enabled + sheets have data → restore from Google Sheets</li>
 *   <li>DB empty + sheets enabled + sheets blank → seed from catalog.json, push everything up</li>
 *   <li>DB empty + sheets disabled → import catalog.json</li>
 * </ol>
 *
 * <p>If the Sheets restore throws (network down, malformed rows), we fall back to the
 * catalog.json import WITHOUT pushing — a transient Sheets outage must not overwrite the
 * spreadsheet (the persistent store) with stale seed data. The failed restore surfaces
 * via GET /api/catalog/sync/status.
 *
 * <p>Auto-import never publishes {@link io.github.alexshamrai.event.CatalogChangedEvent}
 * (it calls {@code importFromJson(path, false)}) — pushes at boot happen only explicitly
 * in case 3, so the fallback path provably leaves the sheet untouched.
 */
@Component
@Slf4j
public class CatalogAutoImporter {

    private final CatalogImportService catalogImportService;
    private final ArtistRepository artistRepository;
    private final ObjectProvider<SheetsCatalogReader> sheetsCatalogReader;
    private final ObjectProvider<SheetSyncService> sheetSyncService;
    private final String catalogPath;

    public CatalogAutoImporter(CatalogImportService catalogImportService,
                               ArtistRepository artistRepository,
                               ObjectProvider<SheetsCatalogReader> sheetsCatalogReader,
                               ObjectProvider<SheetSyncService> sheetSyncService,
                               @Value("${music-cat.catalog-path}") String catalogPath) {
        this.catalogImportService = catalogImportService;
        this.artistRepository = artistRepository;
        this.sheetsCatalogReader = sheetsCatalogReader;
        this.sheetSyncService = sheetSyncService;
        this.catalogPath = catalogPath;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (artistRepository.count() > 0) {
            log.info("Database already contains data, skipping auto-import");
            return;
        }

        SheetsCatalogReader reader = sheetsCatalogReader.getIfAvailable();
        if (reader == null) {
            // Sheets disabled — original behavior
            importCatalogJson();
            return;
        }

        try {
            if (reader.sheetsHaveData()) {
                ImportResult result = reader.loadFromSheets();
                log.info("Restored {} artists / {} albums / {} songs from Google Sheets",
                        result.artistCount(), result.albumCount(), result.songCount());
                return;
            }
        } catch (Exception e) {
            log.error("Sheets restore failed — falling back to catalog.json import WITHOUT pushing, "
                    + "so the spreadsheet stays untouched", e);
            importCatalogJson();
            return;
        }

        // Sheets enabled but blank → seed from catalog.json and push the initial state up
        ImportResult seeded = importCatalogJson();
        if (seeded == null) {
            return;
        }
        SheetSyncService sync = sheetSyncService.getIfAvailable();
        if (sync != null) {
            try {
                sync.pushCatalog(true);
                log.info("Seeded from catalog.json and pushed initial state to Google Sheets");
            } catch (Exception e) {
                log.error("Seeded from catalog.json but the initial push to Google Sheets failed "
                        + "— check GET /api/catalog/sync/status", e);
            }
        }
    }

    /** Imports catalog.json without publishing a CatalogChangedEvent. Returns null on skip/failure. */
    private ImportResult importCatalogJson() {
        Path path = Path.of(catalogPath);
        if (!Files.exists(path)) {
            log.warn("Catalog file not found at {}, skipping auto-import", path.toAbsolutePath());
            return null;
        }

        try {
            log.info("Database is empty, starting auto-import from {}", path.toAbsolutePath());
            ImportResult result = catalogImportService.importFromJson(path, false);
            log.info("Auto-import completed: {} artists, {} albums, {} songs",
                result.artistCount(), result.albumCount(), result.songCount());
            return result;
        } catch (Exception e) {
            log.error("Auto-import failed", e);
            return null;
        }
    }
}
