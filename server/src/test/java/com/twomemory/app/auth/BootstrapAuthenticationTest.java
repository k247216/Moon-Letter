package com.twomemory.app.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 2 acceptance: the deployment secret opens the founding member's slot —
 * creating it on an empty installation and reclaiming it on an installed one —
 * the issued bearer token authenticates real HTTP requests, an invalid
 * bearer token receives 401, and a guessed X-User-Id grants no access.
 *
 * Runs against a dedicated PostgreSQL database created (and dropped) by this
 * class so the "empty installation" precondition is reproducible.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BootstrapAuthenticationTest {

    private static final String ADMIN_URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String ADMIN_USER = "moon_letter";
    private static final String ADMIN_PASSWORD = "moon_letter_dev_only";
    private static final String TEST_DB = "moon_letter_boot_test";
    private static final String BOOTSTRAP_SECRET = "task2-local-bootstrap-secret";

    static {
        try (Connection connection = DriverManager.getConnection(ADMIN_URL, ADMIN_USER, ADMIN_PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + TEST_DB + " WITH (FORCE)");
            statement.execute("CREATE DATABASE " + TEST_DB);
        } catch (Exception exception) {
            throw new IllegalStateException("could not prepare isolated bootstrap test database", exception);
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/" + TEST_DB);
        registry.add("spring.datasource.username", () -> ADMIN_USER);
        registry.add("spring.datasource.password", () -> ADMIN_PASSWORD);
        registry.add("moon-letter.bootstrap.secret", () -> BOOTSTRAP_SECRET);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void bootstrapIsOneTimeAndBearerTokenIsEnforced() {
        // 0. Public health must be reachable without a token.
        ResponseEntity<String> health = restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(health.getStatusCode())
                .as("health body: %s", health.getBody())
                .isEqualTo(HttpStatus.OK);

        // 1. Bootstrap without the deployment secret is rejected.
        ResponseEntity<Map> wrongSecret = postBootstrap("wrong-secret", "小满");
        assertThat(wrongSecret.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 2. Bootstrap on the empty installation creates the first member,
        //    the couple space and the first device session, returning the
        //    opaque token exactly once.
        ResponseEntity<Map> first = postBootstrap(BOOTSTRAP_SECRET, "小满");
        assertThat(first.getStatusCode())
                .as("bootstrap response body: %s", first.getBody())
                .isEqualTo(HttpStatus.CREATED);
        String token = (String) first.getBody().get("token");
        String userId = (String) first.getBody().get("userId");
        String coupleId = (String) first.getBody().get("coupleId");
        assertThat(token).isNotBlank();
        assertThat(userId).isNotBlank();
        assertThat(coupleId).isNotBlank();

        // 3. A repeated bootstrap on an installed space reclaims the founding
        //    member's own slot rather than minting a second identity: the same
        //    userId and coupleId come back, the token is rotated, and the name
        //    already on the profile is kept (a reinstall is not a rename).
        ResponseEntity<Map> reclaim = postBootstrap(BOOTSTRAP_SECRET, "阿屿");
        assertThat(reclaim.getStatusCode())
                .as("reclaim response body: %s", reclaim.getBody())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(reclaim.getBody().get("userId")).isEqualTo(userId);
        assertThat(reclaim.getBody().get("coupleId")).isEqualTo(coupleId);
        String reclaimedToken = (String) reclaim.getBody().get("token");
        assertThat(reclaimedToken).isNotBlank().isNotEqualTo(token);

        // 4. The newest issued bearer token authenticates the space read, while
        //    the superseded one is dead: recovery cannot leave the lost device
        //    holding a working session.
        ResponseEntity<Map> authorized = exchangeWithBearer("/api/v1/couple/" + coupleId, reclaimedToken);
        assertThat(authorized.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchangeWithBearer("/api/v1/couple/" + coupleId, token).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        // 5. An invalid bearer token receives 401.
        ResponseEntity<Map> invalid = exchangeWithBearer("/api/v1/couple/" + coupleId, "not-a-real-token");
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // 6. A guessed X-User-Id without a bearer token grants no access.
        HttpHeaders guessed = new HttpHeaders();
        guessed.set("X-User-Id", userId);
        ResponseEntity<Map> forged = restTemplate.exchange("/api/v1/couple/" + coupleId, HttpMethod.GET,
                new HttpEntity<>(guessed), Map.class);
        assertThat(forged.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ResponseEntity<Map> postBootstrap(String secret, String displayName) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Bootstrap-Secret", secret);
        return restTemplate.postForEntity("/api/v1/bootstrap",
                new HttpEntity<>(Map.of("displayName", displayName), headers), Map.class);
    }

    private ResponseEntity<Map> exchangeWithBearer(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }
}
