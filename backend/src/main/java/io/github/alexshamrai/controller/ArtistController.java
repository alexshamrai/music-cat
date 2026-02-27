package io.github.alexshamrai.controller;

import io.github.alexshamrai.domain.Genre;
import io.github.alexshamrai.dto.ArtistCreateDto;
import io.github.alexshamrai.dto.ArtistDto;
import io.github.alexshamrai.dto.ArtistUpdateDto;
import io.github.alexshamrai.service.ArtistService;
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
@RequestMapping("/api/artists")
@RequiredArgsConstructor
public class ArtistController {

    private final ArtistService artistService;

    @GetMapping
    public ResponseEntity<List<ArtistDto>> list(
            @RequestParam(required = false) Genre genre,
            @RequestParam(required = false) String subgenre,
            @RequestParam(required = false) Boolean favorite,
            @RequestParam(required = false) String tag) {
        return ResponseEntity.ok(artistService.findAll(genre, subgenre, favorite, tag));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ArtistDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(artistService.findById(id));
    }

    @PostMapping
    public ResponseEntity<ArtistDto> create(@Valid @RequestBody ArtistCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(artistService.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ArtistDto> update(@PathVariable Long id, @Valid @RequestBody ArtistUpdateDto dto) {
        return ResponseEntity.ok(artistService.update(id, dto));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ArtistDto> partialUpdate(@PathVariable Long id, @Valid @RequestBody ArtistUpdateDto dto) {
        return ResponseEntity.ok(artistService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        artistService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/favorite")
    public ResponseEntity<ArtistDto> toggleFavorite(@PathVariable Long id) {
        return ResponseEntity.ok(artistService.toggleFavorite(id));
    }

    @PutMapping("/{id}/tags")
    public ResponseEntity<ArtistDto> setTags(@PathVariable Long id, @RequestBody List<String> tagNames) {
        if (tagNames.size() > 50) {
            throw new IllegalArgumentException("Too many tags (max 50)");
        }
        return ResponseEntity.ok(artistService.setTags(id, tagNames));
    }
}
