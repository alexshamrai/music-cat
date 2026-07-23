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
public class ArtistDto {

    private Long id;
    private String name;
    private Genre genre;
    private String subgenre;
    @JsonProperty("isFavorite")
    private boolean favorite;
    private List<String> tags;
    private int albumCount;
}
