package com.twomemory.app.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 2 / H4 acceptance (spec 2026-10-01 §5.4): with NO valid session
 * existing at all — both devices unusable — a local-only admin command
 * revokes leftover sessions, issues a replacement session for a chosen
 * member, records an audit row without token material, and the new session
 * reads the space's existing data through real HTTP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SessionRecoveryTest {

    private static final String ADMIN_URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String ADMIN_USER = "moon_letter";
    private static final String ADMIN_PASSWORD = "moon_letter_dev_only";
    private static final String TEST_DB = "moon_letter_recovery_test";
    private static final String BOOTSTRAP_SECRET = "recovery-test-bootstrap-secret";

    static {
        try (Connection connection = DriverManager.getConnection(ADMIN_URL, ADMIN_USER, ADMIN_PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + TEST_DB + " WITH (FORCE)");
            statement.execute("CREATE DATABASE " + TEST_DB);
        } catch (Exception exception) {
            throw new IllegalStateException("could not prepare isolated recovery test database", exception);
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

    @Autowired
    private SessionAdminCommand sessionAdminCommand;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void lostSessionsAreRecoverableThroughLocalAdminCommand() {
        // 1. Seed: bootstrap member A, pair member B; both sessions work.
        ResponseEntity<Map> boot = postBootstrap(BOOTSTRAP_SECRET, "小满");
        assertThat(boot.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String tokenA = (String) boot.getBody().get("token");
        String userIdA = (String) boot.getBody().get("userId");
        String coupleId = (String) boot.getBody().get("coupleId");

        String pairingToken = exchangeWithBearer("/api/v1/couple/" + coupleId + "/pairing-token", tokenA, "POST")
                .getBody().get("pairingToken").toString();
        ResponseEntity<Map> paired = restTemplate.postForEntity("/api/v1/couple/pair",
                Map.of("token", pairingToken), Map.class);
        assertThat(paired.getStatusCode()).isEqualTo(HttpStatus.OK);
        String tokenB = paired.getBody().get("deviceToken").toString();

        assertThat(exchangeWithBearer("/api/v1/couple/" + coupleId, tokenA, "GET").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(exchangeWithBearer("/api/v1/couple/" + coupleId, tokenB, "GET").getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // 2. Both devices lose their sessions entirely (simulated loss).
        jdbcTemplate.update("UPDATE device_session SET revoked_at = now()");
        assertThat(exchangeWithBearer("/api/v1/couple/" + coupleId, tokenA, "GET").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchangeWithBearer("/api/v1/couple/" + coupleId, tokenB, "GET").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        // 3. The local admin command re-issues a session for member A.
        String rawToken = sessionAdminCommand.executeForTest(
                SessionAdminCommand.Mode.ISSUE_REPLACEMENT, UUID.fromString(userIdA));

        // 4. The replacement authenticates and reads existing data; the old
        //    tokens stay dead. Audit rows exist and carry no token material.
        assertThat(exchangeWithBearer("/api/v1/couple/" + coupleId, rawToken, "GET").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(exchangeWithBearer("/api/v1/couple/" + coupleId, tokenA, "GET").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchangeWithBearer("/api/v1/couple/" + coupleId, tokenB, "GET").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<Map> feed = exchangeWithBearer(
                "/api/v1/sync/changes?coupleId=" + coupleId + "&after=0&limit=50", rawToken, "GET");
        assertThat(feed.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Map<String, Object>> audit = jdbcTemplate.queryForList(
                "SELECT action, target_user_id, note FROM session_admin_audit WHERE target_user_id = ?",
                UUID.fromString(userIdA));
        assertThat(audit).hasSize(1);
        assertThat(audit.get(0).get("action")).isEqualTo("ISSUE_REPLACEMENT");
        assertThat(String.valueOf(audit.get(0))).doesNotContain(rawToken);
    }

    private ResponseEntity<Map> postBootstrap(String secret, String displayName) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Bootstrap-Secret", secret);
        return restTemplate.postForEntity("/api/v1/bootstrap",
                new HttpEntity<>(Map.of("displayName", displayName), headers), Map.class);
    }

    private ResponseEntity<Map> exchangeWithBearer(String path, String token, String method) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpMethod httpMethod = "POST".equals(method) ? HttpMethod.POST : HttpMethod.GET;
        return restTemplate.exchange(path, httpMethod, new HttpEntity<>(headers), Map.class);
    }
}
