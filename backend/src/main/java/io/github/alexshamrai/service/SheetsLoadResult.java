package io.github.alexshamrai.service;

import java.util.List;

/**
 * Outcome of a load from Google Sheets. {@code warnings} lists every skipped row
 * (malformed, blank-keyed, duplicate, or orphaned). A non-empty warnings list means the
 * database does NOT fully mirror the spreadsheet — event-driven pushes must stay
 * suspended or they would erase the skipped rows from the sheet.
 */
public record SheetsLoadResult(int artistCount, int albumCount, int songCount, List<String> warnings) {

    public boolean clean() {
        return warnings.isEmpty();
    }
}
