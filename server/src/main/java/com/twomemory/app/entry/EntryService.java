package com.twomemory.app.entry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twomemory.app.auth.SpaceAccessPolicy;
import com.twomemory.app.media.MediaService;
import com.twomemory.app.sync.ChangeFeedService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EntryService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final SpaceAccessPolicy accessPolicy;
    private final MediaService mediaService;
    private final ChangeFeedService changeFeedService;

    public EntryService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                        SpaceAccessPolicy accessPolicy, MediaService mediaService,
                        ChangeFeedService changeFeedService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.accessPolicy = accessPolicy;
        this.mediaService = mediaService;
        this.changeFeedService = changeFeedService;
    }

    @Transactional
    public EntryView createDraft(UUID actorId, CreateEntryCommand command) {
        if (command == null || command.authorId() == null || command.coupleId() == null
                || command.mode() == null || command.occurredAt() == null
                || command.occurredTimezone() == null || command.occurredTimezone().isBlank()) {
            throw new EntryValidationException("entry author, space, mode and occurrence time are required");
        }
        if (!actorId.equals(command.authorId())) {
            throw new EntryAccessDeniedException("entry author must be the authenticated user");
        }
        accessPolicy.requireMember(command.authorId(), command.coupleId());
        List<BlockMutation> blocks = stableOrder(command.blocks() == null ? List.of() : command.blocks());
        validateTitle(command.title());
        UUID entryId = command.entryId() != null ? command.entryId() : UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO entry(id, couple_id, mode, state, author_id, title,
                                  occurred_at, occurred_timezone, current_revision_no,
                                  row_version, created_at, updated_at)
                VALUES (?, ?, ?::entry_mode, 'DRAFT', ?, ?, ?, ?, 0, 0, now(), now())
                """, entryId, command.coupleId(), command.mode().name(), command.authorId(),
                blankToNull(command.title()), Timestamp.from(command.occurredAt()),
                command.occurredTimezone());
        jdbcTemplate.update("""
                INSERT INTO entry_contributor(entry_id, user_id, contribution_role)
                VALUES (?, ?, 'OWNER')
                """, entryId, command.authorId());
        for (BlockMutation block : blocks) {
            validateBlock(block);
            insertBlock(entryId, block, 0);
        }
        mediaService.requireReady(command.coupleId(), referencedAssetIds(entryId));
        return loadEntry(entryId);
    }

    @Transactional
    public PublishResult publish(UUID entryId, UUID actorId, long baseVersion) {
        EntryRow entry = lockEntry(entryId);
        accessPolicy.requireMember(actorId, entry.coupleId());
        if (!entry.authorId().equals(actorId)) {
            throw new EntryAccessDeniedException("only the author can publish the draft");
        }
        if (entry.rowVersion() != baseVersion) {
            throw new EntryConflict(entryId, List.of());
        }
        if (entry.state() != EntryState.DRAFT) {
            throw new EntryValidationException("only a draft can be published");
        }
        mediaService.requireReady(entry.coupleId(), referencedAssetIds(entryId));
        int revisionNo = entry.currentRevisionNo() + 1;
        updateEntryVersion(entryId, revisionNo, entry.rowVersion() + 1, EntryState.PUBLISHED);
        insertRevision(entryId, revisionNo, entry.currentRevisionNo(), entry.authorId(),
                "published draft");
        // For a personal record this row is the other device's first sight of the
        // entry: private drafts never enter the feed, so the row carries the whole
        // projection instead of just an id.
        EntryView published = loadEntry(entryId);
        changeFeedService.appendChange(entry.coupleId(), "ENTRY", entryId, "PUBLISH",
                writeJson(published));
        return new PublishResult(published, revisionNo);
    }

    @Transactional
    public ApplyChangesResult applyChanges(UUID entryId, UUID actorId, int baseRevision,
                                           List<BlockMutation> mutations) {
        EntryRow entry = lockEntry(entryId);
        accessPolicy.requireMember(actorId, entry.coupleId());
        List<BlockMutation> normalized = stableOrder(mutations == null ? List.of() : mutations);
        if (normalized.stream().anyMatch(block -> !actorId.equals(block.authorId()))) {
            throw new EntryAccessDeniedException("block author must be the authenticated user");
        }
        Set<UUID> incomingIds = normalized.stream().map(BlockMutation::blockId)
                .filter(id -> id != null).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<UUID> changedSinceBase = new HashSet<>();
        if (baseRevision != entry.currentRevisionNo()) {
            changedSinceBase.addAll(jdbcTemplate.query("""
                    SELECT id FROM entry_block
                    WHERE entry_id = ? AND block_version > ?
                    """, (rs, rowNum) -> rs.getObject("id", UUID.class), entryId, baseRevision));
            List<UUID> overlaps = incomingIds.stream().filter(changedSinceBase::contains).toList();
            if (!overlaps.isEmpty()) {
                throw new EntryConflict(entryId, overlaps);
            }
        }
        for (BlockMutation block : normalized) {
            validateBlock(block);
            ensureContributor(entry, block.authorId());
            UUID existingAuthor = existingBlockAuthor(entryId, block.blockId());
            if (existingAuthor != null && !existingAuthor.equals(block.authorId())) {
                throw new EntryAccessDeniedException(
                        "only the block author may edit or delete a shared block");
            }
            if (block.deleted()) {
                jdbcTemplate.update("""
                        UPDATE entry_block
                        SET deleted_at = now(), updated_by = ?, block_version = ?, updated_at = now()
                        WHERE id = ? AND entry_id = ?
                        """, block.authorId(), entry.currentRevisionNo() + 1, block.blockId(), entryId);
            } else {
                int updated = jdbcTemplate.update("""
                        UPDATE entry_block
                        SET type = ?::block_type, order_key = ?, updated_by = ?,
                            block_version = ?, payload = ?::jsonb, asset_id = ?,
                            deleted_at = NULL, updated_at = now()
                        WHERE id = ? AND entry_id = ?
                        """, block.type().name(), block.orderKey(), block.authorId(),
                        entry.currentRevisionNo() + 1, block.payload(), block.assetId(),
                        block.blockId(), entryId);
                if (updated == 0) {
                    insertBlock(entryId, block, entry.currentRevisionNo() + 1);
                }
            }
        }
        int nextRevision = entry.currentRevisionNo() + 1;
        updateEntryVersion(entryId, nextRevision, entry.rowVersion() + 1, entry.state());
        UUID editedBy = normalized.isEmpty() ? entry.authorId() : normalized.get(0).authorId();
        insertRevision(entryId, nextRevision, entry.currentRevisionNo(), editedBy, "applied block changes");
        return new ApplyChangesResult(loadEntry(entryId), baseRevision != entry.currentRevisionNo());
    }

    public EntryView resolveConflict(UUID entryId, UUID actorId, ResolveConflictCommand command) {
        return applyChanges(entryId, actorId, command.baseRevision(), command.mutations()).entry();
    }

    public EntryView readEntry(UUID entryId, UUID userId) {
        EntryRow entry = locklessEntry(entryId);
        accessPolicy.requireMember(userId, entry.coupleId());
        // Personal drafts are private to the author; collaborative drafts are
        // readable by the space (the partner contributes to them directly).
        if (entry.state() == EntryState.DRAFT && entry.mode() == EntryMode.PERSONAL
                && !entry.authorId().equals(userId)) {
            throw new EntryAccessDeniedException("personal draft is private");
        }
        return loadEntry(entryId);
    }

    public TimelinePage readTimeline(UUID userId, UUID coupleId, TimelineCursor cursor, int limit) {
        accessPolicy.requireMember(userId, coupleId);
        int safeLimit = Math.max(1, Math.min(limit, 200));
        List<EntryView> entries;
        if (cursor == null) {
            entries = jdbcTemplate.query("""
                    SELECT id FROM entry
                    WHERE couple_id = ? AND state = 'PUBLISHED' AND deleted_at IS NULL
                    ORDER BY occurred_at DESC, id DESC LIMIT ?
                    """, (rs, rowNum) -> loadEntry(rs.getObject("id", UUID.class)), coupleId, safeLimit);
        } else {
            entries = jdbcTemplate.query("""
                    SELECT id FROM entry
                    WHERE couple_id = ? AND state = 'PUBLISHED' AND deleted_at IS NULL
                      AND (occurred_at, id) < (?, ?)
                    ORDER BY occurred_at DESC, id DESC LIMIT ?
                    """, (rs, rowNum) -> loadEntry(rs.getObject("id", UUID.class)), coupleId,
                    Timestamp.from(cursor.occurredAt()), cursor.id(), safeLimit);
        }
        TimelineCursor next = entries.size() == safeLimit
                ? entries.stream().reduce((first, second) -> second)
                .map(last -> new TimelineCursor(last.blocks().isEmpty()
                        ? Instant.EPOCH : lastOccurredAt(last.id()), last.id())).orElse(null)
                : null;
        return new TimelinePage(List.copyOf(entries), next);
    }

    static String validateText(String raw) {
        if (raw == null) {
            throw new EntryValidationException("text block is required");
        }
        String value = raw.trim();
        long codePoints = value.codePoints().count();
        if (codePoints < 1 || codePoints > 20_000) {
            throw new EntryValidationException("text must contain 1 to 20000 Unicode code points");
        }
        return value;
    }

    static List<BlockMutation> stableOrder(List<BlockMutation> blocks) {
        List<BlockMutation> sorted = new ArrayList<>(blocks == null ? List.of() : blocks);
        sorted.sort(Comparator.comparingLong(BlockMutation::orderKey)
                .thenComparing(block -> block.blockId() == null ? "" : block.blockId().toString()));
        Set<Long> orderKeys = new HashSet<>();
        for (BlockMutation block : sorted) {
            if (block == null || block.blockId() == null || block.orderKey() < 0
                    || !orderKeys.add(block.orderKey())) {
                throw new EntryValidationException("block IDs and order keys must be unique");
            }
        }
        return List.copyOf(sorted);
    }

    static Set<UUID> changedBlockIds(List<UUID> before, List<UUID> after) {
        Set<UUID> result = new HashSet<>(after);
        result.removeAll(before);
        return result;
    }

    static void requireNoOverlappingChanges(List<UUID> first, List<UUID> second) {
        Set<UUID> overlap = new HashSet<>(first);
        overlap.retainAll(second);
        if (!overlap.isEmpty()) {
            throw new EntryConflict(null, List.copyOf(overlap));
        }
    }

    private void validateTitle(String title) {
        if (title != null && title.codePoints().count() > 120) {
            throw new EntryValidationException("title must contain at most 120 Unicode code points");
        }
    }

    private void validateBlock(BlockMutation block) {
        if (block == null || block.blockId() == null || block.type() == null || block.authorId() == null) {
            throw new EntryValidationException("block identity, type and author are required");
        }
        if (block.type() == BlockType.TEXT) {
            try {
                var node = objectMapper.readTree(block.payload());
                validateText(node.path("text").asText(null));
            } catch (JsonProcessingException exception) {
                throw new EntryValidationException("block payload must be valid JSON");
            }
        }
        if (block.type() == BlockType.IMAGE && block.assetId() == null) {
            throw new EntryValidationException("image block requires an asset");
        }
        if (block.payload() == null || block.payload().isBlank()) {
            throw new EntryValidationException("block payload is required");
        }
    }

    private void ensureContributor(EntryRow entry, UUID authorId) {
        Integer exists = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM entry_contributor WHERE entry_id = ? AND user_id = ?
                """, Integer.class, entry.id(), authorId);
        if (exists != null && exists > 0) {
            return;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM entry_contributor WHERE entry_id = ?", Integer.class, entry.id());
        if (entry.mode() == EntryMode.PERSONAL || (count != null && count >= 2)) {
            throw new EntryAccessDeniedException("entry contributor limit reached");
        }
        jdbcTemplate.update("""
                INSERT INTO entry_contributor(entry_id, user_id, contribution_role)
                VALUES (?, ?, 'CONTRIBUTOR')
                """, entry.id(), authorId);
    }

    private void insertBlock(UUID entryId, BlockMutation block, long blockVersion) {
        jdbcTemplate.update("""
                INSERT INTO entry_block(id, entry_id, type, order_key, created_by, updated_by,
                                        block_version, payload, asset_id, created_at, updated_at)
                VALUES (?, ?, ?::block_type, ?, ?, ?, ?, ?::jsonb, ?, now(), now())
                """, block.blockId(), entryId, block.type().name(), block.orderKey(),
                block.authorId(), block.authorId(), blockVersion, block.payload(), block.assetId());
    }

    private void updateEntryVersion(UUID entryId, int revisionNo, long rowVersion, EntryState state) {
        jdbcTemplate.update("""
                UPDATE entry
                SET current_revision_no = ?, row_version = ?, state = ?::entry_state, updated_at = now()
                WHERE id = ?
                """, revisionNo, rowVersion, state.name(), entryId);
    }

    private void insertRevision(UUID entryId, int revisionNo, int baseRevision,
                                UUID editedBy, String summary) {
        String snapshot = writeJson(loadEntry(entryId).blocks());
        jdbcTemplate.update("""
                INSERT INTO entry_revision(id, entry_id, revision_no, base_revision_no,
                                           edited_by, snapshot, change_summary, created_at)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, now())
                """, UUID.randomUUID(), entryId, revisionNo, baseRevision, editedBy, snapshot, summary);
    }

    private EntryRow lockEntry(UUID entryId) {
        return jdbcTemplate.queryForObject("""
                SELECT id, couple_id, mode::text AS mode, state::text AS state, author_id,
                       row_version, current_revision_no, title,
                       EXTRACT(EPOCH FROM occurred_at) * 1000 AS occurred_at_epoch_millis,
                       occurred_timezone
                FROM entry WHERE id = ? FOR UPDATE
                """, this::mapEntryRow, entryId);
    }

    private EntryRow locklessEntry(UUID entryId) {
        return jdbcTemplate.queryForObject("""
                SELECT id, couple_id, mode::text AS mode, state::text AS state, author_id,
                       row_version, current_revision_no, title,
                       EXTRACT(EPOCH FROM occurred_at) * 1000 AS occurred_at_epoch_millis,
                       occurred_timezone
                FROM entry WHERE id = ?
                """, this::mapEntryRow, entryId);
    }

    /** Author of an existing block, or null when the block is new. */
    private UUID existingBlockAuthor(UUID entryId, UUID blockId) {
        if (blockId == null) {
            return null;
        }
        List<UUID> authors = jdbcTemplate.query("""
                SELECT created_by FROM entry_block WHERE id = ? AND entry_id = ?
                """, (rs, rowNum) -> rs.getObject("created_by", UUID.class), blockId, entryId);
        return authors.isEmpty() ? null : authors.getFirst();
    }

    private List<UUID> referencedAssetIds(UUID entryId) {
        return jdbcTemplate.query("""
                SELECT asset_id FROM entry_block
                WHERE entry_id = ? AND asset_id IS NOT NULL AND deleted_at IS NULL
                """, (rs, rowNum) -> rs.getObject("asset_id", UUID.class), entryId);
    }

    private EntryView loadEntry(UUID entryId) {
        EntryRow row = locklessEntry(entryId);
        List<BlockView> blocks = jdbcTemplate.query("""
                SELECT id, type::text AS type, order_key, updated_by, block_version,
                       payload::text AS payload, asset_id, deleted_at
                FROM entry_block WHERE entry_id = ?
                ORDER BY order_key, id
                """, (rs, rowNum) -> new BlockView(
                rs.getObject("id", UUID.class),
                BlockType.valueOf(rs.getString("type")),
                rs.getLong("order_key"),
                rs.getObject("updated_by", UUID.class),
                rs.getLong("block_version"),
                rs.getString("payload"),
                rs.getObject("asset_id", UUID.class),
                rs.getTimestamp("deleted_at") != null), entryId);
        return new EntryView(row.id(), row.coupleId(), row.mode(), row.state(), row.authorId(),
                row.rowVersion(), row.currentRevisionNo(), row.title(),
                row.occurredAtEpochMillis(), row.occurredTimezone(), List.copyOf(blocks));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("could not serialize entry projection", exception);
        }
    }

    private EntryRow mapEntryRow(ResultSet rs, int rowNum) throws SQLException {
        return new EntryRow(
                rs.getObject("id", UUID.class),
                rs.getObject("couple_id", UUID.class),
                EntryMode.valueOf(rs.getString("mode")),
                EntryState.valueOf(rs.getString("state")),
                rs.getObject("author_id", UUID.class),
                rs.getLong("row_version"),
                rs.getInt("current_revision_no"),
                rs.getString("title"),
                rs.getLong("occurred_at_epoch_millis"),
                rs.getString("occurred_timezone"));
    }

    private Instant lastOccurredAt(UUID entryId) {
        Timestamp timestamp = jdbcTemplate.queryForObject(
                "SELECT occurred_at FROM entry WHERE id = ?", Timestamp.class, entryId);
        return timestamp == null ? Instant.EPOCH : timestamp.toInstant();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record EntryRow(UUID id, UUID coupleId, EntryMode mode, EntryState state,
                            UUID authorId, long rowVersion, int currentRevisionNo,
                            String title, long occurredAtEpochMillis, String occurredTimezone) {
    }
}
