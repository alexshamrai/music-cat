package io.github.alexshamrai.sheets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression test for a boot-time crash: with sheets enabled but a missing/unreadable
 * credentials file, GoogleSheetsConfig#sheets used to be forced eager by the
 * SheetSyncListener/SheetSyncService/GoogleSheetsClient chain (SheetSyncListener is
 * @Lazy(false) for event-listener wiring), failing Spring context refresh itself instead of
 * degrading gracefully like every other Sheets failure. The context here must load
 * successfully; the failure should surface only on the first real Sheets API call, where
 * CatalogAutoImporter/SheetSyncService/SheetSyncListener already catch and recover.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        // lazy-initialization matches the cloud profile — this is where the crash-loop bug
        // manifested. Under eager (default) init Spring pre-instantiates every singleton
        // regardless of ObjectProvider indirection, so the bug can't reproduce without this.
        "spring.main.lazy-initialization=true",
        "music-cat.sheets.enabled=true",
        "music-cat.sheets.credentials-path=/nonexistent/does-not-exist.json",
        "music-cat.sheets.spreadsheet-id=dummy",
        "spring.datasource.url=jdbc:h2:mem:testdb-missing-creds;DB_CLOSE_DELAY=-1"
})
class GoogleSheetsClientMissingCredentialsTest {

    @Autowired
    private SheetsClient sheetsClient;

    @Test
    void contextLoads_despiteMissingCredentialsFile() {
        assertThat(sheetsClient).isNotNull();
    }

    @Test
    void firstRealCall_throwsInsteadOfCrashingBoot() {
        assertThatThrownBy(() -> sheetsClient.read("Artists"))
                .isInstanceOf(RuntimeException.class);
    }
}
