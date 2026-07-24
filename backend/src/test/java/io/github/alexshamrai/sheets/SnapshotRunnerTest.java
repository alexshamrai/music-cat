package io.github.alexshamrai.sheets;

import io.github.alexshamrai.config.SheetsProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SnapshotRunnerTest {

    @Test
    void doSnapshot_readsThreeTabs_writesThemToFakeFile_neverWritesToSheet(@TempDir Path dir) {
        Path file = dir.resolve("fake-sheets.json");
        SheetsClient sheetsClient = mock(SheetsClient.class);
        when(sheetsClient.read("Artists")).thenReturn(List.of(List.of("name"), List.of("Pink Floyd")));
        when(sheetsClient.read("Albums")).thenReturn(List.of(List.of("artist"), List.of("Pink Floyd")));
        when(sheetsClient.read("Songs")).thenReturn(List.of(List.of("artist"), List.of("Pink Floyd")));

        FakeSheetStore store = new FakeSheetStore();
        SheetsProperties props = new SheetsProperties(true, null, null, "google", file.toString(), true);

        SnapshotRunner runner = new SnapshotRunner(sheetsClient, store, props, null);
        runner.doSnapshot();

        assertThat(store.read(file, "Artists")).hasSize(2);
        assertThat(store.read(file, "Albums")).hasSize(2);
        assertThat(store.read(file, "Songs")).hasSize(2);
        // Read-only guarantee: the snapshot never writes back to the live sheet.
        verify(sheetsClient, never()).overwrite(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList());
    }
}
