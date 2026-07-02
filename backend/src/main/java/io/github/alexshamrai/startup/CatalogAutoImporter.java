package io.github.alexshamrai.startup;

import java.nio.file.Files;
import java.nio.file.Path;

import io.github.alexshamrai.dto.ImportResult;
import io.github.alexshamrai.repository.ArtistRepository;
import io.github.alexshamrai.service.CatalogImportService;
import io.github.alexshamrai.service.SheetSyncService;
import io.github.alexshamrai.service.SheetsCatalogReader;
import io.github.alexshamrai.service.SheetsLoadResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Populates an empty database on boot. Decision tree:
 * <ol>
 *   <li>DB not empty → skip (resume event pushes)</li>
 *   <li>DB empty + sheets enabled + sheets have data → restore from Google Sheets</li>
 *   <li>DB empty + sheets enabled + sheets blank → seed from catalog.json, push everything up</li>
 *   <li>DB empty + sheets disabled → import catalog.json</li>
 * </ol>
 *
 * <p>Event-driven pushes start SUSPENDED (see {@link SheetSyncService}) and are resumed
 * here only when the DB provably mirrors the spreadsheet. If the Sheets restore throws,
 * produces zero artists despite non-blank tabs, or skips rows, we fall back / continue
 * with pushes still suspended — a diverged DB must never overwrite the spreadsheet (the
 * persistent store). The failure surfaces via GET /api/catalog/sync/status.
 *
 * <p>Auto-import never publishes {@link io.github.alexshamrai.event.CatalogChangedEvent}
 * (it calls {@code importFromJson(path, false)}) — pushes at boot happen only explicitly
 * in case 3, so the fallback paths provably leave the sheet untouched.
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
        SheetSyncService sync = sheetSyncService.getIfAvailable();

        if (artistRepository.count() > 0) {
            log.info("Database already contains data, skipping auto-import");
            if (sync != null) {
                sync.resumeEventPushes();
            }
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
                restoreFromSheets(reader, sync);
                return;
            }
        } catch (Exception e) {
            log.error("Sheets restore failed — falling back to catalog.json import WITHOUT pushing, "
                    + "so the spreadsheet stays untouched", e);
            importCatalogJson();
            if (sync != null) {
                sync.suspendEventPushes("Boot restore from Google Sheets failed: " + e.getMessage()
                        + " — running on catalog.json seed data; repair connectivity/sheet, then POST /api/catalog/sync/pull");
            }
            return;
        }

        // Sheets enabled but blank → seed from catalog.json and push the initial state up
        ImportResult seeded = importCatalogJson();
        if (sync == null) {
            return;
        }
        if (seeded == null) {
            // Nothing to seed (no catalog.json): empty DB and blank sheet are trivially consistent
            sync.resumeEventPushes();
            return;
        }
        try {
            sync.pushCatalog(true); // success resumes event pushes automatically
            log.info("Seeded from catalog.json and pushed initial state to Google Sheets");
        } catch (Exception e) {
            log.error("Seeded from catalog.json but the initial push to Google Sheets failed "
                    + "— event pushes stay suspended; check GET /api/catalog/sync/status", e);
        }
    }

    private void restoreFromSheets(SheetsCatalogReader reader, SheetSyncService sync) {
        SheetsLoadResult result = reader.loadFromSheets();

        if (result.artistCount() == 0) {
            // Tabs had rows but none produced an artist — e.g. a partially-failed prior
            // overwrite left the Artists tab blank. Treat the sheet as inconsistent.
            log.error("Sheets restore produced 0 artists although the spreadsheet has data — "
                    + "seeding from catalog.json WITHOUT pushing; repair the sheet, then POST /api/catalog/sync/pull");
            importCatalogJson();
            if (sync != null) {
                sync.suspendEventPushes("Restore found data in the spreadsheet but produced 0 artists — "
                        + "running on catalog.json seed data; repair the sheet, then POST /api/catalog/sync/pull");
            }
            return;
        }

        log.info("Restored {} artists / {} albums / {} songs from Google Sheets",
                result.artistCount(), result.albumCount(), result.songCount());
        if (sync == null) {
            return;
        }
        if (result.clean()) {
            sync.resumeEventPushes();
        } else {
            sync.suspendEventPushes("Restore skipped " + result.warnings().size()
                    + " sheet row(s) — a push would erase them from the sheet. Repair the sheet, then "
                    + "POST /api/catalog/sync/pull. First warning: " + result.warnings().get(0));
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
