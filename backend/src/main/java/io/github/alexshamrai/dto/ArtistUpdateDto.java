package io.github.alexshamrai.dto;

import io.github.alexshamrai.domain.Genre;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArtistUpdateDto {

    @Size(min = 1, message = "Name must not be empty")
    private String name;

    private Genre genre;

    private String subgenre;
}
