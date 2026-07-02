package io.github.alexshamrai.dto.export;

import java.util.List;

/** One album in the enriched export, with curation fields; year/grade stay null when unset. */
public record ExportAlbum(
        String title,
        Integer year,
        Integer grade,
        boolean isFavorite,
        List<String> tags,
        List<ExportSong> songs
) {}
