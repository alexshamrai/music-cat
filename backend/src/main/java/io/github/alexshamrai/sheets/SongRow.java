package io.github.alexshamrai.sheets;

/**
 * Parsed representation of a row from the Songs sheet tab.
 */
public record SongRow(
        String artistName,
        String albumTitle,
        int disc,
        int track,
        String title
) {}
