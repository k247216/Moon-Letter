package com.twomemory.app.couple;

import com.twomemory.app.auth.SpaceAccessPolicy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CoupleService {

    private static final Duration PAIRING_CODE_LIFETIME = Duration.ofMinutes(15);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbcTemplate;
    private final SpaceAccessPolicy accessPolicy;

    public CoupleService(JdbcTemplate jdbcTemplate, SpaceAccessPolicy accessPolicy) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessPolicy = accessPolicy;
    }

    @Transactional
    public CreateSpaceResult createSpace(UUID ownerId) {
        accessPolicy.requireActiveUser(ownerId);
        Integer existing = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM couple_member cm
                JOIN couple_space cs ON cs.id = cm.couple_id
                WHERE cm.user_id = ? AND cm.left_at IS NULL AND cm.deleted_at IS NULL
                  AND cs.status IN ('ACTIVE', 'UNPAIRED') AND cs.deleted_at IS NULL
                """, Integer.class, ownerId);
        if (existing != null && existing > 0) {
            throw new ConflictException("user already belongs to a couple space");
        }

        UUID coupleId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO couple_space(id, status, created_at, updated_at)
                VALUES (?, 'UNPAIRED', now(), now())
                """, coupleId);
        jdbcTemplate.update("""
                INSERT INTO couple_member(couple_id, user_id, joined_at)
                VALUES (?, ?, now())
                """, coupleId, ownerId);
        ensureProfile(ownerId);

        String pairingCode = nextPairingCode();
        jdbcTemplate.update("""
                INSERT INTO space_pairing_code(id, couple_id, code_hash, expires_at)
                VALUES (?, ?, ?, ?)
                """, UUID.randomUUID(), coupleId, sha256(pairingCode),
                Instant.now().plus(PAIRING_CODE_LIFETIME));
        return new CreateSpaceResult(readSpace(ownerId, coupleId), pairingCode);
    }

    @Transactional
    public PairResult pair(UUID userId, String oneTimeCode) {
        accessPolicy.requireActiveUser(userId);
        String normalizedCode = normalizePairingCode(oneTimeCode);
        String codeHash = sha256(normalizedCode);
        PairingRow pairing = jdbcTemplate.query("""
                SELECT id, couple_id, expires_at, consumed_at
                FROM space_pairing_code
                WHERE code_hash = ?
                FOR UPDATE
                """, this::mapPairing, codeHash).stream().findFirst()
                .orElseThrow(() -> new ConflictException("pairing code is invalid or expired"));
        if (pairing.consumedAt() != null || pairing.expiresAt().isBefore(Instant.now())) {
            throw new ConflictException("pairing code is invalid or expired");
        }

        jdbcTemplate.queryForObject("SELECT id FROM couple_space WHERE id = ? FOR UPDATE",
                UUID.class, pairing.coupleId());
        Integer memberCount = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM couple_member
                WHERE couple_id = ? AND left_at IS NULL AND deleted_at IS NULL
                """, Integer.class, pairing.coupleId());
        if (memberCount != null && memberCount >= 2) {
            throw new ConflictException("couple space is full");
        }
        Integer alreadyPaired = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM couple_member cm
                JOIN couple_space cs ON cs.id = cm.couple_id
                WHERE cm.user_id = ? AND cm.left_at IS NULL AND cm.deleted_at IS NULL
                  AND cs.status IN ('ACTIVE', 'UNPAIRED') AND cs.deleted_at IS NULL
                """, Integer.class, userId);
        if (alreadyPaired != null && alreadyPaired > 0) {
            throw new ConflictException("user already belongs to a couple space");
        }

        jdbcTemplate.update("""
                INSERT INTO couple_member(couple_id, user_id, joined_at)
                VALUES (?, ?, now())
                """, pairing.coupleId(), userId);
        jdbcTemplate.update("UPDATE couple_space SET status = 'ACTIVE', updated_at = now() WHERE id = ?",
                pairing.coupleId());
        jdbcTemplate.update("UPDATE space_pairing_code SET consumed_at = now() WHERE id = ?", pairing.id());
        ensureProfile(userId);
        return new PairResult(readSpace(userId, pairing.coupleId()));
    }

    public CoupleView readSpace(UUID userId, UUID coupleId) {
        accessPolicy.requireMember(userId, coupleId);
        CoupleView couple = jdbcTemplate.query("""
                SELECT cs.id, cs.status, cm.user_id,
                       COALESCE(up.display_name, '未命名') AS display_name,
                       up.avatar_asset_id, COALESCE(up.theme::text, 'WARM_BEIGE') AS theme
                FROM couple_space cs
                JOIN couple_member cm ON cm.couple_id = cs.id
                    AND cm.left_at IS NULL AND cm.deleted_at IS NULL
                LEFT JOIN user_profile up ON up.user_id = cm.user_id
                WHERE cs.id = ? AND cs.deleted_at IS NULL
                ORDER BY cm.joined_at, cm.user_id
                """, (org.springframework.jdbc.core.ResultSetExtractor<CoupleView>) this::mapCouple, coupleId);
        if (couple == null) {
            throw new ConflictException("couple space not found");
        }
        return couple;
    }

    @Transactional
    public ProfileView updateProfile(UUID actorId, UUID coupleId, UUID targetUserId,
                                     UpdateProfileRequest request) {
        accessPolicy.requireMember(actorId, coupleId);
        if (!actorId.equals(targetUserId)) {
            throw new AccessDeniedException("member may only update own profile");
        }
        String displayName = normalizeDisplayName(request.displayName());
        ThemeKind theme = parseTheme(request.theme());
        if (request.avatarAssetId() != null) {
            Integer ownedAsset = jdbcTemplate.queryForObject("""
                    SELECT count(*) FROM media_asset
                    WHERE id = ? AND couple_id = ? AND owner_id = ? AND deleted_at IS NULL
                    """, Integer.class, request.avatarAssetId(), coupleId, actorId);
            if (ownedAsset == null || ownedAsset != 1) {
                throw new AccessDeniedException("avatar does not belong to this space");
            }
        }
        ensureProfile(targetUserId);
        jdbcTemplate.update("""
                UPDATE user_profile
                SET display_name = ?, avatar_asset_id = ?, theme = ?::theme_kind, updated_at = now()
                WHERE user_id = ?
                """, displayName, request.avatarAssetId(), theme.name(), targetUserId);
        return jdbcTemplate.queryForObject("""
                SELECT user_id, display_name, avatar_asset_id, theme::text AS theme
                FROM user_profile WHERE user_id = ?
                """, (rs, rowNum) -> new ProfileView(
                rs.getObject("user_id", UUID.class),
                rs.getString("display_name"),
                rs.getObject("avatar_asset_id", UUID.class),
                ThemeKind.valueOf(rs.getString("theme"))), targetUserId);
    }

    static String normalizeDisplayName(String raw) {
        if (raw == null) {
            throw new ValidationException("display name is required");
        }
        String normalized = raw.trim();
        long codePoints = normalized.codePoints().count();
        if (codePoints < 1 || codePoints > 24) {
            throw new ValidationException("display name must contain 1 to 24 Unicode code points");
        }
        return normalized;
    }

    private static ThemeKind parseTheme(String raw) {
        if (raw == null) {
            throw new ValidationException("theme is required");
        }
        try {
            return ThemeKind.valueOf(raw.trim());
        } catch (IllegalArgumentException exception) {
            throw new ValidationException("theme must be WARM_BEIGE or PURE_WHITE");
        }
    }

    private static String normalizePairingCode(String raw) {
        if (raw == null || !raw.trim().matches("[0-9]{6}")) {
            throw new ValidationException("pairing code must contain six digits");
        }
        return raw.trim();
    }

    private void ensureProfile(UUID userId) {
        jdbcTemplate.update("""
                INSERT INTO user_profile(user_id, display_name, theme)
                VALUES (?, '未命名', 'WARM_BEIGE')
                ON CONFLICT (user_id) DO NOTHING
                """, userId);
    }

    private String nextPairingCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM space_pairing_code WHERE code_hash = ? AND consumed_at IS NULL",
                    Integer.class, sha256(code));
            if (count == null || count == 0) {
                return code;
            }
        }
        throw new ConflictException("could not allocate pairing code");
    }

    private CoupleView mapCouple(ResultSet rs) throws SQLException {
        UUID coupleId = rs.getObject("id", UUID.class);
        SpaceStatus status = SpaceStatus.valueOf(rs.getString("status"));
        List<MemberView> members = new ArrayList<>();
        do {
            UUID userId = rs.getObject("user_id", UUID.class);
            members.add(new MemberView(userId, new ProfileView(
                    userId,
                    rs.getString("display_name"),
                    rs.getObject("avatar_asset_id", UUID.class),
                    ThemeKind.valueOf(rs.getString("theme")))));
        } while (rs.next());
        return new CoupleView(coupleId, status, List.copyOf(members));
    }

    private PairingRow mapPairing(ResultSet rs, int rowNum) throws SQLException {
        return new PairingRow(
                rs.getObject("id", UUID.class),
                rs.getObject("couple_id", UUID.class),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("consumed_at") == null ? null : rs.getTimestamp("consumed_at").toInstant());
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte item : digest) {
                hex.append("%02x".formatted(item));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private record PairingRow(UUID id, UUID coupleId, Instant expiresAt, Instant consumedAt) {
    }
}
