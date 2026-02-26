package io.github.alexshamrai.repository;

import io.github.alexshamrai.domain.AlbumEntity;
import io.github.alexshamrai.domain.ArtistEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import static io.github.alexshamrai.TestDataFactory.album;
import static io.github.alexshamrai.TestDataFactory.artist;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class AlbumRepositoryTest {

    @Autowired
    private AlbumRepository albumRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void existsByArtistIdAndTitle_exists_returnsTrue() {
        ArtistEntity savedArtist = entityManager.persistAndFlush(artist("Pink Floyd", "Rock"));
        entityManager.persistAndFlush(album("The Wall", 1979, savedArtist));

        boolean result = albumRepository.existsByArtistIdAndTitle(savedArtist.getId(), "The Wall");

        assertThat(result).isTrue();
    }

    @Test
    void existsByArtistIdAndTitle_wrongTitle_returnsFalse() {
        ArtistEntity savedArtist = entityManager.persistAndFlush(artist("Pink Floyd", "Rock"));
        entityManager.persistAndFlush(album("The Wall", 1979, savedArtist));

        boolean result = albumRepository.existsByArtistIdAndTitle(savedArtist.getId(), "Animals");

        assertThat(result).isFalse();
    }

    @Test
    void existsByArtistIdAndTitle_wrongArtistId_returnsFalse() {
        ArtistEntity artist1 = entityManager.persistAndFlush(artist("Pink Floyd", "Rock"));
        ArtistEntity artist2 = entityManager.persistAndFlush(artist("Led Zeppelin", "Rock"));
        entityManager.persistAndFlush(album("The Wall", 1979, artist1));

        boolean result = albumRepository.existsByArtistIdAndTitle(artist2.getId(), "The Wall");

        assertThat(result).isFalse();
    }

    @Test
    void existsByArtistIdAndTitle_noAlbums_returnsFalse() {
        ArtistEntity savedArtist = entityManager.persistAndFlush(artist("Empty Artist", "Rock"));

        boolean result = albumRepository.existsByArtistIdAndTitle(savedArtist.getId(), "Any Album");

        assertThat(result).isFalse();
    }
}
