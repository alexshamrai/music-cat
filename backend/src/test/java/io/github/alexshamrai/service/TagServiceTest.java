package io.github.alexshamrai.service;

import io.github.alexshamrai.domain.TagEntity;
import io.github.alexshamrai.dto.TagDto;
import io.github.alexshamrai.exception.NotFoundException;
import io.github.alexshamrai.repository.TagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static io.github.alexshamrai.TestDataFactory.tagWithId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock
    private TagRepository tagRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private TagService tagService;

    // ==================== findAll tests ====================

    @Test
    void findAll_returnsTags() {
        when(tagRepository.findAll()).thenReturn(List.of(tagWithId(1L, "rock"), tagWithId(2L, "jazz")));

        List<TagDto> result = tagService.findAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("rock");
        assertThat(result.get(1).getName()).isEqualTo("jazz");
    }

    @Test
    void findAll_emptyResult_returnsEmptyList() {
        when(tagRepository.findAll()).thenReturn(List.of());

        List<TagDto> result = tagService.findAll();

        assertThat(result).isEmpty();
    }

    // ==================== create tests ====================

    @Test
    void create_newTag_savesAndReturns() {
        when(tagRepository.findByName("rock")).thenReturn(Optional.empty());
        when(tagRepository.save(any(TagEntity.class))).thenAnswer(inv -> {
            TagEntity tag = inv.getArgument(0);
            tag.setId(1L);
            return tag;
        });

        TagDto result = tagService.create("rock");

        assertThat(result.getName()).isEqualTo("rock");
        verify(tagRepository).save(any(TagEntity.class));
    }

    @Test
    void create_existingTag_returnsExistingWithoutSaving() {
        var existing = tagWithId(1L, "rock");
        when(tagRepository.findByName("rock")).thenReturn(Optional.of(existing));

        TagDto result = tagService.create("rock");

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("rock");
        verify(tagRepository, never()).save(any(TagEntity.class));
    }

    @Test
    void create_stripsWhitespace() {
        when(tagRepository.findByName("rock")).thenReturn(Optional.empty());
        when(tagRepository.save(any(TagEntity.class))).thenAnswer(inv -> {
            TagEntity tag = inv.getArgument(0);
            tag.setId(1L);
            return tag;
        });

        TagDto result = tagService.create("  rock  ");

        assertThat(result.getName()).isEqualTo("rock");
    }

    // ==================== delete tests ====================

    @Test
    void delete_existingTag_deletesSuccessfully() {
        var tag = tagWithId(1L, "rock");
        when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));

        tagService.delete(1L);

        verify(tagRepository).delete(tag);
    }

    @Test
    void delete_nonExistentId_throwsNotFoundException() {
        when(tagRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tagService.delete(999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("999");
    }
}
