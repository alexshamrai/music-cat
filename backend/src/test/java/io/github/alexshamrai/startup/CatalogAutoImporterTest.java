package io.github.alexshamrai.startup;

import io.github.alexshamrai.repository.ArtistRepository;
import io.github.alexshamrai.service.SheetSyncService;
import io.github.alexshamrai.service.SheetsCatalogReader;
import io.github.alexshamrai.service.SheetsLoadResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Boot decision tree (Sheets-only):
 * <ol>
 *   <li>DB not empty → skip, resume event pushes</li>
 *   <li>DB empty + sheets disabled → empty DB, no sync interaction</li>
 *   <li>DB empty + sheets have data (clean) → restore, resume</li>
 *   <li>DB empty + restore with warnings / zero artists → empty/partial, suspend</li>
 *   <li>DB empty + sheets blank → empty DB, resume (trivially consistent)</li>
 *   <li>DB empty + restore throws → empty DB, suspend, still ready</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class CatalogAutoImporterTest {

    @Mock private ArtistRepository artistRepository;
    @Mock private ObjectProvider<SheetsCatalogReader> readerProvider;
    @Mock private ObjectProvider<SheetSyncService> syncProvider;
    @Mock private SheetsCatalogReader reader;
    @Mock private SheetSyncService syncService;

    private ReadinessState readinessState;

    private CatalogAutoImporter importer() {
        readinessState = new ReadinessState();
        return new CatalogAutoImporter(artistRepository, readerProvider, syncProvider, readinessState);
    }

    private static SheetsLoadResult cleanLoad() {
        return new SheetsLoadResult(176, 2830, 30876, List.of());
    }

    @Test
    void dbNotEmpty_skips_andResumesEventPushes() {
        when(artistRepository.count()).thenReturn(42L);
        when(syncProvider.getIfAvailable()).thenReturn(syncService);

        importer().onApplicationReady();

        verify(syncService).resumeEventPushes();
        verify(readerProvider, never()).getIfAvailable();
        assertThat(readinessState.isReady()).isTrue();
    }

    @Test
    void dbEmpty_sheetsDisabled_startsEmpty() {
        when(artistRepository.count()).thenReturn(0L);
        when(readerProvider.getIfAvailable()).thenReturn(null);
        when(syncProvider.getIfAvailable()).thenReturn(null);

        importer().onApplicationReady();

        assertThat(readinessState.isReady()).isTrue();
    }

    @Test
    void dbEmpty_sheetsHaveData_clean_restoresAndResumes() {
        when(artistRepository.count()).thenReturn(0L);
        when(readerProvider.getIfAvailable()).thenReturn(reader);
        when(syncProvider.getIfAvailable()).thenReturn(syncService);
        when(reader.sheetsHaveData()).thenReturn(true);
        when(reader.loadFromSheets()).thenReturn(cleanLoad());

        importer().onApplicationReady();

        verify(reader).loadFromSheets();
        verify(syncService).resumeEventPushes();
        verify(syncService, never()).suspendEventPushes(anyString());
    }

    @Test
    void dbEmpty_restoreWithWarnings_suspends() {
        when(artistRepository.count()).thenReturn(0L);
        when(readerProvider.getIfAvailable()).thenReturn(reader);
        when(syncProvider.getIfAvailable()).thenReturn(syncService);
        when(reader.sheetsHaveData()).thenReturn(true);
        when(reader.loadFromSheets()).thenReturn(
                new SheetsLoadResult(175, 2830, 30876, List.of("Artists row skipped — bad genre")));

        importer().onApplicationReady();

        verify(syncService).suspendEventPushes(anyString());
        verify(syncService, never()).resumeEventPushes();
    }

    @Test
    void dbEmpty_restoreProducesZeroArtists_startsEmptyAndSuspends() {
        when(artistRepository.count()).thenReturn(0L);
        when(readerProvider.getIfAvailable()).thenReturn(reader);
        when(syncProvider.getIfAvailable()).thenReturn(syncService);
        when(reader.sheetsHaveData()).thenReturn(true);
        when(reader.loadFromSheets()).thenReturn(new SheetsLoadResult(0, 0, 0, List.of()));

        importer().onApplicationReady();

        verify(syncService).suspendEventPushes(anyString());
        verify(syncService, never()).resumeEventPushes();
    }

    @Test
    void dbEmpty_sheetsBlank_resumesEventPushes() {
        when(artistRepository.count()).thenReturn(0L);
        when(readerProvider.getIfAvailable()).thenReturn(reader);
        when(syncProvider.getIfAvailable()).thenReturn(syncService);
        when(reader.sheetsHaveData()).thenReturn(false);

        importer().onApplicationReady();

        verify(syncService).resumeEventPushes();
        verify(syncService, never()).suspendEventPushes(anyString());
    }

    @Test
    void dbEmpty_sheetsHaveDataThrows_startsEmptySuspendedAndReady() {
        when(artistRepository.count()).thenReturn(0L);
        when(readerProvider.getIfAvailable()).thenReturn(reader);
        when(syncProvider.getIfAvailable()).thenReturn(syncService);
        when(reader.sheetsHaveData()).thenThrow(new RuntimeException("Sheets API down"));

        importer().onApplicationReady();

        verify(syncService).suspendEventPushes(anyString());
        verify(syncService, never()).resumeEventPushes();
        verify(syncService, never()).pushCatalog(anyBoolean());
        assertThat(readinessState.isReady()).isTrue();
    }

    @Test
    void dbEmpty_loadFromSheetsThrows_startsEmptySuspended() {
        when(artistRepository.count()).thenReturn(0L);
        when(readerProvider.getIfAvailable()).thenReturn(reader);
        when(syncProvider.getIfAvailable()).thenReturn(syncService);
        when(reader.sheetsHaveData()).thenReturn(true);
        when(reader.loadFromSheets()).thenThrow(new RuntimeException("Malformed rows"));

        importer().onApplicationReady();

        verify(syncService).suspendEventPushes(anyString());
        verify(syncService, never()).resumeEventPushes();
    }
}
