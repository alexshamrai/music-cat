package io.github.alexshamrai.dto;

import java.time.Instant;

/**
 * Status returned by GET /api/catalog/sync/status.
 * lastPullAt is always null until Task 11 implements the read path.
 */
public record SyncStatusDto(
        boolean enabled,
        Instant lastPushAt,
        Instant lastPullAt,
        boolean dirty,
        String lastError
) {}
