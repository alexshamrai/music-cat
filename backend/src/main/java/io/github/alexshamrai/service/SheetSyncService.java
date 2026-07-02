package io.github.alexshamrai.service;

import io.github.alexshamrai.domain.AlbumEntity;
import io.github.alexshamrai.domain.ArtistEntity;
import io.github.alexshamrai.domain.SongEntity;
import io.github.alexshamrai.dto.SyncResultDto;
import io.github.alexshamrai.dto.SyncStatusDto;
import io.github.alexshamrai.repository.AlbumRepository;
import io.github.alexshamrai.repository.ArtistRepository;
import io.github.alexshamrai.repository.SongRepository;
import io.github.alexshamrai.sheets.SheetMapper;
import io.github.alexshamrai.sheets.SheetsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

@Service
@ConditionalOnProperty(name = "music-cat.sheets.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SheetSyncService {

    private static final List<Object> ARTISTS_HEADER = List.of("name", "genre", "subgenre", "favorite", "tags");
    private static final List<Object> ALBUMS_HEADER = List.of("artist", "title", "year", "grade", "favorite", "tags");
    private static final List<Object> SONGS_HEADER = List.of("artist", "album", "disc", "track", "title");

    private final SheetsClient sheetsClient;
    private final ArtistRepository artistRepository;
    private final AlbumRepository albumRepository;
    private final SongRepository songRepository;

    private final AtomicBoolean songsDirty = new AtomicBoolean(false);
    private volatile Instant lastPushAt;
    private volatile Instant lastPullAt;
    private volatile String lastError;

    /** Serializes concurrent pushCatalog calls — clear-then-write (overwrite) is not atomic. */
    private final ReentrantLock pushLock = new ReentrantLock();

    /**
     * Pushes the entire catalog to Google Sheets.
     *
     * <p>Always runs inside its own read-only transaction so that lazy associations can be
     * traversed safely regardless of the caller's context (e.g. an ApplicationReadyEvent
     * fired from a non-request thread where OSIV is absent).
     *
     * @param includeSongs if true, also rewrites the Songs tab; if false, Songs are only
     *                     rewritten when {@code songsDirty} is set (self-heal after a prior failure)
     */
    @Transactional(readOnly = true)
    public SyncResultDto pushCatalog(boolean includeSongs) {
        pushLock.lock();
        try {
            return doPushCatalog(includeSongs);
        } finally {
            pushLock.unlock();
        }
    }

    private SyncResultDto doPushCatalog(boolean includeSongs) {
        boolean shouldWriteSongs = includeSongs || songsDirty.get();

        try {
            // Artists: sorted by genre displayName, then name
            Comparator<ArtistEntity> artistComparator = Comparator
                    .comparing((ArtistEntity a) -> a.getGenre().getDisplayName())
                    .thenComparing(ArtistEntity::getName);

            List<ArtistEntity> artists = artistRepository.findAllForSync().stream()
                    .sorted(artistComparator)
                    .toList();

            List<List<Object>> artistRows = new ArrayList<>();
            artistRows.add(ARTISTS_HEADER);
            artists.stream()
                    .map(SheetMapper::toArtistRow)
                    .forEach(artistRows::add);
            sheetsClient.overwrite("Artists", artistRows);

            // Albums: sorted by artist name, then year null-last, then title
            Comparator<AlbumEntity> albumComparator = Comparator
                    .comparing((AlbumEntity a) -> a.getArtist().getName())
                    .thenComparing(AlbumEntity::getYear, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(AlbumEntity::getTitle);

            List<AlbumEntity> albums = albumRepository.findAllForSync().stream()
                    .sorted(albumComparator)
                    .toList();

            List<List<Object>> albumRows = new ArrayList<>();
            albumRows.add(ALBUMS_HEADER);
            albums.stream()
                    .map(SheetMapper::toAlbumRow)
                    .forEach(albumRows::add);
            sheetsClient.overwrite("Albums", albumRows);

            int songCount = 0;
            if (shouldWriteSongs) {
                // Songs: sorted by artist, album, disc, track
                Comparator<SongEntity> songComparator = Comparator
                        .comparing((SongEntity s) -> s.getAlbum().getArtist().getName())
                        .thenComparing((SongEntity s) -> s.getAlbum().getTitle())
                        .thenComparingInt(SongEntity::getDiscNumber)
                        .thenComparingInt(SongEntity::getTrackNumber);

                List<SongEntity> songs = songRepository.findAllForSync().stream()
                        .sorted(songComparator)
                        .toList();

                List<List<Object>> songRows = new ArrayList<>();
                songRows.add(SONGS_HEADER);
                songs.stream()
                        .map(SheetMapper::toSongRow)
                        .forEach(songRows::add);
                sheetsClient.overwrite("Songs", songRows);
                songCount = songs.size();
                songsDirty.set(false);
            }

            lastPushAt = Instant.now();
            lastError = null;

            return new SyncResultDto(artists.size(), albums.size(), songCount, lastPushAt);

        } catch (Exception e) {
            String msg = e.getMessage();
            lastError = (msg != null) ? msg : e.getClass().getSimpleName();
            if (shouldWriteSongs) {
                // A failure mid-songs write — mark dirty for self-heal
                songsDirty.set(true);
            }
            throw e;
        }
    }

    /** Records a successful pull-from-Sheets (POST /api/catalog/sync/pull). */
    public void recordPull(Instant pulledAt) {
        this.lastPullAt = pulledAt;
    }

    public SyncStatusDto getStatus() {
        return new SyncStatusDto(true, lastPushAt, lastPullAt, songsDirty.get(), lastError);
    }
}
