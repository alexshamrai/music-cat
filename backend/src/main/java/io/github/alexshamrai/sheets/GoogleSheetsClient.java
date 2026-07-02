package io.github.alexshamrai.sheets;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ClearValuesRequest;
import com.google.api.services.sheets.v4.model.ValueRange;
import io.github.alexshamrai.config.SheetsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Service
@ConditionalOnProperty(name = "music-cat.sheets.enabled", havingValue = "true")
public class GoogleSheetsClient implements SheetsClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleSheetsClient.class);

    private static final int CHUNK_SIZE = 10_000;
    private static final int MAX_RETRIES = 3;

    private final Sheets sheets;
    private final String spreadsheetId;

    public GoogleSheetsClient(Sheets sheets, SheetsProperties props) {
        this.sheets = sheets;
        this.spreadsheetId = props.spreadsheetId();
    }

    @Override
    public List<List<Object>> read(String sheetName) {
        return executeWithRetry(() -> {
            var response = sheets.spreadsheets().values()
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
     */
    @Override
    public void overwrite(String sheetName, List<List<Object>> rows) {
        executeWithRetry(() -> {
            sheets.spreadsheets().values()
                    .clear(spreadsheetId, sheetName, new ClearValuesRequest())
                    .execute();
            return null;
        });

        int total = rows.size();
        int offset = 0;
        while (offset < total) {
            int end = Math.min(offset + CHUNK_SIZE, total);
            List<List<Object>> chunk = rows.subList(offset, end);

            String range = offset == 0
                    ? sheetName + "!A1"
                    : sheetName + "!A" + (offset + 1);

            ValueRange body = new ValueRange().setValues(chunk);
            final String finalRange = range;

            executeWithRetry(() -> {
                sheets.spreadsheets().values()
                        .update(spreadsheetId, finalRange, body)
                        .setValueInputOption("RAW")
                        .execute();
                return null;
            });

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
