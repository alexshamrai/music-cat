package io.github.alexshamrai.sheets;

import java.util.List;

/**
 * Parsed representation of a row from the Albums sheet tab.
 */
public record AlbumRow(
        String artistName,
        String title,
        Integer year,
        Integer grade,
        boolean favorite,
        List<String> tags
) {}
