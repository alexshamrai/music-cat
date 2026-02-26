package io.github.alexshamrai.startup;

import java.nio.file.Files;
import java.nio.file.Path;

import io.github.alexshamrai.dto.ImportResult;
import io.github.alexshamrai.repository.ArtistRepository;
import io.github.alexshamrai.service.CatalogImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CatalogAutoImporter {

    private final CatalogImportService catalogImportService;
    private final ArtistRepository artistRepository;

    @Value("${music-cat.catalog-path}")
    private String catalogPath;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (artistRepository.count() > 0) {
            log.info("Database already contains data, skipping auto-import");
            return;
        }

        Path path = Path.of(catalogPath);
        if (!Files.exists(path)) {
            log.warn("Catalog file not found at {}, skipping auto-import", path.toAbsolutePath());
            return;
        }

        try {
            log.info("Database is empty, starting auto-import from {}", path.toAbsolutePath());
            ImportResult result = catalogImportService.importFromJson(path);
            log.info("Auto-import completed: {} artists, {} albums, {} songs",
                result.artistCount(), result.albumCount(), result.songCount());
        } catch (Exception e) {
            log.error("Auto-import failed", e);
        }
    }
}
