package io.github.alexshamrai.dto;

import java.time.Instant;

/**
 * Result returned by POST /api/catalog/sync/push.
 */
public record SyncResultDto(int artistCount, int albumCount, int songCount, Instant syncedAt) {}
