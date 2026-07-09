package io.github.alexshamrai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Batch edit of an album: its title/year plus the full desired set of songs.
 * The song list is reconciled against the album's current songs — existing
 * songs (by id) are kept/renamed, songs absent from the list are deleted, and
 * entries without an id are created. Applied atomically in one transaction.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlbumEditDto {

    @NotBlank(message = "Title must not be empty")
    private String title;

    private Integer year;

    @NotNull(message = "Songs list is required")
    @Valid
    private List<SongEditInput> songs;
}
