package io.github.alexshamrai.service;

import io.github.alexshamrai.domain.AlbumEntity;
import io.github.alexshamrai.domain.SongEntity;
import io.github.alexshamrai.domain.TagEntity;
import io.github.alexshamrai.dto.AlbumCreateDto;
import io.github.alexshamrai.dto.AlbumDto;
import io.github.alexshamrai.dto.AlbumEditDto;
import io.github.alexshamrai.dto.AlbumFilterParams;
import io.github.alexshamrai.dto.AlbumSummaryDto;
import io.github.alexshamrai.dto.AlbumUpdateDto;
import io.github.alexshamrai.dto.SongDto;
import io.github.alexshamrai.dto.SongEditInput;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

        if (dto.getTitle() != null && !dto.getTitle().equals(album.getTitle())) {
            requireNoSiblingTitleCollision(album, dto.getTitle());
            album.setTitle(dto.getTitle());
        }
        if (dto.getYear() != null) {
            album.setYear(dto.getYear());
        }

        AlbumSummaryDto result = toSummaryDto(albumRepository.save(album));
        eventPublisher.publishEvent(new CatalogChangedEvent(true));
        return result;
    }

    /**
     * Batch edit: replace the album's title/year and reconcile its songs against the
     * supplied desired set — all in one transaction and one structural sync push.
     * Existing songs (matched by id) are renamed with track/disc preserved; songs
     * absent from the payload are deleted (orphanRemoval); id-less entries are created
     * on disc 1 with the next free track number.
     */
    @Transactional
    public AlbumDto edit(Long id, AlbumEditDto dto) {
        var album = getEntityById(id);

        if (!dto.getTitle().equals(album.getTitle())) {
            requireNoSiblingTitleCollision(album, dto.getTitle());
            album.setTitle(dto.getTitle());
        }
        album.setYear(dto.getYear());

        reconcileSongs(album, dto.getSongs());

        albumRepository.save(album);
        eventPublisher.publishEvent(new CatalogChangedEvent(true));
        return toDto(album);
    }

    /** Rejects renaming an album to a title another album by the same artist already has. */
    private void requireNoSiblingTitleCollision(AlbumEntity album, String newTitle) {
        if (albumRepository.existsByArtistIdAndTitle(album.getArtist().getId(), newTitle)) {
            throw new IllegalArgumentException(
                    "Another album titled '" + newTitle + "' already exists for this artist");
        }
    }

    private void reconcileSongs(AlbumEntity album, List<SongEditInput> desired) {
        List<SongEntity> current = album.getSongs();
        Map<Long, SongEntity> byId = current.stream()
                .filter(s -> s.getId() != null)
                .collect(Collectors.toMap(SongEntity::getId, s -> s));

        int nextTrack = current.stream()
                .filter(s -> s.getDiscNumber() == 1)
                .mapToInt(SongEntity::getTrackNumber)
                .max()
                .orElse(0);

        Set<Long> keptIds = new HashSet<>();
        List<SongEntity> additions = new ArrayList<>();
        for (SongEditInput input : desired) {
            if (input.getId() != null) {
                SongEntity song = byId.get(input.getId());
                if (song == null) {
                    throw new IllegalArgumentException(
                            "Song " + input.getId() + " does not belong to this album — reload and try again");
                }
                song.setTitle(input.getTitle());
                keptIds.add(input.getId());
            } else {
                additions.add(SongEntity.builder()
                        .title(input.getTitle())
                        .trackNumber(++nextTrack)
                        .discNumber(1)
                        .album(album)
                        .build());
            }
        }

        current.removeIf(s -> s.getId() != null && !keptIds.contains(s.getId()));
        current.addAll(additions);
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
