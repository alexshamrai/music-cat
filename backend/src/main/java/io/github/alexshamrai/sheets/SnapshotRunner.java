package io.github.alexshamrai.sheets;

import io.github.alexshamrai.config.SheetsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only snapshot: reads the three tabs from the LIVE sheet (via the real SheetsClient under
 * mode=google) and writes them verbatim into the fake-sheet file, then shuts the app down.
 * Activated only by {@code music-cat.sheets.snapshot=true}. It calls only {@code read()} — never
 * {@code overwrite()} — so it can never modify the live spreadsheet.
 */
@Component
@org.springframework.context.annotation.Lazy(false)
@ConditionalOnProperty(name = "music-cat.sheets.snapshot", havingValue = "true")
public class SnapshotRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SnapshotRunner.class);
    private static final List<String> TABS = List.of("Artists", "Albums", "Songs");

    private final SheetsClient sheetsClient;
    private final FakeSheetStore store;
    private final Path file;
    private final ConfigurableApplicationContext context;

    public SnapshotRunner(SheetsClient sheetsClient, FakeSheetStore store,
                          SheetsProperties props, ConfigurableApplicationContext context) {
        this.sheetsClient = sheetsClient;
        this.store = store;
        this.file = Path.of(props.fakeFile());
        this.context = context;
    }

    /** Reads the three tabs and writes them to the fake file. No process exit — unit-testable. */
    void doSnapshot() {
        log.info("Snapshotting live Google Sheets tabs {} into {}", TABS, file.toAbsolutePath());
        Map<String, List<List<Object>>> snapshot = new LinkedHashMap<>();
        for (String tab : TABS) {
            List<List<Object>> rows = sheetsClient.read(tab);
            snapshot.put(tab, rows);
            log.info("  {} — {} row(s)", tab, rows.size());
        }
        store.writeAll(file, snapshot);
        log.info("Snapshot complete → {}", file.toAbsolutePath());
    }

    @Override
    public void run(ApplicationArguments args) {
        doSnapshot();
        int code = SpringApplication.exit(context, () -> 0);
        System.exit(code);
    }
}
