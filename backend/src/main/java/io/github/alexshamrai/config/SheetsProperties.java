package io.github.alexshamrai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Google Sheets integration.
 * Bind with prefix "music-cat.sheets".
 */
@ConfigurationProperties(prefix = "music-cat.sheets")
public record SheetsProperties(
        boolean enabled,
        String credentialsPath,
        String spreadsheetId
) {}
