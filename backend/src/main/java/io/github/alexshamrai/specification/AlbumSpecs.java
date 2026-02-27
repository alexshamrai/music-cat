package io.github.alexshamrai.specification;

import io.github.alexshamrai.domain.AlbumEntity;
import io.github.alexshamrai.domain.ArtistEntity;
import io.github.alexshamrai.domain.Genre;
import io.github.alexshamrai.domain.TagEntity;
import jakarta.persistence.criteria.Join;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AlbumSpecs {

    public static Specification<AlbumEntity> artistGenreEquals(Genre genre) {
        return (root, query, cb) -> {
            Join<AlbumEntity, ArtistEntity> artistJoin = root.join("artist");
            return cb.equal(artistJoin.get("genre"), genre);
        };
    }

    public static Specification<AlbumEntity> artistSubgenreEquals(String subgenre) {
        return (root, query, cb) -> {
            Join<AlbumEntity, ArtistEntity> artistJoin = root.join("artist");
            return cb.equal(artistJoin.get("subgenre"), subgenre);
        };
    }

    public static Specification<AlbumEntity> byArtist(Long artistId) {
        return (root, query, cb) -> cb.equal(root.get("artist").get("id"), artistId);
    }

    public static Specification<AlbumEntity> artistNameContains(String name) {
        return (root, query, cb) -> {
            Join<AlbumEntity, ArtistEntity> artistJoin = root.join("artist");
            return cb.like(cb.lower(artistJoin.get("name")),
                    "%" + name.toLowerCase() + "%");
        };
    }

    public static Specification<AlbumEntity> hasTag(String tagName) {
        return (root, query, cb) -> {
            query.distinct(true);
            Join<AlbumEntity, TagEntity> tagJoin = root.join("tags");
            return cb.equal(tagJoin.get("name"), tagName);
        };
    }

    public static Specification<AlbumEntity> hasAnyTag(List<String> tagNames) {
        return (root, query, cb) -> {
            query.distinct(true);
            Join<AlbumEntity, TagEntity> tagJoin = root.join("tags");
            return tagJoin.get("name").in(tagNames);
        };
    }

    public static Specification<AlbumEntity> gradeGte(int min) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("grade"), min);
    }

    public static Specification<AlbumEntity> gradeLte(int max) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("grade"), max);
    }

    public static Specification<AlbumEntity> isFavorite() {
        return (root, query, cb) -> cb.isTrue(root.get("isFavorite"));
    }

    public static Specification<AlbumEntity> isUnrated() {
        return (root, query, cb) -> cb.isNull(root.get("grade"));
    }
}
