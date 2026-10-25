package com.twomemory.app.occasion;

import com.twomemory.app.auth.SpaceAccessPolicy;
import com.twomemory.app.media.MediaService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@Service
public class OccasionService {
    private final JdbcTemplate db;
    private final SpaceAccessPolicy access;
    private final MediaService media;

    public OccasionService(JdbcTemplate db, SpaceAccessPolicy access, MediaService media) {
        this.db = db; this.access = access; this.media = media;
    }

    public List<OccasionView> list(UUID actor, UUID couple) {
        requireMember(actor, couple);
        return db.query("""
                SELECT * FROM occasion WHERE couple_id=? AND deleted_at IS NULL
                  AND (visibility='SHARED' OR author_id=?)
                  AND (kind <> 'SURPRISE' OR author_id=? OR
                       (recipient_id=? AND date <= (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Shanghai')::date))
                ORDER BY date, id
                """, this::map, couple, actor, actor, actor);
    }

    @Transactional
    public OccasionView save(UUID actor, UUID couple, UUID id, SaveOccasionRequest request) {
        requireMember(actor, couple);
        Normalized value = normalize(request);
        lockSpace(couple);
        // Inspect and authorize the original kind BEFORE accepting a replacement kind.
        Stored existing = find(id);
        if (existing != null) authorizeEdit(actor, couple, existing, value.kind());
        String visibility = request.visibility() == null
                ? (existing == null ? "SHARED" : existing.view().visibility()) : request.visibility();
        if (!List.of("SHARED", "PRIVATE").contains(visibility)) throw error(HttpStatus.BAD_REQUEST, "invalid visibility");
        if (existing != null && !existing.view().authorId().equals(actor) && !existing.view().visibility().equals(visibility))
            throw error(HttpStatus.FORBIDDEN, "only the author may change visibility");
        if (value.kind() == OccasionKind.SURPRISE) {
            UUID author = existing == null ? actor : existing.view().authorId();
            if ("SHARED".equals(visibility) && request.recipientId() == null)
                throw error(HttpStatus.BAD_REQUEST, "shared surprise requires the active other recipient");
            if (request.recipientId() != null) {
            Integer recipients = db.queryForObject("""
                    SELECT count(*) FROM couple_member m JOIN app_user u ON u.id=m.user_id
                    WHERE m.couple_id=? AND m.user_id=? AND m.user_id<>?
                      AND m.left_at IS NULL AND m.deleted_at IS NULL AND u.status='ACTIVE'
                    """, Integer.class, couple, request.recipientId(), author);
            if (recipients == null || recipients != 1)
                throw error(HttpStatus.BAD_REQUEST, "surprise recipient must be the active other member");
            }
        } else if (request.recipientId() != null) {
            throw error(HttpStatus.BAD_REQUEST, "shared occasions do not have a recipient");
        }
        if (request.photoAssetId() != null) {
            Integer ready = db.queryForObject("""
                    SELECT count(*) FROM media_asset WHERE id=? AND couple_id=?
                      AND kind='IMAGE' AND status='READY' AND deleted_at IS NULL
                    """, Integer.class, request.photoAssetId(), couple);
            if (ready == null || ready != 1)
                throw error(HttpStatus.CONFLICT, "photo must be a READY image in this space");
            // A partner's private upload is not made readable merely by guessing its UUID.
            try { media.readAsset(actor, request.photoAssetId()); }
            catch (RuntimeException exception) {
                if (exception instanceof org.springframework.dao.DataAccessException) throw exception;
                throw error(HttpStatus.FORBIDDEN, "photo is not readable");
            }
        }
        if (existing == null) {
            db.update("""
                    INSERT INTO occasion(id,couple_id,author_id,kind,title,body,date,repeats_yearly,recipient_id,photo_asset_id,visibility)
                    VALUES (?,?,?,?,?,?,?::date,?,?,?,?) ON CONFLICT (id) DO NOTHING
                    """, id, couple, actor, value.kind().name(), value.title(), value.body(), value.date().toString(),
                    value.repeatsYearly(), request.recipientId(), request.photoAssetId(), visibility);
            // Concurrent insertion in another space can win the UUID; never overwrite it.
            existing = find(id);
            authorizeEdit(actor, couple, existing, value.kind());
        }
        db.update("""
                UPDATE occasion SET kind=?,title=?,body=?,date=?::date,repeats_yearly=?,
                    recipient_id=?,photo_asset_id=?,visibility=?,updated_at=now()
                WHERE id=? AND couple_id=? AND deleted_at IS NULL
                  AND (kind,title,body,date,repeats_yearly,recipient_id,photo_asset_id,visibility)
                      IS DISTINCT FROM (?, ?, ?, ?::date, ?, ?::uuid, ?::uuid, ?)
                """, value.kind().name(), value.title(), value.body(), value.date().toString(), value.repeatsYearly(),
                request.recipientId(), request.photoAssetId(), visibility, id, couple, value.kind().name(), value.title(),
                value.body(), value.date().toString(), value.repeatsYearly(), request.recipientId(), request.photoAssetId(), visibility);
        return find(id).view();
    }

