package io.github.alexshamrai.sheets;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ClearValuesRequest;
import com.google.api.services.sheets.v4.model.ValueRange;
import io.github.alexshamrai.config.SheetsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Service
@ConditionalOnExpression(
        "${music-cat.sheets.enabled:false} and '${music-cat.sheets.mode:google}'.toLowerCase() == 'google'")
public class GoogleSheetsClient implements SheetsClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleSheetsClient.class);

    private static final int CHUNK_SIZE = 10_000;
    private static final int MAX_RETRIES = 3;

    // ObjectProvider defers credential loading (GoogleSheetsConfig#sheets does file I/O) until
    // the first real API call, instead of at bean construction time. SheetSyncListener/
    // CatalogAutoImporter force this bean's dependency chain eager (@Lazy(false), needed for
    // event-listener wiring), so a direct Sheets dependency here would fail Spring context
    // refresh itself on a missing/bad credentials file — crash-looping the whole app instead of
    // degrading gracefully like every other Sheets failure already does.
    private final ObjectProvider<Sheets> sheetsProvider;
    private final String spreadsheetId;

    public GoogleSheetsClient(ObjectProvider<Sheets> sheetsProvider, SheetsProperties props) {
        this.sheetsProvider = sheetsProvider;
        this.spreadsheetId = props.spreadsheetId();
        String tail = (spreadsheetId == null || spreadsheetId.isBlank())
                ? "unset"
                : (spreadsheetId.length() < 6 ? spreadsheetId : "…" + spreadsheetId.substring(spreadsheetId.length() - 6));
        log.info("Sheets client: GOOGLE (spreadsheet {})", tail);
    }

    private Sheets sheets() {
        return sheetsProvider.getObject();
    }

    @Override
    public List<List<Object>> read(String sheetName) {
        return executeWithRetry(() -> {
            var response = sheets().spreadsheets().values()
                    .get(spreadsheetId, sheetName)
                    .execute();
            List<List<Object>> values = response.getValues();
            return values != null ? values : Collections.emptyList();
        });
    }

    /**
     * Replaces all content in {@code sheetName} with {@code rows}.
     *
     * <p><b>Non-atomic:</b> the operation clears the tab first, then writes rows in chunks.
     * A failure between the clear and the final write chunk leaves the tab empty or partially
     * filled. Callers (e.g. the Task 10 sync) must be able to re-invoke {@code overwrite}
     * to self-heal: the next successful call will restore the full dataset.
     *
     * <p>The first chunk is written with {@code values.update} at A1; every later chunk uses
     * {@code values.append} instead of another {@code update} at a computed offset — confirmed
     * against a real spreadsheet (Task 16) that {@code update} refuses to write beyond the
     * sheet's current row count even with a fully-bounded target range (e.g.
     * "Songs!A10001:E20000" against a 10,000-row grid fails with "exceeds grid limits");
     * {@code append} is the API's actual mechanism for growing a sheet's grid to fit more rows.
     */
    @Override
    public void overwrite(String sheetName, List<List<Object>> rows) {
        executeWithRetry(() -> {
            sheets().spreadsheets().values()
                    .clear(spreadsheetId, sheetName, new ClearValuesRequest())
                    .execute();
            return null;
        });

        int total = rows.size();
        int offset = 0;
        boolean firstChunk = true;
        while (offset < total) {
            int end = Math.min(offset + CHUNK_SIZE, total);
            List<List<Object>> chunk = rows.subList(offset, end);
            ValueRange body = new ValueRange().setValues(chunk);
            boolean isFirst = firstChunk;

            if (isFirst) {
                executeWithRetry(() -> {
                    sheets().spreadsheets().values()
                            .update(spreadsheetId, sheetName + "!A1", body)
                            .setValueInputOption("RAW")
                            .execute();
                    return null;
                });
            } else {
                executeWithRetry(() -> {
                    sheets().spreadsheets().values()
                            .append(spreadsheetId, sheetName + "!A1", body)
                            .setValueInputOption("RAW")
                            .setInsertDataOption("INSERT_ROWS")
                            .execute();
                    return null;
                });
            }

            firstChunk = false;
            offset = end;
        }
    }

    @FunctionalInterface
    private interface SheetsOperation<T> {
        T execute() throws IOException;
    }

    private <T> T executeWithRetry(SheetsOperation<T> operation) {
        int attempt = 0;
        while (true) {
            try {
                return operation.execute();
            } catch (GoogleJsonResponseException e) {
                if (e.getStatusCode() == 429 && attempt < MAX_RETRIES) {
                    long delayMs = (long) Math.pow(2, attempt) * 1000L;
                    log.warn("Sheets API rate limited (429), retrying in {}ms (attempt {}/{})",
                            delayMs, attempt + 1, MAX_RETRIES);
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during Sheets API retry", ie);
                    }
                    attempt++;
                } else {
                    throw new RuntimeException("Sheets API request failed", e);
                }
            } catch (IOException e) {
                throw new RuntimeException("Sheets API I/O error", e);
            }
        }
    }
}
