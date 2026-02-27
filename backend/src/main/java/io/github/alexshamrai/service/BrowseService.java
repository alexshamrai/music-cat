package io.github.alexshamrai.service;

import io.github.alexshamrai.domain.Genre;
import io.github.alexshamrai.dto.AlbumFilterParams;
import io.github.alexshamrai.dto.AlbumSummaryDto;
import io.github.alexshamrai.dto.ArtistDto;
import io.github.alexshamrai.dto.BrowseFavoritesDto;
import io.github.alexshamrai.dto.BrowseGenreDto;
import io.github.alexshamrai.dto.BrowseStatsDto;
import io.github.alexshamrai.dto.BrowseTagDto;
import io.github.alexshamrai.repository.AlbumRepository;
import io.github.alexshamrai.repository.ArtistRepository;
import io.github.alexshamrai.repository.SongRepository;
import io.github.alexshamrai.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrowseService {

    private final ArtistRepository artistRepository;
    private final AlbumRepository albumRepository;
    private final SongRepository songRepository;
    private final TagRepository tagRepository;
    private final ArtistService artistService;
    private final AlbumService albumService;

    public List<BrowseGenreDto> getGenres() {
        return Arrays.stream(Genre.values())
                .map(genre -> {
                    var artists = artistRepository.findAll((root, query, cb) ->
                            cb.equal(root.get("genre"), genre));
                    if (artists.isEmpty()) {
                        return null;
                    }
                    long albumCount = artists.stream()
                            .mapToLong(a -> a.getAlbums().size())
                            .sum();
                    return BrowseGenreDto.builder()
                            .genre(genre)
                            .artistCount(artists.size())
                            .albumCount(albumCount)
                            .build();
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(dto -> dto.getGenre().getDisplayName()))
                .toList();
    }

    public List<ArtistDto> getArtistsByGenre(Genre genre) {
        return artistService.findAll(genre, null, null, null);
    }

    public List<AlbumSummaryDto> getAlbumsByArtist(Long artistId) {
        var filters = AlbumFilterParams.builder()
                .artistId(artistId)
                .build();
        return albumService.findAll(filters);
    }

    public List<BrowseTagDto> getTags() {
        return tagRepository.findAll().stream()
                .map(tag -> BrowseTagDto.builder()
                        .tag(tag.getName())
                        .artistCount(tag.getArtists().size())
                        .albumCount(tag.getAlbums().size())
                        .build())
                .sorted(Comparator.comparingLong((BrowseTagDto dto) -> dto.getArtistCount() + dto.getAlbumCount()).reversed())
                .toList();
    }

    public BrowseFavoritesDto getFavorites() {
        var favoriteArtists = artistService.findAll(null, null, true, null);
        var favoriteAlbums = albumService.findAll(AlbumFilterParams.builder().favorite(true).build());

        return BrowseFavoritesDto.builder()
                .favoriteArtists(favoriteArtists)
                .favoriteAlbums(favoriteAlbums)
                .build();
    }

    public BrowseStatsDto getStats() {
        long totalArtists = artistRepository.count();
        long totalAlbums = albumRepository.count();
        long totalSongs = songRepository.count();
        long totalTags = tagRepository.count();

        long totalGenres = Arrays.stream(Genre.values())
                .filter(genre -> artistRepository.count((root, query, cb) ->
                        cb.equal(root.get("genre"), genre)) > 0)
                .count();

        long favoriteArtists = artistRepository.count((root, query, cb) ->
                cb.isTrue(root.get("isFavorite")));
        long favoriteAlbums = albumRepository.count((root, query, cb) ->
                cb.isTrue(root.get("isFavorite")));
        long ratedAlbums = albumRepository.count((root, query, cb) ->
                cb.isNotNull(root.get("grade")));
        long unratedAlbums = albumRepository.count((root, query, cb) ->
                cb.isNull(root.get("grade")));

        Map<String, Long> gradeDistribution = new LinkedHashMap<>();
        for (int grade = 1; grade <= 5; grade++) {
            final int g = grade;
            long count = albumRepository.count((root, query, cb) ->
                    cb.equal(root.get("grade"), g));
            gradeDistribution.put(String.valueOf(grade), count);
        }

        return BrowseStatsDto.builder()
                .totalArtists(totalArtists)
                .totalAlbums(totalAlbums)
                .totalSongs(totalSongs)
                .totalTags(totalTags)
                .totalGenres(totalGenres)
                .favoriteArtists(favoriteArtists)
                .favoriteAlbums(favoriteAlbums)
                .ratedAlbums(ratedAlbums)
                .unratedAlbums(unratedAlbums)
                .gradeDistribution(gradeDistribution)
                .build();
    }
}
