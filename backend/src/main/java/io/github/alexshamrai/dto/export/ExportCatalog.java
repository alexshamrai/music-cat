package io.github.alexshamrai.dto.export;

import java.time.Instant;
import java.util.List;

/**
 * Root of the enriched catalog export (GET /api/catalog/export/json) — the original
 * catalog structure extended with curation fields (grades, favorites, tags).
 */
public record ExportCatalog(
        Instant exportedAt,
        Stats stats,
        List<ExportGenre> genres
) {}
