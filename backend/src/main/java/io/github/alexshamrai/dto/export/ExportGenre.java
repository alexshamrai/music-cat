package io.github.alexshamrai.dto.export;

import java.util.List;

/** One genre group in the enriched export; {@code genre} is the Genre display name. */
public record ExportGenre(
        String genre,
        List<ExportArtist> artists
) {}
