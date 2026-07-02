package io.github.alexshamrai.service;

import io.github.alexshamrai.domain.AlbumEntity;
import io.github.alexshamrai.domain.SongEntity;
import io.github.alexshamrai.domain.TagEntity;
import io.github.alexshamrai.dto.AlbumCreateDto;
import io.github.alexshamrai.dto.AlbumDto;
import io.github.alexshamrai.dto.AlbumFilterParams;
import io.github.alexshamrai.dto.AlbumSummaryDto;
import io.github.alexshamrai.dto.AlbumUpdateDto;
import io.github.alexshamrai.dto.SongDto;
import io.github.alexshamrai.event.CatalogChangedEvent;
import io.github.alexshamrai.exception.NotFoundException;
import io.github.alexshamrai.repository.AlbumRepository;
import io.github.alexshamrai.repository.ArtistRepository;
import io.github.alexshamrai.repository.TagRepository;
import io.github.alexshamrai.specification.AlbumSpecs;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlbumService {

    private final AlbumRepository albumRepository;
    private final ArtistRepository artistRepository;
    private final TagRepository tagRepository;
    private final ApplicationEventPublisher eventPublisher;

    public List<AlbumSummaryDto> findAll(AlbumFilterParams filters) {
        Specification<AlbumEntity> spec = buildSpecification(filters);

        return albumRepository.findAll(spec).stream()
                .map(this::toSummaryDto)
                .toList();
    }

    public AlbumDto findById(Long id) {
        return toDto(getEntityById(id));
    }

    @Transactional
    public AlbumSummaryDto create(AlbumCreateDto dto) {
        var artist = artistRepository.findById(dto.getArtistId())
                .orElseThrow(() -> new NotFoundException("Artist not found with id: " + dto.getArtistId()));

        var album = AlbumEntity.builder()
                .title(dto.getTitle())
                .year(dto.getYear())
                .artist(artist)
                .build();

        AlbumSummaryDto result = toSummaryDto(albumRepository.save(album));
        eventPublisher.publishEvent(new CatalogChangedEvent(true));
        return result;
    }

    @Transactional
    public AlbumSummaryDto update(Long id, AlbumUpdateDto dto) {
        var album = getEntityById(id);

        if (dto.getTitle() != null) {
            album.setTitle(dto.getTitle());
        }
        if (dto.getYear() != null) {
            album.setYear(dto.getYear());
        }

        AlbumSummaryDto result = toSummaryDto(albumRepository.save(album));
        eventPublisher.publishEvent(new CatalogChangedEvent(true));
        return result;
    }

    @Transactional
    public void delete(Long id) {
        var album = albumRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Album not found with id: " + id));
        albumRepository.delete(album);
        eventPublisher.publishEvent(new CatalogChangedEvent(true));
    }

    @Transactional
    public AlbumSummaryDto setGrade(Long id, int grade) {
        var album = getEntityById(id);
        album.setGrade(grade);
        AlbumSummaryDto result = toSummaryDto(albumRepository.save(album));
        eventPublisher.publishEvent(new CatalogChangedEvent(false));
        return result;
    }

    @Transactional
    public AlbumSummaryDto toggleFavorite(Long id) {
        var album = getEntityById(id);
        album.setFavorite(!album.isFavorite());
        AlbumSummaryDto result = toSummaryDto(albumRepository.save(album));
        eventPublisher.publishEvent(new CatalogChangedEvent(false));
        return result;
    }

    @Transactional
    public AlbumSummaryDto setTags(Long id, List<String> tagNames) {
        var album = getEntityById(id);

        Set<String> uniqueNames = tagNames.stream()
                .map(String::strip)
                .filter(name -> !name.isEmpty())
                .map(TagNames::requireValid)
                .collect(Collectors.toSet());

        Set<TagEntity> tags = uniqueNames.stream()
                .map(name -> tagRepository.findByName(name)
                        .orElseGet(() -> tagRepository.save(TagEntity.builder().name(name).build())))
                .collect(Collectors.toSet());

        album.setTags(tags);
        AlbumSummaryDto result = toSummaryDto(albumRepository.save(album));
        eventPublisher.publishEvent(new CatalogChangedEvent(false));
        return result;
    }

    private AlbumEntity getEntityById(Long id) {
        return albumRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Album not found with id: " + id));
    }

    Specification<AlbumEntity> buildSpecification(AlbumFilterParams filters) {
        Specification<AlbumEntity> spec = Specification.where(
                (Specification<AlbumEntity>) (root, query, cb) -> cb.conjunction());

        if (filters == null) {
            return spec;
        }

        if (filters.getGenre() != null) {
            spec = spec.and(AlbumSpecs.artistGenreEquals(filters.getGenre()));
        }
        if (filters.getSubgenre() != null) {
            spec = spec.and(AlbumSpecs.artistSubgenreEquals(filters.getSubgenre()));
        }
        if (filters.getArtistId() != null) {
            spec = spec.and(AlbumSpecs.byArtist(filters.getArtistId()));
        }
        if (filters.getArtistName() != null) {
            spec = spec.and(AlbumSpecs.artistNameContains(filters.getArtistName()));
        }
        if (filters.getTag() != null && !filters.getTag().isEmpty()) {
            spec = spec.and(AlbumSpecs.hasAnyTag(filters.getTag()));
        }
        if (filters.getMinGrade() != null) {
            spec = spec.and(AlbumSpecs.gradeGte(filters.getMinGrade()));
        }
        if (filters.getMaxGrade() != null) {
            spec = spec.and(AlbumSpecs.gradeLte(filters.getMaxGrade()));
        }
        if (Boolean.TRUE.equals(filters.getFavorite())) {
            spec = spec.and(AlbumSpecs.isFavorite());
        }
        if (Boolean.TRUE.equals(filters.getUnrated())) {
            spec = spec.and(AlbumSpecs.isUnrated());
        }

        return spec;
    }

    private AlbumDto toDto(AlbumEntity entity) {
        return AlbumDto.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .year(entity.getYear())
                .grade(entity.getGrade())
                .favorite(entity.isFavorite())
                .artist(AlbumDto.ArtistSummaryDto.builder()
                        .id(entity.getArtist().getId())
                        .name(entity.getArtist().getName())
                        .genre(entity.getArtist().getGenre())
                        .build())
                .tags(entity.getTags().stream()
                        .map(TagEntity::getName)
                        .sorted()
                        .toList())
                .songs(entity.getSongs().stream()
                        .sorted(Comparator.comparingInt(SongEntity::getDiscNumber)
                                .thenComparingInt(SongEntity::getTrackNumber))
                        .map(this::toSongDto)
                        .toList())
                .build();
    }

    private AlbumSummaryDto toSummaryDto(AlbumEntity entity) {
        return AlbumSummaryDto.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .year(entity.getYear())
                .grade(entity.getGrade())
                .favorite(entity.isFavorite())
                .artistName(entity.getArtist().getName())
                .genre(entity.getArtist().getGenre())
                .tags(entity.getTags().stream()
                        .map(TagEntity::getName)
                        .sorted()
                        .toList())
                .songCount(entity.getSongs().size())
                .build();
    }

    private SongDto toSongDto(SongEntity entity) {
        return SongDto.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .trackNumber(entity.getTrackNumber())
                .discNumber(entity.getDiscNumber())
                .build();
    }
}
