package io.github.alexshamrai.sheets;

import io.github.alexshamrai.domain.AlbumEntity;
import io.github.alexshamrai.domain.ArtistEntity;
import io.github.alexshamrai.domain.Genre;
import io.github.alexshamrai.domain.SongEntity;
import io.github.alexshamrai.domain.TagEntity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static io.github.alexshamrai.TestDataFactory.album;
import static io.github.alexshamrai.TestDataFactory.albumWithTags;
import static io.github.alexshamrai.TestDataFactory.artist;
import static io.github.alexshamrai.TestDataFactory.artistWithTags;
import static io.github.alexshamrai.TestDataFactory.song;
import static io.github.alexshamrai.TestDataFactory.tag;
import static io.github.alexshamrai.TestDataFactory.tagWithId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SheetMapperTest {

    // ==================== toArtistRow / parseArtistRow round-trip ====================

    @Test
    void artistRow_roundTrip_preservesAllFields() {
        // Use tagWithId so Set.of() deduplication works (TagEntity equality is id-based)
        TagEntity t1 = tagWithId(1L, "rock");
        TagEntity t2 = tagWithId(2L, "classic");
        ArtistEntity artist = ArtistEntity.builder()
                .name("Pink Floyd")
                .genre(Genre.PROGRESSIVE_ROCK)
                .subgenre("Psychedelic")
                .isFavorite(true)
                .tags(Set.of(t1, t2))
                .build();

        List<Object> row = SheetMapper.toArtistRow(artist);
        ArtistRow parsed = SheetMapper.parseArtistRow(row);

        assertThat(parsed.name()).isEqualTo("Pink Floyd");
        assertThat(parsed.genre()).isEqualTo(Genre.PROGRESSIVE_ROCK);
        assertThat(parsed.subgenre()).isEqualTo("Psychedelic");
        assertThat(parsed.favorite()).isTrue();
        assertThat(parsed.tags()).containsExactlyInAnyOrder("rock", "classic");
    }

    @Test
    void artistRow_nullSubgenre_emptyCell() {
        ArtistEntity artist = artist("Miles Davis", Genre.JAZZ_AND_FUNK);

        List<Object> row = SheetMapper.toArtistRow(artist);
        ArtistRow parsed = SheetMapper.parseArtistRow(row);

        assertThat(row.get(2)).isEqualTo("");
        assertThat(parsed.subgenre()).isNull();
    }

    @Test
    void artistRow_noTags_emptyList() {
        ArtistEntity artist = artist("Miles Davis", Genre.JAZZ_AND_FUNK);

        List<Object> row = SheetMapper.toArtistRow(artist);
        ArtistRow parsed = SheetMapper.parseArtistRow(row);

        assertThat(parsed.tags()).isEmpty();
    }

    @Test
    void artistRow_tags_roundTrip() {
        // Use tagWithId so Set.of() deduplication works (TagEntity equality is id-based)
        Set<TagEntity> tags = Set.of(tagWithId(1L, "rock"), tagWithId(2L, "classic"), tagWithId(3L, "90s"));
        ArtistEntity artist = artistWithTags(1L, "The Beatles", Genre.POP_AND_ROCK, tags);

        List<Object> row = SheetMapper.toArtistRow(artist);
        ArtistRow parsed = SheetMapper.parseArtistRow(row);

        assertThat(parsed.tags()).containsExactlyInAnyOrder("rock", "classic", "90s");
    }

    @Test
    void parseArtistRow_unknownGenre_throwsIllegalArgumentException() {
        List<Object> row = List.of("Some Artist", "Unknown Genre", "", "FALSE", "");

        assertThatThrownBy(() -> SheetMapper.parseArtistRow(row))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown genre");
    }

    // ==================== toAlbumRow / parseAlbumRow round-trip ====================

    @Test
    void albumRow_roundTrip_preservesAllFields() {
        ArtistEntity artist = artist("Led Zeppelin", Genre.HARD_ROCK_AND_METAL);
        TagEntity t = tag("hard-rock");
        AlbumEntity albumEntity = AlbumEntity.builder()
                .title("Led Zeppelin IV")
                .year(1971)
                .grade(5)
                .isFavorite(true)
                .artist(artist)
                .tags(Set.of(t))
                .build();

        List<Object> row = SheetMapper.toAlbumRow(albumEntity);
        AlbumRow parsed = SheetMapper.parseAlbumRow(row);

        assertThat(parsed.artistName()).isEqualTo("Led Zeppelin");
        assertThat(parsed.title()).isEqualTo("Led Zeppelin IV");
        assertThat(parsed.year()).isEqualTo(1971);
        assertThat(parsed.grade()).isEqualTo(5);
        assertThat(parsed.favorite()).isTrue();
        assertThat(parsed.tags()).containsExactly("hard-rock");
    }

    @Test
    void albumRow_nullYearAndGrade_emptyCells() {
        ArtistEntity artist = artist("Miles Davis", Genre.JAZZ_AND_FUNK);
        AlbumEntity albumEntity = album("Kind of Blue", null, artist);

        List<Object> row = SheetMapper.toAlbumRow(albumEntity);
        AlbumRow parsed = SheetMapper.parseAlbumRow(row);

        assertThat(row.get(2)).isEqualTo("");  // year
        assertThat(row.get(3)).isEqualTo("");  // grade
        assertThat(parsed.year()).isNull();
        assertThat(parsed.grade()).isNull();
    }

    @Test
    void albumRow_numericStringYear_parsesCorrectly() {
        // Simulate Google Sheets returning "1959.0" for a numeric cell
        List<Object> row = List.of("Miles Davis", "Kind of Blue", "1959.0", "", "FALSE", "");

        AlbumRow parsed = SheetMapper.parseAlbumRow(row);

        assertThat(parsed.year()).isEqualTo(1959);
    }

    @Test
    void albumRow_numericStringGrade_parsesCorrectly() {
        List<Object> row = List.of("Some Artist", "Some Album", "2000", "4.0", "TRUE", "");

        AlbumRow parsed = SheetMapper.parseAlbumRow(row);

        assertThat(parsed.grade()).isEqualTo(4);
    }

    @Test
    void albumRow_shortRow_doesNotThrow() {
        // 5 cells instead of expected 6 (missing tags)
        List<Object> row = List.of("Artist", "Album", "2000", "3", "FALSE");

        AlbumRow parsed = SheetMapper.parseAlbumRow(row);

        assertThat(parsed.tags()).isEmpty();
    }

    // ==================== toSongRow / parseSongRow round-trip ====================

    @Test
    void songRow_roundTrip_preservesAllFields() {
        ArtistEntity artist = artist("Bach", Genre.CLASSICAL);
        AlbumEntity albumEntity = album("The Well-Tempered Clavier", 1722, artist);
        SongEntity songEntity = song("Prelude No. 1", 1, 2, albumEntity);

        List<Object> row = SheetMapper.toSongRow(songEntity);
        SongRow parsed = SheetMapper.parseSongRow(row);

        assertThat(parsed.artistName()).isEqualTo("Bach");
        assertThat(parsed.albumTitle()).isEqualTo("The Well-Tempered Clavier");
        assertThat(parsed.disc()).isEqualTo(2);
        assertThat(parsed.track()).isEqualTo(1);
        assertThat(parsed.title()).isEqualTo("Prelude No. 1");
    }

    @Test
    void songRow_numericStringDiscAndTrack_parsesCorrectly() {
        List<Object> row = List.of("Artist", "Album", "1.0", "5.0", "Song Title");

        SongRow parsed = SheetMapper.parseSongRow(row);

        assertThat(parsed.disc()).isEqualTo(1);
        assertThat(parsed.track()).isEqualTo(5);
    }

    @Test
    void songRow_shortRow_doesNotThrow() {
        // 4 cells instead of 5 (missing title)
        List<Object> row = List.of("Artist", "Album", "1", "5");

        SongRow parsed = SheetMapper.parseSongRow(row);

        assertThat(parsed.title()).isEqualTo("");
    }

    // ==================== Tag edge cases ====================

    @Test
    void parseTags_emptyEntries_filtered() {
        // Stray whitespace and empty entries between commas
        List<Object> row = List.of("Artist", "Progressive Rock", "", "FALSE", "rock, , 90s");

        ArtistRow parsed = SheetMapper.parseArtistRow(row);

        assertThat(parsed.tags()).containsExactlyInAnyOrder("rock", "90s");
    }

    @Test
    void parseTags_emptyCell_returnsEmptyList() {
        List<Object> row = List.of("Artist", "Progressive Rock", "", "FALSE", "");

        ArtistRow parsed = SheetMapper.parseArtistRow(row);

        assertThat(parsed.tags()).isEmpty();
    }

    @Test
    void parseTags_whitespaceOnly_returnsEmptyList() {
        List<Object> row = List.of("Artist", "Progressive Rock", "", "FALSE", "   ");

        ArtistRow parsed = SheetMapper.parseArtistRow(row);

        assertThat(parsed.tags()).isEmpty();
    }

    // ==================== Stray-whitespace trimming on JOIN KEY fields ====================

    @Test
    void parseArtistRow_strayWhitespaceInName_isTrimmed() {
        // The spec requires parsing to tolerate stray whitespace; a hand-edited sheet cell
        // like "Pink Floyd " must not silently break artist→album joins in Task 11.
        List<Object> row = List.of("  Pink Floyd  ", "Progressive Rock", "", "FALSE", "");

        ArtistRow parsed = SheetMapper.parseArtistRow(row);

        assertThat(parsed.name()).isEqualTo("Pink Floyd");
    }

    @Test
    void parseAlbumRow_strayWhitespaceInArtistNameAndTitle_isTrimmed() {
        // Trailing space in artistName or title would silently drop the album when
        // matching against artist names in Task 11 sync.
        List<Object> row = List.of("  Miles Davis  ", " Kind of Blue ", "1959", "5", "FALSE", "");

        AlbumRow parsed = SheetMapper.parseAlbumRow(row);

        assertThat(parsed.artistName()).isEqualTo("Miles Davis");
        assertThat(parsed.title()).isEqualTo("Kind of Blue");
    }

    @Test
    void parseSongRow_strayWhitespaceInKeyFields_isTrimmed() {
        // artistName, albumTitle, and title are all JOIN KEY / display fields that must be trimmed.
        List<Object> row = List.of("  Bach  ", " The Well-Tempered Clavier ", "1", "2", " Prelude No. 1 ");

        SongRow parsed = SheetMapper.parseSongRow(row);

        assertThat(parsed.artistName()).isEqualTo("Bach");
        assertThat(parsed.albumTitle()).isEqualTo("The Well-Tempered Clavier");
        assertThat(parsed.title()).isEqualTo("Prelude No. 1");
    }
}
