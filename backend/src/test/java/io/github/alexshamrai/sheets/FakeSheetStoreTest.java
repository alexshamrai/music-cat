package io.github.alexshamrai.sheets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FakeSheetStoreTest {

    private final FakeSheetStore store = new FakeSheetStore();

    @Test
    void missingFile_readReturnsEmptyList(@TempDir Path dir) {
        Path file = dir.resolve("nope.json");
        assertThat(store.read(file, "Artists")).isEmpty();
    }

    @Test
    void write_thenRead_roundTripsRowsAndCreatesParentDir(@TempDir Path dir) {
        Path file = dir.resolve("nested/fake-sheets.json");
        List<List<Object>> rows = List.of(
                List.of("name", "genre", "subgenre", "favorite", "tags"),
                List.of("Pink Floyd", "Progressive Rock", "Psychedelic", "TRUE", "classic"));

        store.write(file, "Artists", rows);

        assertThat(store.read(file, "Artists"))
                .containsExactly(
                        List.of("name", "genre", "subgenre", "favorite", "tags"),
                        List.of("Pink Floyd", "Progressive Rock", "Psychedelic", "TRUE", "classic"));
    }

    @Test
    void write_secondTab_keepsFirstTab(@TempDir Path dir) {
        Path file = dir.resolve("fake-sheets.json");
        store.write(file, "Artists", List.of(List.of("name"), List.of("A")));
        store.write(file, "Albums", List.of(List.of("artist"), List.of("A")));

        assertThat(store.read(file, "Artists")).isNotEmpty();
        assertThat(store.read(file, "Albums")).isNotEmpty();
    }
}
