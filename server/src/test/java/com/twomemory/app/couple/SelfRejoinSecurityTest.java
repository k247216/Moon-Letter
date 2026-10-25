package com.twomemory.app.couple;

import com.twomemory.app.TestDatabaseConfig;
import com.twomemory.app.entry.BlockMutation;
import com.twomemory.app.entry.BlockType;
import com.twomemory.app.entry.CreateEntryCommand;
import com.twomemory.app.entry.EntryMode;
import com.twomemory.app.entry.EntryService;
import com.twomemory.app.entry.EntryVisibility;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Real HTTP authentication and Flyway behavior, confined to a dedicated harness database. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SelfRejoinSecurityTest {
    private static final String TEST_DB = "moon_letter_self_rejoin_test";
    // Synthetic fixture only; never a deployment secret.
    private static final String BOOTSTRAP_SECRET = "self-rejoin-isolated-fixture";

    static {
        // Same isolated database setup used by DeviceReplacementPairingTest.
        try (Connection connection = DriverManager.getConnection(TestDatabaseConfig.adminUrl(),
                TestDatabaseConfig.user(), TestDatabaseConfig.password());
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + TEST_DB + " WITH (FORCE)");
            statement.execute("CREATE DATABASE " + TEST_DB);
        } catch (Exception failure) {
            throw new IllegalStateException("could not prepare isolated self-rejoin test database", failure);
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/" + TEST_DB);
        registry.add("spring.datasource.username", TestDatabaseConfig::user);
        registry.add("spring.datasource.password", TestDatabaseConfig::password);
        registry.add("moon-letter.bootstrap.secret", () -> BOOTSTRAP_SECRET);
    }

    @Autowired private TestRestTemplate http;
    @Autowired private EntryService entries;
    @Autowired private JdbcTemplate db;
    @Autowired private DataSource dataSource;

    @Test
    void selfRejoinRotatesOnlyRequesterAndKeepsPartnersPrivateEntriesIsolated() {
        HttpHeaders bootstrapHeaders = new HttpHeaders();
        bootstrapHeaders.setContentType(MediaType.APPLICATION_JSON);
        bootstrapHeaders.set("X-Bootstrap-Secret", BOOTSTRAP_SECRET);
        Map<String, Object> owner = success(http.postForEntity("/api/v1/bootstrap",
                new HttpEntity<>(Map.of("displayName", "member-A"), bootstrapHeaders), Map.class));
        String ownerId = (String) owner.get("userId");
        String ownerToken = (String) owner.get("token");
        String space = (String) owner.get("coupleId");

        Map<String, Object> invite = issue(space, ownerToken);
        assertThat(invite.get("pairingTokenKind")).isEqualTo("INVITE");
        Map<String, Object> partner = success(pair((String) invite.get("pairingToken")));
        String partnerId = (String) partner.get("userId");
        String partnerToken = (String) partner.get("deviceToken");
        UUID ownerPrivate = publishPrivate(ownerId, space, "only-A-fixture");
        UUID partnerPrivate = publishPrivate(partnerId, space, "only-B-fixture");
        assertPrivateAccess(ownerPrivate, ownerToken, partnerToken, "only-A-fixture");
        assertPrivateAccess(partnerPrivate, partnerToken, ownerToken, "only-B-fixture");

        // Regression: B must receive B, never A, even when consuming their own
        // token through the public pairing endpoint on another device.
        Map<String, Object> partnerRejoin = issue(space, partnerToken);
        assertThat(partnerRejoin.get("pairingTokenKind")).isEqualTo("REJOIN");
        String partnerCode = (String) partnerRejoin.get("pairingToken");
        Map<String, Object> partnerBack = success(pair(partnerCode));
        assertThat(partnerBack.get("userId"))
                .as("B's recovery credential may restore only B's identity")
                .isEqualTo(partnerId);
        String partnerFresh = (String) partnerBack.get("deviceToken");
        assertThat(partnerFresh.equals(partnerToken)).as("B's session must rotate").isFalse();
        assertThat(spaceStatus(space, partnerToken)).isEqualTo(401);
        assertThat(spaceStatus(space, partnerFresh)).isEqualTo(200);
        assertThat(spaceStatus(space, ownerToken)).as("A's original session survives B's replacement").isEqualTo(200);
        assertPrivateAccess(ownerPrivate, ownerToken, partnerFresh, "only-A-fixture");
        assertPrivateAccess(partnerPrivate, partnerFresh, ownerToken, "only-B-fixture");
        assertThat(read("/api/v1/export?coupleId=" + space, partnerFresh).getBody())
                .contains("only-B-fixture").doesNotContain("only-A-fixture");
        assertThat(read("/api/v1/sync/changes?coupleId=" + space + "&after=0", partnerFresh).getBody())
                .contains("only-B-fixture").doesNotContain("only-A-fixture");
        assertThat(pair(partnerCode).getStatusCode().value()).as("B's recovery credential is single use").isEqualTo(409);

        // A can also transfer their own identity; only A's old session expires.
        Map<String, Object> ownerRejoin = issue(space, ownerToken);
        assertThat(ownerRejoin.get("pairingTokenKind")).isEqualTo("REJOIN");
        String ownerCode = (String) ownerRejoin.get("pairingToken");
        Map<String, Object> ownerBack = success(pair(ownerCode));
        assertThat(ownerBack.get("userId")).isEqualTo(ownerId);
        String ownerFresh = (String) ownerBack.get("deviceToken");
        assertThat(ownerFresh.equals(ownerToken)).as("A's session must rotate").isFalse();
        assertThat(spaceStatus(space, ownerToken)).isEqualTo(401);
        assertThat(spaceStatus(space, ownerFresh)).isEqualTo(200);
        assertThat(spaceStatus(space, partnerFresh)).as("B's fresh session survives A's replacement").isEqualTo(200);
        assertPrivateAccess(ownerPrivate, ownerFresh, partnerFresh, "only-A-fixture");
        assertPrivateAccess(partnerPrivate, partnerFresh, ownerFresh, "only-B-fixture");
        assertThat(pair(ownerCode).getStatusCode().value()).as("A's recovery credential is single use").isEqualTo(409);
        assertThat(db.queryForObject("SELECT count(*) FROM app_user", Integer.class)).isEqualTo(2);
        assertThat(db.queryForObject("SELECT count(*) FROM couple_member", Integer.class)).isEqualTo(2);
    }

    @Test
    void v16InvalidatesUnconsumedLegacyRejoinCodesAndPreservesInvitesAndConsumedHistory() throws Exception {
        String schema = "self_rejoin_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        Flyway before = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .target("15").cleanDisabled(false).load();
        try {
            before.migrate();
            try (Connection connection = dataSource.getConnection()) {
                assertThat(connection.getCatalog()).isEqualTo(TEST_DB);
                connection.setSchema(schema);
                JdbcTemplate upgrade = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
                UUID actor = UUID.randomUUID();
                upgrade.update("INSERT INTO app_user(id,status) VALUES (?,'ACTIVE')", actor);
                UUID activeRejoin = seedLegacyCode(upgrade, actor, false, false, 'a');
                UUID expiredRejoin = seedLegacyCode(upgrade, actor, false, true, 'b');
                UUID invite = seedLegacyCode(upgrade, null, false, false, 'c');
                UUID consumedRejoin = seedLegacyCode(upgrade, actor, true, false, 'd');
                Instant originalConsumedAt = upgrade.queryForObject(
                        "SELECT consumed_at FROM space_pairing_code WHERE id=?",
                        (rs, row) -> rs.getTimestamp(1).toInstant(), consumedRejoin);

                // Migrate available upgrades: before V16 exists this is a no-op,
                // so RED is the missing invalidation below, not a missing-target error.
                Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                        .load().migrate();

                assertThat(isConsumed(upgrade, activeRejoin)).as("old live cross-member recovery code must be invalidated").isTrue();
                assertThat(isConsumed(upgrade, expiredRejoin)).as("all old unconsumed recovery codes must be invalidated").isTrue();
                assertThat(isConsumed(upgrade, invite)).as("a pending first invitation must remain usable").isFalse();
                Instant consumedAtAfterUpgrade = upgrade.queryForObject(
                        "SELECT consumed_at FROM space_pairing_code WHERE id=?",
                        (rs, row) -> rs.getTimestamp(1).toInstant(), consumedRejoin);
                assertThat(consumedAtAfterUpgrade).isEqualTo(originalConsumedAt);
                assertThat(upgrade.queryForObject("SELECT count(*) FROM space_pairing_code", Integer.class)).isEqualTo(4);
            }
        } finally {
            // Only this test's generated schema, never public or the harness database itself.
            before.clean();
        }
    }

    private UUID seedLegacyCode(JdbcTemplate upgrade, UUID target, boolean consumed, boolean expired, char hashCharacter) {
        UUID space = UUID.randomUUID();
        UUID code = UUID.randomUUID();
        upgrade.update("INSERT INTO couple_space(id,status) VALUES (?,'UNPAIRED')", space);
        upgrade.update("""
                INSERT INTO space_pairing_code(id,couple_id,token_hash,expires_at,consumed_at,rejoin_user_id)
                VALUES (?,?,?, ?, ?, ?)
                """, code, space, String.valueOf(hashCharacter).repeat(64),
                java.sql.Timestamp.from(Instant.now().plusSeconds(expired ? -60 : 600)),
                consumed ? java.sql.Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")) : null, target);
        return code;
    }

    private boolean isConsumed(JdbcTemplate upgrade, UUID code) {
        return Boolean.TRUE.equals(upgrade.queryForObject(
                "SELECT consumed_at IS NOT NULL FROM space_pairing_code WHERE id=?", Boolean.class, code));
    }

    private UUID publishPrivate(String userId, String space, String body) {
        UUID author = UUID.fromString(userId);
        var draft = entries.createDraft(author, new CreateEntryCommand(author, UUID.fromString(space),
                EntryMode.PERSONAL, "isolated-private-fixture", Instant.now().minusSeconds(5), "UTC",
                List.of(new BlockMutation(UUID.randomUUID(), BlockType.TEXT, 0, author,
                        "{\"text\":\"" + body + "\"}", null, false)), UUID.randomUUID(), EntryVisibility.PRIVATE));
        return entries.publish(draft.id(), author, 0).entry().id();
    }

    private void assertPrivateAccess(UUID entry, String authorToken, String otherToken, String body) {
        ResponseEntity<String> own = read("/api/v1/entries/" + entry, authorToken);
        assertThat(own.getStatusCode().value()).isEqualTo(200);
        assertThat(own.getBody()).contains(body);
        ResponseEntity<String> other = read("/api/v1/entries/" + entry, otherToken);
        assertThat(other.getStatusCode().value()).isEqualTo(403);
        assertThat(other.getBody()).doesNotContain(body);
    }

    private Map<String, Object> issue(String space, String token) {
        HttpHeaders headers = authenticated(token);
        return success(http.postForEntity("/api/v1/couple/" + space + "/pairing-token",
                new HttpEntity<>(Map.of(), headers), Map.class));
    }

    private ResponseEntity<Map> pair(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.postForEntity("/api/v1/couple/pair",
                new HttpEntity<>(Map.of("token", code, "displayName", "replacement-device"), headers), Map.class);
    }

    private int spaceStatus(String space, String token) {
        return read("/api/v1/couple/" + space, token).getStatusCode().value();
    }

    private ResponseEntity<String> read(String path, String token) {
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(authenticated(token)), String.class);
    }

    private HttpHeaders authenticated(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> success(ResponseEntity<Map> response) {
        // Pair/bootstrap bodies contain credentials: never include them in assertion diagnostics.
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("expected successful HTTP status, got %s", response.getStatusCode()).isTrue();
        assertThat(response.hasBody()).isTrue();
        return response.getBody();
    }
}
