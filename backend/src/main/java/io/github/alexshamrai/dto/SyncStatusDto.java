package io.github.alexshamrai.dto;

import java.time.Instant;

/**
 * Status returned by GET /api/catalog/sync/status.
 *
 * @param suspended true when event-driven pushes are suspended because the DB may diverge
 *                  from the spreadsheet (boot restore failed or skipped rows). Recover
 *                  with a clean POST sync/pull, or force the DB state up with POST sync/push.
 */
public record SyncStatusDto(
        boolean enabled,
        Instant lastPushAt,
        Instant lastPullAt,
        boolean dirty,
        boolean suspended,
        String lastError
) {}
