package io.github.alexshamrai.event;

/**
 * Published after every mutating service call that modifies catalog data.
 *
 * @param structural true when artists/albums/songs were created, deleted, or imported
 *                   (Songs tab must be rewritten). false when only grades, favorites, or
 *                   tags changed (Songs tab can be skipped).
 */
public record CatalogChangedEvent(boolean structural) {}
