package io.github.alexshamrai.sheets;

import io.github.alexshamrai.config.SheetsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

/**
 * Local, file-backed stand-in for Google Sheets (mode=fake). Reads/writes the three tabs to a
 * JSON file via {@link FakeSheetStore} — no network, no credentials, no connection to prod.
 */
@Service
@ConditionalOnExpression(
        "${music-cat.sheets.enabled:false} and '${music-cat.sheets.mode:google}'.toLowerCase() == 'fake'")
public class FakeSheetsClient implements SheetsClient {

    private static final Logger log = LoggerFactory.getLogger(FakeSheetsClient.class);

    private final FakeSheetStore store;
    private final Path file;

    public FakeSheetsClient(FakeSheetStore store, SheetsProperties props) {
        this.store = store;
        this.file = Path.of(props.fakeFile());
        log.warn("Sheets client: FAKE (file {}) — NOT connected to Google Sheets", file.toAbsolutePath());
    }

    @Override
    public List<List<Object>> read(String sheetName) {
        return store.read(file, sheetName);
    }

    @Override
    public void overwrite(String sheetName, List<List<Object>> rows) {
        store.write(file, sheetName, rows);
    }
}
