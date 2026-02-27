package io.github.alexshamrai.service;

import io.github.alexshamrai.domain.TagEntity;
import io.github.alexshamrai.dto.TagDto;
import io.github.alexshamrai.exception.NotFoundException;
import io.github.alexshamrai.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TagService {

    private final TagRepository tagRepository;

    public List<TagDto> findAll() {
        return tagRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public TagDto create(String name) {
        var existing = tagRepository.findByName(name.strip());
        if (existing.isPresent()) {
            return toDto(existing.get());
        }

        var tag = TagEntity.builder()
                .name(name.strip())
                .build();
        return toDto(tagRepository.save(tag));
    }

    @Transactional
    public void delete(Long id) {
        var tag = tagRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Tag not found with id: " + id));

        tag.getArtists().forEach(artist -> artist.getTags().remove(tag));
        tag.getAlbums().forEach(album -> album.getTags().remove(tag));

        tagRepository.delete(tag);
    }

    private TagDto toDto(TagEntity entity) {
        return TagDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .build();
    }
}