    @Transactional
    public void delete(UUID actor, UUID couple, UUID id) {
        requireMember(actor, couple);
        lockSpace(couple);
        Stored existing = find(id);
        if (existing == null) throw error(HttpStatus.NOT_FOUND, "occasion not found");
        if (!existing.view().coupleId().equals(couple)) throw error(HttpStatus.FORBIDDEN, "occasion belongs to another space");
        if ((existing.view().kind() == OccasionKind.SURPRISE || "PRIVATE".equals(existing.view().visibility())) && !existing.view().authorId().equals(actor))
            throw error(HttpStatus.FORBIDDEN, "only the surprise author may withdraw it");
        db.update("UPDATE occasion SET deleted_at=now(),updated_at=now() WHERE id=? AND deleted_at IS NULL", id);
    }

    private void requireMember(UUID actor, UUID couple) {
        access.requireActiveUser(actor);
        access.requireMember(actor, couple);
    }
    private void lockSpace(UUID couple) {
        db.queryForObject("SELECT id FROM couple_space WHERE id=? FOR UPDATE", UUID.class, couple);
    }
    private Stored find(UUID id) {
        return db.query("SELECT * FROM occasion WHERE id=? FOR UPDATE",
                (rs, row) -> new Stored(map(rs, row), rs.getTimestamp("deleted_at") != null), id)
                .stream().findFirst().orElse(null);
    }
    private void authorizeEdit(UUID actor, UUID couple, Stored existing, OccasionKind replacement) {
        var card = existing.view();
        if (!card.coupleId().equals(couple)) throw error(HttpStatus.FORBIDDEN, "occasion belongs to another space");
        if ((card.kind() == OccasionKind.SURPRISE || replacement == OccasionKind.SURPRISE || "PRIVATE".equals(card.visibility())) && !card.authorId().equals(actor))
            throw error(HttpStatus.FORBIDDEN, "only the author may edit a surprise");
        if (existing.deleted()) throw error(HttpStatus.GONE, "occasion was deleted");
    }
    private Normalized normalize(SaveOccasionRequest request) {
        if (request == null) throw error(HttpStatus.BAD_REQUEST, "occasion is required");
        OccasionKind kind;
        try { kind = OccasionKind.valueOf(request.kind()); }
        catch (IllegalArgumentException | NullPointerException exception) { throw error(HttpStatus.BAD_REQUEST, "invalid occasion kind"); }
        String title = request.title() == null ? "" : request.title().trim();
        String body = request.body() == null ? "" : request.body();
        if (title.isEmpty() || title.codePointCount(0, title.length()) > 80 || body.codePointCount(0, body.length()) > 4000)
            throw error(HttpStatus.BAD_REQUEST, "title must contain 1..80 and body 0..4000 characters");
        LocalDate date;
        try {
            if (request.date() == null || !request.date().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
                throw new DateTimeParseException("ISO date required", "", 0);
            date = LocalDate.parse(request.date());
            if (date.getYear() < 1) throw new DateTimeParseException("positive year required", request.date(), 0);
        } catch (DateTimeParseException exception) { throw error(HttpStatus.BAD_REQUEST, "invalid Gregorian ISO date"); }
        return new Normalized(kind, title, body, date,
                request.repeatsYearly() == null ? kind == OccasionKind.BIRTHDAY : request.repeatsYearly());
    }
    private OccasionView map(ResultSet rs, int row) throws SQLException {
        return new OccasionView(rs.getObject("id", UUID.class), rs.getObject("couple_id", UUID.class),
                rs.getObject("author_id", UUID.class), OccasionKind.valueOf(rs.getString("kind")), rs.getString("title"),
                rs.getString("body"), rs.getObject("date", LocalDate.class), rs.getBoolean("repeats_yearly"),
                rs.getObject("recipient_id", UUID.class), rs.getObject("photo_asset_id", UUID.class), rs.getTimestamp("updated_at").toInstant(), rs.getString("visibility"));
    }
    private static ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
    private record Normalized(OccasionKind kind, String title, String body, LocalDate date, boolean repeatsYearly) { }
    private record Stored(OccasionView view, boolean deleted) { }
}
