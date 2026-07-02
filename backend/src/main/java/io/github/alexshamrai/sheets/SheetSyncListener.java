package io.github.alexshamrai.sheets;

import io.github.alexshamrai.event.CatalogChangedEvent;
import io.github.alexshamrai.service.SheetSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for {@link CatalogChangedEvent} and pushes the catalog to Google Sheets
 * after the DB transaction commits.
 *
 * <p>Sheets failures are caught and logged — a Sheets outage must not turn a
 * successful DB mutation into a 500. The service tracks {@code songsDirty} so the
 * next successful push self-heals.
 */
@Component
@ConditionalOnProperty(name = "music-cat.sheets.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SheetSyncListener {

    private final SheetSyncService sheetSyncService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCatalogChanged(CatalogChangedEvent event) {
        if (sheetSyncService.eventPushesSuspended()) {
            log.warn("Sheets sync is suspended — skipping push after a catalog change "
                    + "(check GET /api/catalog/sync/status; recover via sync/pull or an explicit sync/push)");
            return;
        }
        try {
            sheetSyncService.pushCatalog(event.structural());
        } catch (Exception e) {
            log.error("Google Sheets sync failed — catalog update was committed to DB but not synced to Sheets", e);
        }
    }
}
