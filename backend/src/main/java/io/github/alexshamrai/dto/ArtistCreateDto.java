package io.github.alexshamrai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArtistCreateDto {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Genre is required")
    private String genre;

    private String subgenre;
}
