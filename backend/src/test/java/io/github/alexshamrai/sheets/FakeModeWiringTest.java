package io.github.alexshamrai.sheets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With sheets enabled + mode=fake, the SheetsClient bean must be the FakeSheetsClient and the
 * GoogleSheetsClient must be absent (so no credentials are ever required).
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "music-cat.sheets.enabled=true",
        "music-cat.sheets.mode=fake",
        "music-cat.sheets.fake-file=build/tmp/fake-mode-wiring.json"
})
class FakeModeWiringTest {

    @Autowired
    private ObjectProvider<SheetsClient> sheetsClientProvider;

    @Autowired
    private ObjectProvider<GoogleSheetsClient> googleClientProvider;

    @Test
    void fakeMode_wiresFakeClient_notGoogleClient() {
        assertThat(sheetsClientProvider.getIfAvailable()).isInstanceOf(FakeSheetsClient.class);
        assertThat(googleClientProvider.getIfAvailable()).isNull();
    }
}
