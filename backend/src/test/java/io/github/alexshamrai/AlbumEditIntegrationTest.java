package io.github.alexshamrai;

import io.github.alexshamrai.domain.Genre;
import io.github.alexshamrai.dto.AlbumCreateDto;
import io.github.alexshamrai.dto.AlbumDto;
import io.github.alexshamrai.dto.AlbumEditDto;
import io.github.alexshamrai.dto.AlbumSummaryDto;
import io.github.alexshamrai.dto.ArtistCreateDto;
import io.github.alexshamrai.dto.ArtistDto;
import io.github.alexshamrai.dto.SongDto;
import io.github.alexshamrai.dto.SongEditInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end edit flow over real HTTP against an in-memory H2 DB: exercises the
 * batch reconcile endpoint (add / rename / delete songs, album rename), the
 * collision guard, auth, and the RequireXhrHeaderFilter — the same layers a
 * browser hits, minus the (test-profile-disabled) Google Sheets push.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
class AlbumEditIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    void authenticate() {
        restTemplate = restTemplate.withBasicAuth("admin", "admin");
        restTemplate.getRestTemplate().getInterceptors().add((request, body, execution) -> {
            request.getHeaders().add("X-Requested-With", "XMLHttpRequest");
            return execution.execute(request, body);
        });
    }

    private Long createArtist(String name) {
        var created = restTemplate.postForEntity("/api/artists",
                new ArtistCreateDto(name, Genre.JAZZ_AND_FUNK, null), ArtistDto.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return created.getBody().getId();
    }

    private Long createAlbum(String title, Long artistId) {
        var created = restTemplate.postForEntity("/api/albums",
                new AlbumCreateDto(title, 1959, artistId), AlbumSummaryDto.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return created.getBody().getId();
    }

    private AlbumDto edit(Long albumId, AlbumEditDto dto) {
        var response = restTemplate.exchange("/api/albums/" + albumId + "/edit",
                HttpMethod.PUT, new HttpEntity<>(dto), AlbumDto.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private Map<String, Long> songIdsByTitle(AlbumDto album) {
        return album.getSongs().stream()
                .collect(Collectors.toMap(SongDto::getTitle, SongDto::getId));
    }

    @Test
    void editLifecycle_addRenameDeleteSongs_andRenameAlbum() {
        Long artistId = createArtist("Edit Lifecycle Artist");
        Long albumId = createAlbum("Original Title", artistId);

        // ADD three songs to the (initially empty) album
        AlbumDto afterAdd = edit(albumId, new AlbumEditDto("Original Title", 1959, List.of(
                new SongEditInput(null, "Alpha"),
                new SongEditInput(null, "Bravo"),
                new SongEditInput(null, "Charlie"))));
        assertThat(afterAdd.getSongs()).hasSize(3);
        assertThat(afterAdd.getSongs()).extracting(SongDto::getTrackNumber).containsExactly(1, 2, 3);

        Map<String, Long> ids = songIdsByTitle(afterAdd);

        // RENAME Alpha, DELETE Bravo (omit it), KEEP Charlie, ADD Delta
        AlbumDto afterReconcile = edit(albumId, new AlbumEditDto("Original Title", 1959, List.of(
                new SongEditInput(ids.get("Alpha"), "Alpha Prime"),
                new SongEditInput(ids.get("Charlie"), "Charlie"),
                new SongEditInput(null, "Delta"))));
        assertThat(afterReconcile.getSongs()).extracting(SongDto::getTitle)
                .containsExactlyInAnyOrder("Alpha Prime", "Charlie", "Delta");
        // new song auto-numbered after the last disc-1 track (Charlie was 3) → 4
        assertThat(songIdsByTitle(afterReconcile)).doesNotContainKey("Bravo");
        SongDto delta = afterReconcile.getSongs().stream()
                .filter(s -> s.getTitle().equals("Delta")).findFirst().orElseThrow();
        assertThat(delta.getTrackNumber()).isEqualTo(4);
        assertThat(delta.getDiscNumber()).isEqualTo(1);
        // renamed song kept its id and track
        assertThat(afterReconcile.getSongs().stream()
                .filter(s -> s.getTitle().equals("Alpha Prime")).findFirst().orElseThrow().getId())
                .isEqualTo(ids.get("Alpha"));

        // RENAME the album (title + year), keeping the current songs
        Map<String, Long> ids2 = songIdsByTitle(afterReconcile);
        AlbumDto afterRename = edit(albumId, new AlbumEditDto("Renamed Title", 1971, List.of(
                new SongEditInput(ids2.get("Alpha Prime"), "Alpha Prime"),
                new SongEditInput(ids2.get("Charlie"), "Charlie"),
                new SongEditInput(ids2.get("Delta"), "Delta"))));
        assertThat(afterRename.getTitle()).isEqualTo("Renamed Title");
        assertThat(afterRename.getYear()).isEqualTo(1971);
        assertThat(afterRename.getSongs()).hasSize(3);

        // PERSISTENCE: a fresh GET reflects the reconciled state
        var reloaded = restTemplate.getForEntity("/api/albums/" + albumId, AlbumDto.class);
        assertThat(reloaded.getBody().getTitle()).isEqualTo("Renamed Title");
        assertThat(reloaded.getBody().getSongs()).extracting(SongDto::getTitle)
                .containsExactlyInAnyOrder("Alpha Prime", "Charlie", "Delta");

        // Cleanup
        restTemplate.delete("/api/albums/" + albumId);
        restTemplate.delete("/api/artists/" + artistId);
    }

    @Test
    void edit_renameToSiblingTitle_isRejectedWith400() {
        Long artistId = createArtist("Collision Artist");
        Long first = createAlbum("First Album", artistId);
        Long second = createAlbum("Second Album", artistId);

        // Renaming the first album to the second's title must be rejected
        var response = restTemplate.exchange("/api/albums/" + first + "/edit",
                HttpMethod.PUT,
                new HttpEntity<>(new AlbumEditDto("Second Album", 1959, List.of())),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // The first album is unchanged (transaction rolled back)
        var reloaded = restTemplate.getForEntity("/api/albums/" + first, AlbumDto.class);
        assertThat(reloaded.getBody().getTitle()).isEqualTo("First Album");

        // Cleanup
        restTemplate.delete("/api/albums/" + first);
        restTemplate.delete("/api/albums/" + second);
        restTemplate.delete("/api/artists/" + artistId);
    }
}
