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
public class ArtistDto {

    private Long id;
    private String name;
    private String genre;
    private String subgenre;
    private boolean favorite;
    private List<String> tags;
    private int albumCount;
}
