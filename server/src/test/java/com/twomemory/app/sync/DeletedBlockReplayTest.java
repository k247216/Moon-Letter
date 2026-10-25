package com.twomemory.app.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twomemory.app.TestDatabaseConfig;
import com.twomemory.app.auth.TestAuth;
import com.twomemory.app.entry.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** F2: real PostgreSQL and HTTP projections, with synthetic data only. */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class DeletedBlockReplayTest {
    private static final String DELETED_BODY = "f2-synthetic-deleted-body";
    private static final String RETAINED_BODY = "f2-synthetic-retained-body";
    private static final String OTHER_BODY = "f2-synthetic-other-entry-body";
    private static final String PRIVATE_BODY = "f2-synthetic-private-body";

    // Never fall back to applicationUrl(): migrations and fixtures run in a new,
    // randomly named database, not an existing application or production DB.
    // No DROP/TRUNCATE is performed; the runner may dispose of this test DB later.
    private static final String DATABASE = "moon_letter_f2_" + UUID.randomUUID().toString().replace("-", "");
    private static final String DATABASE_URL = prepareDatabase();

    private static String prepareDatabase() {
        String adminUrl = TestDatabaseConfig.adminUrl();
        try {
            URI admin = URI.create(adminUrl.substring("jdbc:".length()));
            if (!"postgresql".equals(admin.getScheme()) || !"/postgres".equals(admin.getPath())) {
                throw new IllegalStateException("F2 tests require TEST_DB_ADMIN_URL to target the postgres admin database");
            }
            try (var connection = DriverManager.getConnection(adminUrl,
                    TestDatabaseConfig.user(), TestDatabaseConfig.password());
                 var statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE " + DATABASE);
            }
            return "jdbc:" + new URI(admin.getScheme(), admin.getUserInfo(), admin.getHost(),
                    admin.getPort(), "/" + DATABASE, admin.getQuery(), null);
        } catch (Exception exception) {
            throw new IllegalStateException("could not create isolated F2 PostgreSQL database " + DATABASE, exception);
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE_URL);
        registry.add("spring.datasource.username", TestDatabaseConfig::user);
        registry.add("spring.datasource.password", TestDatabaseConfig::password);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired JdbcTemplate db;
    @Autowired EntryService entries;
    @Autowired ChangeFeedService feed;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    UUID author, partner, space;

    @BeforeEach
    void seed() {
        author = UUID.randomUUID();
        partner = UUID.randomUUID();
        space = UUID.randomUUID();
        db.update("INSERT INTO couple_space(id,status) VALUES (?,'ACTIVE')", space);
        for (UUID user : List.of(author, partner)) {
            db.update("INSERT INTO app_user(id,status) VALUES (?,'ACTIVE')", user);
            db.update("INSERT INTO couple_member(couple_id,user_id) VALUES (?,?)", space, user);
        }
        assertThat(db.queryForObject("SELECT current_database()", String.class)).isEqualTo(DATABASE);
    }

    @ParameterizedTest
    @EnumSource(value = BlockType.class, names = {"TEXT", "IMAGE"})
    void currentGetReturnsContentFreeTombstoneAndPreservesLiveContent(BlockType type) throws Exception {
        Fixture fixture = deletePublishedBlock(type);
        for (UUID actor : List.of(partner, author)) {
            JsonNode current = readCurrent(fixture.entryId(), actor);
            assertThat(current.toString()).doesNotContain(DELETED_BODY);
            assertTombstone(block(current, fixture.deletedId()));
            assertLiveBlock(block(current, fixture.retainedId()), RETAINED_BODY);
            assertThat(current.path("rowVersion").asLong()).isEqualTo(2);
            assertThat(current.path("currentRevisionNo").asInt()).isEqualTo(2);
            assertThat(readCurrent(fixture.otherEntryId(), actor).toString()).contains(OTHER_BODY);
        }
        assertAuditRetained(fixture, type);
    }

    @ParameterizedTest
    @EnumSource(value = BlockType.class, names = {"TEXT", "IMAGE"})
    void cursorZeroReplaySanitizesEveryHistoricalSnapshotWithoutChangingAudienceOrPaging(BlockType type) throws Exception {
        Fixture fixture = deletePublishedBlock(type);
        List<JsonNode> changes = new ArrayList<>();
        long cursor = 0;
        boolean hasMore;
        do {
            JsonNode page = json.readTree(mvc.perform(get("/api/v1/sync/changes")
                            .param("coupleId", space.toString()).param("after", Long.toString(cursor))
                            .param("limit", "1").with(TestAuth.deviceSession(partner, space)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            for (JsonNode change : page.path("changes")) changes.add(change);
            long next = page.path("nextSequence").asLong();
            assertThat(next).isGreaterThan(cursor);
            cursor = next;
            hasMore = page.path("hasMore").asBoolean();
            assertThat(changes.size()).isLessThanOrEqualTo(3);
        } while (hasMore);

        assertThat(changes).extracting(row -> row.path("sequence").asLong()).containsExactly(2L, 3L, 4L);
        assertThat(cursor).isEqualTo(5L); // invisible private rows at sequences 1 and 5
        assertThat(feed.lastSequence(space)).isEqualTo(5L);
        assertThat(changes).allSatisfy(row -> assertThat(row.toString())
                .doesNotContain(DELETED_BODY, PRIVATE_BODY, fixture.privateEntryId().toString()));
        for (JsonNode row : changes) {
            JsonNode payload = json.readTree(row.path("payload").asText());
            if (row.path("entityId").asText().equals(fixture.entryId().toString())) {
                assertThat(row.path("operation").asText()).isIn("PUBLISH", "UPDATE");
                assertTombstone(block(payload, fixture.deletedId()));
                assertLiveBlock(block(payload, fixture.retainedId()), RETAINED_BODY);
            } else {
                assertThat(row.path("entityId").asText()).isEqualTo(fixture.otherEntryId().toString());
                assertThat(payload.toString()).contains(OTHER_BODY);
            }
        }
        // Also sanitize author replay, while retaining the author's private audience.
        var authorChanges = feed.readChanges(author, space, 0, 200).changes();
        assertThat(authorChanges).hasSize(5).allSatisfy(row -> assertThat(row.payload()).doesNotContain(DELETED_BODY));
        assertThat(authorChanges).anySatisfy(row -> assertThat(row.payload()).contains(PRIVATE_BODY));
        assertAuditRetained(fixture, type);
    }

    private Fixture deletePublishedBlock(BlockType type) throws Exception {
        UUID deletedId = UUID.randomUUID(), retainedId = UUID.randomUUID();
        UUID assetId = type == BlockType.IMAGE ? UUID.randomUUID() : null;
        if (assetId != null) {
            db.update("INSERT INTO media_asset(id,couple_id,owner_id,kind,status,object_key,mime_type,byte_size,sha256) "
                            + "VALUES (?,?,?,'IMAGE','READY',?,'image/png',10,?)",
                    assetId, space, author, "f2-synthetic/" + assetId, "a".repeat(64));
        }
        var hidden = publish(List.of(text(UUID.randomUUID(), 0, PRIVATE_BODY)), EntryVisibility.PRIVATE);
        String payload = json.writeValueAsString(java.util.Map.of(type == BlockType.TEXT ? "text" : "caption", DELETED_BODY));
        var deleted = new BlockMutation(deletedId, type, 0, author, payload, assetId, false);
        var target = publish(List.of(deleted, text(retainedId, 1, RETAINED_BODY)), EntryVisibility.SHARED);
        var other = publish(List.of(text(UUID.randomUUID(), 0, OTHER_BODY)), EntryVisibility.SHARED);
        // Establish that an older partner device actually received this block.
        assertThat(readCurrent(target.id(), partner).toString()).contains(DELETED_BODY, RETAINED_BODY);
        assertThat(feed.readChanges(partner, space, 0, 200).changes())
                .anySatisfy(row -> assertThat(row.payload()).contains(DELETED_BODY));
        mvc.perform(post("/api/v1/entries/{id}/changes", target.id())
                        .with(TestAuth.deviceSession(author, space)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("baseRevision", 1, "mutations", List.of(
                                new BlockMutation(deletedId, type, 0, author, payload, assetId, true))))))
                .andExpect(status().isOk());
        entries.applyChanges(hidden.id(), author, 1, List.of());
        return new Fixture(target.id(), deletedId, retainedId, other.id(), hidden.id(), assetId);
    }

    private EntryView publish(List<BlockMutation> blocks, EntryVisibility visibility) {
        var draft = entries.createDraft(author, new CreateEntryCommand(author, space, EntryMode.PERSONAL,
                "synthetic F2 entry", Instant.parse("2026-01-01T00:00:00Z"), "UTC", blocks,
                UUID.randomUUID(), visibility));
        return entries.publish(draft.id(), author, 0).entry();
    }

    private BlockMutation text(UUID id, long order, String body) {
        return new BlockMutation(id, BlockType.TEXT, order, author, "{\"text\":\"" + body + "\"}", null, false);
    }

    private JsonNode readCurrent(UUID entry, UUID actor) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/entries/{id}", entry)
                        .with(TestAuth.deviceSession(actor, space)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode block(JsonNode entry, UUID id) {
        List<JsonNode> matches = new ArrayList<>();
        entry.path("blocks").forEach(block -> {
            if (block.path("id").asText().equals(id.toString())) matches.add(block);
        });
        assertThat(matches).as("Room must receive the block identity, including tombstones").hasSize(1);
        return matches.getFirst();
    }

    private void assertTombstone(JsonNode block) {
        assertThat(block.path("deleted").asBoolean()).isTrue();
        assertThat(block.path("blockVersion").asLong()).isEqualTo(2);
        assertThat(block.path("payload").isNull() || block.path("payload").isMissingNode()
                || "{}".equals(block.path("payload").asText())).as("tombstone carries no content").isTrue();
        assertThat(block.path("assetId").isNull() || block.path("assetId").isMissingNode()).isTrue();
    }

    private void assertLiveBlock(JsonNode block, String body) {
        assertThat(block.path("deleted").asBoolean()).isFalse();
        assertThat(block.path("payload").asText()).contains(body);
        assertThat(block.path("blockVersion").asLong()).isZero();
    }

    private void assertAuditRetained(Fixture fixture, BlockType type) {
        assertThat(db.queryForObject("SELECT payload::text FROM entry_block WHERE id=?", String.class,
                fixture.deletedId())).contains(DELETED_BODY);
        assertThat(db.queryForObject("SELECT deleted_at IS NOT NULL FROM entry_block WHERE id=?", Boolean.class,
                fixture.deletedId())).isTrue();
        assertThat(db.queryForObject("SELECT payload::text FROM sync_change WHERE couple_id=? AND space_sequence=2",
                String.class, space)).contains(DELETED_BODY);
        assertThat(db.queryForObject("SELECT snapshot::text FROM entry_revision WHERE entry_id=? AND revision_no=1",
                String.class, fixture.entryId())).contains(DELETED_BODY);
        if (type == BlockType.IMAGE) {
            assertThat(db.queryForObject("SELECT asset_id FROM entry_block WHERE id=?", UUID.class,
                    fixture.deletedId())).isEqualTo(fixture.assetId());
            assertThat(db.queryForObject("SELECT status::text FROM media_asset WHERE id=?", String.class,
                    fixture.assetId())).isEqualTo("READY");
        }
    }

    private record Fixture(UUID entryId, UUID deletedId, UUID retainedId, UUID otherEntryId,
                           UUID privateEntryId, UUID assetId) { }
}
