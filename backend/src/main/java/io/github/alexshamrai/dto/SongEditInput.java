package io.github.alexshamrai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One entry in the desired final song set of an {@link AlbumEditDto}.
 * A {@code null} {@link #id} means a brand-new song to create; a non-null id
 * identifies an existing song of the album to keep (and possibly rename).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SongEditInput {

    private Long id;

    @NotBlank(message = "Song title must not be empty")
    private String title;
}
