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
public class AlbumFilterParams {

    private String genre;
    private String subgenre;
    private Long artistId;
    private String artistName;
    private List<String> tag;
    private Integer minGrade;
    private Integer maxGrade;
    private Boolean favorite;
    private Boolean unrated;
}
