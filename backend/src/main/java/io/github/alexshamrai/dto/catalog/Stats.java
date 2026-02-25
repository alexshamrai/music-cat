package io.github.alexshamrai.dto.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Stats(int totalGenres, int totalArtists, int totalAlbums, int totalTracks) {}
