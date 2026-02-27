package io.github.alexshamrai.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlbumUpdateDto {

    @Size(min = 1, message = "Title must not be empty")
    private String title;

    private Integer year;
}
