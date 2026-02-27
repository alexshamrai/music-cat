package io.github.alexshamrai.controller;

import io.github.alexshamrai.domain.Genre;
import io.github.alexshamrai.dto.AlbumSummaryDto;
import io.github.alexshamrai.dto.ArtistDto;
import io.github.alexshamrai.dto.BrowseFavoritesDto;
import io.github.alexshamrai.dto.BrowseGenreDto;
import io.github.alexshamrai.dto.BrowseStatsDto;
import io.github.alexshamrai.dto.BrowseTagDto;
import io.github.alexshamrai.service.BrowseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/browse")
@RequiredArgsConstructor
public class BrowseController {

    private final BrowseService browseService;

    @GetMapping("/genres")
    public ResponseEntity<List<BrowseGenreDto>> getGenres() {
        return ResponseEntity.ok(browseService.getGenres());
    }

    @GetMapping("/genres/{genre}")
    public ResponseEntity<List<ArtistDto>> getArtistsByGenre(@PathVariable Genre genre) {
        return ResponseEntity.ok(browseService.getArtistsByGenre(genre));
    }

    @GetMapping("/genres/{genre}/artists/{artistId}")
    public ResponseEntity<List<AlbumSummaryDto>> getAlbumsByArtist(
            @PathVariable Genre genre,
            @PathVariable Long artistId) {
        return ResponseEntity.ok(browseService.getAlbumsByArtist(artistId));
    }

    @GetMapping("/tags")
    public ResponseEntity<List<BrowseTagDto>> getTags() {
        return ResponseEntity.ok(browseService.getTags());
    }

    @GetMapping("/favorites")
    public ResponseEntity<BrowseFavoritesDto> getFavorites() {
        return ResponseEntity.ok(browseService.getFavorites());
    }

    @GetMapping("/stats")
    public ResponseEntity<BrowseStatsDto> getStats() {
        return ResponseEntity.ok(browseService.getStats());
    }
}
