package io.github.alexshamrai.sheets;

import io.github.alexshamrai.config.SheetsProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FakeSheetsClientTest {

    @Test
    void overwrite_thenRead_roundTripsThroughFile(@TempDir Path dir) {
        Path file = dir.resolve("fake-sheets.json");
        SheetsProperties props = new SheetsProperties(true, null, null, "fake", file.toString(), false);
        FakeSheetsClient client = new FakeSheetsClient(new FakeSheetStore(), props);

        client.overwrite("Albums", List.of(
                List.of("artist", "title", "year", "grade", "favorite", "tags"),
                List.of("Pink Floyd", "The Dark Side of the Moon", "1973", "5", "TRUE", "classic")));

        assertThat(client.read("Albums")).hasSize(2);
        assertThat(client.read("Albums").get(1))
                .containsExactly("Pink Floyd", "The Dark Side of the Moon", "1973", "5", "TRUE", "classic");
    }

    @Test
    void read_unknownTab_returnsEmpty(@TempDir Path dir) {
        SheetsProperties props = new SheetsProperties(
                true, null, null, "fake", dir.resolve("fake.json").toString(), false);
        FakeSheetsClient client = new FakeSheetsClient(new FakeSheetStore(), props);

        assertThat(client.read("Songs")).isEmpty();
    }
}
