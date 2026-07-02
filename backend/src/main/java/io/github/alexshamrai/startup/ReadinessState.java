package io.github.alexshamrai.startup;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tracks whether {@link CatalogAutoImporter} has finished its boot decision (restore from
 * Sheets / seed from catalog.json / skip). On Cloud Run the embedded server starts accepting
 * connections during Spring context refresh — before {@code ApplicationReadyEvent} runs the
 * importer — so {@link io.github.alexshamrai.config.ReadinessGateFilter} uses this to reject
 * requests until the DB is in a known state.
 */
@Component
public class ReadinessState {

    private final AtomicBoolean ready = new AtomicBoolean(false);

    public void markReady() {
        ready.set(true);
    }

    public boolean isReady() {
        return ready.get();
    }
}
