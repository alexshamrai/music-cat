package io.github.alexshamrai.sheets;

import io.github.alexshamrai.domain.AlbumEntity;
import io.github.alexshamrai.domain.ArtistEntity;
import io.github.alexshamrai.domain.Genre;
import io.github.alexshamrai.domain.SongEntity;
import io.github.alexshamrai.domain.TagEntity;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pure static utility for converting between JPA entities and Google Sheets row values.
 *
 * <p>Sheet schema (row 1 is the header):
 * <ul>
 *   <li>Artists: name | genre | subgenre | favorite | tags</li>
 *   <li>Albums:  artist | title | year | grade | favorite | tags</li>
 *   <li>Songs:   artist | album | disc | track | title</li>
 * </ul>
 *
 * <p>Formats: genre = Genre displayName; favorite = TRUE/FALSE;
 * tags = comma-separated ("rock, classic"); null year/grade/subgenre = empty string.
 *
 * <p>Known limitation: artists are keyed by name only — two artists with the same name in
 * different genres are unsupported by the sync and must be renamed before syncing.
 */
public final class SheetMapper {

    private SheetMapper() {}

    // ==================== Artists ====================

    /**
     * Converts an {@link ArtistEntity} to a Sheets row.
     * Columns: name | genre | subgenre | favorite | tags
     */
    public static List<Object> toArtistRow(ArtistEntity artist) {
        return List.of(
                artist.getName(),
                artist.getGenre().getDisplayName(),
                nullToEmpty(artist.getSubgenre()),
                booleanToString(artist.isFavorite()),
                tagsToString(artist.getTags())
        );
    }

    /**
     * Parses a Sheets row into an {@link ArtistRow}.
     * Tolerates short rows (missing trailing cells).
     *
     * @throws IllegalArgumentException if the genre display name is unknown
     */
    public static ArtistRow parseArtistRow(List<Object> row) {
        String name = cell(row, 0);
        Genre genre = Genre.fromDisplayName(cell(row, 1));  // throws IllegalArgumentException on unknown genre
        String subgenreRaw = cell(row, 2);
        String subgenre = subgenreRaw.isBlank() ? null : subgenreRaw.trim();
        boolean favorite = parseFavorite(cell(row, 3));
        List<String> tags = parseTags(cell(row, 4));
        return new ArtistRow(name, genre, subgenre, favorite, tags);
    }

    // ==================== Albums ====================

    /**
     * Converts an {@link AlbumEntity} to a Sheets row.
     * Columns: artist | title | year | grade | favorite | tags
     * The album's {@code artist} association must be initialised.
     */
    public static List<Object> toAlbumRow(AlbumEntity album) {
        return List.of(
                album.getArtist().getName(),
                album.getTitle(),
                nullToEmpty(album.getYear()),
                nullToEmpty(album.getGrade()),
                booleanToString(album.isFavorite()),
                tagsToString(album.getTags())
        );
    }

    /**
     * Parses a Sheets row into an {@link AlbumRow}.
     * Tolerates short rows, numeric-string cells (e.g. "1959.0").
     */
    public static AlbumRow parseAlbumRow(List<Object> row) {
        String artistName = cell(row, 0);
        String title = cell(row, 1);
        Integer year = parseInteger(cell(row, 2));
        Integer grade = parseInteger(cell(row, 3));
        boolean favorite = parseFavorite(cell(row, 4));
        List<String> tags = parseTags(cell(row, 5));
        return new AlbumRow(artistName, title, year, grade, favorite, tags);
    }

    // ==================== Songs ====================

    /**
     * Converts a {@link SongEntity} to a Sheets row.
     * Columns: artist | album | disc | track | title
     * Both the song's {@code album} and the album's {@code artist} associations must be initialised.
     */
    public static List<Object> toSongRow(SongEntity song) {
        AlbumEntity album = song.getAlbum();
        return List.of(
                album.getArtist().getName(),
                album.getTitle(),
                song.getDiscNumber(),
                song.getTrackNumber(),
                song.getTitle()
        );
    }

    /**
     * Parses a Sheets row into a {@link SongRow}.
     * Tolerates short rows and numeric-string cells (e.g. "1.0").
     */
    public static SongRow parseSongRow(List<Object> row) {
        String artistName = cell(row, 0);
        String albumTitle = cell(row, 1);
        int disc = parseIntOrDefault(cell(row, 2), 1);
        int track = parseIntOrDefault(cell(row, 3), 0);
        String title = cell(row, 4);
        return new SongRow(artistName, albumTitle, disc, track, title);
    }

    // ==================== Helpers ====================

    /** Returns the string value of cell at {@code index}, or "" if the row is too short. */
    private static String cell(List<Object> row, int index) {
        if (index >= row.size()) {
            return "";
        }
        Object value = row.get(index);
        return value == null ? "" : value.toString();
    }

    private static String nullToEmpty(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String booleanToString(boolean value) {
        return value ? "TRUE" : "FALSE";
    }

    private static boolean parseFavorite(String cell) {
        return "TRUE".equalsIgnoreCase(cell.trim());
    }

    private static String tagsToString(Set<TagEntity> tags) {
        if (tags == null || tags.isEmpty()) {
            return "";
        }
        return tags.stream()
                .map(TagEntity::getName)
                .sorted()
                .collect(Collectors.joining(", "));
    }

    private static List<String> parseTags(String cell) {
        String trimmed = cell.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(trimmed.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Parses a cell value that may be an integer, a decimal-string (e.g. "1959.0"),
     * or empty/blank (returns null).
     */
    private static Integer parseInteger(String cell) {
        String trimmed = cell.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            // Handle "1959.0" style numeric strings from Google Sheets
            try {
                return (int) Double.parseDouble(trimmed);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }

    private static int parseIntOrDefault(String cell, int defaultValue) {
        Integer parsed = parseInteger(cell);
        return parsed != null ? parsed : defaultValue;
    }
}
