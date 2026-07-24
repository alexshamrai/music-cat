package io.github.alexshamrai.sheets;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * File-backed store for the fake Google Sheets. Persists a map of tab name → rows as JSON at
 * a caller-supplied path. Single source of the on-disk format, shared by {@link FakeSheetsClient}
 * (mode=fake) and {@link SnapshotRunner} (mode=google snapshot). Methods are synchronized; the
 * sync layer additionally serializes access via SheetsSyncLock.
 */
@Component
public class FakeSheetStore {

    private static final TypeReference<Map<String, List<List<Object>>>> TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper = new ObjectMapper();

    public synchronized Map<String, List<List<Object>>> readAll(Path file) {
        if (!Files.exists(file)) {
            return new LinkedHashMap<>();
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length == 0) {
                return new LinkedHashMap<>();
            }
            return objectMapper.readValue(bytes, TYPE);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read fake sheet file: " + file, e);
        }
    }

    public synchronized List<List<Object>> read(Path file, String tab) {
        List<List<Object>> rows = readAll(file).get(tab);
        return rows != null ? rows : List.of();
    }

    public synchronized void write(Path file, String tab, List<List<Object>> rows) {
        Map<String, List<List<Object>>> all = readAll(file);
        all.put(tab, rows);
        writeAll(file, all);
    }

    public synchronized void writeAll(Path file, Map<String, List<List<Object>>> all) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), all);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write fake sheet file: " + file, e);
        }
    }
}
