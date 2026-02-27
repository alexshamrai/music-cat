package io.github.alexshamrai.dto;

import io.github.alexshamrai.domain.Genre;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArtistCreateDto {

    @NotBlank(message = "Name is required")
    private String name;

    @NotNull(message = "Genre is required")
    private Genre genre;

    private String subgenre;
}
