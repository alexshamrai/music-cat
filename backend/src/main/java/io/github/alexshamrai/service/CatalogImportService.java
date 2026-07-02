package io.github.alexshamrai.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.alexshamrai.domain.AlbumEntity;
import io.github.alexshamrai.domain.ArtistEntity;
import io.github.alexshamrai.domain.SongEntity;
import io.github.alexshamrai.dto.ImportResult;
import io.github.alexshamrai.dto.catalog.Album;
import io.github.alexshamrai.dto.catalog.Artist;
import io.github.alexshamrai.dto.catalog.Catalog;
import io.github.alexshamrai.domain.Genre;
import io.github.alexshamrai.dto.catalog.GenreGroup;
import io.github.alexshamrai.event.CatalogChangedEvent;
import io.github.alexshamrai.repository.AlbumRepository;
import io.github.alexshamrai.repository.ArtistRepository;
import io.github.alexshamrai.repository.SongRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogImportService {

    private final ArtistRepository artistRepository;
    private final AlbumRepository albumRepository;
    private final SongRepository songRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Patterns for parsing track number from filename (ordered by priority)
    // "01 - Song Title.mp3", "01 - Artist - Song Title.mp3"
    private static final Pattern PATTERN_NUM_DASH = Pattern.compile("^(\\d+)\\s*-\\s*(.+)\\.mp3$", Pattern.CASE_INSENSITIVE);
    // "01. Song Title.mp3"
    private static final Pattern PATTERN_NUM_DOT = Pattern.compile("^(\\d+)\\.\\s*(.+)\\.mp3$", Pattern.CASE_INSENSITIVE);
    // "01_Song_Title.mp3"
    private static final Pattern PATTERN_NUM_UNDERSCORE = Pattern.compile("^(\\d+)_(.+)\\.mp3$", Pattern.CASE_INSENSITIVE);
    // "01 Song Title.mp3" (number followed by space, no separator)
    private static final Pattern PATTERN_NUM_SPACE = Pattern.compile("^(\\d+)\\s+([A-Za-z].+)\\.mp3$", Pattern.CASE_INSENSITIVE);
    // Fallback: just strip .mp3
    private static final Pattern PATTERN_FALLBACK = Pattern.compile("^(.+)\\.mp3$", Pattern.CASE_INSENSITIVE);

    // Pattern to detect "Artist - Title" within the title portion
    private static final Pattern PATTERN_ARTIST_TITLE = Pattern.compile("^[^-]+-\\s*(.+)$");

    @Transactional
    public ImportResult importFromJson(Path catalogFile) throws IOException {
        return importFromJson(catalogFile, true);
    }

    /**
     * Imports catalog.json into the database.
     *
     * @param publishEvent whether to publish a structural {@link CatalogChangedEvent} after the
     *                     import. The boot-time auto-importer passes {@code false} so that boot
     *                     seeding never triggers an implicit Sheets push — pushes at boot are
     *                     decided explicitly by {@code CatalogAutoImporter}'s decision tree.
     */
    @Transactional
    public ImportResult importFromJson(Path catalogFile, boolean publishEvent) throws IOException {
        String json = Files.readString(catalogFile);
        Catalog catalog = objectMapper.readValue(json, Catalog.class);

        int artistCount = 0;
        int albumCount = 0;
        int songCount = 0;

        for (GenreGroup genreEntry : catalog.catalog()) {
            Genre genre = genreEntry.genre();

            for (Artist artistEntry : genreEntry.artists()) {
                ArtistEntity artist = findOrCreateArtist(artistEntry.name(), genre);
                artistCount++;

                for (Album albumEntry : artistEntry.albums()) {
                    if (albumExistsForArtist(artist, albumEntry.title())) {
                        log.debug("Skipping existing album: {} by {}", albumEntry.title(), artist.getName());
                        continue;
                    }

                    AlbumEntity album = AlbumEntity.builder()
                        .title(albumEntry.title())
                        .year(albumEntry.year())
                        .artist(artist)
                        .build();
                    albumRepository.save(album);
                    albumCount++;

                    for (int i = 0; i < albumEntry.songs().size(); i++) {
                        String filename = albumEntry.songs().get(i);
                        SongParseResult parsed = parseSongFilename(filename, i + 1);

                        SongEntity song = SongEntity.builder()
                            .title(parsed.title())
                            .trackNumber(parsed.trackNumber())
                            .discNumber(parsed.discNumber())
                            .album(album)
                            .build();
                        songRepository.save(song);
                        songCount++;
                    }
                }
            }
        }

        log.info("Import completed: {} artists, {} albums, {} songs", artistCount, albumCount, songCount);
        if (publishEvent) {
            eventPublisher.publishEvent(new CatalogChangedEvent(true));
        }
        return new ImportResult(artistCount, albumCount, songCount);
    }

    private ArtistEntity findOrCreateArtist(String name, Genre genre) {
        return artistRepository.findByNameAndGenre(name, genre)
            .orElseGet(() -> {
                ArtistEntity artist = ArtistEntity.builder()
                    .name(name)
                    .genre(genre)
                    .build();
                return artistRepository.save(artist);
            });
    }

    private boolean albumExistsForArtist(ArtistEntity artist, String albumTitle) {
        return albumRepository.existsByArtistIdAndTitle(artist.getId(), albumTitle);
    }

    SongParseResult parseSongFilename(String filename, int positionalIndex) {
        int discNumber = 1;
        String workingFilename = filename;

        // Try each pattern in priority order
        Matcher matcher;

        // Pattern: "01 - Song Title.mp3" or "01 - Artist - Song Title.mp3"
        matcher = PATTERN_NUM_DASH.matcher(workingFilename);
        if (matcher.matches()) {
            int trackNumber = parseTrackAndDisc(matcher.group(1));
            discNumber = extractDiscNumber(matcher.group(1));
            String title = cleanTitle(matcher.group(2));
            return new SongParseResult(title, trackNumber, discNumber);
        }

        // Pattern: "01. Song Title.mp3"
        matcher = PATTERN_NUM_DOT.matcher(workingFilename);
        if (matcher.matches()) {
            int trackNumber = parseTrackAndDisc(matcher.group(1));
            discNumber = extractDiscNumber(matcher.group(1));
            String title = cleanTitle(matcher.group(2));
            return new SongParseResult(title, trackNumber, discNumber);
        }

        // Pattern: "01_Song_Title.mp3"
        matcher = PATTERN_NUM_UNDERSCORE.matcher(workingFilename);
        if (matcher.matches()) {
            int trackNumber = parseTrackAndDisc(matcher.group(1));
            discNumber = extractDiscNumber(matcher.group(1));
            String title = cleanTitle(matcher.group(2).replace('_', ' '));
            return new SongParseResult(title, trackNumber, discNumber);
        }

        // Pattern: "01 Song Title.mp3"
        matcher = PATTERN_NUM_SPACE.matcher(workingFilename);
        if (matcher.matches()) {
            int trackNumber = parseTrackAndDisc(matcher.group(1));
            discNumber = extractDiscNumber(matcher.group(1));
            String title = cleanTitle(matcher.group(2));
            return new SongParseResult(title, trackNumber, discNumber);
        }

        // Fallback: just strip .mp3 extension, use positional index
        matcher = PATTERN_FALLBACK.matcher(workingFilename);
        if (matcher.matches()) {
            String title = cleanTitle(matcher.group(1));
            return new SongParseResult(title, positionalIndex, discNumber);
        }

        // Last resort: use filename as-is
        return new SongParseResult(workingFilename, positionalIndex, discNumber);
    }

    private int parseTrackAndDisc(String numberStr) {
        int num = Integer.parseInt(numberStr);
        // Track numbers like 101, 102 → disc 1, track 1/2
        // Track numbers like 201, 202 → disc 2, track 1/2
        if (num >= 100) {
            return num % 100;
        }
        return num;
    }

    private int extractDiscNumber(String numberStr) {
        int num = Integer.parseInt(numberStr);
        if (num >= 100) {
            return num / 100;
        }
        return 1;
    }

    private String cleanTitle(String rawTitle) {
        String title = rawTitle.trim();

        // Remove "Artist - " prefix if present (e.g., "Cosmosquad - Sheer Drama")
        // Only strip if there's meaningful text after the dash
        Matcher artistMatcher = PATTERN_ARTIST_TITLE.matcher(title);
        if (artistMatcher.matches()) {
            String afterDash = artistMatcher.group(1).trim();
            // Only strip if the part before dash looks like an artist name
            // (not a number or very short), and part after dash is substantial
            String beforeDash = title.substring(0, title.indexOf('-')).trim();
            if (!beforeDash.isEmpty() && afterDash.length() > 1 && !beforeDash.matches("\\d+")) {
                // Check if it looks like "Artist Name - Song Title" format
                // Be conservative: only strip if the before-dash part contains spaces (likely a name)
                // or matches known patterns like "Roland Dyens - 20 Lettres - 01 - ..."
                if (beforeDash.contains(" ") || beforeDash.length() > 3) {
                    title = afterDash;
                }
            }
        }

        return title.trim();
    }

    record SongParseResult(String title, int trackNumber, int discNumber) {}
}
