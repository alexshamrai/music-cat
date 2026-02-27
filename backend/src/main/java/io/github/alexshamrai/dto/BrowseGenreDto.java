package io.github.alexshamrai.dto;

import io.github.alexshamrai.domain.Genre;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BrowseGenreDto {

    private Genre genre;
    private long artistCount;
    private long albumCount;
}
