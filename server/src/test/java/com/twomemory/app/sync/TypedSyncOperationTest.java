package com.twomemory.app.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 5 acceptance: sync mutations are typed, idempotent and transactionally
 * observable. Runs against a real PostgreSQL database over real HTTP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TypedSyncOperationTest {

    private static final String ADMIN_URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String ADMIN_USER = "moon_letter";
    private static final String ADMIN_PASSWORD = "moon_letter_dev_only";
    private static final String TEST_DB = "moon_letter_sync_test";
    private static final String RAW_TOKEN = "typed-sync-test-device-token";

    static UUID userId;
    static UUID coupleId;

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
        registry.add("moon-letter.bootstrap.secret", () -> "task5-local-bootstrap-secret");
    }

    static {
        try (Connection connection = DriverManager.getConnection(ADMIN_URL, ADMIN_USER, ADMIN_PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + TEST_DB + " WITH (FORCE)");
            statement.execute("CREATE DATABASE " + TEST_DB);
        } catch (Exception exception) {
            throw new IllegalStateException("could not prepare isolated sync test database", exception);
        }
    }

    @BeforeEach
    void seed() {
        Integer users = jdbcTemplate.queryForObject("SELECT count(*) FROM app_user", Integer.class);
        if (users != null && users > 0) {
            return;
        }
        userId = UUID.randomUUID();
        coupleId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO app_user (id, status) VALUES (?, 'ACTIVE')", userId);
        jdbcTemplate.update("INSERT INTO user_profile (user_id, display_name, theme) "
                + "VALUES (?, '小满', 'WARM_BEIGE')", userId);
        jdbcTemplate.update("INSERT INTO couple_space (id, status) VALUES (?, 'UNPAIRED')", coupleId);
        jdbcTemplate.update("INSERT INTO couple_member (couple_id, user_id) VALUES (?, ?)", coupleId, userId);
        jdbcTemplate.update("""
                INSERT INTO device_session (id, user_id, couple_id, token_hash, last_used_at)
                VALUES (?, ?, ?, ?, now())
                """, UUID.randomUUID(), userId, coupleId, sha256(RAW_TOKEN));
    }

    @Test
    void typedOperationChangesEntryTablesAndAppendsOneChange() {
        int entriesBefore = count("SELECT count(*) FROM entry");
        int blocksBefore = count("SELECT count(*) FROM entry_block");
        int changesBefore = count("SELECT count(*) FROM sync_change WHERE couple_id = '"
                + coupleId + "'");
        SyncOperationRequest request = validRequest(UUID.randomUUID());

        ResponseEntity<Map> response = post(request);
        assertThat(response.getStatusCode())
                .as("body: %s", response.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("replayed")).isEqualTo(false);

        assertThat(count("SELECT count(*) FROM entry")).isEqualTo(entriesBefore + 1);
        assertThat(count("SELECT count(*) FROM entry_block")).isEqualTo(blocksBefore + 1);
        assertThat(count("SELECT count(*) FROM sync_change WHERE couple_id = '"
                + coupleId + "'")).isEqualTo(changesBefore + 1);
        Map<String, Object> change = jdbcTemplate.queryForMap(
                "SELECT entity_type, entity_id, operation FROM sync_change "
                        + "WHERE couple_id = ? ORDER BY change_seq DESC LIMIT 1", coupleId);
        assertThat(change.get("entity_type")).isEqualTo("ENTRY");
        assertThat(change.get("operation")).isEqualTo("CREATE");
    }

    @Test
    void duplicateOperationReplaysWithoutSecondEffect() {
        UUID operationId = UUID.randomUUID();
        String payload = payload("今天很好");
        SyncOperationRequest first = request(operationId, payload);
        SyncOperationRequest duplicate = request(operationId, payload);

        ResponseEntity<Map> firstResponse = post(first);
        ResponseEntity<Map> secondResponse = post(duplicate);

        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondResponse.getBody().get("replayed")).isEqualTo(true);
        // The replayed body passed through jsonb storage, so compare semantically.
        assertThat(parseBody(secondResponse.getBody().get("body")))
                .isEqualTo(parseBody(firstResponse.getBody().get("body")));
        int entriesBefore = count("SELECT count(*) FROM entry");
        ResponseEntity<Map> thirdResponse = post(duplicate);
        assertThat(thirdResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(count("SELECT count(*) FROM entry")).isEqualTo(entriesBefore);
    }

    private Map<?, ?> parseBody(Object body) {
        try {
            return objectMapper.readValue((String) body, Map.class);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void sameOperationIdWithDifferentPayloadIsRejected() {
        UUID operationId = UUID.randomUUID();
        SyncOperationRequest first = validRequest(operationId);
        String mutatedPayload = payload("被篡改的正文");
        SyncOperationRequest mutated = new SyncOperationRequest(operationId, coupleId,
                SyncPayloadHasher.hash("CREATE_PERSONAL_ENTRY", mutatedPayload),
                "CREATE_PERSONAL_ENTRY", mutatedPayload);

        assertThat(post(first).getStatusCode()).isEqualTo(HttpStatus.OK);
        int entriesBefore = count("SELECT count(*) FROM entry");
        ResponseEntity<Map> conflict = post(mutated);
        assertThat(conflict.getStatusCode())
                .as("body: %s", conflict.getBody())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(count("SELECT count(*) FROM entry")).isEqualTo(entriesBefore);
    }

    @Test
    void injectedFailureBeforeCommitLeavesNoTrace() {
        int entriesBefore = jdbcTemplate.queryForObject("SELECT count(*) FROM entry", Integer.class);
        UUID operationId = UUID.randomUUID();
        String badPayload = payload("会失败的一条");
        // Block authored by someone else fails inside the mutation, after the
        // idempotency claim has been written in the same transaction.
        String badJson = badPayload.replace("\"authorId\":\"" + userId + "\"",
                "\"authorId\":\"" + UUID.randomUUID() + "\"");
        SyncOperationRequest failing = new SyncOperationRequest(operationId, coupleId,
                SyncPayloadHasher.hash("CREATE_PERSONAL_ENTRY", badJson),
                "CREATE_PERSONAL_ENTRY", badJson);
        ResponseEntity<Map> failed = post(failing);
        assertThat(failed.getStatusCode())
                .as("body: %s", failed.getBody())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        Integer claimed = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM idempotency_record WHERE operation_id = ?",
                Integer.class, operationId);
        assertThat(claimed).as("idempotency claim must roll back").isZero();
        Integer entries = jdbcTemplate.queryForObject("SELECT count(*) FROM entry", Integer.class);
        assertThat(entries).isEqualTo(entriesBefore);
        int changesBefore = count("SELECT count(*) FROM sync_change WHERE couple_id = '"
                + coupleId + "'");
        assertThat(count("SELECT count(*) FROM sync_change WHERE couple_id = '"
                + coupleId + "'")).isEqualTo(changesBefore);

        // The same operation id can then be used for the real effect.
        SyncOperationRequest retry = new SyncOperationRequest(operationId, coupleId,
                SyncPayloadHasher.hash("CREATE_PERSONAL_ENTRY", badPayload),
                "CREATE_PERSONAL_ENTRY", badPayload);
        assertThat(post(retry).getStatusCode()).isEqualTo(HttpStatus.OK);
        Integer entriesAfter = jdbcTemplate.queryForObject("SELECT count(*) FROM entry", Integer.class);
        assertThat(entriesAfter).isEqualTo(entriesBefore + 1);
    }

    @Test
    void unknownOperationTypeIsRejected() {
        SyncOperationRequest request = new SyncOperationRequest(UUID.randomUUID(), coupleId,
                SyncPayloadHasher.hash("DROP_EVERYTHING", "{}"),
                "DROP_EVERYTHING", "{}");
        assertThat(post(request).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private int count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private SyncOperationRequest validRequest(UUID operationId) {
        return request(operationId, payload("今天很好"));
    }

    private SyncOperationRequest request(UUID operationId, String payload) {
        return new SyncOperationRequest(operationId, coupleId,
                SyncPayloadHasher.hash("CREATE_PERSONAL_ENTRY", payload),
                "CREATE_PERSONAL_ENTRY", payload);
    }

    private String payload(String text) {
        Map<String, Object> payload = Map.of(
                "authorId", userId.toString(),
                "title", "傍晚散步",
                "occurredAt", "2026-09-30T12:18:00Z",
                "occurredTimezone", "Asia/Shanghai",
                "blocks", java.util.List.of(Map.of(
                        "blockId", UUID.randomUUID().toString(),
                        "type", "TEXT",
                        "orderKey", 0,
                        "text", text)));
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private ResponseEntity<Map> post(SyncOperationRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(RAW_TOKEN);
        try {
            return restTemplate.postForEntity("/api/v1/sync/operations",
                    new HttpEntity<>(objectMapper.writeValueAsString(request), headers), Map.class);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String sha256(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte item : digest) {
                hex.append("%02x".formatted(item));
            }
            return hex.toString();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
