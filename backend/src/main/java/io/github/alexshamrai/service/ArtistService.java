package io.github.alexshamrai.service;

import io.github.alexshamrai.domain.ArtistEntity;
import io.github.alexshamrai.domain.Genre;
import io.github.alexshamrai.domain.TagEntity;
import io.github.alexshamrai.dto.ArtistCreateDto;
import io.github.alexshamrai.dto.ArtistDto;
import io.github.alexshamrai.dto.ArtistUpdateDto;
import io.github.alexshamrai.exception.NotFoundException;
import io.github.alexshamrai.repository.ArtistRepository;
import io.github.alexshamrai.repository.TagRepository;
import jakarta.persistence.criteria.Join;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArtistService {

    private final ArtistRepository artistRepository;
    private final TagRepository tagRepository;

    public List<ArtistDto> findAll(Genre genre, String subgenre, Boolean favorite, String tag) {
        Specification<ArtistEntity> spec = Specification.where(
                (Specification<ArtistEntity>) (root, query, cb) -> cb.conjunction());

        if (genre != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("genre"), genre));
        }
        if (subgenre != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("subgenre"), subgenre));
        }
        if (Boolean.TRUE.equals(favorite)) {
            spec = spec.and((root, query, cb) -> cb.isTrue(root.get("isFavorite")));
        }
        if (tag != null) {
            spec = spec.and((root, query, cb) -> {
                query.distinct(true);
                Join<ArtistEntity, TagEntity> tagJoin = root.join("tags");
                return cb.equal(tagJoin.get("name"), tag);
            });
        }

        return artistRepository.findAll(spec).stream()
                .map(this::toDto)
                .toList();
    }

    public ArtistDto findById(Long id) {
        return toDto(getEntityById(id));
    }

    @Transactional
    public ArtistDto create(ArtistCreateDto dto) {
        var artist = ArtistEntity.builder()
                .name(dto.getName())
                .genre(dto.getGenre())
                .subgenre(dto.getSubgenre())
                .build();

        return toDto(artistRepository.save(artist));
    }

    @Transactional
    public ArtistDto update(Long id, ArtistUpdateDto dto) {
        var artist = getEntityById(id);

        if (dto.getName() != null) {
            artist.setName(dto.getName());
        }
        if (dto.getGenre() != null) {
            artist.setGenre(dto.getGenre());
        }
        if (dto.getSubgenre() != null) {
            artist.setSubgenre(dto.getSubgenre());
        }

        return toDto(artistRepository.save(artist));
    }

    @Transactional
    public void delete(Long id) {
        var artist = artistRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Artist not found with id: " + id));
        artistRepository.delete(artist);
    }

    @Transactional
    public ArtistDto toggleFavorite(Long id) {
        var artist = getEntityById(id);
        artist.setFavorite(!artist.isFavorite());
        return toDto(artistRepository.save(artist));
    }

    @Transactional
    public ArtistDto setTags(Long id, List<String> tagNames) {
        var artist = getEntityById(id);

        Set<String> uniqueNames = tagNames.stream()
                .map(String::strip)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toSet());

        Set<TagEntity> tags = uniqueNames.stream()
                .map(name -> tagRepository.findByName(name)
                        .orElseGet(() -> tagRepository.save(TagEntity.builder().name(name).build())))
                .collect(Collectors.toSet());

        artist.setTags(tags);
        return toDto(artistRepository.save(artist));
    }

    private ArtistEntity getEntityById(Long id) {
        return artistRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Artist not found with id: " + id));
    }

    private ArtistDto toDto(ArtistEntity entity) {
        return ArtistDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .genre(entity.getGenre())
                .subgenre(entity.getSubgenre())
                .favorite(entity.isFavorite())
                .tags(entity.getTags().stream()
                        .map(TagEntity::getName)
                        .sorted()
                        .toList())
                .albumCount(entity.getAlbums().size())
                .build();
    }
}
