package io.github.alexshamrai.repository;

import io.github.alexshamrai.domain.TagEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static io.github.alexshamrai.TestDataFactory.tag;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class TagRepositoryTest {

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findByName_exists_returnsTag() {
        entityManager.persistAndFlush(tag("rock"));

        var result = tagRepository.findByName("rock");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("rock");
    }

    @Test
    void findByName_notExists_returnsEmpty() {
        var result = tagRepository.findByName("nonexistent");

        assertThat(result).isEmpty();
    }

    @Test
    void findByNameIn_allExist_returnsAll() {
        entityManager.persistAndFlush(tag("rock"));
        entityManager.persistAndFlush(tag("jazz"));
        entityManager.persistAndFlush(tag("blues"));

        List<TagEntity> result = tagRepository.findByNameIn(List.of("rock", "jazz", "blues"));

        assertThat(result).hasSize(3);
        assertThat(result).extracting(TagEntity::getName).containsExactlyInAnyOrder("rock", "jazz", "blues");
    }

    @Test
    void findByNameIn_someExist_returnsOnlyExisting() {
        entityManager.persistAndFlush(tag("rock"));
        entityManager.persistAndFlush(tag("jazz"));

        List<TagEntity> result = tagRepository.findByNameIn(List.of("rock", "jazz", "nonexistent"));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(TagEntity::getName).containsExactlyInAnyOrder("rock", "jazz");
    }

    @Test
    void findByNameIn_noneExist_returnsEmptyList() {
        List<TagEntity> result = tagRepository.findByNameIn(List.of("a", "b", "c"));

        assertThat(result).isEmpty();
    }

    @Test
    void findByNameIn_emptyCollection_returnsEmptyList() {
        List<TagEntity> result = tagRepository.findByNameIn(List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void save_duplicateName_throwsException() {
        entityManager.persistAndFlush(tag("unique"));

        TagEntity duplicate = tag("unique");

        assertThatThrownBy(() -> {
            tagRepository.saveAndFlush(duplicate);
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}
