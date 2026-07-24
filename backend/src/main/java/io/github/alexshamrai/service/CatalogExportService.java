package io.github.alexshamrai.service;

import io.github.alexshamrai.domain.AlbumEntity;
import io.github.alexshamrai.domain.ArtistEntity;
import io.github.alexshamrai.domain.SongEntity;
import io.github.alexshamrai.domain.TagEntity;
import io.github.alexshamrai.dto.export.Stats;
import io.github.alexshamrai.dto.export.ExportAlbum;
import io.github.alexshamrai.dto.export.ExportArtist;
import io.github.alexshamrai.dto.export.ExportCatalog;
import io.github.alexshamrai.dto.export.ExportGenre;
import io.github.alexshamrai.dto.export.ExportSong;
import io.github.alexshamrai.repository.AlbumRepository;
import io.github.alexshamrai.repository.ArtistRepository;
import io.github.alexshamrai.repository.SongRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Offline export of the enriched catalog — an independent backup layer beside Sheets.
 * JSON keeps the original catalog shape extended with curation fields; CSV produces a ZIP
 * of artists.csv + albums.csv that opens directly in a spreadsheet app.
 */
@Service
@RequiredArgsConstructor
public class CatalogExportService {

    private static final Comparator<AlbumEntity> ALBUM_ORDER = Comparator
            .comparing(AlbumEntity::getYear, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(AlbumEntity::getTitle);

    private final ArtistRepository artistRepository;
    private final AlbumRepository albumRepository;
    private final SongRepository songRepository;

    @Transactional(readOnly = true)
    public ExportCatalog exportJson() {
        List<ArtistEntity> artists = artistRepository.findAllForSync();
        Map<Long, List<AlbumEntity>> albumsByArtist = albumsByArtistId();
        Map<Long, List<SongEntity>> songsByAlbum = songsByAlbumId();

        int totalAlbums = albumsByArtist.values().stream().mapToInt(List::size).sum();
        int totalTracks = songsByAlbum.values().stream().mapToInt(List::size).sum();

        // TreeMap keyed by genre displayName → genres sorted alphabetically
        Map<String, List<ArtistEntity>> byGenre = artists.stream()
                .collect(Collectors.groupingBy(a -> a.getGenre().getDisplayName(),
                        TreeMap::new, Collectors.toList()));

        List<ExportGenre> genres = byGenre.entrySet().stream()
                .map(entry -> new ExportGenre(
                        entry.getKey(),
                        entry.getValue().stream()
                                .sorted(Comparator.comparing(ArtistEntity::getName))
                                .map(artist -> toExportArtist(artist, albumsByArtist, songsByAlbum))
                                .toList()))
                .toList();

        Stats stats = new Stats(byGenre.size(), artists.size(), totalAlbums, totalTracks);
        return new ExportCatalog(Instant.now(), stats, genres);
    }

    @Transactional(readOnly = true)
    public byte[] exportCsvZip() {
        List<ArtistEntity> artists = artistRepository.findAllForSync().stream()
                .sorted(Comparator.comparing((ArtistEntity a) -> a.getGenre().getDisplayName())
                        .thenComparing(ArtistEntity::getName))
                .toList();
        Map<Long, List<AlbumEntity>> albumsByArtist = albumsByArtistId();
        Map<Long, List<SongEntity>> songsByAlbum = songsByAlbumId();

        StringBuilder artistsCsv = new StringBuilder("name,genre,subgenre,isFavorite,tags,albumCount\n");
        for (ArtistEntity artist : artists) {
            artistsCsv.append(csvRow(
                    artist.getName(),
                    artist.getGenre().getDisplayName(),
                    artist.getSubgenre(),
                    artist.isFavorite(),
                    tagsField(artist.getTags()),
                    albumsByArtist.getOrDefault(artist.getId(), List.of()).size()));
        }

        StringBuilder albumsCsv = new StringBuilder("artistName,genre,title,year,grade,isFavorite,tags,songCount\n");
        for (ArtistEntity artist : artists) {
            for (AlbumEntity album : albumsByArtist.getOrDefault(artist.getId(), List.of())) {
                albumsCsv.append(csvRow(
                        artist.getName(),
                        artist.getGenre().getDisplayName(),
                        album.getTitle(),
                        album.getYear(),
                        album.getGrade(),
                        album.isFavorite(),
                        tagsField(album.getTags()),
                        songsByAlbum.getOrDefault(album.getId(), List.of()).size()));
            }
        }

        return zip(Map.of(
                "artists.csv", artistsCsv.toString(),
                "albums.csv", albumsCsv.toString()));
    }

    // ==================== assembly helpers ====================

    private Map<Long, List<AlbumEntity>> albumsByArtistId() {
        return albumRepository.findAllForSync().stream()
                .sorted(ALBUM_ORDER)
                .collect(Collectors.groupingBy(a -> a.getArtist().getId()));
    }

    private Map<Long, List<SongEntity>> songsByAlbumId() {
        return songRepository.findAllForSync().stream()
                .sorted(Comparator.comparingInt(SongEntity::getDiscNumber)
                        .thenComparingInt(SongEntity::getTrackNumber))
                .collect(Collectors.groupingBy(s -> s.getAlbum().getId()));
    }

    private static ExportArtist toExportArtist(ArtistEntity artist,
                                               Map<Long, List<AlbumEntity>> albumsByArtist,
                                               Map<Long, List<SongEntity>> songsByAlbum) {
        List<ExportAlbum> albums = albumsByArtist.getOrDefault(artist.getId(), List.of()).stream()
                .map(album -> new ExportAlbum(
                        album.getTitle(),
                        album.getYear(),
                        album.getGrade(),
                        album.isFavorite(),
                        sortedTagNames(album.getTags()),
                        songsByAlbum.getOrDefault(album.getId(), List.of()).stream()
                                .map(s -> new ExportSong(s.getTitle(), s.getTrackNumber(), s.getDiscNumber()))
                                .toList()))
                .toList();
        return new ExportArtist(
                artist.getName(),
                artist.getSubgenre(),
                artist.isFavorite(),
                sortedTagNames(artist.getTags()),
                albums);
    }

    private static List<String> sortedTagNames(Set<TagEntity> tags) {
        return tags.stream().map(TagEntity::getName).sorted().toList();
    }

    // ==================== CSV helpers ====================

    private static String tagsField(Set<TagEntity> tags) {
        return tags.stream().map(TagEntity::getName).sorted().collect(Collectors.joining(", "));
    }

    private static String csvRow(Object... fields) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                row.append(',');
            }
            row.append(csvEscape(fields[i]));
        }
        return row.append('\n').toString();
    }

    /**
     * Quotes fields containing comma/quote/newline, doubling embedded quotes; null → empty.
     * Values starting with =/+/-/@ are prefixed with a leading quote to prevent them being
     * interpreted as a live formula when the CSV is opened in a spreadsheet app (CSV/formula
     * injection, CWE-1236) — artist/album titles and tags are unrestricted free text.
     */
    private static String csvEscape(Object field) {
        if (field == null) {
            return "";
        }
        String value = field.toString();
        if (!value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0) {
            value = "'" + value;
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    private static byte[] zip(Map<String, String> files) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to build export ZIP", e);
        }
        return out.toByteArray();
    }
}
