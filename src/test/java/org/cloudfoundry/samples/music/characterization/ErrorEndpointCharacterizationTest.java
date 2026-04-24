package org.cloudfoundry.samples.music.characterization;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization tests for the ErrorController chaos endpoints.
 *
 * IMPORTANT — ENDPOINTS DELIBERATELY EXCLUDED FROM AUTOMATED TESTING:
 *
 *   GET /errors/kill      — calls System.exit(1), terminates the JVM immediately.
 *                           Including this in CI would silently kill the test process.
 *
 *   GET /errors/fill-heap — infinite loop allocating ~38 MB per iteration until OutOfMemoryError.
 *                           This would crash the test JVM and any co-located processes.
 *
 * Both endpoints are publicly accessible with no authentication or rate-limiting.
 * They represent an unconditional denial-of-service capability in the monolith.
 * See ADR-001 for the decision not to automate tests for these endpoints.
 *
 * What IS tested here:
 *   GET /errors/throw — throws NullPointerException, returns HTTP 500.
 *   This is safe to exercise repeatedly in CI.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ErrorEndpointCharacterizationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    public void throw_endpoint_returns_500_internal_server_error() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/errors/throw", String.class);

        assertThat(response.getStatusCode())
                .as("GET /errors/throw returns HTTP 500 — the endpoint unconditionally " +
                    "throws NullPointerException and Spring's default error handler " +
                    "maps it to Internal Server Error")
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    public void throw_endpoint_is_accessible_without_authentication() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/errors/throw", String.class);

        // Any response (even 500) means the endpoint was reached without credentials.
        assertThat(response.getStatusCode().value())
                .as("GET /errors/throw is reachable without any authentication — " +
                    "no security layer protects the chaos endpoints")
                .isNotEqualTo(401)
                .isNotEqualTo(403);
    }

    @Test
    public void throw_endpoint_response_body_contains_standard_spring_error_fields() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/errors/throw", String.class);

        assertThat(response.getBody())
                .as("Spring Boot default error response body must contain 'status' field")
                .contains("status");
        assertThat(response.getBody())
                .as("Spring Boot default error response body must contain 'error' field")
                .contains("error");
        assertThat(response.getBody())
                .as("Spring Boot default error response body must contain 'path' field")
                .contains("path");
    }

    // -------------------------------------------------------------------------
    // The following tests are intentionally NOT implemented.
    // Attempting to call GET /errors/kill or GET /errors/fill-heap in a test
    // would terminate the JVM or exhaust heap, making the test suite unsafe
    // to run in CI. The behavior is documented here as comments only.
    // -------------------------------------------------------------------------

    // BEHAVIOR: GET /errors/kill
    //   Calls System.exit(1) — JVM terminates immediately.
    //   No HTTP response is returned. Process exit code is 1.
    //   Accessible without authentication at any time.

    // BEHAVIOR: GET /errors/fill-heap
    //   Enters an infinite loop: while(true) { junk.add(new int[9999999]); }
    //   Each iteration allocates ~38 MB. Loop continues until OutOfMemoryError.
    //   The junk ArrayList is an instance field — memory is never released.
    //   Accessible without authentication at any time.
}
