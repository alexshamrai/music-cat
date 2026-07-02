package io.github.alexshamrai.sheets;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import io.github.alexshamrai.config.SheetsProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression test for a bug only caught by a live Task 16 smoke test against a real
 * spreadsheet: writing chunks beyond the first via values.update at a computed row offset
 * (even with a fully-bounded range) is rejected with "exceeds grid limits" once an earlier
 * chunk has already sized the grid to exactly its own row count — values.update never grows
 * a sheet's grid to fit a write beyond current bounds. Every mocked-SheetsClient test
 * elsewhere in the suite bypasses GoogleSheetsClient entirely, so this was never exercised
 * against the real API's grid-sizing semantics. values.append is the API's actual mechanism
 * for growing a sheet to fit more rows, so it's used for every chunk after the first.
 */
class GoogleSheetsClientTest {

    private static final int CHUNK_SIZE = 10_000;

    @Test
    void overwrite_largeDataset_firstChunkUpdatesAtA1_laterChunksAppend() throws Exception {
        Sheets sheets = mock(Sheets.class, RETURNS_DEEP_STUBS);
        SheetsProperties props = new SheetsProperties(true, "creds.json", "sheet-123");
        @SuppressWarnings("unchecked")
        ObjectProvider<Sheets> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(sheets);

        GoogleSheetsClient client = new GoogleSheetsClient(provider, props);

        // header (5 columns) + 10,876 data rows = 10,877 total rows -> two chunks
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("artist", "album", "disc", "track", "title"));
        for (int i = 0; i < 10_876; i++) {
            rows.add(List.of("Artist " + i, "Album " + i, 1, i + 1, "Song " + i));
        }

        client.overwrite("Songs", rows);

        ArgumentCaptor<ValueRange> updateBodyCaptor = ArgumentCaptor.forClass(ValueRange.class);
        verify(sheets.spreadsheets().values(), times(1))
                .update(eq("sheet-123"), eq("Songs!A1"), updateBodyCaptor.capture());
        assertThat(updateBodyCaptor.getValue().getValues()).hasSize(CHUNK_SIZE);

        ArgumentCaptor<ValueRange> appendBodyCaptor = ArgumentCaptor.forClass(ValueRange.class);
        verify(sheets.spreadsheets().values(), times(1))
                .append(eq("sheet-123"), eq("Songs!A1"), appendBodyCaptor.capture());
        assertThat(appendBodyCaptor.getValue().getValues()).hasSize(877);
    }

    @Test
    void overwrite_singleChunk_onlyUpdatesAtA1_neverAppends() throws Exception {
        Sheets sheets = mock(Sheets.class, RETURNS_DEEP_STUBS);
        SheetsProperties props = new SheetsProperties(true, "creds.json", "sheet-123");
        @SuppressWarnings("unchecked")
        ObjectProvider<Sheets> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(sheets);

        GoogleSheetsClient client = new GoogleSheetsClient(provider, props);

        List<List<Object>> rows = List.of(
                List.of("name", "genre", "subgenre", "favorite", "tags"),
                List.of("Test Artist", "Rock", "", "FALSE", ""));

        client.overwrite("Artists", rows);

        verify(sheets.spreadsheets().values())
                .update(eq("sheet-123"), eq("Artists!A1"), any(ValueRange.class));
        verify(sheets.spreadsheets().values(), never())
                .append(any(), any(), any());
    }
}
