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
    void onApplicationReady_missingFile_skipsGracefully() {
        // The test profile has catalog-path=non-existent-catalog.json
        // Auto-importer should skip without error, DB should be empty
        assertThat(artistRepository.count()).isEqualTo(0);
    }
}
