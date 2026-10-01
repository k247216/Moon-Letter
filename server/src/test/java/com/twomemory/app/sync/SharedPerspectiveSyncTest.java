package com.twomemory.app.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 12 functional chain over real HTTP + real PostgreSQL: a shared entry
 * is created through the sync path, the partner appends their own
 * perspective block, both sides see the same entry through the change feed,
 * and comments flow to the partner as COMMENT changes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SharedPerspectiveSyncTest {

    private static final String ADMIN_URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String ADMIN_USER = "moon_letter";
    private static final String ADMIN_PASSWORD = "moon_letter_dev_only";
    private static final String TEST_DB = "moon_letter_shared_test";
    private static final String TOKEN_A = "shared-test-token-a";
    private static final String TOKEN_B = "shared-test-token-b";

    static UUID userA;
    static UUID userB;
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
        registry.add("moon-letter.bootstrap.secret", () -> "shared-test-bootstrap-secret");
    }

    static {
        try (Connection connection = DriverManager.getConnection(ADMIN_URL, ADMIN_USER, ADMIN_PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + TEST_DB + " WITH (FORCE)");
            statement.execute("CREATE DATABASE " + TEST_DB);
        } catch (Exception exception) {
            throw new IllegalStateException("could not prepare isolated shared test database", exception);
        }
    }

    @BeforeEach
    void seed() {
        Integer users = jdbcTemplate.queryForObject("SELECT count(*) FROM app_user", Integer.class);
        if (users != null && users > 0) {
            return;
        }
        userA = UUID.randomUUID();
        userB = UUID.randomUUID();
        coupleId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO app_user (id, status) VALUES (?, 'ACTIVE')", userA);
        jdbcTemplate.update("INSERT INTO app_user (id, status) VALUES (?, 'ACTIVE')", userB);
        jdbcTemplate.update("INSERT INTO user_profile (user_id, display_name, theme) "
                + "VALUES (?, '小满', 'WARM_BEIGE')", userA);
        jdbcTemplate.update("INSERT INTO user_profile (user_id, display_name, theme) "
                + "VALUES (?, '阿屿', 'WARM_BEIGE')", userB);
        jdbcTemplate.update("INSERT INTO couple_space (id, status) VALUES (?, 'ACTIVE')", coupleId);
        jdbcTemplate.update("INSERT INTO couple_member (couple_id, user_id) VALUES (?, ?)", coupleId, userA);
        jdbcTemplate.update("INSERT INTO couple_member (couple_id, user_id) VALUES (?, ?)", coupleId, userB);
        insertSession(userA, TOKEN_A);
        insertSession(userB, TOKEN_B);
    }

    @Test
    void sharedEntryAppendAndCommentsReachPartnerThroughChangeFeed() throws Exception {
        // 1. A creates a COLLABORATIVE entry through the sync path.
        UUID entryId = UUID.randomUUID();
        String createPayload = objectMapper.writeValueAsString(Map.of(
                "entryId", entryId.toString(),
                "authorId", userA.toString(),
                "title", "周末去了海边",
                "occurredAt", "2026-10-01T09:00:00Z",
                "occurredTimezone", "Asia/Shanghai",
                "blocks", java.util.List.of(Map.of(
                        "blockId", UUID.randomUUID().toString(),
                        "type", "TEXT",
                        "orderKey", 0,
                        "text", "A 的部分：海风很大，但很舒服。"))));
        ResponseEntity<Map> created = postOperation("CREATE_SHARED_ENTRY", createPayload, TOKEN_A);
        assertThat(created.getStatusCode())
                .as("create body: %s", created.getBody())
                .isEqualTo(HttpStatus.OK);

        // 2. B appends their own perspective block to the same entry.
        UUID blockB = UUID.randomUUID();
        String appendPayload = objectMapper.writeValueAsString(Map.of(
                "entryId", entryId.toString(),
                "block", Map.of(
                        "blockId", blockB.toString(),
                        "type", "TEXT",
                        "orderKey", 1,
                        "text", "B 的部分：下次我们也一起来。")));
        ResponseEntity<Map> appended = postOperation("APPEND_BLOCK", appendPayload, TOKEN_B);
        assertThat(appended.getStatusCode())
                .as("append body: %s", appended.getBody())
                .isEqualTo(HttpStatus.OK);

        // 3. B cannot edit A's block (author ownership): same blockId as A's
        //    first block, but B is the authenticated author -> rejected.
        String forgedPayload = objectMapper.writeValueAsString(Map.of(
                "entryId", entryId.toString(),
                "block", Map.of(
                        "blockId", firstBlockIdOf(entryId).toString(),
                        "type", "TEXT",
                        "orderKey", 0,
                        "text", "B 偷改 A 的内容")));
        ResponseEntity<Map> denied = postOperation("APPEND_BLOCK", forgedPayload, TOKEN_B);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 4. A publishes the shared entry. B's append above moved the entry
        //    version, so A publishes against the newest version it has seen.
        long versionAfterAppend = objectMapper.readTree(extractBody(appended))
                .path("rowVersion").asLong(-1);
        assertThat(versionAfterAppend)
                .as("the append response must carry the version a client publishes against")
                .isNotEqualTo(-1L);
        String publishPayload = objectMapper.writeValueAsString(Map.of(
                "entryId", entryId.toString(),
                "baseVersion", versionAfterAppend));
        assertThat(postOperation("PUBLISH_ENTRY", publishPayload, TOKEN_A).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // 5. B adds a comment; the same operation id replays without a second row.
        UUID commentId = UUID.randomUUID();
        String commentPayload = objectMapper.writeValueAsString(Map.of(
                "entryId", entryId.toString(),
                "commentId", commentId.toString(),
                "body", "这条真好看"));
        ResponseEntity<Map> comment = postOperation(UUID.randomUUID(), "ADD_COMMENT", commentPayload, TOKEN_B);
        assertThat(comment.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> commentRetry = postOperation(UUID.randomUUID(), "ADD_COMMENT", commentPayload, TOKEN_B);
        assertThat(commentRetry.getStatusCode()).isEqualTo(HttpStatus.OK);
        int commentRows = countComments(entryId);
        assertThat(commentRows).isEqualTo(1);

        // One comment is one feed row: the mutation owns its change row,
        // and the sync controller must not append a second copy of it.
        int commentFeedRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sync_change WHERE couple_id = ? AND entity_type = 'COMMENT'"
                        + " AND entity_id = ?",
                Integer.class, coupleId, entryId);
        assertThat(commentFeedRows).isEqualTo(1);

        // 6. A pulls the change feed: the rows about this entry, in order. The feed is
        //    shared with other tests in this database, so positions are not meaningful.
        java.util.List<JsonNode> own = changesAbout(TOKEN_A, entryId);
        assertThat(own).hasSize(4);
        assertThat(own.get(0).path("operation").asText()).isEqualTo("CREATE");
        assertThat(own.get(0).path("entityType").asText()).isEqualTo("ENTRY");
        assertThat(own.get(1).path("operation").asText()).isEqualTo("UPDATE");
        assertThat(own.get(2).path("operation").asText()).isEqualTo("PUBLISH");
        assertThat(own.get(3).path("entityType").asText()).isEqualTo("COMMENT");
        assertThat(own.get(3).path("operation").asText()).isEqualTo("ADD");

        // 7. Both members read the same entry: two blocks, B's block intact.
        JsonNode viewA = objectMapper.valueToTree(getEntry(entryId, TOKEN_A).getBody());
        JsonNode viewB = objectMapper.valueToTree(getEntry(entryId, TOKEN_B).getBody());
        assertThat(viewA.path("blocks").size()).isEqualTo(2);
        assertThat(viewB.path("blocks").size()).isEqualTo(2);
        assertThat(viewB.path("mode").asText()).isEqualTo("COLLABORATIVE");
        assertThat(viewB.path("state").asText()).isEqualTo("PUBLISHED");

        // 8. Duplicate APPEND_BLOCK with the same operation id replays once.
        UUID appendOperationId = UUID.randomUUID();
        String replayPayload = objectMapper.writeValueAsString(Map.of(
                "entryId", entryId.toString(),
                "block", Map.of(
                        "blockId", UUID.randomUUID().toString(),
                        "type", "TEXT",
                        "orderKey", 2,
                        "text", "B 补充：照片洗出来贴在后面。")));
        ResponseEntity<Map> firstAppend = postOperation(appendOperationId, "APPEND_BLOCK", replayPayload, TOKEN_B);
        ResponseEntity<Map> secondAppend = postOperation(appendOperationId, "APPEND_BLOCK", replayPayload, TOKEN_B);
        assertThat(firstAppend.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondAppend.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondAppend.getBody().get("replayed")).isEqualTo(true);
        JsonNode viewAfterReplay = objectMapper.valueToTree(getEntry(entryId, TOKEN_A).getBody());
        assertThat(viewAfterReplay.path("blocks").size()).isEqualTo(3);
    }

    @Test
    void personalDraftStaysOutOfTheFeedAndPublishCarriesTheWholeEntry() throws Exception {
        UUID entryId = UUID.randomUUID();
        String createPayload = objectMapper.writeValueAsString(Map.of(
                "entryId", entryId.toString(),
                "authorId", userA.toString(),
                "title", "只有我自己看得见的草稿",
                "occurredAt", "2026-10-01T12:00:00Z",
                "occurredTimezone", "Asia/Shanghai",
                "blocks", java.util.List.of(Map.of(
                        "blockId", UUID.randomUUID().toString(),
                        "type", "TEXT",
                        "orderKey", 0,
                        "text", "私密正文第一段。"))));
        ResponseEntity<Map> created = postOperation("CREATE_PERSONAL_ENTRY", createPayload, TOKEN_A);
        assertThat(created.getStatusCode()).as("create: %s", created.getBody()).isEqualTo(HttpStatus.OK);

        // A keeps writing in the same draft. Every private edit stays private:
        // the couple feed is what delivers content to the partner device, so a
        // personal draft must not append a row to it at all.
        String appendPayload = objectMapper.writeValueAsString(Map.of(
                "entryId", entryId.toString(),
                "block", Map.of(
                        "blockId", UUID.randomUUID().toString(),
                        "type", "TEXT",
                        "orderKey", 1,
                        "text", "私密正文第二段。")));
        ResponseEntity<Map> appended = postOperation("APPEND_BLOCK", appendPayload, TOKEN_A);
        assertThat(appended.getStatusCode()).as("append: %s", appended.getBody()).isEqualTo(HttpStatus.OK);
        long versionAfterAppend = objectMapper.readTree(extractBody(appended)).path("rowVersion").asLong(-1);
        assertThat(versionAfterAppend).isNotEqualTo(-1L);

        assertThat(entryFeedRows(entryId)).isZero();
        assertThat(changesAbout(TOKEN_B, entryId)).isEmpty();
        assertThat(getEntry(entryId, TOKEN_B).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // An unpublished record has no audience: not even a comment.
        String commentPayload = objectMapper.writeValueAsString(Map.of(
                "entryId", entryId.toString(),
                "commentId", UUID.randomUUID().toString(),
                "body", "抢先评论"));
        assertThat(postOperation("ADD_COMMENT", commentPayload, TOKEN_B).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        String publishPayload = objectMapper.writeValueAsString(Map.of(
                "entryId", entryId.toString(),
                "baseVersion", versionAfterAppend));
        assertThat(postOperation("PUBLISH_ENTRY", publishPayload, TOKEN_A).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT state FROM entry WHERE id = ?", String.class, entryId)).isEqualTo("PUBLISHED");

        // Publishing a private draft is the one and only row the partner
        // receives, so it must carry the whole entry, not just an id.
        assertThat(entryFeedRows(entryId))
                .as("one publish is one change row")
                .isEqualTo(1);
        JsonNode published = objectMapper.readTree(feedPayloadOf(entryId));
        assertThat(published.path("id").asText()).isEqualTo(entryId.toString());
        assertThat(published.path("mode").asText()).isEqualTo("PERSONAL");
        assertThat(published.path("state").asText()).isEqualTo("PUBLISHED");
        assertThat(published.path("title").asText()).isEqualTo("只有我自己看得见的草稿");
        assertThat(published.path("occurredAtEpochMillis").asLong()).isPositive();
        assertThat(published.path("blocks").size()).isEqualTo(2);

        // The partner rebuilds the record from that single row.
        assertThat(changesAbout(TOKEN_B, entryId)).hasSize(1);
        ResponseEntity<Map> readByB = getEntry(entryId, TOKEN_B);
        assertThat(readByB.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.valueToTree(readByB.getBody()).path("blocks").size()).isEqualTo(2);

        // And may now comment on the published record.
        assertThat(postOperation("ADD_COMMENT", commentPayload, TOKEN_B).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    private int entryFeedRows(UUID entryId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sync_change WHERE couple_id = ? AND entity_type = 'ENTRY'"
                        + " AND entity_id = ?",
                Integer.class, coupleId, entryId);
        return count == null ? 0 : count;
    }

    private String feedPayloadOf(UUID entryId) {
        return jdbcTemplate.queryForObject(
                "SELECT payload::text FROM sync_change WHERE couple_id = ? AND entity_type = 'ENTRY'"
                        + " AND entity_id = ?",
                String.class, coupleId, entryId);
    }

    private java.util.List<JsonNode> changesAbout(String token, UUID entryId) throws Exception {
        java.util.List<JsonNode> matching = new java.util.ArrayList<>();
        for (JsonNode change : objectMapper.valueToTree(pullChanges(token).getBody()).path("changes")) {
            if (entryId.toString().equals(change.path("entityId").asText())) {
                matching.add(change);
            }
        }
        return matching;
    }

    private UUID firstBlockIdOf(UUID entryId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM entry_block WHERE entry_id = ? ORDER BY order_key LIMIT 1",
                UUID.class, entryId);
    }

    private int countComments(UUID entryId) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM comment WHERE entry_id = ?", Integer.class, entryId);
        return value == null ? 0 : value;
    }

    private String extractBody(ResponseEntity<Map> response) {
        Object body = response.getBody() == null ? null : response.getBody().get("body");
        return body == null ? String.valueOf(response.getBody()) : body.toString();
    }

    private ResponseEntity<Map> getEntry(UUID entryId, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange("/api/v1/entries/" + entryId, HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
    }

    private ResponseEntity<Map> pullChanges(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(
                "/api/v1/sync/changes?coupleId=" + coupleId + "&after=0&limit=100",
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }

    private ResponseEntity<Map> postOperation(String type, String payload, String token) {
        return postOperation(UUID.randomUUID(), type, payload, token);
    }

    private ResponseEntity<Map> postOperation(UUID operationId, String type, String payload, String token) {
        SyncOperationRequest request = new SyncOperationRequest(operationId, coupleId,
                SyncPayloadHasher.hash(type, payload), type, payload);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return restTemplate.postForEntity("/api/v1/sync/operations",
                new HttpEntity<>(request, headers), Map.class);
    }

    private void insertSession(UUID userId, String rawToken) {
        jdbcTemplate.update("""
                INSERT INTO device_session (id, user_id, couple_id, token_hash, last_used_at)
                VALUES (?, ?, ?, ?, now())
                """, UUID.randomUUID(), userId, coupleId, sha256(rawToken));
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
