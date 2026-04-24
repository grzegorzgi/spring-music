package org.cloudfoundry.samples.music.characterization;

import org.cloudfoundry.samples.music.domain.Album;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization tests for the Album CRUD API contract.
 *
 * These tests pin current behavior AS-IS, bugs included.
 * Do NOT "fix" a failing test by changing the assertion — a failure means
 * someone changed what the monolith does. Update the test only after
 * deliberately deciding to accept the behavioral change.
 *
 * Key anomalies pinned here:
 *  - PUT creates new albums (not idempotent update as REST convention dictates)
 *  - POST updates existing albums (not create as REST convention dictates)
 *  - GET /albums/{id} returns HTTP 200 + null body for missing IDs, not 404
 *  - DELETE /albums/{id} returns HTTP 200 silently even for non-existent IDs
 *  - Creation returns HTTP 200, not 201 Created
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AlbumCrudCharacterizationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private final List<String> createdIds = new ArrayList<>();

    @After
    public void cleanup() {
        for (String id : createdIds) {
            restTemplate.delete("/albums/" + id);
        }
        createdIds.clear();
    }

    // --- helpers ---

    private ResponseEntity<Album> putAlbumForResponse(Album album) {
        return restTemplate.exchange("/albums", HttpMethod.PUT,
                new HttpEntity<>(album), Album.class);
    }

    private Album putAlbum(Album album) {
        Album saved = putAlbumForResponse(album).getBody();
        if (saved != null && saved.getId() != null) {
            createdIds.add(saved.getId());
        }
        return saved;
    }

    private Album postAlbum(Album album) {
        return restTemplate.exchange("/albums", HttpMethod.POST,
                new HttpEntity<>(album), Album.class).getBody();
    }

    // --- tests ---

    @Test
    public void put_creates_album_and_returns_200_not_201() {
        ResponseEntity<Album> response = putAlbumForResponse(
                new Album("Nevermind", "Nirvana", "1991", "Rock"));

        Album saved = response.getBody();
        if (saved != null && saved.getId() != null) createdIds.add(saved.getId());

        assertThat(response.getStatusCode())
                .as("PUT /albums returns HTTP 200 — monolith does not use 201 Created " +
                    "even though PUT is used as the create verb")
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    public void put_assigns_server_generated_id_to_new_album() {
        Album saved = putAlbum(new Album("Nevermind", "Nirvana", "1991", "Rock"));

        assertThat(saved)
                .as("PUT /albums returns the saved album in the response body")
                .isNotNull();
        assertThat(saved.getId())
                .as("PUT /albums assigns a non-null, non-empty server-generated id")
                .isNotNull()
                .isNotEmpty();
    }

    @Test
    public void put_called_twice_with_identical_data_creates_two_distinct_albums() {
        Album first = putAlbum(new Album("Nevermind", "Nirvana", "1991", "Rock"));
        Album second = putAlbum(new Album("Nevermind", "Nirvana", "1991", "Rock"));

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(first.getId())
                .as("Two consecutive PUT /albums calls with identical payload produce two " +
                    "different IDs — PUT is not idempotent, it always creates a new record")
                .isNotEqualTo(second.getId());
    }

    @Test
    public void post_updates_existing_album_and_returns_200() {
        Album created = putAlbum(new Album("Nevermind", "Nirvana", "1991", "Rock"));
        assertThat(created).isNotNull();

        created.setTitle("In Utero");
        ResponseEntity<Album> response = restTemplate.exchange("/albums", HttpMethod.POST,
                new HttpEntity<>(created), Album.class);

        assertThat(response.getStatusCode())
                .as("POST /albums returns HTTP 200 when updating an existing album")
                .isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getTitle())
                .as("POST /albums persists the updated title")
                .isEqualTo("In Utero");
    }

    @Test
    public void post_fully_overwrites_stored_album_including_fields_absent_from_request() {
        // Create with explicit trackCount=12
        Album toCreate = new Album("Nevermind", "Nirvana", "1991", "Rock");
        toCreate.setTrackCount(12);
        Album created = putAlbum(toCreate);
        assertThat(created).isNotNull();
        assertThat(created.getTrackCount()).isEqualTo(12);

        // POST the same album with a new title; trackCount defaults to 0 in Java
        Album update = new Album("In Utero", created.getArtist(),
                created.getReleaseYear(), created.getGenre());
        update.setId(created.getId());
        // trackCount intentionally not set — Java int primitive defaults to 0

        Album result = postAlbum(update);

        assertThat(result.getTrackCount())
                .as("POST /albums is a full replace — trackCount reverts to 0 because the " +
                    "update payload omitted it (Java int serialises as 0 when unset). " +
                    "There is no partial-update (PATCH) support.")
                .isEqualTo(0);
    }

    @Test
    public void delete_existing_album_returns_200_ok() {
        Album created = putAlbum(new Album("Nevermind", "Nirvana", "1991", "Rock"));
        assertThat(created).isNotNull();
        String id = created.getId();
        createdIds.remove(id);

        ResponseEntity<Void> response = restTemplate.exchange(
                "/albums/" + id, HttpMethod.DELETE, null, Void.class);

        assertThat(response.getStatusCode())
                .as("DELETE /albums/{id} returns HTTP 200 — monolith does not use 204 No Content")
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    public void delete_nonexistent_album_returns_500_not_404() {
        ResponseEntity<Void> response = restTemplate.exchange(
                "/albums/id-that-does-not-exist-xyz", HttpMethod.DELETE, null, Void.class);

        assertThat(response.getStatusCode())
                .as("DELETE /albums/{id} with a non-existent ID returns HTTP 500 — " +
                    "Spring Data JPA deleteById() throws EmptyResultDataAccessException " +
                    "when the entity is missing; no 404 is returned, the error propagates as 500")
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    public void get_by_nonexistent_id_returns_200_not_404() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/albums/id-that-does-not-exist-xyz", String.class);

        assertThat(response.getStatusCode())
                .as("GET /albums/{id} with a non-existent ID returns HTTP 200, not 404 — " +
                    "the monolith returns a null body instead of a proper 404 Not Found response")
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    public void get_all_albums_returns_json_array_not_object() {
        ResponseEntity<String> response = restTemplate.getForEntity("/albums", String.class);

        assertThat(response.getStatusCode())
                .as("GET /albums returns HTTP 200")
                .isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .as("GET /albums response body is a JSON array (starts with '['), not a wrapped object")
                .isNotNull()
                .startsWith("[");
    }
}
