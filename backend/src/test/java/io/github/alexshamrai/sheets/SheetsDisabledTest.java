package io.github.alexshamrai.sheets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the Spring context starts successfully when sheets are disabled (the default)
 * and that no SheetsClient bean is registered.
 */
@SpringBootTest
@ActiveProfiles("test")
class SheetsDisabledTest {

    @Autowired
    private ObjectProvider<SheetsClient> sheetsClientProvider;

    @Test
    void contextLoads_withSheetsDisabled_noBeanPresent() {
        SheetsClient client = sheetsClientProvider.getIfAvailable();
        assertThat(client)
                .as("SheetsClient bean must NOT be present when music-cat.sheets.enabled=false")
                .isNull();
    }
}
