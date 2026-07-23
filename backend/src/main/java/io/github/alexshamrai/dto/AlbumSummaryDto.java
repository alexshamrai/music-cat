package io.github.alexshamrai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class AlbumSummaryDto {

    private Long id;
    private String title;
    private Integer year;
    private Integer grade;
    @JsonProperty("isFavorite")
    private boolean favorite;
    private String artistName;
    private Genre genre;
    private List<String> tags;
    private int songCount;
}
