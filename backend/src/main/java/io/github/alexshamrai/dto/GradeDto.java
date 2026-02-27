package io.github.alexshamrai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GradeDto {

    @Min(value = 1, message = "Grade must be at least 1")
    @Max(value = 5, message = "Grade must be at most 5")
    private int grade;
}
