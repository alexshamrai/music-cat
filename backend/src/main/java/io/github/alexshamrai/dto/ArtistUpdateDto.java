package io.github.alexshamrai.dto;

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

    @Size(min = 1, message = "Genre must not be empty")
    private String genre;

    private String subgenre;
}
