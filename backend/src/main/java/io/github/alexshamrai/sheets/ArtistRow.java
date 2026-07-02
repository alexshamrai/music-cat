package io.github.alexshamrai.sheets;

import io.github.alexshamrai.domain.Genre;

import java.util.List;

/**
 * Parsed representation of a row from the Artists sheet tab.
 *
 * <p>Known limitation: artists are keyed by name. Two artists with the same name in different
 * genres are unsupported by the sync and must be renamed before syncing.
 */
public record ArtistRow(
        String name,
        Genre genre,
        String subgenre,
        boolean favorite,
        List<String> tags
) {}
