package io.github.alexshamrai.dto.catalog;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GenreGroup(io.github.alexshamrai.domain.Genre genre, List<Artist> artists) {}
