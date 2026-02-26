package io.github.alexshamrai.repository;

import io.github.alexshamrai.domain.ArtistEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import static io.github.alexshamrai.TestDataFactory.artist;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ArtistRepositoryTest {

    @Autowired
    private ArtistRepository artistRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findByNameAndGenre_exists_returnsArtist() {
        entityManager.persistAndFlush(artist("Genesis", "Rock"));

        var result = artistRepository.findByNameAndGenre("Genesis", "Rock");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Genesis");
        assertThat(result.get().getGenre()).isEqualTo("Rock");
    }

    @Test
    void findByNameAndGenre_wrongGenre_returnsEmpty() {
        entityManager.persistAndFlush(artist("Genesis", "Rock"));

        var result = artistRepository.findByNameAndGenre("Genesis", "Jazz");

        assertThat(result).isEmpty();
    }

    @Test
    void findByNameAndGenre_wrongName_returnsEmpty() {
        entityManager.persistAndFlush(artist("Genesis", "Rock"));

        var result = artistRepository.findByNameAndGenre("Pink Floyd", "Rock");

        assertThat(result).isEmpty();
    }

    @Test
    void findByNameAndGenre_caseSensitive() {
        entityManager.persistAndFlush(artist("Genesis", "Rock"));

        var result = artistRepository.findByNameAndGenre("genesis", "Rock");

        assertThat(result).isEmpty();
    }

    @Test
    void save_validArtist_generatesId() {
        ArtistEntity saved = artistRepository.save(artist("New Artist", "Jazz"));

        assertThat(saved.getId()).isNotNull();
    }
}
