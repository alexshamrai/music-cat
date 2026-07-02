package io.github.alexshamrai.dto.export;

import java.util.List;

/** One artist in the enriched export, with curation fields. */
public record ExportArtist(
        String name,
        String subgenre,
        boolean isFavorite,
        List<String> tags,
        List<ExportAlbum> albums
) {}
