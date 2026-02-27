package io.github.alexshamrai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BrowseStatsDto {

    private long totalArtists;
    private long totalAlbums;
    private long totalSongs;
    private long totalTags;
    private long totalGenres;
    private long favoriteArtists;
    private long favoriteAlbums;
    private long ratedAlbums;
    private long unratedAlbums;
    private Map<String, Long> gradeDistribution;
}
