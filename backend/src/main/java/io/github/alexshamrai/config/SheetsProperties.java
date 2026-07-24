package io.github.alexshamrai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

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
    /**
     * Canonical constructor. Explicitly annotated {@code @ConstructorBinding} so Spring
     * unambiguously binds via this constructor even though a second (3-arg) convenience
     * constructor exists below for direct/manual construction (e.g. tests).
     */
    @ConstructorBinding
    public SheetsProperties(boolean enabled, String credentialsPath, String spreadsheetId, String mode, String fakeFile, boolean snapshot) {
        this.enabled = enabled;
        this.credentialsPath = credentialsPath;
        this.spreadsheetId = spreadsheetId;
        String resolvedMode = (mode == null || mode.isBlank()) ? "google" : mode;
        resolvedMode = resolvedMode.trim().toLowerCase(java.util.Locale.ROOT);
        if (!resolvedMode.equals("google") && !resolvedMode.equals("fake")) {
            throw new IllegalArgumentException(
                    "Invalid music-cat.sheets.mode '" + mode + "': valid values are 'google' or 'fake'");
        }
        this.mode = resolvedMode;
        this.fakeFile = (fakeFile == null || fakeFile.isBlank()) ? "./data/fake-sheets.json" : fakeFile;
        this.snapshot = snapshot;
    }

    /** Back-compat convenience for call sites (tests) that predate the mode/fake fields. */
    public SheetsProperties(boolean enabled, String credentialsPath, String spreadsheetId) {
        this(enabled, credentialsPath, spreadsheetId, "google", "./data/fake-sheets.json", false);
    }
}
