package com.twomemory.app.entry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Task 4 acceptance: actor ownership is enforced in every business
 * operation. All scenarios run against a real PostgreSQL database.
 *
 * Seeds two couples:
 *   couple1: author (entry owner) + partner
 *   couple2: outsider
 */
@SpringBootTest
class EntryOwnershipTest {

    private static final String ADMIN_URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String ADMIN_USER = "moon_letter";
    private static final String ADMIN_PASSWORD = "moon_letter_dev_only";
    private static final String TEST_DB = "moon_letter_own_test";

    static UUID author;
    static UUID partner;
    static UUID outsider;
    static UUID couple1;
    static UUID couple2;

    @Autowired
    private EntryService entryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/" + TEST_DB);
        registry.add("spring.datasource.username", () -> ADMIN_USER);
        registry.add("spring.datasource.password", () -> ADMIN_PASSWORD);
    }

    static {
        try (Connection connection = DriverManager.getConnection(ADMIN_URL, ADMIN_USER, ADMIN_PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + TEST_DB + " WITH (FORCE)");
            statement.execute("CREATE DATABASE " + TEST_DB);
        } catch (Exception exception) {
            throw new IllegalStateException("could not prepare isolated ownership test database", exception);
        }
    }

    @BeforeEach
    void seed() {
        Integer users = jdbcTemplate.queryForObject("SELECT count(*) FROM app_user", Integer.class);
        if (users != null && users > 0) {
            return;
        }
        author = UUID.randomUUID();
        partner = UUID.randomUUID();
        outsider = UUID.randomUUID();
        couple1 = UUID.randomUUID();
        couple2 = UUID.randomUUID();
        for (UUID user : List.of(author, partner, outsider)) {
            jdbcTemplate.update("INSERT INTO app_user (id, status) VALUES (?, 'ACTIVE')", user);
            jdbcTemplate.update("INSERT INTO user_profile (user_id, display_name, theme) "
                    + "VALUES (?, '成员', 'WARM_BEIGE')", user);
        }
        for (UUID couple : List.of(couple1, couple2)) {
            jdbcTemplate.update("INSERT INTO couple_space (id, status) VALUES (?, 'ACTIVE')", couple);
        }
        jdbcTemplate.update("INSERT INTO couple_member (couple_id, user_id) VALUES (?, ?)", couple1, author);
        jdbcTemplate.update("INSERT INTO couple_member (couple_id, user_id) VALUES (?, ?)", couple1, partner);
        jdbcTemplate.update("INSERT INTO couple_member (couple_id, user_id) VALUES (?, ?)", couple2, outsider);
    }

    @Test
    void memberOfAnotherSpaceCannotReadTheEntry() {
        UUID entryId = insertEntry(EntryMode.PERSONAL, EntryState.PUBLISHED, author);

        assertThatThrownBy(() -> entryService.readEntry(entryId, outsider))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("space access denied");
        assertThat(entryService.readEntry(entryId, author).id()).isEqualTo(entryId);
    }

    @Test
    void partnerCannotPublishTheAuthorsDraft() {
        UUID entryId = insertEntry(EntryMode.PERSONAL, EntryState.DRAFT, author);

        assertThatThrownBy(() -> entryService.publish(entryId, partner, 0))
                .isInstanceOf(EntryAccessDeniedException.class);
        assertThatThrownBy(() -> entryService.publish(entryId, outsider, 0))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("space access denied");
        assertThat(entryService.publish(entryId, author, 0).revisionNo()).isEqualTo(1);
    }

    @Test
    void partnerMayAppendOwnBlockButNotEditTheAuthorsBlock() {
        UUID entryId = insertEntry(EntryMode.COLLABORATIVE, EntryState.DRAFT, author);
        UUID authorBlockId = insertTextBlock(entryId, author, 0, "作者的第一视角");

        // The partner may append their own perspective block.
        ApplyChangesResult append = entryService.applyChanges(entryId, partner, 0, List.of(
                new BlockMutation(UUID.randomUUID(), BlockType.TEXT, 1, partner,
                        "{\"text\":\"伴侣的视角\"}", null, false)));
        assertThat(append.entry().blocks()).hasSize(2);

        // The partner may not edit or delete the author's block.
        assertThatThrownBy(() -> entryService.applyChanges(entryId, partner, 1, List.of(
                new BlockMutation(authorBlockId, BlockType.TEXT, 0, partner,
                        "{\"text\":\"篡改\"}", null, false))))
                .isInstanceOf(EntryAccessDeniedException.class);
        assertThatThrownBy(() -> entryService.applyChanges(entryId, partner, 1, List.of(
                new BlockMutation(authorBlockId, BlockType.TEXT, 0, partner,
                        "{\"text\":\"删除\"}", null, true))))
                .isInstanceOf(EntryAccessDeniedException.class);
        assertThatThrownBy(() -> entryService.applyChanges(entryId, outsider, 1, List.of(
                new BlockMutation(UUID.randomUUID(), BlockType.TEXT, 2, outsider,
                        "{\"text\":\"外来者\"}", null, false))))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("space access denied");
    }

    @Test
    void publicationRejectsNonReadyMedia() {
        UUID entryId = insertEntry(EntryMode.PERSONAL, EntryState.DRAFT, author);
        UUID notReadyAsset = insertMediaAsset(couple1, author, "UPLOADING");
        insertImageBlock(entryId, author, 1, notReadyAsset);

        assertThatThrownBy(() -> entryService.publish(entryId, author, 0))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("READY");
    }

    @Test
    void publicationRejectsMediaFromAnotherSpace() {
        UUID entryId = insertEntry(EntryMode.PERSONAL, EntryState.DRAFT, author);
        UUID foreignAsset = insertMediaAsset(couple2, outsider, "READY");
        insertImageBlock(entryId, author, 1, foreignAsset);

        assertThatThrownBy(() -> entryService.publish(entryId, author, 0))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("READY");
    }

    private UUID insertEntry(EntryMode mode, EntryState state, UUID ownerId) {
        UUID entryId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO entry(id, couple_id, mode, state, author_id, title,
                                  occurred_at, occurred_timezone, current_revision_no, row_version,
                                  created_at, updated_at)
                VALUES (?, ?, ?::entry_mode, ?::entry_state, ?, NULL, ?, 'Asia/Shanghai', 0, 0, now(), now())
                """, entryId, couple1, mode.name(), state.name(), ownerId,
                Timestamp.from(Instant.parse("2026-09-30T12:18:00Z")));
        jdbcTemplate.update("INSERT INTO entry_contributor(entry_id, user_id, contribution_role) "
                + "VALUES (?, ?, 'OWNER')", entryId, ownerId);
        return entryId;
    }

    private UUID insertTextBlock(UUID entryId, UUID blockAuthor, long orderKey, String text) {
        UUID blockId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO entry_block(id, entry_id, type, order_key, created_by, updated_by,
                                        block_version, payload, created_at, updated_at)
                VALUES (?, ?, 'TEXT', ?, ?, ?, 0, ?::jsonb, now(), now())
                """, blockId, entryId, orderKey, blockAuthor, blockAuthor,
                "{\"text\":\"" + text + "\"}");
        return blockId;
    }

    private void insertImageBlock(UUID entryId, UUID blockAuthor, long orderKey, UUID assetId) {
        jdbcTemplate.update("""
                INSERT INTO entry_block(id, entry_id, type, order_key, created_by, updated_by,
                                        block_version, payload, asset_id, created_at, updated_at)
                VALUES (?, ?, 'IMAGE', ?, ?, ?, 0, ?::jsonb, ?, now(), now())
                """, UUID.randomUUID(), entryId, orderKey, blockAuthor, blockAuthor,
                "{\"caption\":\"\"}", assetId);
    }

    private UUID insertMediaAsset(UUID couple, UUID owner, String status) {
        UUID assetId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO media_asset(id, couple_id, owner_id, kind, status, object_key,
                                        mime_type, byte_size, sha256)
                VALUES (?, ?, ?, 'IMAGE', ?::media_status, 'test/key.jpg', 'image/jpeg', 1024, ?)
                """, assetId, couple, owner, status, "a".repeat(64));
        return assetId;
    }
}
