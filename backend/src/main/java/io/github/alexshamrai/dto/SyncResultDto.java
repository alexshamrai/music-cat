package io.github.alexshamrai.dto;

import java.time.Instant;

/**
 * Result returned by POST /api/catalog/sync/push.
 *
 * @param songCount number of songs written to the Songs tab in this push, or {@code 0} if
 *                  the Songs tab was not touched (i.e. a non-structural push with no dirty flag set).
 *                  A value of {@code 0} does NOT mean the catalog has no songs.
 */
public record SyncResultDto(int artistCount, int albumCount, int songCount, Instant syncedAt) {}
