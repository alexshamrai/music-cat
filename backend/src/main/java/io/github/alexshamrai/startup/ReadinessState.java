package io.github.alexshamrai.startup;

import org.springframework.stereotype.Component;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tracks whether {@link CatalogAutoImporter} has finished its boot decision (restore from
 * Sheets / start empty / skip). On Cloud Run the embedded server starts accepting
 * connections during Spring context refresh — before {@code ApplicationReadyEvent} runs the
 * importer — so {@link io.github.alexshamrai.config.ReadinessGateFilter} uses this to hold
 * requests until the DB is in a known state.
 *
 * <p>A {@link CountDownLatch} rather than a plain flag: Cloud Run only allocates full CPU to
 * the container while a request is actively being processed, so a request that BLOCKS here
 * (instead of failing fast) keeps the container's CPU allocated long enough for the boot-time
 * restore running on a different thread to actually finish. Verified live: failing fast with
 * an instant 503, a restore that takes ~3s locally took ~5 minutes on Cloud Run under CPU
 * throttling; blocking one request fixes it.
 */
@Component
public class ReadinessState {

    private final CountDownLatch latch = new CountDownLatch(1);

    public void markReady() {
        latch.countDown();
    }

    public boolean isReady() {
        return latch.getCount() == 0;
    }

    /** Blocks up to the given timeout for readiness; returns true if it became ready in time. */
    public boolean awaitReady(long timeout, TimeUnit unit) throws InterruptedException {
        return latch.await(timeout, unit);
    }
}
