package io.github.alexshamrai.controller;

import io.github.alexshamrai.domain.Genre;
import io.github.alexshamrai.dto.AlbumDto;
import io.github.alexshamrai.dto.AlbumFilterParams;
import io.github.alexshamrai.service.RandomPickService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/random")
@RequiredArgsConstructor
public class RandomController {

    private final RandomPickService randomPickService;

    @GetMapping("/album")
    public ResponseEntity<AlbumDto> randomAlbum(
            @RequestParam(required = false) Genre genre,
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

        return ResponseEntity.ok(randomPickService.randomAlbum(filters));
    }

    @GetMapping("/albums")
    public ResponseEntity<List<AlbumDto>> randomAlbums(
            @RequestParam(required = false) Genre genre,
            @RequestParam(required = false) String subgenre,
            @RequestParam(required = false) Long artistId,
            @RequestParam(required = false) String artistName,
            @RequestParam(required = false) List<String> tag,
            @RequestParam(required = false) Integer minGrade,
            @RequestParam(required = false) Integer maxGrade,
            @RequestParam(required = false) Boolean favorite,
            @RequestParam(required = false) Boolean unrated,
            @RequestParam(defaultValue = "5") int count) {

        count = Math.clamp(count, 1, 20);

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

        return ResponseEntity.ok(randomPickService.randomAlbums(filters, count));
    }
}
