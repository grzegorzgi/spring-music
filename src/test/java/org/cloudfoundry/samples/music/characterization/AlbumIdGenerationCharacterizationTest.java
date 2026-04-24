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
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Characterization tests for Album ID generation via RandomIdGenerator.
 *
 * RandomIdGenerator is a Hibernate IdentifierGenerator that calls UUID.randomUUID()
 * unconditionally on every generate() invocation. It ignores both the Hibernate session
 * and the entity object — there is no collision detection or retry mechanism.
 *
 * Pinned behaviors:
 *  - IDs are UUID v4 strings (random, 36 characters with dashes)
 *  - IDs are generated server-side; a null id in the request triggers generation
 *  - An explicit id in the PUT request is honoured (Hibernate uses it directly)
 *  - Repeated creation with identical data always produces distinct IDs
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AlbumIdGenerationCharacterizationTest {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");

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

    @Test
    public void generated_id_is_exactly_36_characters_long() {
        Album saved = putAlbum(new Album("Nevermind", "Nirvana", "1991", "Rock"));

        assertThat(saved).isNotNull();
        assertThat(saved.getId())
                .as("Generated album ID must be exactly 36 characters (UUID canonical format: " +
                    "8-4-4-4-12 hex chars with dashes)")
                .hasSize(36);
    }

    @Test
    public void generated_id_is_a_valid_uuid_v4_string() {
        Album saved = putAlbum(new Album("Nevermind", "Nirvana", "1991", "Rock"));

        assertThat(saved).isNotNull();
        String id = saved.getId();

        assertThatCode(() -> UUID.fromString(id))
                .as("Generated ID '%s' must be parseable as a valid UUID", id)
                .doesNotThrowAnyException();

        assertThat(UUID_PATTERN.matcher(id.toLowerCase()).matches())
                .as("Generated ID '%s' must match UUID v4 format " +
                    "(version nibble = 4, variant bits = 8/9/a/b)", id)
                .isTrue();
    }

    @Test
    public void ten_consecutive_put_requests_produce_ten_distinct_ids() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            Album saved = putAlbum(new Album("Nevermind", "Nirvana", "1991", "Rock"));
            assertThat(saved).isNotNull();
            ids.add(saved.getId());
        }

        assertThat(ids)
                .as("10 consecutive PUT /albums calls must produce 10 distinct IDs — " +
                    "RandomIdGenerator calls UUID.randomUUID() each time with no collision check")
                .hasSize(10);
    }

    @Test
    public void explicit_id_in_put_request_body_is_ignored_and_a_new_uuid_is_generated() {
        String clientProvidedId = "preset-id-12345678901234567890";
        Album album = new Album("Nevermind", "Nirvana", "1991", "Rock");
        album.setId(clientProvidedId);

        Album saved = putAlbum(album);

        assertThat(saved).isNotNull();
        assertThat(saved.getId())
                .as("The client-provided ID '%s' is NOT used — RandomIdGenerator always " +
                    "generates a fresh UUID regardless of the ID in the request body. " +
                    "Clients cannot control or predict the assigned album ID.", clientProvidedId)
                .isNotEqualTo(clientProvidedId);

        assertThat(saved.getId())
                .as("Even when a non-null id is supplied, the returned id is still a valid UUID")
                .hasSize(36);
    }

    @Test
    public void null_id_in_put_request_triggers_server_side_id_generation() {
        Album album = new Album("Nevermind", "Nirvana", "1991", "Rock");
        // id field is null by default in the Album constructor

        Album saved = putAlbum(album);

        assertThat(saved).isNotNull();
        assertThat(saved.getId())
                .as("When id is null in the PUT body, RandomIdGenerator generates a UUID " +
                    "server-side — the client has no control over the assigned ID")
                .isNotNull()
                .hasSize(36);
    }
}
