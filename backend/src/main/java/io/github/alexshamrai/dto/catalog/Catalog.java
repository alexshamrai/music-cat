package io.github.alexshamrai.dto.catalog;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Catalog(
    String scannedAt,
    String rootPath,
    Stats stats,
    List<String> warnings,
    List<GenreGroup> catalog
) {}
