package io.github.alexshamrai.startup;

import io.github.alexshamrai.repository.ArtistRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CatalogAutoImporterIntegrationTest {

    @Autowired
    private ArtistRepository artistRepository;

    @Test
    void onApplicationReady_sheetsDisabled_startsEmpty() {
        // Sheets are disabled in the test profile → boot leaves the DB empty, no error.
        assertThat(artistRepository.count()).isEqualTo(0);
    }
}
