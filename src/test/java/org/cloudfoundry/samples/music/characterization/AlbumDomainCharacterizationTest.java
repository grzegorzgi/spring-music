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
 * Characterization tests for Album domain model edge cases and type safety.
 *
 * These tests expose structural quirks in the domain model:
 *  - releaseYear is a String, not a numeric type — accepts any text value
 *  - trackCount is an int primitive that defaults to 0 when absent from JSON
 *  - albumId field exists but is never populated by the application
 *  - No field-level validation constraints are defined on the Album entity
 *  - Jackson ignores unknown JSON properties silently (FAIL_ON_UNKNOWN_PROPERTIES=false)
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AlbumDomainCharacterizationTest {

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

    private Album putAlbum(Album album) {
        Album saved = restTemplate.exchange("/albums", HttpMethod.PUT,
                new HttpEntity<>(album), Album.class).getBody();
        if (saved != null && saved.getId() != null) {
            createdIds.add(saved.getId());
        }
        return saved;
    }

    // --- releaseYear is a String, not int ---

    @Test
    public void release_year_accepts_non_numeric_string_and_persists_it_unchanged() {
        Album album = new Album("Texas Flood", "Stevie Ray Vaughan", "NOT-A-YEAR", "Blues");
        Album saved = putAlbum(album);

        assertThat(saved).isNotNull();
        assertThat(saved.getReleaseYear())
                .as("releaseYear is declared as String, not int — " +
                    "any text value including 'NOT-A-YEAR' is accepted and stored verbatim")
                .isEqualTo("NOT-A-YEAR");
    }

    @Test
    public void release_year_accepts_empty_string() {
        Album album = new Album("Texas Flood", "Stevie Ray Vaughan", "", "Blues");
        Album saved = putAlbum(album);

        assertThat(saved).isNotNull();
        assertThat(saved.getReleaseYear())
                .as("releaseYear accepts empty string — no minimum-length or format validation exists")
                .isEmpty();
    }

    @Test
    public void release_year_accepts_null_and_persists_as_null() {
        Album album = new Album("Texas Flood", "Stevie Ray Vaughan", null, "Blues");
        Album saved = putAlbum(album);

        assertThat(saved).isNotNull();
        assertThat(saved.getReleaseYear())
                .as("releaseYear accepts null — no @NotNull constraint is defined on the field")
                .isNull();
    }

    // --- trackCount primitive default ---

    @Test
    public void track_count_defaults_to_zero_when_omitted_from_json_request() {
        // Album constructed without calling setTrackCount — Java int defaults to 0
        Album album = new Album("Abbey Road", "The Beatles", "1969", "Rock");
        Album saved = putAlbum(album);

        assertThat(saved).isNotNull();
        assertThat(saved.getTrackCount())
                .as("trackCount defaults to 0 when absent from the JSON payload — " +
                    "Java int primitive serialises to 0 by default, no null-safe handling")
                .isEqualTo(0);
    }

    @Test
    public void track_count_persists_explicit_positive_value() {
        Album album = new Album("Abbey Road", "The Beatles", "1969", "Rock");
        album.setTrackCount(17);
        Album saved = putAlbum(album);

        assertThat(saved).isNotNull();
        assertThat(saved.getTrackCount())
                .as("trackCount persists the explicitly set value 17")
                .isEqualTo(17);
    }

    // --- albumId ghost field ---

    @Test
    public void album_id_field_is_null_after_creation_and_is_never_populated() {
        Album album = new Album("Rumours", "Fleetwood Mac", "1977", "Rock");
        Album saved = putAlbum(album);

        assertThat(saved).isNotNull();
        assertThat(saved.getAlbumId())
                .as("albumId field exists on the Album entity but is never set by the application — " +
                    "it is always null after creation despite appearing in the JSON response schema")
                .isNull();
    }

    // --- JSON deserialization tolerance ---

    @Test
    public void extra_json_fields_in_request_body_are_silently_ignored() {
        // Send raw JSON with an extra field the Album class does not have
        String jsonWithExtraField =
                "{\"title\":\"Thriller\",\"artist\":\"Michael Jackson\"," +
                "\"releaseYear\":\"1982\",\"genre\":\"Pop\"," +
                "\"unknownExtraField\":\"this should be silently dropped\"}";

        ResponseEntity<Album> response = restTemplate.exchange(
                "/albums", HttpMethod.PUT,
                new HttpEntity<>(jsonWithExtraField,
                        buildJsonHeaders()),
                Album.class);

        assertThat(response.getStatusCode())
                .as("PUT /albums with an unknown JSON field returns HTTP 200 — " +
                    "Jackson is configured with FAIL_ON_UNKNOWN_PROPERTIES=false, " +
                    "extra fields are silently discarded without error")
                .isEqualTo(HttpStatus.OK);

        Album saved = response.getBody();
        if (saved != null && saved.getId() != null) createdIds.add(saved.getId());

        assertThat(saved).isNotNull();
        assertThat(saved.getTitle())
                .as("Album is saved correctly despite the extra JSON field")
                .isEqualTo("Thriller");
    }

    @Test
    public void null_title_is_accepted_and_stored() {
        Album album = new Album(null, "Unknown Artist", "2000", "Rock");
        Album saved = putAlbum(album);

        assertThat(saved).isNotNull();
        assertThat(saved.getTitle())
                .as("title accepts null — no @NotNull or @NotBlank validation constraint exists")
                .isNull();
    }

    // --- helper ---

    private org.springframework.http.HttpHeaders buildJsonHeaders() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return headers;
    }
}
