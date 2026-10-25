package com.twomemory.app.sync;

import com.twomemory.app.auth.SpaceAccessPolicy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ChangeFeedService {

    private final JdbcTemplate jdbcTemplate;
    private final SpaceAccessPolicy accessPolicy;

    public ChangeFeedService(JdbcTemplate jdbcTemplate, SpaceAccessPolicy accessPolicy) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessPolicy = accessPolicy;
    }

    /**
     * Allocates the next per-couple sequence under a lock on the couple's
     * sync state row, inside the caller's transaction. Concurrent mutations
     * for the same couple serialize, so the sequence order matches commit
     * order and no committed change can be skipped by a paging client.
     */
    @Transactional
    public long appendChange(UUID coupleId, String entityType, UUID entityId,
                             String operation, String payload) {
        UUID audience = jdbcTemplate.query("SELECT author_id FROM entry WHERE id=? AND visibility='PRIVATE' AND ? IN ('ENTRY','COMMENT')",
                (rs, row) -> rs.getObject(1, UUID.class), entityId, entityType).stream().findFirst().orElse(null);
        return appendChangeFor(coupleId, entityType, entityId, operation, payload, audience);
    }

    @Transactional
    public long appendChangeFor(UUID coupleId, String entityType, UUID entityId,
                               String operation, String payload, UUID audience) {
        if (coupleId == null || entityType == null || entityType.isBlank()
                || entityId == null || operation == null || operation.isBlank()) {
            throw new SyncValidationException("change identity and operation are required");
        }
        jdbcTemplate.update("""
                INSERT INTO couple_sync_state(couple_id, last_space_sequence)
                VALUES (?, 0)
                ON CONFLICT (couple_id) DO NOTHING
                """, coupleId);
        Long sequence = jdbcTemplate.queryForObject("""
                UPDATE couple_sync_state
                SET last_space_sequence = last_space_sequence + 1
                WHERE couple_id = ?
                RETURNING last_space_sequence
                """, Long.class, coupleId);
        if (sequence == null) {
            throw new IllegalStateException("couple sync state row went missing");
        }
        jdbcTemplate.update("""
                INSERT INTO sync_change(couple_id, space_sequence, entity_type, entity_id,
                                        operation, payload, created_at, audience_user_id)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, now(), ?)
                """, coupleId, sequence, entityType, entityId, operation, payload, audience);
        return sequence;
    }

    /**
     * High-water sequence for the couple. Used when the mutation itself appended
     * the change row, so the wake-up notification still reports a real position.
     */
    public long lastSequence(UUID coupleId) {
        return jdbcTemplate.query("""
                SELECT last_space_sequence FROM couple_sync_state WHERE couple_id = ?
                """, (rs, rowNum) -> rs.getLong(1), coupleId)
                .stream().findFirst().orElse(0L);
    }

    public boolean isAuthorOnlyChange(UUID coupleId, long sequence) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT audience_user_id IS NOT NULL FROM sync_change WHERE couple_id=? AND space_sequence=?",
                Boolean.class, coupleId, sequence));
    }

    public ChangePage readChanges(UUID userId, UUID coupleId, long after, int limit) {
        accessPolicy.requireMember(userId, coupleId);
        return readChangesFor(userId, coupleId, after, limit);
    }

    public ChangePage readChanges(UUID coupleId, long after, int limit) {
        // Legacy internal callers receive only the shared projection.
        return readChangesFor(new UUID(0, 0), coupleId, after, limit);
    }

    private ChangePage readChangesFor(UUID userId, UUID coupleId, long after, int limit) {
        if (after < 0 || limit < 1 || limit > 200) {
            throw new SyncValidationException("after must be non-negative and limit must be between 1 and 200");
        }
        long highWater = lastSequence(coupleId);
        List<ChangeView> raw = jdbcTemplate.query("""
                SELECT s.space_sequence,
                       CASE WHEN e.id IS NOT NULL AND e.author_id<>? AND
                            (e.visibility='PRIVATE' OR (e.mode='PERSONAL' AND e.state='DRAFT') OR e.deleted_at IS NOT NULL)
                            THEN 'ENTRY' ELSE s.entity_type END AS entity_type,
                       s.entity_id,
                       CASE WHEN e.id IS NOT NULL AND e.author_id<>? AND
                            (e.visibility='PRIVATE' OR (e.mode='PERSONAL' AND e.state='DRAFT') OR e.deleted_at IS NOT NULL)
                            THEN 'DELETE' ELSE s.operation END AS operation,
                       CASE WHEN e.id IS NOT NULL AND e.author_id<>? AND
                            (e.visibility='PRIVATE' OR (e.mode='PERSONAL' AND e.state='DRAFT') OR e.deleted_at IS NOT NULL)
                            THEN jsonb_build_object('id', s.entity_id, 'deleted', true)::text
                            WHEN s.entity_type='ENTRY' AND jsonb_typeof(s.payload->'blocks')='array'
                            THEN jsonb_set(s.payload, '{blocks}', COALESCE((
                                SELECT jsonb_agg(
                                    CASE WHEN b.deleted_at IS NOT NULL OR historical.block->'deleted'='true'::jsonb
                                    THEN jsonb_build_object(
                                        'id', historical.block->'id',
                                        'type', historical.block->'type',
                                        'orderKey', historical.block->'orderKey',
                                        'updatedBy', historical.block->'updatedBy',
                                        'blockVersion', CASE WHEN b.deleted_at IS NOT NULL
                                            THEN to_jsonb(b.block_version) ELSE historical.block->'blockVersion' END,
                                        'payload', '{}'::text, 'assetId', NULL, 'deleted', true)
                                    ELSE historical.block END ORDER BY historical.position)
                                FROM jsonb_array_elements(s.payload->'blocks') WITH ORDINALITY AS historical(block, position)
                                LEFT JOIN entry_block b ON b.entry_id=s.entity_id AND b.id::text=historical.block->>'id'
                            ), '[]'::jsonb))::text
                            ELSE s.payload::text END AS payload,
                       s.created_at
                FROM sync_change s LEFT JOIN entry e ON e.id=s.entity_id AND s.entity_type IN ('ENTRY','COMMENT')
                WHERE s.couple_id = ? AND s.space_sequence > ?
                  AND s.space_sequence <= ?
                  AND (s.audience_user_id IS NULL OR s.audience_user_id=?)
                ORDER BY s.space_sequence
                LIMIT ?
                """, (rs, rowNum) -> new ChangeView(
                rs.getLong("space_sequence"),
                rs.getString("entity_type"),
                rs.getObject("entity_id", UUID.class),
                rs.getString("operation"),
                rs.getString("payload"),
                rs.getTimestamp("created_at").toInstant()), userId, userId, userId, coupleId, after, highWater, userId, limit + 1);
        boolean hasMore = raw.size() > limit;
        List<ChangeView> changes = hasMore ? raw.subList(0, limit) : raw;
        long next = hasMore ? changes.get(changes.size() - 1).sequence()
                : Math.max(after, highWater);
        return new ChangePage(List.copyOf(changes), next, hasMore);
    }
}
