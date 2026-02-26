package io.github.alexshamrai;

import io.github.alexshamrai.domain.AlbumEntity;
import io.github.alexshamrai.domain.ArtistEntity;
import io.github.alexshamrai.domain.SongEntity;
import io.github.alexshamrai.domain.TagEntity;
import io.github.alexshamrai.dto.ArtistCreateDto;
import io.github.alexshamrai.dto.ArtistDto;
import io.github.alexshamrai.dto.ArtistUpdateDto;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TestDataFactory {

    private TestDataFactory() {}

    public static ArtistEntity artist(String name, String genre) {
        return ArtistEntity.builder()
                .name(name)
                .genre(genre)
                .albums(new ArrayList<>())
                .tags(new HashSet<>())
                .build();
    }

    public static ArtistEntity artistWithId(Long id, String name, String genre) {
        return ArtistEntity.builder()
                .id(id)
                .name(name)
                .genre(genre)
                .albums(new ArrayList<>())
                .tags(new HashSet<>())
                .build();
    }

    public static ArtistEntity artistWithAlbums(Long id, String name, String genre, List<AlbumEntity> albums) {
        return ArtistEntity.builder()
                .id(id)
                .name(name)
                .genre(genre)
                .albums(albums)
                .tags(new HashSet<>())
                .build();
    }

    public static ArtistEntity artistWithTags(Long id, String name, String genre, Set<TagEntity> tags) {
        return ArtistEntity.builder()
                .id(id)
                .name(name)
                .genre(genre)
                .albums(new ArrayList<>())
                .tags(tags)
                .build();
    }

    public static AlbumEntity album(String title, Integer year, ArtistEntity artist) {
        return AlbumEntity.builder()
                .title(title)
                .year(year)
                .artist(artist)
                .songs(new ArrayList<>())
                .tags(new HashSet<>())
                .build();
    }

    public static SongEntity song(String title, int track, int disc, AlbumEntity album) {
        return SongEntity.builder()
                .title(title)
                .trackNumber(track)
                .discNumber(disc)
                .album(album)
                .build();
    }

    public static TagEntity tag(String name) {
        return TagEntity.builder()
                .name(name)
                .artists(new HashSet<>())
                .albums(new HashSet<>())
                .build();
    }

    public static TagEntity tagWithId(Long id, String name) {
        return TagEntity.builder()
                .id(id)
                .name(name)
                .artists(new HashSet<>())
                .albums(new HashSet<>())
                .build();
    }

    public static ArtistCreateDto createDto(String name, String genre) {
        return new ArtistCreateDto(name, genre, null);
    }

    public static ArtistCreateDto createDto(String name, String genre, String subgenre) {
        return new ArtistCreateDto(name, genre, subgenre);
    }

    public static ArtistUpdateDto updateDto(String name, String genre, String subgenre) {
        return new ArtistUpdateDto(name, genre, subgenre);
    }

    public static ArtistDto artistDto(Long id, String name, String genre) {
        return ArtistDto.builder()
                .id(id)
                .name(name)
                .genre(genre)
                .favorite(false)
                .tags(List.of())
                .albumCount(0)
                .build();
    }
}
