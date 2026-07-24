package io.github.alexshamrai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Google Sheets integration.
 * Bind with prefix "music-cat.sheets".
 *
 * <p>{@code mode} selects the SheetsClient implementation: "google" (default, real API) or
 * "fake" (local file-backed stand-in for offline dev/testing). {@code fakeFile} is where the
 * fake stores its data; {@code snapshot} activates the read-only prod → fake-file snapshot.
 */
@ConfigurationProperties(prefix = "music-cat.sheets")
public record SheetsProperties(
        boolean enabled,
        String credentialsPath,
        String spreadsheetId,
        String mode,
        String fakeFile,
        boolean snapshot
) {
    public SheetsProperties {
        if (mode == null || mode.isBlank()) {
            mode = "google";
        }
        if (fakeFile == null || fakeFile.isBlank()) {
            fakeFile = "./data/fake-sheets.json";
        }
    }

    /** Back-compat convenience for call sites (tests) that predate the mode/fake fields. */
    public SheetsProperties(boolean enabled, String credentialsPath, String spreadsheetId) {
        this(enabled, credentialsPath, spreadsheetId, "google", "./data/fake-sheets.json", false);
    }
}
