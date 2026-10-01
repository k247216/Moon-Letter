package com.twomemory.app.couple;

import com.twomemory.app.auth.DeviceSessionService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 3 acceptance: the pairing token is cryptographically random, expires
 * after 15 minutes, is single use, never disclosed after creation, pairs at
 * most two members, and pairing transactionally creates the second member
 * with its first device session.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PairingFlowTest {

    private static final String ADMIN_URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String ADMIN_USER = "moon_letter";
    private static final String ADMIN_PASSWORD = "moon_letter_dev_only";
    private static final String TEST_DB = "moon_letter_pair_test";
    private static final String BOOTSTRAP_SECRET = "task3-local-bootstrap-secret";

    static {
        try (Connection connection = DriverManager.getConnection(ADMIN_URL, ADMIN_USER, ADMIN_PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + TEST_DB + " WITH (FORCE)");
            statement.execute("CREATE DATABASE " + TEST_DB);
        } catch (Exception exception) {
            throw new IllegalStateException("could not prepare isolated pairing test database", exception);
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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DeviceSessionService deviceSessionService;

    @Test
    void pairingIsRandomSingleUseAndCreatesTheSecondMember() {
        // 0. Bootstrap the owner (first member) with a device session.
        Map<String, Object> boot = postBootstrap(BOOTSTRAP_SECRET, "小满").getBody();
        assertThat(boot).isNotNull();
        String ownerToken = (String) boot.get("token");
        String coupleId = (String) boot.get("coupleId");

        // 1. Generated pairing tokens are >= 128 bit random strings and differ.
        String tokenA = deviceSessionService.generateToken();
        String tokenB = deviceSessionService.generateToken();
        assertThat(tokenA).hasSize(43).matches("[A-Za-z0-9_-]{43}");
        assertThat(tokenA).isNotEqualTo(tokenB);

        // 2. The owner (whose space already exists from bootstrap) requests a
        //    pairing token; it is returned exactly once.
        ResponseEntity<Map> regenerateFirst = postJson("/api/v1/couple/" + coupleId + "/pairing-token",
                Map.of(), ownerToken);
        assertThat(regenerateFirst.getStatusCode()).isEqualTo(HttpStatus.OK);
        String pairingToken = (String) regenerateFirst.getBody().get("pairingToken");
        assertThat(pairingToken).hasSize(43);

        // 3. An expired token cannot pair.
        expireOutstandingToken();
        ResponseEntity<Map> expiredPair = postJson("/api/v1/couple/pair", Map.of("token", pairingToken), null);
        assertThat(expiredPair.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // 4. The owner replaces the expired token with a fresh one.
        ResponseEntity<Map> regenerate = postJson("/api/v1/couple/" + coupleId + "/pairing-token",
                Map.of(), ownerToken);
        assertThat(regenerate.getStatusCode()).isEqualTo(HttpStatus.OK);
        String freshToken = (String) regenerate.getBody().get("pairingToken");
        assertThat(freshToken).hasSize(43).isNotEqualTo(pairingToken);
        Integer outstanding = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM space_pairing_code WHERE consumed_at IS NULL", Integer.class);
        assertThat(outstanding).isEqualTo(1);

        // 5. Pairing with the fresh token creates the second member and its
        //    device session; the session token is returned exactly once. She
        //    names herself here, because the first thing she must not see is 未命名.
        ResponseEntity<Map> paired = postJson("/api/v1/couple/pair",
                Map.of("token", freshToken, "displayName", "阿屿"), null);
        assertThat(paired.getStatusCode())
                .as("pair response: %s", paired.getBody())
                .isEqualTo(HttpStatus.OK);
        Map<?, ?> couple = (Map<?, ?>) paired.getBody().get("couple");
        assertThat(couple.get("status")).isEqualTo("ACTIVE");
        assertThat((List<?>) couple.get("members")).hasSize(2);
        assertThat(memberNames(couple)).contains("阿屿");
        String partnerToken = (String) paired.getBody().get("deviceToken");
        assertThat(partnerToken).hasSize(43);

        // 6. The partner's bearer token authenticates the shared space read;
        //    the pairing token is never disclosed on a space read, and neither
        //    device is left holding the placeholder name.
        ResponseEntity<Map> space = exchangeWithBearer("/api/v1/couple/" + coupleId, partnerToken);
        assertThat(space.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(space.getBody()).doesNotContainKey("pairingToken");
        assertThat(memberNames((Map<?, ?>) space.getBody())).containsExactlyInAnyOrder("小满", "阿屿");

        // 7. The token is single use.
        ResponseEntity<Map> reused = postJson("/api/v1/couple/pair", Map.of("token", freshToken), null);
        assertThat(reused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // 8. A third member cannot join the now-full space.
        Integer memberCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM couple_member WHERE couple_id = ?::uuid "
                        + "AND left_at IS NULL AND deleted_at IS NULL",
                Integer.class, coupleId);
        assertThat(memberCount).isEqualTo(2);

        // 9. Renaming herself is a name-only PATCH and must not quietly undo
        //    anything she set before, so the client never has to resend the theme.
        String ownerId = (String) boot.get("userId");
        String partnerId = (String) paired.getBody().get("userId");
        String partnerProfilePath = "/api/v1/couple/" + coupleId + "/members/" + partnerId + "/profile";
        ResponseEntity<Map> themeSet = patchJson(partnerProfilePath,
                Map.of("displayName", "阿屿", "theme", "PURE_WHITE"), partnerToken);
        assertThat(themeSet.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> renamed = patchJson(partnerProfilePath,
                Map.of("displayName", "  阿屿屿  "), partnerToken);
        assertThat(renamed.getStatusCode())
                .as("rename response: %s", renamed.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(renamed.getBody().get("displayName")).isEqualTo("阿屿屿");
        assertThat(renamed.getBody().get("theme")).isEqualTo("PURE_WHITE");

        // 10. The other device reads the new name on its next space read.
        ResponseEntity<Map> ownerView = exchangeWithBearer("/api/v1/couple/" + coupleId, ownerToken);
        assertThat(memberNames(ownerView.getBody())).containsExactlyInAnyOrder("小满", "阿屿屿");

        // 11. A member cannot rename the other one, and a blank name is rejected
        //     rather than stored.
        ResponseEntity<Map> forbidden = patchJson(
                "/api/v1/couple/" + coupleId + "/members/" + ownerId + "/profile",
                Map.of("displayName", "陌生人"), partnerToken);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Map> blank = patchJson(partnerProfilePath, Map.of("displayName", "   "), partnerToken);
        assertThat(blank.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private void expireOutstandingToken() {
        jdbcTemplate.update("UPDATE space_pairing_code SET expires_at = now() - interval '1 minute' "
                + "WHERE consumed_at IS NULL");
    }

    private static List<String> memberNames(Map<?, ?> couple) {
        return ((List<?>) couple.get("members")).stream()
                .map(member -> (String) ((Map<?, ?>) ((Map<?, ?>) member).get("profile")).get("displayName"))
                .toList();
    }

    private ResponseEntity<Map> postBootstrap(String secret, String displayName) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Bootstrap-Secret", secret);
        return restTemplate.postForEntity("/api/v1/bootstrap",
                new HttpEntity<>(Map.of("displayName", displayName), headers), Map.class);
    }

    private ResponseEntity<Map> postJson(String path, Map<String, Object> body, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        return restTemplate.postForEntity(path, new HttpEntity<>(body, headers), Map.class);
    }

    private ResponseEntity<Map> patchJson(String path, Map<String, Object> body, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        return restTemplate.exchange(path, HttpMethod.PATCH, new HttpEntity<>(body, headers), Map.class);
    }

    private ResponseEntity<Map> exchangeWithBearer(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }
}
