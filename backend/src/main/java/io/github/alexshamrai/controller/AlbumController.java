package io.github.alexshamrai.controller;

import io.github.alexshamrai.dto.AlbumCreateDto;
import io.github.alexshamrai.dto.AlbumDto;
import io.github.alexshamrai.dto.AlbumFilterParams;
import io.github.alexshamrai.dto.AlbumSummaryDto;
import io.github.alexshamrai.dto.AlbumUpdateDto;
import io.github.alexshamrai.dto.GradeDto;
import io.github.alexshamrai.service.AlbumService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/albums")
@RequiredArgsConstructor
public class AlbumController {

    private final AlbumService albumService;

    @GetMapping
    public ResponseEntity<List<AlbumSummaryDto>> list(
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String subgenre,
            @RequestParam(required = false) Long artistId,
            @RequestParam(required = false) String artistName,
            @RequestParam(required = false) List<String> tag,
            @RequestParam(required = false) Integer minGrade,
            @RequestParam(required = false) Integer maxGrade,
            @RequestParam(required = false) Boolean favorite,
            @RequestParam(required = false) Boolean unrated) {

        var filters = AlbumFilterParams.builder()
                .genre(genre)
                .subgenre(subgenre)
                .artistId(artistId)
                .artistName(artistName)
                .tag(tag)
                .minGrade(minGrade)
                .maxGrade(maxGrade)
                .favorite(favorite)
                .unrated(unrated)
                .build();

        return ResponseEntity.ok(albumService.findAll(filters));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlbumDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(albumService.findById(id));
    }

    @PostMapping
    public ResponseEntity<AlbumSummaryDto> create(@Valid @RequestBody AlbumCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(albumService.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AlbumSummaryDto> update(@PathVariable Long id, @Valid @RequestBody AlbumUpdateDto dto) {
        return ResponseEntity.ok(albumService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        albumService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/grade")
    public ResponseEntity<AlbumSummaryDto> setGrade(@PathVariable Long id, @Valid @RequestBody GradeDto dto) {
        return ResponseEntity.ok(albumService.setGrade(id, dto.getGrade()));
    }

    @PatchMapping("/{id}/favorite")
    public ResponseEntity<AlbumSummaryDto> toggleFavorite(@PathVariable Long id) {
        return ResponseEntity.ok(albumService.toggleFavorite(id));
    }

    @PutMapping("/{id}/tags")
    public ResponseEntity<AlbumSummaryDto> setTags(@PathVariable Long id, @RequestBody List<String> tagNames) {
        if (tagNames.size() > 50) {
            throw new IllegalArgumentException("Too many tags (max 50)");
        }
        return ResponseEntity.ok(albumService.setTags(id, tagNames));
    }
}
