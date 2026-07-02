package io.github.alexshamrai.service;

import io.github.alexshamrai.domain.AlbumEntity;
import io.github.alexshamrai.domain.ArtistEntity;
import io.github.alexshamrai.domain.SongEntity;
import io.github.alexshamrai.domain.TagEntity;
import io.github.alexshamrai.dto.ImportResult;
import io.github.alexshamrai.repository.AlbumRepository;
import io.github.alexshamrai.repository.ArtistRepository;
import io.github.alexshamrai.repository.SongRepository;
import io.github.alexshamrai.repository.TagRepository;
import io.github.alexshamrai.sheets.AlbumRow;
import io.github.alexshamrai.sheets.ArtistRow;
import io.github.alexshamrai.sheets.SheetMapper;
import io.github.alexshamrai.sheets.SheetsClient;
import io.github.alexshamrai.sheets.SongRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rebuilds the H2 database from the Google Sheets tabs — the read half of the sync loop.
 *
 * <p>This is what makes an ephemeral filesystem (Cloud Run) safe: on boot with an empty
 * DB, the catalog is restored from the spreadsheet instead of the original catalog.json.
 *
 * <p>Artists are keyed by name, albums by (artist name, title) — matching the natural
 * keys used by the write path in {@link SheetSyncService}.
 */
@Service
@ConditionalOnProperty(name = "music-cat.sheets.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SheetsCatalogReader {

    private final SheetsClient sheetsClient;
    private final ArtistRepository artistRepository;
    private final AlbumRepository albumRepository;
    private final SongRepository songRepository;
    private final TagRepository tagRepository;

    /**
     * Loads the full catalog from the three sheet tabs into the database.
     * Rows referencing an unknown artist/album are skipped with a warning rather than
     * aborting the whole load.
     */
    @Transactional
    public ImportResult loadFromSheets() {
        List<List<Object>> artistRows = dataRows(sheetsClient.read("Artists"));
        List<List<Object>> albumRows = dataRows(sheetsClient.read("Albums"));
        List<List<Object>> songRows = dataRows(sheetsClient.read("Songs"));

        List<String> warnings = new ArrayList<>();
        Map<String, TagEntity> tagCache = new HashMap<>();

        Map<String, ArtistEntity> artistsByName = new LinkedHashMap<>();
        for (List<Object> row : artistRows) {
            ArtistRow parsed = SheetMapper.parseArtistRow(row);
            ArtistEntity artist = ArtistEntity.builder()
                    .name(parsed.name())
                    .genre(parsed.genre())
                    .subgenre(parsed.subgenre())
                    .isFavorite(parsed.favorite())
                    .tags(resolveTags(parsed.tags(), tagCache))
                    .build();
            artistsByName.put(parsed.name(), artistRepository.save(artist));
        }

        Map<String, AlbumEntity> albumsByKey = new LinkedHashMap<>();
        for (List<Object> row : albumRows) {
            AlbumRow parsed = SheetMapper.parseAlbumRow(row);
            ArtistEntity artist = artistsByName.get(parsed.artistName());
            if (artist == null) {
                warnings.add("Albums row skipped — unknown artist '%s' (album '%s')"
                        .formatted(parsed.artistName(), parsed.title()));
                continue;
            }
            AlbumEntity album = AlbumEntity.builder()
                    .title(parsed.title())
                    .year(parsed.year())
                    .grade(parsed.grade())
                    .isFavorite(parsed.favorite())
                    .artist(artist)
                    .tags(resolveTags(parsed.tags(), tagCache))
                    .build();
            albumsByKey.put(albumKey(parsed.artistName(), parsed.title()), albumRepository.save(album));
        }

        int songCount = 0;
        for (List<Object> row : songRows) {
            SongRow parsed = SheetMapper.parseSongRow(row);
            AlbumEntity album = albumsByKey.get(albumKey(parsed.artistName(), parsed.albumTitle()));
            if (album == null) {
                warnings.add("Songs row skipped — unknown album '%s' by '%s'"
                        .formatted(parsed.albumTitle(), parsed.artistName()));
                continue;
            }
            songRepository.save(SongEntity.builder()
                    .title(parsed.title())
                    .trackNumber(parsed.track())
                    .discNumber(parsed.disc())
                    .album(album)
                    .build());
            songCount++;
        }

        warnings.forEach(log::warn);
        log.info("Loaded from Google Sheets: {} artists, {} albums, {} songs ({} rows skipped)",
                artistsByName.size(), albumsByKey.size(), songCount, warnings.size());
        return new ImportResult(artistsByName.size(), albumsByKey.size(), songCount);
    }

    /**
     * Wipes the database (songs → albums → artists → tags) and reloads it from the
     * sheets. Used by POST /api/catalog/sync/pull.
     */
    @Transactional
    public ImportResult replaceFromSheets() {
        songRepository.deleteAll();
        albumRepository.deleteAll();
        artistRepository.deleteAll();
        tagRepository.deleteAll();
        return loadFromSheets();
    }

    /**
     * True when the Artists tab has more than just a header row — used at boot to decide
     * between restoring from Sheets and seeding from catalog.json.
     */
    public boolean sheetsHaveData() {
        List<List<Object>> rows = sheetsClient.read("Artists");
        return rows != null && rows.size() > 1;
    }

    /** Strips the header row (row 1); tolerates null/empty tabs. */
    private static List<List<Object>> dataRows(List<List<Object>> rows) {
        if (rows == null || rows.size() <= 1) {
            return List.of();
        }
        return rows.subList(1, rows.size());
    }

    private static String albumKey(String artistName, String albumTitle) {
        // NUL separator — cannot appear in a sheet cell value, so keys never collide
        return artistName + "\0" + albumTitle;
    }

    /**
     * Resolves tag names to entities, creating missing ones — the same tag-resolution
     * pattern as ArtistService.setTags, plus an in-memory cache so tags shared across
     * rows resolve to a single entity within the load.
     */
    private Set<TagEntity> resolveTags(List<String> tagNames, Map<String, TagEntity> cache) {
        Set<TagEntity> tags = new HashSet<>();
        for (String rawName : tagNames) {
            String name = rawName.strip();
            if (name.isEmpty()) {
                continue;
            }
            tags.add(cache.computeIfAbsent(name,
                    n -> tagRepository.findByName(n)
                            .orElseGet(() -> tagRepository.save(TagEntity.builder().name(n).build()))));
        }
        return tags;
    }
}
