package io.github.alexshamrai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlbumCreateDto {

    @NotBlank(message = "Title is required")
    private String title;

    private Integer year;

    @NotNull(message = "Artist ID is required")
    private Long artistId;
}
