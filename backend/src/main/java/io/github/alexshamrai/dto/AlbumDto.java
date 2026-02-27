package io.github.alexshamrai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import io.github.alexshamrai.domain.Genre;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlbumDto {

    private Long id;
    private String title;
    private Integer year;
    private Integer grade;
    private boolean favorite;
    private ArtistSummaryDto artist;
    private List<String> tags;
    private List<SongDto> songs;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ArtistSummaryDto {
        private Long id;
        private String name;
        private Genre genre;
    }
}
