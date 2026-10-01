package com.twomemory.app.couple;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A phone that was uninstalled, lost, or restored from a backup still has to
 * get back into the space it already belongs to. Nothing on this path may
 * create a third member or a fresh identity, because the records already
 * written are attributed to the member id a reinstall would otherwise lose.
 *
 * Two ways back in, both exercised here: the bootstrap secret reclaims the
 * founding member's own slot, and a token minted by the member who still has a
 * working phone is bound to the other member's slot (REJOIN) rather than
 * inviting a stranger into a full space.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DeviceReplacementPairingTest {

    private static final String ADMIN_URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String ADMIN_USER = "moon_letter";
    private static final String ADMIN_PASSWORD = "moon_letter_dev_only";
    private static final String TEST_DB = "moon_letter_replace_test";
    private static final String BOOTSTRAP_SECRET = "replacement-local-bootstrap-secret";

    static {
        try (Connection connection = DriverManager.getConnection(ADMIN_URL, ADMIN_USER, ADMIN_PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + TEST_DB + " WITH (FORCE)");
            statement.execute("CREATE DATABASE " + TEST_DB);
        } catch (Exception exception) {
            throw new IllegalStateException("could not prepare isolated replacement test database", exception);
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

    @Test
    void aReinstalledPhoneReclaimsItsOwnMemberSlotWithoutCreatingAThirdIdentity() {
        // 1. Owner bootstraps, partner accepts the invitation token.
        HttpHeaders bootstrapHeaders = new HttpHeaders();
        bootstrapHeaders.setContentType(MediaType.APPLICATION_JSON);
        bootstrapHeaders.set("X-Bootstrap-Secret", BOOTSTRAP_SECRET);

        Map<String, Object> ownerBoot = ok(restTemplate.postForEntity("/api/v1/bootstrap",
                new HttpEntity<>(Map.of("displayName", "小满"), bootstrapHeaders), Map.class));
        String ownerToken = (String) ownerBoot.get("token");
        String ownerId = (String) ownerBoot.get("userId");
        String coupleId = (String) ownerBoot.get("coupleId");

        Map<String, Object> invitation = requestPairingToken(coupleId, ownerToken);
        assertThat(invitation.get("pairingTokenKind"))
                .as("a space with a free slot invites, it does not rejoin")
                .isEqualTo("INVITE");
        Map<String, Object> paired = pair((String) invitation.get("pairingToken"), "阿屿");
        String partnerToken = (String) paired.get("deviceToken");
        String partnerId = (String) paired.get("userId");

        // 2. The owner's phone is wiped and reinstalled. The same secret returns
        //    the same member in the same space.
        Map<String, Object> recovered = ok(restTemplate.postForEntity("/api/v1/bootstrap",
                new HttpEntity<>(Map.of("displayName", "小满"), bootstrapHeaders), Map.class));
        assertThat(recovered.get("userId")).isEqualTo(ownerId);
        assertThat(recovered.get("coupleId")).isEqualTo(coupleId);
        String recoveredOwnerToken = (String) recovered.get("token");
        assertThat(recoveredOwnerToken).isNotEqualTo(ownerToken);

        // 3. The session the lost phone held stops working; the partner's is
        //    untouched, so recovering one device cannot log the other one out.
        assertThat(readSpaceStatus(coupleId, ownerToken)).isEqualTo(401);
        assertThat(readSpaceStatus(coupleId, recoveredOwnerToken)).isEqualTo(200);
        assertThat(readSpaceStatus(coupleId, partnerToken)).isEqualTo(200);
        assertThat(memberIds(readSpace(coupleId, recoveredOwnerToken)))
                .containsExactlyInAnyOrder(ownerId, partnerId);

        // 4. The partner's phone is lost too. The token the owner mints now is
        //    bound to the partner's own slot, because the space has no free one.
        Map<String, Object> rejoin = requestPairingToken(coupleId, recoveredOwnerToken);
        assertThat(rejoin.get("pairingTokenKind")).isEqualTo("REJOIN");
        Map<String, Object> partnerBack = pair((String) rejoin.get("pairingToken"), "阿屿");
        assertThat(partnerBack.get("userId"))
                .as("rejoining returns the same member id, so past records stay attributed")
                .isEqualTo(partnerId);
        String partnerFreshToken = (String) partnerBack.get("deviceToken");

        assertThat(readSpaceStatus(coupleId, partnerToken)).isEqualTo(401);
        assertThat(readSpaceStatus(coupleId, partnerFreshToken)).isEqualTo(200);
        assertThat(readSpaceStatus(coupleId, recoveredOwnerToken)).isEqualTo(200);
        assertThat(count("couple_member")).isEqualTo(2);
        assertThat(count("app_user")).isEqualTo(2);

        // 5. A rejoin token is single use, and minting a new one revokes the
        //    outstanding one, so an old code cannot kick the partner off again.
        String rejoinToken = (String) rejoin.get("pairingToken");
        assertThat(pairStatus(rejoinToken)).isEqualTo(409);
        assertThat(requestPairingToken(coupleId, partnerFreshToken).get("pairingTokenKind")).isEqualTo("REJOIN");
        assertThat(pairStatus(rejoinToken)).isEqualTo(409);

        // 6. Recovery does not rewrite a name a member already chose: the
        //    bootstrap call carries a name, the stored one survives.
        HttpHeaders renameHeaders = new HttpHeaders();
        renameHeaders.setContentType(MediaType.APPLICATION_JSON);
        renameHeaders.setBearerAuth(partnerFreshToken);
        ok(restTemplate.exchange("/api/v1/couple/" + coupleId + "/members/" + partnerId + "/profile",
                HttpMethod.PATCH, new HttpEntity<>(Map.of("displayName", "阿屿屿"), renameHeaders), Map.class));

        Map<String, Object> secondRecovery = ok(restTemplate.postForEntity("/api/v1/bootstrap",
                new HttpEntity<>(Map.of("displayName", "随便写的名字"), bootstrapHeaders), Map.class));
        assertThat(secondRecovery.get("userId")).isEqualTo(ownerId);
        assertThat(memberNames(readSpace(coupleId, (String) secondRecovery.get("token"))))
                .containsExactlyInAnyOrder("小满", "阿屿屿");
    }

    private Map<String, Object> requestPairingToken(String coupleId, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(bearerToken);
        return ok(restTemplate.postForEntity("/api/v1/couple/" + coupleId + "/pairing-token",
                new HttpEntity<>(Map.of(), headers), Map.class));
    }

    private Map<String, Object> pair(String token, String displayName) {
        return ok(pairResponse(token, displayName));
    }

    private int pairStatus(String token) {
        return pairResponse(token, "路人").getStatusCode().value();
    }

    private ResponseEntity<Map> pairResponse(String token, String displayName) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity("/api/v1/couple/pair",
                new HttpEntity<>(Map.of("token", token, "displayName", displayName), headers), Map.class);
    }

    private Map<String, Object> readSpace(String coupleId, String bearerToken) {
        ResponseEntity<Map> response = spaceResponse(coupleId, bearerToken);
        return response.getBody();
    }

    private int readSpaceStatus(String coupleId, String bearerToken) {
        return spaceResponse(coupleId, bearerToken).getStatusCode().value();
    }

    private ResponseEntity<Map> spaceResponse(String coupleId, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearerToken);
        return restTemplate.exchange("/api/v1/couple/" + coupleId,
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }

    private Map<String, Object> ok(ResponseEntity<Map> response) {
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("expected success, got %s with body %s", response.getStatusCode(), response.getBody())
                .isTrue();
        return response.getBody();
    }

    private int count(String table) {
        Integer value = jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
        return value == null ? 0 : value;
    }

    private static List<String> memberNames(Map<?, ?> couple) {
        return ((List<?>) couple.get("members")).stream()
                .map(member -> (String) ((Map<?, ?>) ((Map<?, ?>) member).get("profile")).get("displayName"))
                .toList();
    }

    private static List<String> memberIds(Map<?, ?> couple) {
        return ((List<?>) couple.get("members")).stream()
                .map(member -> (String) ((Map<?, ?>) member).get("userId"))
                .toList();
    }
}
