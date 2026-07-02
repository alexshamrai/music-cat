package io.github.alexshamrai.dto.export;

/** One song in the enriched export. */
public record ExportSong(
        String title,
        int trackNumber,
        int discNumber
) {}
