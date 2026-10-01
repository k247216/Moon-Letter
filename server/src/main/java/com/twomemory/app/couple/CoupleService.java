package com.twomemory.app.couple;

import com.twomemory.app.auth.DeviceSessionService;
import com.twomemory.app.auth.SpaceAccessPolicy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CoupleService {

    private static final Duration PAIRING_TOKEN_LIFETIME = Duration.ofMinutes(15);
    private static final String PLACEHOLDER_DISPLAY_NAME = "未命名";
    private static final int SPACE_MEMBER_CAPACITY = 2;

    /** Reopens an existing member's slot; invites someone the space does not have. */
    static final String TOKEN_KIND_REJOIN = "REJOIN";
    static final String TOKEN_KIND_INVITE = "INVITE";

    private final JdbcTemplate jdbcTemplate;
    private final SpaceAccessPolicy accessPolicy;
    private final DeviceSessionService deviceSessionService;

    public CoupleService(JdbcTemplate jdbcTemplate, SpaceAccessPolicy accessPolicy,
                         DeviceSessionService deviceSessionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessPolicy = accessPolicy;
        this.deviceSessionService = deviceSessionService;
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

        return new CreateSpaceResult(readSpace(ownerId, coupleId),
                issueOutstandingToken(coupleId, null), TOKEN_KIND_INVITE);
    }

    /**
     * Consumes a one-time pairing token. A plain token creates the second
     * member, its profile and its first device session; a token that names a
     * member slot reopens that slot, because the phone holding it is the same
     * person who already wrote records under that id. The raw session token is
     * returned once either way. displayName is what she types for herself on
     * her own device, blank keeps the placeholder for callers that pair without
     * a name.
     */
    @Transactional
    public PairResult pair(String pairingToken, String displayName) {
        String normalized = normalizePairingToken(pairingToken);
        String tokenHash = sha256(normalized);
        PairingRow pairing = jdbcTemplate.query("""
                SELECT id, couple_id, expires_at, consumed_at, rejoin_user_id
                FROM space_pairing_code
                WHERE token_hash = ?
                FOR UPDATE
                """, this::mapPairing, tokenHash).stream().findFirst()
                .orElseThrow(() -> new ConflictException("pairing token is invalid or expired"));
        if (pairing.consumedAt() != null || pairing.expiresAt().isBefore(Instant.now())) {
            throw new ConflictException("pairing token is invalid or expired");
        }

        jdbcTemplate.queryForObject("SELECT id FROM couple_space WHERE id = ? FOR UPDATE",
                UUID.class, pairing.coupleId());
        if (pairing.rejoinUserId() != null) {
            return consumeRejoinToken(pairing);
        }

        if (activeMembers(pairing.coupleId()).size() >= SPACE_MEMBER_CAPACITY) {
            throw new ConflictException("couple space is full");
        }

        UUID partnerId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO app_user (id, status) VALUES (?, 'ACTIVE')", partnerId);
        jdbcTemplate.update("""
                INSERT INTO couple_member(couple_id, user_id, joined_at)
                VALUES (?, ?, now())
                """, pairing.coupleId(), partnerId);
        jdbcTemplate.update("UPDATE couple_space SET status = 'ACTIVE', updated_at = now() WHERE id = ?",
                pairing.coupleId());
        markConsumed(pairing.id());
        ensureProfile(partnerId, displayName);
        String deviceToken = deviceSessionService.issueSession(partnerId, pairing.coupleId());
        return new PairResult(readSpace(partnerId, pairing.coupleId()), deviceToken, partnerId);
    }

    /**
     * A lost or reinstalled phone coming back as the member it already was.
     * Only that member's sessions are revoked, so recovering one device never
     * logs the other one out, and the stored display name is left alone: the
     * name the other phone shows was chosen once, not re-declared per install.
     */
    private PairResult consumeRejoinToken(PairingRow pairing) {
        UUID memberId = pairing.rejoinUserId();
        boolean activeMember = activeMembers(pairing.coupleId()).contains(memberId);
        if (!activeMember) {
            throw new ConflictException("pairing token no longer matches a member of this space");
        }
        markConsumed(pairing.id());
        String deviceToken = deviceSessionService.issueReplacementSession(memberId, pairing.coupleId());
        return new PairResult(readSpace(memberId, pairing.coupleId()), deviceToken, memberId);
    }

    /**
     * Replaces an outstanding pairing token with a fresh one and says what that
     * token is for. A space with a free slot issues an invitation; a full space
     * has nobody left to invite, so its token reopens the other member's slot —
     * which is exactly what a phone that was uninstalled or lost needs.
     */
    @Transactional
    public CreateSpaceResult regeneratePairingToken(UUID actorId, UUID coupleId) {
        accessPolicy.requireMember(actorId, coupleId);
        jdbcTemplate.queryForObject("SELECT id FROM couple_space WHERE id = ? FOR UPDATE",
                UUID.class, coupleId);
        List<UUID> members = activeMembers(coupleId);
        UUID rejoinTarget = members.size() < SPACE_MEMBER_CAPACITY
                ? null
                : members.stream().filter(member -> !member.equals(actorId)).findFirst()
                        .orElseThrow(() -> new ConflictException("no other member to rejoin"));
        String pairingToken = issueOutstandingToken(coupleId, rejoinTarget);
        return new CreateSpaceResult(readSpace(actorId, coupleId), pairingToken,
                rejoinTarget == null ? TOKEN_KIND_INVITE : TOKEN_KIND_REJOIN);
    }

    public CoupleView readSpace(UUID userId, UUID coupleId) {
        accessPolicy.requireMember(userId, coupleId);
        CoupleView couple = jdbcTemplate.query("""
                SELECT cs.id, cs.status, cm.user_id,
                       COALESCE(up.display_name, ?::text) AS display_name,
                       up.avatar_asset_id, COALESCE(up.theme::text, 'WARM_BEIGE') AS theme
                FROM couple_space cs
                JOIN couple_member cm ON cm.couple_id = cs.id
                    AND cm.left_at IS NULL AND cm.deleted_at IS NULL
                LEFT JOIN user_profile up ON up.user_id = cm.user_id
                WHERE cs.id = ? AND cs.deleted_at IS NULL
                ORDER BY cm.joined_at, cm.user_id
                """, (org.springframework.jdbc.core.ResultSetExtractor<CoupleView>) this::mapCouple,
                PLACEHOLDER_DISPLAY_NAME, coupleId);
        if (couple == null) {
            throw new ConflictException("couple space not found");
        }
        return couple;
    }

    /**
     * Partial update of the caller's own profile: a field absent from the
     * request keeps its stored value, so renaming never has to resend (and can
     * never silently reset) the theme or avatar.
     */
    @Transactional
    public ProfileView updateProfile(UUID actorId, UUID coupleId, UUID targetUserId,
                                     UpdateProfileRequest request) {
        accessPolicy.requireMember(actorId, coupleId);
        if (!actorId.equals(targetUserId)) {
            throw new AccessDeniedException("member may only update own profile");
        }
        String displayName = request.displayName() == null ? null : normalizeDisplayName(request.displayName());
        ThemeKind theme = request.theme() == null ? null : parseTheme(request.theme());
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
                SET display_name = COALESCE(?, display_name),
                    avatar_asset_id = COALESCE(?, avatar_asset_id),
                    theme = COALESCE(?::theme_kind, theme),
                    updated_at = now()
                WHERE user_id = ?
                """, displayName, request.avatarAssetId(),
                theme == null ? null : theme.name(), targetUserId);
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
        if (codePoints < 1 || codePoints > 40) {
            throw new ValidationException("display name must contain 1 to 40 Unicode code points");
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

    private static String normalizePairingToken(String raw) {
        if (raw == null || !raw.trim().matches("[A-Za-z0-9_-]{43}")) {
            throw new ValidationException("pairing token format is invalid");
        }
        return raw.trim();
    }

    private void ensureProfile(UUID userId) {
        ensureProfile(userId, null);
    }

    /**
     * Creates the profile row if absent. A device that pairs without naming
     * itself falls back to the placeholder, which is the one string the app
     * must never show as someone's name.
     */
    private void ensureProfile(UUID userId, String displayName) {
        String name = displayName == null || displayName.isBlank()
                ? PLACEHOLDER_DISPLAY_NAME
                : normalizeDisplayName(displayName);
        jdbcTemplate.update("""
                INSERT INTO user_profile(user_id, display_name, theme)
                VALUES (?, ?, 'WARM_BEIGE')
                ON CONFLICT (user_id) DO NOTHING
                """, userId, name);
    }

    /**
     * Issues the space's one outstanding token, revoking whatever was still
     * waiting to be used: an old code left live beside a new one is a way for a
     * superseded invitation to kick a member off later.
     */
    private String issueOutstandingToken(UUID coupleId, UUID rejoinUserId) {
        jdbcTemplate.update("""
                UPDATE space_pairing_code SET consumed_at = now()
                WHERE couple_id = ? AND consumed_at IS NULL
                """, coupleId);
        String token = deviceSessionService.generateToken();
        jdbcTemplate.update("""
                INSERT INTO space_pairing_code(id, couple_id, token_hash, expires_at, rejoin_user_id)
                VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), coupleId, sha256(token),
                java.sql.Timestamp.from(Instant.now().plus(PAIRING_TOKEN_LIFETIME)), rejoinUserId);
        return token;
    }

    private void markConsumed(UUID pairingId) {
        jdbcTemplate.update("UPDATE space_pairing_code SET consumed_at = now() WHERE id = ?", pairingId);
    }

    private List<UUID> activeMembers(UUID coupleId) {
        return jdbcTemplate.queryForList("""
                SELECT user_id FROM couple_member
                WHERE couple_id = ? AND left_at IS NULL AND deleted_at IS NULL
                ORDER BY joined_at, user_id
                """, UUID.class, coupleId);
    }

    private CoupleView mapCouple(ResultSet rs) throws SQLException {
        if (!rs.next()) {
            return null;
        }
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
                rs.getTimestamp("consumed_at") == null ? null : rs.getTimestamp("consumed_at").toInstant(),
                rs.getObject("rejoin_user_id", UUID.class));
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

    private record PairingRow(UUID id, UUID coupleId, Instant expiresAt, Instant consumedAt, UUID rejoinUserId) {
    }
}
