package org.cloudfoundry.samples.music.characterization;

import org.cloudfoundry.samples.music.domain.ApplicationInfo;
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
 * Characterization tests for the application info endpoints.
 *
 * Pinned behaviors:
 *  - /appinfo exposes all active Spring profiles without authentication
 *  - /appinfo exposes all bound Cloud Foundry service names without authentication
 *  - /service exposes the full CfService object list without authentication
 *  - In a non-CF environment (tests, local dev) profiles and services arrays are empty
 *
 * Security note: these endpoints are unauthenticated and leak runtime topology.
 * Any change that adds authentication or removes the endpoints will break these tests —
 * that is intentional; such changes must be explicitly acknowledged.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AppInfoCharacterizationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    public void appinfo_endpoint_is_publicly_accessible_without_authentication() {
        ResponseEntity<ApplicationInfo> response =
                restTemplate.getForEntity("/appinfo", ApplicationInfo.class);

        assertThat(response.getStatusCode())
                .as("GET /appinfo returns HTTP 200 with no credentials — " +
                    "the endpoint has no authentication or authorisation guard")
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    public void appinfo_profiles_is_empty_array_when_no_spring_profile_is_active() {
        ApplicationInfo info = restTemplate.getForObject("/appinfo", ApplicationInfo.class);

        assertThat(info).isNotNull();
        assertThat(info.getProfiles())
                .as("profiles array is empty in the default H2 test context — " +
                    "SpringApplicationContextInitializer does not run under @SpringBootTest, " +
                    "so no database profile is activated")
                .isEmpty();
    }

    @Test
    public void appinfo_services_is_empty_array_when_vcap_services_is_not_set() {
        ApplicationInfo info = restTemplate.getForObject("/appinfo", ApplicationInfo.class);

        assertThat(info).isNotNull();
        assertThat(info.getServices())
                .as("services array is empty when VCAP_SERVICES environment variable is not set — " +
                    "CfEnv returns no services in non-Cloud-Foundry environments")
                .isEmpty();
    }

    @Test
    public void service_endpoint_is_publicly_accessible_and_returns_empty_list_locally() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/service", String.class);

        assertThat(response.getStatusCode())
                .as("GET /service returns HTTP 200 with no credentials — " +
                    "this endpoint leaks Cloud Foundry service details with no authentication")
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody())
                .as("GET /service response body is '[]' when no CF services are bound")
                .isEqualTo("[]");
    }

    @Test
    public void appinfo_response_contains_both_profiles_and_services_fields() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/appinfo", String.class);

        assertThat(response.getBody())
                .as("GET /appinfo JSON body includes 'profiles' key")
                .contains("profiles");
        assertThat(response.getBody())
                .as("GET /appinfo JSON body includes 'services' key")
                .contains("services");
    }
}
