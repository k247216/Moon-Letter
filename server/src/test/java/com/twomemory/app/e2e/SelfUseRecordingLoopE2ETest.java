package com.twomemory.app.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twomemory.app.sync.SyncOperationRequest;
import com.twomemory.app.sync.SyncPayloadHasher;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 7 acceptance: the real recording loop over real HTTP against a real
 * PostgreSQL database — bootstrap, pairing, personal draft via a typed sync
 * operation, publish, partner pull with pagination, idempotent retry,
 * unauthorized token and dual perspectives. A process-level server restart is
 * exercised manually at deployment (not feasible in this harness) and noted
 * in the acceptance record.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SelfUseRecordingLoopE2ETest {

    private static final String ADMIN_URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String ADMIN_USER = "moon_letter";
    private static final String ADMIN_PASSWORD = "moon_letter_dev_only";
    private static final String TEST_DB = "moon_letter_e2e_test";
    private static final String BOOTSTRAP_SECRET = "task7-local-bootstrap-secret";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/" + TEST_DB);
        registry.add("spring.datasource.username", () -> ADMIN_USER);
        registry.add("spring.datasource.password", () -> ADMIN_PASSWORD);
        registry.add("moon-letter.bootstrap.secret", () -> BOOTSTRAP_SECRET);
    }

    static {
        try (Connection connection = DriverManager.getConnection(ADMIN_URL, ADMIN_USER, ADMIN_PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + TEST_DB + " WITH (FORCE)");
            statement.execute("CREATE DATABASE " + TEST_DB);
        } catch (Exception exception) {
            throw new IllegalStateException("could not prepare isolated e2e test database", exception);
        }
    }

    @Test
    void fullRecordingLoopForTwoDevices() throws Exception {
        // 1. Bootstrap device A (first member).
        Map<?, ?> boot = postBootstrap("小满").getBody();
        assertThat(boot).isNotNull();
        String tokenA = boot.get("token").toString();
        String coupleId = boot.get("coupleId").toString();

        // 2. Pairing creates device B's member and its first session.
        String pairingToken = post("/api/v1/couple/" + coupleId + "/pairing-token",
                Map.of(), tokenA).getBody().get("pairingToken").toString();
        Map<?, ?> pairBody = post("/api/v1/couple/pair", Map.of("token", pairingToken), null).getBody();
        assertThat(pairBody).isNotNull();
        String tokenB = pairBody.get("deviceToken").toString();

        // 3. Device A creates a personal draft via a typed sync operation and
        //    retries it; the retry replays without a second entry.
        String entryId = createPersonalEntryViaSync(coupleId, boot.get("userId").toString(), tokenA);
        assertThat(count("SELECT count(*) FROM entry")).isEqualTo(1);

        // 4. Device A publishes the draft.
        ResponseEntity<Map> published = post("/api/v1/entries/" + entryId + "/publish",
                Map.of("baseVersion", 0), tokenA);
        assertThat(published.getStatusCode())
                .as("publish response: %s", published.getBody())
                .isEqualTo(HttpStatus.OK);

        // 5. Device B pulls the change feed with pagination and sees both the
        //    creation and the publish change, then reads the published entry.
        ResponseEntity<Map> page1 = get("/api/v1/sync/changes?coupleId=" + coupleId
                + "&after=0&limit=1", tokenB);
        assertThat(page1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) page1.getBody().get("changes")).hasSize(1);
        assertThat(page1.getBody().get("hasMore")).isEqualTo(true);
        long next = ((Number) page1.getBody().get("nextSequence")).longValue();
        ResponseEntity<Map> page2 = get("/api/v1/sync/changes?coupleId=" + coupleId
                + "&after=" + next + "&limit=1", tokenB);
        assertThat((List<?>) page2.getBody().get("changes")).hasSize(1);
        assertThat(page2.getBody().get("changes").toString()).contains("PUBLISH");
        ResponseEntity<Map> readByB = get("/api/v1/entries/" + entryId, tokenB);
        assertThat(readByB.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 6. A third, unauthorized token is rejected.
        assertThat(get("/api/v1/entries/" + entryId, "forged-token").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        // 7. Dual perspectives: A opens a collaborative entry, B appends its
        //    own block; the entry then has two blocks by two authors.
        String partnerId = partnerIdFromPair(pairBody);
        Map<String, Object> collab = Map.of(
                "authorId", boot.get("userId"),
                "coupleId", coupleId,
                "mode", "COLLABORATIVE",
                "title", "一起的一天",
                "occurredAt", "2026-09-30T12:18:00Z",
                "occurredTimezone", "Asia/Shanghai",
                "blocks", List.of(Map.of(
                        "blockId", UUID.randomUUID().toString(),
                        "type", "TEXT",
                        "orderKey", 0,
                        "authorId", boot.get("userId"),
                        "payload", "{\"text\":\"A 的一天\"}",
                        "deleted", false)));
        ResponseEntity<Map> collabCreated = post("/api/v1/entries", collab, tokenA);
        assertThat(collabCreated.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String collabId = collabCreated.getBody().get("id").toString();

        Map<String, Object> appendByB = Map.of(
                "baseRevision", 0,
                "mutations", List.of(Map.of(
                        "blockId", UUID.randomUUID().toString(),
                        "type", "TEXT",
                        "orderKey", 1,
                        "authorId", partnerId,
                        "payload", "{\"text\":\"B 的一天\"}",
                        "deleted", false)));
        ResponseEntity<Map> appended = post("/api/v1/entries/" + collabId + "/changes",
                appendByB, tokenB);
        assertThat(appended.getStatusCode())
                .as("append response: %s", appended.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat((List<?>) ((Map<?, ?>) appended.getBody().get("entry")).get("blocks"))
                .hasSize(2);

        // 8. B tries to edit A's block and is rejected.
        String blockA = ((Map<?, ?>) ((List<?>) ((Map<?, ?>) appended.getBody().get("entry"))
                .get("blocks")).get(0)).get("id").toString();
        Map<String, Object> editByB = Map.of(
                "baseRevision", 1,
                "mutations", List.of(Map.of(
                        "blockId", blockA,
                        "type", "TEXT",
                        "orderKey", 0,
                        "authorId", partnerId,
                        "payload", "{\"text\":\"篡改\"}",
                        "deleted", false)));
        assertThat(post("/api/v1/entries/" + collabId + "/changes", editByB, tokenB).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    private String createPersonalEntryViaSync(String coupleId, String authorId, String tokenA)
            throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "authorId", authorId,
                "title", "周末",
                "occurredAt", "2026-09-30T12:18:00Z",
                "occurredTimezone", "Asia/Shanghai",
                "blocks", List.of(Map.of(
                        "blockId", UUID.randomUUID().toString(),
                        "type", "TEXT",
                        "orderKey", 0,
                        "text", "暴雨天窝在家"))));
        SyncOperationRequest op = new SyncOperationRequest(UUID.randomUUID(), UUID.fromString(coupleId),
                SyncPayloadHasher.hash("CREATE_PERSONAL_ENTRY", payload),
                "CREATE_PERSONAL_ENTRY", payload);
        ResponseEntity<Map> created = postJson("/api/v1/sync/operations", op, tokenA);
        assertThat(created.getStatusCode())
                .as("create response: %s", created.getBody())
                .isEqualTo(HttpStatus.OK);
        Map<?, ?> entryView = objectMapper.readValue(
                created.getBody().get("body").toString(), Map.class);
        String entryId = entryView.get("id").toString();

        ResponseEntity<Map> retry = postJson("/api/v1/sync/operations", op, tokenA);
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(retry.getBody().get("replayed")).isEqualTo(true);
        return entryId;
    }

    private String partnerIdFromPair(Map<?, ?> pairBody) {
        Map<?, ?> couple = (Map<?, ?>) pairBody.get("couple");
        List<?> members = (List<?>) couple.get("members");
        Map<?, ?> partner = (Map<?, ?>) members.get(1);
        return partner.get("userId").toString();
    }

    private int count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private ResponseEntity<Map> postBootstrap(String displayName) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Bootstrap-Secret", BOOTSTRAP_SECRET);
        return restTemplate.postForEntity("/api/v1/bootstrap",
                new HttpEntity<>(Map.of("displayName", displayName), headers), Map.class);
    }

    private ResponseEntity<Map> post(String path, Map<String, Object> body, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        return restTemplate.postForEntity(path, new HttpEntity<>(body, headers), Map.class);
    }

    private ResponseEntity<Map> postJson(String path, Object body, String bearerToken) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(bearerToken);
        return restTemplate.postForEntity(path,
                new HttpEntity<>(objectMapper.writeValueAsString(body), headers), Map.class);
    }

    private ResponseEntity<Map> get(String path, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearerToken);
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }
}
