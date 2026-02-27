package io.github.alexshamrai.repository;

import io.github.alexshamrai.domain.ArtistEntity;
import io.github.alexshamrai.domain.Genre;
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
        entityManager.persistAndFlush(artist("Genesis", Genre.PROGRESSIVE_ROCK));

        var result = artistRepository.findByNameAndGenre("Genesis", Genre.PROGRESSIVE_ROCK);

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Genesis");
        assertThat(result.get().getGenre()).isEqualTo(Genre.PROGRESSIVE_ROCK);
    }

    @Test
    void findByNameAndGenre_wrongGenre_returnsEmpty() {
        entityManager.persistAndFlush(artist("Genesis", Genre.PROGRESSIVE_ROCK));

        var result = artistRepository.findByNameAndGenre("Genesis", Genre.JAZZ_AND_FUNK);

        assertThat(result).isEmpty();
    }

    @Test
    void findByNameAndGenre_wrongName_returnsEmpty() {
        entityManager.persistAndFlush(artist("Genesis", Genre.PROGRESSIVE_ROCK));

        var result = artistRepository.findByNameAndGenre("Pink Floyd", Genre.PROGRESSIVE_ROCK);

        assertThat(result).isEmpty();
    }

    @Test
    void findByNameAndGenre_caseSensitive() {
        entityManager.persistAndFlush(artist("Genesis", Genre.PROGRESSIVE_ROCK));

        var result = artistRepository.findByNameAndGenre("genesis", Genre.PROGRESSIVE_ROCK);

        assertThat(result).isEmpty();
    }

    @Test
    void save_validArtist_generatesId() {
        ArtistEntity saved = artistRepository.save(artist("New Artist", Genre.JAZZ_AND_FUNK));

        assertThat(saved.getId()).isNotNull();
    }
}
