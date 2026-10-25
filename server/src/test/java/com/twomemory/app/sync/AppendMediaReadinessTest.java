package com.twomemory.app.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twomemory.app.TestDatabaseConfig;
import com.twomemory.app.entry.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** F3 append gate only; no avatar, download, or production DTO changes. */
@SpringBootTest
@Transactional
class AppendMediaReadinessTest {
    // All fixtures and migrations stay in a newly created database. No DROP,
    // TRUNCATE, existing application DB, or object storage access is involved.
    private static final String DATABASE = "moon_letter_f3_" + UUID.randomUUID().toString().replace("-", "");
    private static final String DATABASE_URL = prepareDatabase();

    private static String prepareDatabase() {
        String adminUrl = TestDatabaseConfig.adminUrl();
        try {
            URI admin = URI.create(adminUrl.substring("jdbc:".length()));
            if (!"postgresql".equals(admin.getScheme()) || !"/postgres".equals(admin.getPath())) {
                throw new IllegalStateException("F3 tests require TEST_DB_ADMIN_URL to target the postgres admin database");
            }
            try (var connection = DriverManager.getConnection(adminUrl,
                    TestDatabaseConfig.user(), TestDatabaseConfig.password());
                 var statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE " + DATABASE);
            }
            return "jdbc:" + new URI(admin.getScheme(), admin.getUserInfo(), admin.getHost(),
                    admin.getPort(), "/" + DATABASE, admin.getQuery(), null);
        } catch (Exception exception) {
            throw new IllegalStateException("could not create isolated F3 PostgreSQL database " + DATABASE, exception);
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
    @Autowired ObjectMapper json;
    UUID author, space, entry;

    @BeforeEach
    void seedPublishedEntry() {
        author = UUID.randomUUID();
        space = UUID.randomUUID();
        db.update("INSERT INTO app_user(id,status) VALUES (?,'ACTIVE')", author);
        db.update("INSERT INTO couple_space(id,status) VALUES (?,'ACTIVE')", space);
        db.update("INSERT INTO couple_member(couple_id,user_id) VALUES (?,?)", space, author);
        assertThat(db.queryForObject("SELECT current_database()", String.class)).isEqualTo(DATABASE);
        var draft = entries.createDraft(author, new CreateEntryCommand(author, space, EntryMode.PERSONAL,
                "synthetic F3 entry", Instant.parse("2026-01-01T00:00:00Z"), "UTC", List.of(), UUID.randomUUID()));
        entry = draft.id();
        entries.publish(entry, author, 0);
    }

    @ParameterizedTest
    @CsvSource({"IMAGE,UPLOADING", "IMAGE,FAILED", "VIDEO,UPLOADING", "VIDEO,FAILED",
            "AUDIO,UPLOADING", "AUDIO,FAILED"})
    void appendRejectsNonReadyMediaWithoutWritingBlockRevisionOrChange(BlockType type, String state) {
        UUID asset = asset(type, state);
        UUID block = UUID.randomUUID();
        assertThatThrownBy(() -> entries.applyChanges(entry, author, 1,
                List.of(mutation(block, type, asset, false))))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("READY");
        assertThat(db.queryForObject("SELECT count(*) FROM entry_block WHERE entry_id=?", Integer.class, entry)).isZero();
        assertThat(db.queryForObject("SELECT current_revision_no FROM entry WHERE id=?", Integer.class, entry)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT row_version FROM entry WHERE id=?", Long.class, entry)).isEqualTo(1L);
        assertThat(db.queryForObject("SELECT count(*) FROM entry_revision WHERE entry_id=?", Integer.class, entry)).isEqualTo(1);
        assertThat(feed.lastSequence(space)).isEqualTo(1L);
    }

    @ParameterizedTest
    @CsvSource({"IMAGE,VIDEO", "VIDEO,AUDIO", "AUDIO,IMAGE"})
    void appendRejectsReadyMediaOfTheWrongKind(BlockType blockType, BlockType assetType) {
        UUID asset = asset(assetType, "READY");
        assertThatThrownBy(() -> entries.applyChanges(entry, author, 1,
                List.of(mutation(UUID.randomUUID(), blockType, asset, false))))
                .isInstanceOf(EntryValidationException.class).hasMessageContaining("media kind must match block type");
        assertThat(db.queryForObject("SELECT count(*) FROM entry_block WHERE entry_id=?", Integer.class, entry)).isZero();
        assertThat(feed.lastSequence(space)).isEqualTo(1L);
    }

    @ParameterizedTest
    @EnumSource(value = BlockType.class, names = {"IMAGE", "VIDEO", "AUDIO"})
    void appendRejectsReadyMediaFromAnotherSpaceEvenWhenActorOwnsIt(BlockType type) {
        UUID otherSpace = UUID.randomUUID();
        db.update("INSERT INTO couple_space(id,status) VALUES (?,'ACTIVE')", otherSpace);
        db.update("INSERT INTO couple_member(couple_id,user_id) VALUES (?,?)", otherSpace, author);
        UUID asset = asset(type, "READY");
        db.update("UPDATE media_asset SET couple_id=? WHERE id=?", otherSpace, asset);
        assertThatThrownBy(() -> entries.applyChanges(entry, author, 1,
                List.of(mutation(UUID.randomUUID(), type, asset, false))))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("READY");
        assertThat(db.queryForObject("SELECT count(*) FROM entry_block WHERE entry_id=?", Integer.class, entry)).isZero();
        assertThat(feed.lastSequence(space)).isEqualTo(1L);
    }

    @ParameterizedTest
    @EnumSource(value = BlockType.class, names = {"IMAGE", "VIDEO", "AUDIO"})
    void readyMediaCanBeAppendedAndEditedAgain(BlockType type) throws Exception {
        UUID asset = asset(type, "READY");
        UUID block = UUID.randomUUID();
        entries.applyChanges(entry, author, 1, List.of(mutation(block, type, asset, false)));
        var edited = entries.applyChanges(entry, author, 2, List.of(mutation(block, type, asset, false))).entry();
        var projection = json.valueToTree(edited).path("blocks");
        assertThat(projection.size()).isEqualTo(1);
        assertThat(projection.get(0).path("assetId").asText()).isEqualTo(asset.toString());
        assertThat(projection.get(0).path("deleted").asBoolean()).isFalse();
        assertThat(projection.get(0).path("blockVersion").asLong()).isEqualTo(3L);
        assertThat(feed.lastSequence(space)).isEqualTo(3L);
    }

    @ParameterizedTest
    @EnumSource(value = BlockType.class, names = {"IMAGE", "VIDEO", "AUDIO"})
    void deletingExistingMediaBlockDoesNotRequireAssetToRemainReady(BlockType type) {
        UUID asset = asset(type, "READY");
        UUID block = UUID.randomUUID();
        entries.applyChanges(entry, author, 1, List.of(mutation(block, type, asset, false)));
        db.update("UPDATE media_asset SET status='FAILED' WHERE id=?", asset);
        var deleted = entries.applyChanges(entry, author, 2, List.of(mutation(block, type, asset, true))).entry();
        var tombstone = json.valueToTree(deleted).path("blocks").get(0);
        assertThat(tombstone.path("id").asText()).isEqualTo(block.toString());
        assertThat(tombstone.path("deleted").asBoolean()).isTrue();
        assertThat(tombstone.path("blockVersion").asLong()).isEqualTo(3L);
        assertThat(tombstone.path("payload").asText()).isEqualTo("{}");
        assertThat(tombstone.path("assetId").isNull()).isTrue();
        assertThat(db.queryForObject("SELECT asset_id FROM entry_block WHERE id=?", UUID.class, block)).isEqualTo(asset);
    }

    private BlockMutation mutation(UUID block, BlockType type, UUID asset, boolean deleted) {
        return new BlockMutation(block, type, 0, author, "{\"caption\":\"synthetic F3 media\"}", asset, deleted);
    }

    private UUID asset(BlockType type, String state) {
        UUID id = UUID.randomUUID();
        String mime = switch (type) {
            case IMAGE -> "image/png";
            case VIDEO -> "video/mp4";
            case AUDIO -> "audio/mp4";
            default -> throw new IllegalArgumentException("media block type required");
        };
        db.update("INSERT INTO media_asset(id,couple_id,owner_id,kind,status,object_key,mime_type,byte_size,sha256) "
                        + "VALUES (?,?,?,?::media_kind,?::media_status,?,?,10,?)",
                id, space, author, type.name(), state, "f3-synthetic/" + id, mime, "b".repeat(64));
        return id;
    }
}
