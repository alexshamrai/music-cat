package io.github.alexshamrai.service;

import io.github.alexshamrai.domain.AlbumEntity;
import io.github.alexshamrai.dto.AlbumDto;
import io.github.alexshamrai.dto.AlbumFilterParams;
import io.github.alexshamrai.exception.NoMatchException;
import io.github.alexshamrai.repository.AlbumRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RandomPickService {

    private final AlbumRepository albumRepository;
    private final AlbumService albumService;

    public AlbumDto randomAlbum(AlbumFilterParams filters) {
        Specification<AlbumEntity> spec = albumService.buildSpecification(filters);
        long count = albumRepository.count(spec);

        if (count == 0) {
            throw new NoMatchException("No albums match the given filters");
        }

        int randomOffset = ThreadLocalRandom.current().nextInt((int) count);
        AlbumEntity entity = albumRepository.findAll(spec, PageRequest.of(randomOffset, 1))
                .getContent()
                .getFirst();

        return albumService.findById(entity.getId());
    }

    public List<AlbumDto> randomAlbums(AlbumFilterParams filters, int count) {
        Specification<AlbumEntity> spec = albumService.buildSpecification(filters);
        List<AlbumEntity> candidates = new ArrayList<>(albumRepository.findAll(spec));

        if (candidates.isEmpty()) {
            return List.of();
        }

        Collections.shuffle(candidates);
        return candidates.stream()
                .limit(count)
                .map(entity -> albumService.findById(entity.getId()))
                .toList();
    }
}
