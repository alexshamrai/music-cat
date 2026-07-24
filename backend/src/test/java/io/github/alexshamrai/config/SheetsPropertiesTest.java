package io.github.alexshamrai.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies mode normalization/validation and fakeFile defaulting in the canonical constructor.
 */
class SheetsPropertiesTest {

    @Test
    void mode_google_isAccepted() {
        SheetsProperties props = new SheetsProperties(true, null, null, "google", null, false);

        assertThat(props.mode()).isEqualTo("google");
        assertThat(props.fakeFile()).isEqualTo("./data/fake-sheets.json");
    }

    @Test
    void mode_fake_isAccepted() {
        SheetsProperties props = new SheetsProperties(true, null, null, "fake", null, false);

        assertThat(props.mode()).isEqualTo("fake");
        assertThat(props.fakeFile()).isEqualTo("./data/fake-sheets.json");
    }

    @Test
    void mode_uppercaseGoogle_isNormalizedToLowercase() {
        SheetsProperties props = new SheetsProperties(true, null, null, "GOOGLE", null, false);

        assertThat(props.mode()).isEqualTo("google");
    }

    @Test
    void mode_mixedCaseFake_isNormalizedToLowercase() {
        SheetsProperties props = new SheetsProperties(true, null, null, "Fake", null, false);

        assertThat(props.mode()).isEqualTo("fake");
    }

    @Test
    void mode_nullOrBlank_defaultsToGoogle() {
        assertThat(new SheetsProperties(true, null, null, null, null, false).mode()).isEqualTo("google");
        assertThat(new SheetsProperties(true, null, null, "", null, false).mode()).isEqualTo("google");
        assertThat(new SheetsProperties(true, null, null, "   ", null, false).mode()).isEqualTo("google");
    }

    @Test
    void mode_invalidValue_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new SheetsProperties(true, null, null, "googel", null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("googel")
                .hasMessageContaining("google")
                .hasMessageContaining("fake");
    }
}
