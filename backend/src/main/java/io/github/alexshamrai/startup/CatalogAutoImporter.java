package io.github.alexshamrai.startup;

import io.github.alexshamrai.repository.ArtistRepository;
import io.github.alexshamrai.service.SheetSyncService;
import io.github.alexshamrai.service.SheetsCatalogReader;
import io.github.alexshamrai.service.SheetsLoadResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Populates an empty database on boot from Google Sheets. Decision tree (Sheets-only):
 * <ol>
 *   <li>DB not empty → skip (resume event pushes)</li>
 *   <li>DB empty + sheets disabled → empty catalog (automated-test context only)</li>
 *   <li>DB empty + sheets have data → restore from Google Sheets</li>
 *   <li>DB empty + sheets blank → empty catalog (trivially consistent → resume pushes)</li>
 *   <li>DB empty + restore throws / produces 0 artists → empty catalog, pushes SUSPENDED</li>
 * </ol>
 *
 * <p>There is no local fallback: a Sheets outage on a cold start leaves the catalog empty until
 * Sheets recovers (repair connectivity, then POST /api/catalog/sync/pull). Event-driven pushes
 * start SUSPENDED (see {@link SheetSyncService}) and resume only when the DB provably mirrors the
 * sheet — a diverged/empty DB must never overwrite the spreadsheet.
 */
@Component
@org.springframework.context.annotation.Lazy(false)
@Slf4j
public class CatalogAutoImporter {

    private final ArtistRepository artistRepository;
    private final ObjectProvider<SheetsCatalogReader> sheetsCatalogReader;
    private final ObjectProvider<SheetSyncService> sheetSyncService;
    private final ReadinessState readinessState;

    public CatalogAutoImporter(ArtistRepository artistRepository,
                               ObjectProvider<SheetsCatalogReader> sheetsCatalogReader,
                               ObjectProvider<SheetSyncService> sheetSyncService,
                               ReadinessState readinessState) {
        this.artistRepository = artistRepository;
        this.sheetsCatalogReader = sheetsCatalogReader;
        this.sheetSyncService = sheetSyncService;
        this.readinessState = readinessState;
    }

    /**
     * Wraps the decision in try/finally so {@link ReadinessState#markReady()} always runs on exit
     * (including every early return), closing the {@code ReadinessGateFilter} window as soon as
     * the decision is made.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            doOnApplicationReady();
        } finally {
            readinessState.markReady();
        }
    }

    private void doOnApplicationReady() {
        SheetSyncService sync = sheetSyncService.getIfAvailable();

        if (artistRepository.count() > 0) {
            log.info("Database already contains data, skipping restore");
            if (sync != null) {
                sync.resumeEventPushes();
            }
            return;
        }

        SheetsCatalogReader reader = sheetsCatalogReader.getIfAvailable();
        if (reader == null) {
            log.info("Google Sheets disabled and DB is empty — starting with an empty catalog");
            return;
        }

        try {
            if (reader.sheetsHaveData()) {
                restoreFromSheets(reader, sync);
                return;
            }
        } catch (Exception e) {
            log.error("Sheets restore failed — starting with an empty catalog; pushes suspended so the "
                    + "spreadsheet stays untouched. Repair connectivity/sheet, then POST /api/catalog/sync/pull", e);
            if (sync != null) {
                sync.suspendEventPushes("Boot restore from Google Sheets failed: " + e.getMessage()
                        + " — running on an empty catalog; repair connectivity/sheet, then POST /api/catalog/sync/pull");
            }
            return;
        }

        // Sheets enabled but blank → empty DB and blank sheet are trivially consistent
        log.info("Google Sheets is blank and DB is empty — nothing to restore");
        if (sync != null) {
            sync.resumeEventPushes();
        }
    }

    private void restoreFromSheets(SheetsCatalogReader reader, SheetSyncService sync) {
        SheetsLoadResult result = reader.loadFromSheets();

        if (result.artistCount() == 0) {
            log.error("Sheets restore produced 0 artists although the spreadsheet has data — starting "
                    + "with an empty catalog; pushes suspended. Repair the sheet, then POST /api/catalog/sync/pull");
            if (sync != null) {
                sync.suspendEventPushes("Restore found data in the spreadsheet but produced 0 artists — "
                        + "running on an empty catalog; repair the sheet, then POST /api/catalog/sync/pull");
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
}
