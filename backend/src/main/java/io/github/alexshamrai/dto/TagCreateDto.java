package io.github.alexshamrai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TagCreateDto {

    @NotBlank(message = "Tag name is required")
    private String name;
}
