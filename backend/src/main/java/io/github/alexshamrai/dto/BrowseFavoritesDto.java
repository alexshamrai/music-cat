package io.github.alexshamrai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BrowseFavoritesDto {

    private List<ArtistDto> favoriteArtists;
    private List<AlbumSummaryDto> favoriteAlbums;
}
