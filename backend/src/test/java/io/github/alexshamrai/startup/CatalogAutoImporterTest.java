package io.github.alexshamrai.startup;

import io.github.alexshamrai.dto.ImportResult;
import io.github.alexshamrai.repository.ArtistRepository;
import io.github.alexshamrai.service.CatalogImportService;
import io.github.alexshamrai.service.SheetSyncService;
import io.github.alexshamrai.service.SheetsCatalogReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the CatalogAutoImporter boot decision tree:
 * <ol>
 *   <li>DB not empty → skip</li>
 *   <li>DB empty + sheets enabled + sheets have data → restore from Sheets</li>
 *   <li>DB empty + sheets enabled + sheets blank → seed from catalog.json + push</li>
 *   <li>DB empty + sheets disabled → import catalog.json (original behavior)</li>
 *   <li>Sheets restore throws → fall back to catalog.json WITHOUT pushing</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class CatalogAutoImporterTest {

    @Mock
    private CatalogImportService catalogImportService;

    @Mock
    private ArtistRepository artistRepository;

    @Mock
    private ObjectProvider<SheetsCatalogReader> readerProvider;

    @Mock
    private ObjectProvider<SheetSyncService> syncProvider;

    @Mock
    private SheetsCatalogReader reader;

    @Mock
    private SheetSyncService syncService;

    @TempDir
    Path tempDir;

    private Path catalogFile;

    @BeforeEach
    void createCatalogFile() throws IOException {
        catalogFile = tempDir.resolve("catalog.json");
        Files.writeString(catalogFile, "{\"catalog\": []}");
    }

    private CatalogAutoImporter importer(String catalogPath) {
        return new CatalogAutoImporter(
                catalogImportService, artistRepository, readerProvider, syncProvider, catalogPath);
    }

    @Test
    void dbNotEmpty_skipsEverything() {
        when(artistRepository.count()).thenReturn(42L);

        importer(catalogFile.toString()).onApplicationReady();

        verifyNoInteractions(catalogImportService, readerProvider, syncProvider);
    }

    @Test
    void dbEmpty_sheetsDisabled_importsCatalogJson() throws IOException {
        when(artistRepository.count()).thenReturn(0L);
        when(readerProvider.getIfAvailable()).thenReturn(null);
        when(catalogImportService.importFromJson(catalogFile, false))
                .thenReturn(new ImportResult(1, 2, 3));

        importer(catalogFile.toString()).onApplicationReady();

        verify(catalogImportService).importFromJson(catalogFile, false);
        verifyNoInteractions(syncProvider);
    }

    @Test
    void dbEmpty_sheetsDisabled_missingCatalogFile_skipsGracefully() {
        when(artistRepository.count()).thenReturn(0L);
        when(readerProvider.getIfAvailable()).thenReturn(null);

        importer(tempDir.resolve("does-not-exist.json").toString()).onApplicationReady();

        verifyNoInteractions(catalogImportService);
    }

    @Test
    void dbEmpty_sheetsHaveData_restoresFromSheets_catalogJsonNotImported() {
        when(artistRepository.count()).thenReturn(0L);
        when(readerProvider.getIfAvailable()).thenReturn(reader);
        when(reader.sheetsHaveData()).thenReturn(true);
        when(reader.loadFromSheets()).thenReturn(new ImportResult(176, 2830, 30876));

        importer(catalogFile.toString()).onApplicationReady();

        verify(reader).loadFromSheets();
        verifyNoInteractions(catalogImportService, syncProvider);
    }

    @Test
    void dbEmpty_sheetsBlank_seedsFromCatalogJsonAndPushes() throws IOException {
        when(artistRepository.count()).thenReturn(0L);
        when(readerProvider.getIfAvailable()).thenReturn(reader);
        when(reader.sheetsHaveData()).thenReturn(false);
        when(catalogImportService.importFromJson(catalogFile, false))
                .thenReturn(new ImportResult(176, 2830, 30876));
        when(syncProvider.getIfAvailable()).thenReturn(syncService);

        importer(catalogFile.toString()).onApplicationReady();

        verify(catalogImportService).importFromJson(catalogFile, false);
        verify(syncService).pushCatalog(true);
    }

    @Test
    void dbEmpty_sheetsHaveDataThrows_fallsBackToCatalogJsonWithoutPush() throws IOException {
        when(artistRepository.count()).thenReturn(0L);
        when(readerProvider.getIfAvailable()).thenReturn(reader);
        when(reader.sheetsHaveData()).thenThrow(new RuntimeException("Sheets API down"));
        when(catalogImportService.importFromJson(catalogFile, false))
                .thenReturn(new ImportResult(176, 2830, 30876));

        importer(catalogFile.toString()).onApplicationReady();

        verify(catalogImportService).importFromJson(catalogFile, false);
        verifyNoInteractions(syncProvider);
    }

    @Test
    void dbEmpty_loadFromSheetsThrows_fallsBackToCatalogJsonWithoutPush() throws IOException {
        when(artistRepository.count()).thenReturn(0L);
        when(readerProvider.getIfAvailable()).thenReturn(reader);
        when(reader.sheetsHaveData()).thenReturn(true);
        when(reader.loadFromSheets()).thenThrow(new RuntimeException("Malformed rows"));
        when(catalogImportService.importFromJson(catalogFile, false))
                .thenReturn(new ImportResult(176, 2830, 30876));

        importer(catalogFile.toString()).onApplicationReady();

        verify(catalogImportService).importFromJson(catalogFile, false);
        verifyNoInteractions(syncProvider);
    }

    @Test
    void dbEmpty_sheetsBlank_seedImportFails_doesNotPush() throws IOException {
        when(artistRepository.count()).thenReturn(0L);
        when(readerProvider.getIfAvailable()).thenReturn(reader);
        when(reader.sheetsHaveData()).thenReturn(false);
        when(catalogImportService.importFromJson(catalogFile, false))
                .thenThrow(new IOException("corrupt catalog.json"));

        importer(catalogFile.toString()).onApplicationReady();

        verify(syncService, never()).pushCatalog(anyBoolean());
    }
}
