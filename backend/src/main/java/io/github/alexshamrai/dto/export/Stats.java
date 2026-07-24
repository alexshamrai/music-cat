package io.github.alexshamrai.dto.export;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Stats(int totalGenres, int totalArtists, int totalAlbums, int totalTracks) {}
