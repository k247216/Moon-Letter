package com.twomemory.app.sync;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class IdempotencyService {

    private final JdbcTemplate jdbcTemplate;

    public IdempotencyService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public MutationResult executeOnce(UUID operationId, UUID userId, String payloadHash,
                                      Supplier<MutationResult> mutation) {
        if (operationId == null || userId == null) {
            throw new SyncValidationException("operation id and user are required");
        }
        String normalizedHash = normalizeHash(payloadHash);
        int inserted = jdbcTemplate.update("""
                INSERT INTO idempotency_record(operation_id, user_id, payload_hash,
                                               response_status, response_body, created_at)
                VALUES (?, ?, ?, 0, '{}'::jsonb, now())
                ON CONFLICT (operation_id) DO NOTHING
                """, operationId, userId, normalizedHash);
        if (inserted == 0) {
            ExistingOperation existing = jdbcTemplate.queryForObject("""
                    SELECT user_id, payload_hash, response_status, response_body::text AS response_body
                    FROM idempotency_record WHERE operation_id = ?
                    """, (rs, rowNum) -> new ExistingOperation(
                    rs.getObject("user_id", UUID.class),
                    rs.getString("payload_hash"),
                    rs.getInt("response_status"),
                    rs.getString("response_body")), operationId);
            if (!userId.equals(existing.userId())
                    || !normalizedHash.equalsIgnoreCase(existing.payloadHash())) {
                throw new SyncConflictException("operation id was used with another payload");
            }
            return new MutationResult(existing.status(), existing.body(), true);
        }

        MutationResult result = mutation.get();
        if (result == null || result.body() == null || result.body().isBlank()) {
            throw new SyncValidationException("mutation must return a JSON body");
        }
        if (!isJson(result.body())) {
            throw new SyncValidationException("mutation body must be valid JSON");
        }
        jdbcTemplate.update("""
                UPDATE idempotency_record
                SET response_status = ?, response_body = ?::jsonb
                WHERE operation_id = ?
                """, result.status(), result.body(), operationId);
        return new MutationResult(result.status(), result.body(), false);
    }

    static String normalizeHash(String payloadHash) {
        if (payloadHash == null || !payloadHash.matches("[0-9a-fA-F]{64}")) {
            throw new SyncValidationException("payload hash must be 64 hexadecimal characters");
        }
        return payloadHash.toLowerCase(Locale.ROOT);
    }

    private static boolean isJson(String body) {
        String value = body.trim();
        return (value.startsWith("{") && value.endsWith("}"))
                || (value.startsWith("[") && value.endsWith("]"));
    }

    private record ExistingOperation(UUID userId, String payloadHash, int status, String body) {
    }
}
