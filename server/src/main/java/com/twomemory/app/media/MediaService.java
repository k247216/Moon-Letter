package com.twomemory.app.media;

import com.twomemory.app.auth.SpaceAccessPolicy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
public class MediaService {

    private static final long MAX_IMAGE_BYTES = 20L * 1024 * 1024;
    private static final Duration UPLOAD_TICKET_LIFETIME = Duration.ofMinutes(15);

    private final JdbcTemplate jdbcTemplate;
    private final SpaceAccessPolicy accessPolicy;
    private final ObjectStorage objectStorage;

    public MediaService(JdbcTemplate jdbcTemplate, SpaceAccessPolicy accessPolicy,
                        ObjectStorage objectStorage) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessPolicy = accessPolicy;
        this.objectStorage = objectStorage;
    }

    @Transactional
    public UploadTicket createUpload(CreateMediaCommand command) {
        validate(command);
        accessPolicy.requireMember(command.ownerId(), command.coupleId());
        List<MediaAssetView> existing = jdbcTemplate.query("""
                SELECT id, couple_id, owner_id, kind::text AS kind, status::text AS status,
                       object_key, mime_type, byte_size, width, height, duration_ms,
                       sha256, NULL AS failure_code
                FROM media_asset WHERE operation_id = ? AND deleted_at IS NULL
                """, this::mapAsset, command.operationId());
        if (!existing.isEmpty()) {
            MediaAssetView asset = existing.get(0);
            ObjectStorage.PresignedUpload presigned = objectStorage.presignPut(
                    asset.objectKey(), asset.mimeType(), asset.byteSize(), Instant.now().plus(UPLOAD_TICKET_LIFETIME));
            return new UploadTicket(asset.id(), asset.objectKey(), presigned.url(), presigned.expiresAt(), asset);
        }

        UUID assetId = UUID.randomUUID();
        String objectKey = objectKey(command.coupleId(), assetId);
        jdbcTemplate.update("""
                INSERT INTO media_asset(id, couple_id, owner_id, kind, status, object_key,
                                        mime_type, byte_size, sha256, width, height, duration_ms,
                                        operation_id, created_at, updated_at)
                VALUES (?, ?, ?, ?::media_kind, 'UPLOADING', ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                """, assetId, command.coupleId(), command.ownerId(), command.kind().name(), objectKey,
                command.mimeType(), command.byteSize(), command.sha256(), command.width(), command.height(),
                command.durationMs(), command.operationId());
        Instant expiresAt = Instant.now().plus(UPLOAD_TICKET_LIFETIME);
        ObjectStorage.PresignedUpload presigned = objectStorage.presignPut(
                objectKey, command.mimeType(), command.byteSize(), expiresAt);
        return new UploadTicket(assetId, objectKey, presigned.url(), presigned.expiresAt(),
                new MediaAssetView(assetId, command.coupleId(), command.ownerId(), command.kind(),
                        MediaStatus.UPLOADING, objectKey, command.mimeType(), command.byteSize(),
                        command.width(), command.height(), command.durationMs(), command.sha256(), null));
    }

    @Transactional
    public MediaAssetView completeUpload(UUID userId, UUID assetId, String sha256) {
        return completeUploadInternal(userId, assetId, sha256);
    }

    @Transactional
    public MediaAssetView markFailed(UUID userId, UUID assetId, String code) {
        return markFailedInternal(userId, assetId, code);
    }

    /** The asset row for authorization checks on raw data transfer. */
    public MediaAssetView readAsset(UUID userId, UUID assetId) {
        MediaAssetView asset = jdbcTemplate.queryForObject("""
                SELECT id, couple_id, owner_id, kind::text AS kind, status::text AS status,
                       object_key, mime_type, byte_size, width, height, duration_ms,
                       sha256, NULL AS failure_code
                FROM media_asset WHERE id = ? AND deleted_at IS NULL
                """, this::mapAsset, assetId);
        accessPolicy.requireMember(userId, asset.coupleId());
        return asset;
    }

    public void requireReady(UUID coupleId, Collection<UUID> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return;
        }
        List<UUID> distinctIds = assetIds.stream().filter(id -> id != null).distinct().toList();
        String placeholders = String.join(",", java.util.Collections.nCopies(distinctIds.size(), "?"));
        Object[] params = new Object[distinctIds.size() + 1];
        params[0] = coupleId;
        for (int i = 0; i < distinctIds.size(); i++) {
            params[i + 1] = distinctIds.get(i);
        }
        String sql = "SELECT count(*) FROM media_asset "
                + "WHERE couple_id = ? AND id IN (" + placeholders + ") "
                + "AND status = 'READY' AND deleted_at IS NULL";
        Integer ready = jdbcTemplate.queryForObject(sql, Integer.class, params);
        if (ready == null || ready != distinctIds.size()) {
            throw new MediaNotReadyException("all referenced media must be READY");
        }
    }

    static void validate(CreateMediaCommand command) {
        if (command == null || command.ownerId() == null || command.coupleId() == null
                || command.kind() == null || command.operationId() == null) {
            throw new MediaValidationException("media owner, space, kind and operation are required");
        }
        if (command.kind() != MediaKind.IMAGE) {
            throw new MediaValidationException("M1 only accepts image uploads");
        }
        if (!List.of("image/jpeg", "image/png", "image/webp").contains(command.mimeType())) {
            throw new MediaValidationException("image MIME type is not supported");
        }
        if (command.byteSize() <= 0 || command.byteSize() > MAX_IMAGE_BYTES) {
            throw new MediaValidationException("image must be greater than 0 and at most 20 MiB");
        }
        if (command.width() == null || command.height() == null
                || command.width() <= 0 || command.height() <= 0) {
            throw new MediaValidationException("image dimensions must be positive");
        }
        if (command.sha256() == null || !command.sha256().matches("[0-9a-fA-F]{64}")) {
            throw new MediaValidationException("sha256 must be 64 hexadecimal characters");
        }
    }

    static String objectKey(UUID coupleId, UUID assetId) {
        return "couples/%s/assets/%s".formatted(coupleId, assetId);
    }

    private MediaAssetView completeUploadInternal(UUID userId, UUID assetId, String sha256) {
        MediaAssetView asset = jdbcTemplate.queryForObject("""
                SELECT id, couple_id, owner_id, kind::text AS kind, status::text AS status,
                       object_key, mime_type, byte_size, width, height, duration_ms,
                       sha256, NULL AS failure_code
                FROM media_asset WHERE id = ? AND deleted_at IS NULL FOR UPDATE
                """, this::mapAsset, assetId);
        accessPolicy.requireMember(userId, asset.coupleId());
        if (!userId.equals(asset.ownerId())) {
            throw new MediaAccessDeniedException("only the uploader may complete this asset");
        }
        if (!asset.sha256().equalsIgnoreCase(sha256)) {
            markFailedInternal(userId, assetId, "SHA256_MISMATCH");
            throw new MediaValidationException("uploaded object checksum does not match");
        }
        ObjectStorage.ObjectMetadata metadata = objectStorage.head(asset.objectKey());
        if (!asset.sha256().equalsIgnoreCase(metadata.sha256()) || metadata.byteSize() != asset.byteSize()) {
            markFailedInternal(userId, assetId, "OBJECT_METADATA_MISMATCH");
            throw new MediaValidationException("uploaded object metadata does not match");
        }
        jdbcTemplate.update("UPDATE media_asset SET status = 'PROCESSING', updated_at = now() WHERE id = ?", assetId);
        jdbcTemplate.update("UPDATE media_asset SET status = 'READY', updated_at = now() WHERE id = ?", assetId);
        return jdbcTemplate.queryForObject("""
                SELECT id, couple_id, owner_id, kind::text AS kind, status::text AS status,
                       object_key, mime_type, byte_size, width, height, duration_ms,
                       sha256, NULL AS failure_code
                FROM media_asset WHERE id = ?
                """, this::mapAsset, assetId);
    }

    private MediaAssetView markFailedInternal(UUID userId, UUID assetId, String code) {
        MediaAssetView asset = jdbcTemplate.queryForObject("""
                SELECT id, couple_id, owner_id, kind::text AS kind, status::text AS status,
                       object_key, mime_type, byte_size, width, height, duration_ms,
                       sha256, ? AS failure_code
                FROM media_asset WHERE id = ? AND deleted_at IS NULL
                """, this::mapAsset, code, assetId);
        accessPolicy.requireMember(userId, asset.coupleId());
        if (!userId.equals(asset.ownerId())) {
            throw new MediaAccessDeniedException("only the uploader may mark this asset failed");
        }
        jdbcTemplate.update("UPDATE media_asset SET status = 'FAILED', updated_at = now() WHERE id = ?", assetId);
        return new MediaAssetView(asset.id(), asset.coupleId(), asset.ownerId(), asset.kind(),
                MediaStatus.FAILED, asset.objectKey(), asset.mimeType(), asset.byteSize(), asset.width(),
                asset.height(), asset.durationMs(), asset.sha256(), code);
    }

    private MediaAssetView mapAsset(ResultSet rs, int rowNum) throws SQLException {
        return new MediaAssetView(
                rs.getObject("id", UUID.class), rs.getObject("couple_id", UUID.class),
                rs.getObject("owner_id", UUID.class), MediaKind.valueOf(rs.getString("kind")),
                MediaStatus.valueOf(rs.getString("status")), rs.getString("object_key"),
                rs.getString("mime_type"), rs.getLong("byte_size"),
                (Integer) rs.getObject("width"), (Integer) rs.getObject("height"),
                (Long) rs.getObject("duration_ms"), rs.getString("sha256"),
                rs.getString("failure_code"));
    }
}
