package io.github.alexshamrai;

import io.github.alexshamrai.dto.ArtistCreateDto;
import io.github.alexshamrai.dto.ArtistDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
class ArtistCrudIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void fullCrudLifecycle() {
        // CREATE
        var createDto = new ArtistCreateDto("Integration Band", "Rock", "Indie");
        var createResponse = restTemplate.postForEntity("/api/artists", createDto, ArtistDto.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody()).isNotNull();
        Long id = createResponse.getBody().getId();
        assertThat(id).isNotNull();
        assertThat(createResponse.getBody().getName()).isEqualTo("Integration Band");

        // READ
        var getResponse = restTemplate.getForEntity("/api/artists/" + id, ArtistDto.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().getName()).isEqualTo("Integration Band");
        assertThat(getResponse.getBody().getGenre()).isEqualTo("Rock");
        assertThat(getResponse.getBody().getSubgenre()).isEqualTo("Indie");

        // UPDATE
        var updateResponse = restTemplate.exchange(
                "/api/artists/" + id, HttpMethod.PUT,
                new HttpEntity<>(new io.github.alexshamrai.dto.ArtistUpdateDto("Updated Band", null, null)),
                ArtistDto.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().getName()).isEqualTo("Updated Band");
        assertThat(updateResponse.getBody().getGenre()).isEqualTo("Rock"); // unchanged

        // DELETE
        var deleteResponse = restTemplate.exchange(
                "/api/artists/" + id, HttpMethod.DELETE, null, Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // VERIFY DELETED
        var afterDelete = restTemplate.getForEntity("/api/artists/" + id, String.class);
        assertThat(afterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void toggleFavorite_roundTrip() {
        // Create artist
        var createDto = new ArtistCreateDto("Favorite Band", "Jazz", null);
        var created = restTemplate.postForEntity("/api/artists", createDto, ArtistDto.class);
        Long id = created.getBody().getId();
        assertThat(created.getBody().isFavorite()).isFalse();

        // Toggle ON
        var toggle1 = restTemplate.exchange(
                "/api/artists/" + id + "/favorite", HttpMethod.PATCH, null, ArtistDto.class);
        assertThat(toggle1.getBody().isFavorite()).isTrue();

        // Toggle OFF
        var toggle2 = restTemplate.exchange(
                "/api/artists/" + id + "/favorite", HttpMethod.PATCH, null, ArtistDto.class);
        assertThat(toggle2.getBody().isFavorite()).isFalse();

        // Cleanup
        restTemplate.delete("/api/artists/" + id);
    }

    @Test
    void setTags_createsAndAssigns() {
        // Create artist
        var createDto = new ArtistCreateDto("Tagged Band", "Rock", null);
        var created = restTemplate.postForEntity("/api/artists", createDto, ArtistDto.class);
        Long id = created.getBody().getId();

        // Set tags
        var setTagsResponse = restTemplate.exchange(
                "/api/artists/" + id + "/tags", HttpMethod.PUT,
                new HttpEntity<>(List.of("rock", "classic")),
                ArtistDto.class);
        assertThat(setTagsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(setTagsResponse.getBody().getTags()).containsExactly("classic", "rock");

        // Replace tags
        var replaceTags = restTemplate.exchange(
                "/api/artists/" + id + "/tags", HttpMethod.PUT,
                new HttpEntity<>(List.of("new-tag")),
                ArtistDto.class);
        assertThat(replaceTags.getBody().getTags()).containsExactly("new-tag");

        // Cleanup
        restTemplate.delete("/api/artists/" + id);
    }

    @Test
    void listWithFilters_returnsFilteredResults() {
        // Create artists in different genres
        var rock = restTemplate.postForEntity("/api/artists",
                new ArtistCreateDto("Rock Band", "FilterTestRock", null), ArtistDto.class);
        var jazz = restTemplate.postForEntity("/api/artists",
                new ArtistCreateDto("Jazz Band", "FilterTestJazz", null), ArtistDto.class);

        // Filter by genre
        var filtered = restTemplate.exchange(
                "/api/artists?genre=FilterTestRock", HttpMethod.GET, null,
                new ParameterizedTypeReference<List<ArtistDto>>() {});
        assertThat(filtered.getBody()).hasSize(1);
        assertThat(filtered.getBody().get(0).getName()).isEqualTo("Rock Band");

        // Cleanup
        restTemplate.delete("/api/artists/" + rock.getBody().getId());
        restTemplate.delete("/api/artists/" + jazz.getBody().getId());
    }

    @Test
    void listWithFilters_subgenreFilter() {
        var indie = restTemplate.postForEntity("/api/artists",
                new ArtistCreateDto("Indie Band", "SubgenreTestRock", "Indie"), ArtistDto.class);
        var prog = restTemplate.postForEntity("/api/artists",
                new ArtistCreateDto("Prog Band", "SubgenreTestRock", "Progressive"), ArtistDto.class);

        var filtered = restTemplate.exchange(
                "/api/artists?subgenre=Indie", HttpMethod.GET, null,
                new ParameterizedTypeReference<List<ArtistDto>>() {});
        assertThat(filtered.getBody()).extracting(ArtistDto::getName).contains("Indie Band");
        assertThat(filtered.getBody()).extracting(ArtistDto::getName).doesNotContain("Prog Band");

        // Cleanup
        restTemplate.delete("/api/artists/" + indie.getBody().getId());
        restTemplate.delete("/api/artists/" + prog.getBody().getId());
    }

    @Test
    void listWithFilters_favoriteFilter() {
        var artist = restTemplate.postForEntity("/api/artists",
                new ArtistCreateDto("Fav Test Band", "FavFilterGenre", null), ArtistDto.class);
        Long id = artist.getBody().getId();

        // Toggle to favorite
        restTemplate.exchange("/api/artists/" + id + "/favorite", HttpMethod.PATCH, null, ArtistDto.class);

        var filtered = restTemplate.exchange(
                "/api/artists?favorite=true&genre=FavFilterGenre", HttpMethod.GET, null,
                new ParameterizedTypeReference<List<ArtistDto>>() {});
        assertThat(filtered.getBody()).hasSize(1);
        assertThat(filtered.getBody().get(0).getName()).isEqualTo("Fav Test Band");

        // Cleanup
        restTemplate.delete("/api/artists/" + id);
    }

    @Test
    void listWithFilters_tagFilter() {
        var artist = restTemplate.postForEntity("/api/artists",
                new ArtistCreateDto("Tag Test Band", "TagFilterGenre", null), ArtistDto.class);
        Long id = artist.getBody().getId();

        // Set tags
        restTemplate.exchange("/api/artists/" + id + "/tags", HttpMethod.PUT,
                new HttpEntity<>(List.of("unique-filter-tag")), ArtistDto.class);

        // Filter by tag
        var filtered = restTemplate.exchange(
                "/api/artists?tag=unique-filter-tag", HttpMethod.GET, null,
                new ParameterizedTypeReference<List<ArtistDto>>() {});
        assertThat(filtered.getBody()).extracting(ArtistDto::getName).contains("Tag Test Band");

        // Filter by non-existing tag
        var noMatch = restTemplate.exchange(
                "/api/artists?tag=nonexistent-tag", HttpMethod.GET, null,
                new ParameterizedTypeReference<List<ArtistDto>>() {});
        assertThat(noMatch.getBody()).extracting(ArtistDto::getName).doesNotContain("Tag Test Band");

        // Cleanup
        restTemplate.delete("/api/artists/" + id);
    }
}
