package com.twomemory.app.capsule;

import com.twomemory.app.auth.SpaceAccessPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@Service
public class CapsuleService {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate db;
    private final SpaceAccessPolicy access;
    private final Clock clock;

    @Autowired
    public CapsuleService(JdbcTemplate db, SpaceAccessPolicy access) {
        this(db, access, Clock.systemUTC());
    }

    // Clock seam keeps server-date tests deterministic without changing global configuration.
    CapsuleService(JdbcTemplate db, SpaceAccessPolicy access, Clock clock) {
        this.db = db; this.access = access; this.clock = clock;
    }

    public List<CapsuleView> list(UUID actor, UUID couple) {
        requireMember(actor, couple);
        LocalDate today = today();
        // Redact in the projection itself: sealed partner bodies do not leave PostgreSQL.
        return db.query("""
                SELECT id,couple_id,author_id,title,unlock_date,visibility,updated_at,
                       CASE WHEN author_id=? OR unlock_date<=?::date THEN body ELSE NULL END AS body
                FROM capsule_letter
                WHERE couple_id=? AND deleted_at IS NULL AND (author_id=? OR visibility='SHARED')
                ORDER BY unlock_date,id
                """, (rs, row) -> map(rs, today), actor, today.toString(), couple, actor);
    }

    @Transactional
    public CapsuleView save(UUID actor, UUID couple, UUID id, SaveCapsuleRequest request) {
        requireMember(actor, couple);
        Normalized value = normalize(request);
        lockSpace(couple);
        Stored existing = find(id);
        if (existing != null) authorize(actor, couple, existing, true);
        if (existing == null) {
            db.update("""
                    INSERT INTO capsule_letter(id,couple_id,author_id,title,body,unlock_date,visibility)
                    VALUES (?,?,?,?,?,?::date,?) ON CONFLICT (id) DO NOTHING
                    """, id, couple, actor, value.title(), value.body(), value.date().toString(), value.visibility().name());
            // Another space may win a concurrent insert of this UUID. Inspect the winner before updating.
            existing = find(id);
            authorize(actor, couple, existing, true);
        }
        db.update("""
                UPDATE capsule_letter SET title=?,body=?,unlock_date=?::date,visibility=?,updated_at=now()
                WHERE id=? AND couple_id=? AND author_id=? AND deleted_at IS NULL
                  AND (title,body,unlock_date,visibility) IS DISTINCT FROM (?,?,?::date,?)
                """, value.title(), value.body(), value.date().toString(), value.visibility().name(), id, couple, actor,
                value.title(), value.body(), value.date().toString(), value.visibility().name());
        return find(id).view();
    }

    @Transactional
    public void delete(UUID actor, UUID couple, UUID id) {
        requireMember(actor, couple);
        lockSpace(couple);
        Stored existing = find(id);
        if (existing == null) throw error(HttpStatus.NOT_FOUND, "capsule not found");
        authorize(actor, couple, existing, false);
        db.update("""
                UPDATE capsule_letter SET deleted_at=now(),updated_at=now()
                WHERE id=? AND couple_id=? AND author_id=? AND deleted_at IS NULL
                """, id, couple, actor);
    }

    private void requireMember(UUID actor, UUID couple) {
        access.requireActiveUser(actor);
        access.requireMember(actor, couple);
    }

    private void lockSpace(UUID couple) {
        db.queryForObject("SELECT id FROM couple_space WHERE id=? FOR UPDATE", UUID.class, couple);
    }

    private Stored find(UUID id) {
        LocalDate today = today();
        return db.query("SELECT * FROM capsule_letter WHERE id=? FOR UPDATE",
                (rs, row) -> new Stored(map(rs, today), rs.getTimestamp("deleted_at") != null), id)
                .stream().findFirst().orElse(null);
    }

    private void authorize(UUID actor, UUID couple, Stored stored, boolean editing) {
        CapsuleView letter = stored.view();
        if (!letter.coupleId().equals(couple.toString())) throw error(HttpStatus.FORBIDDEN, "capsule belongs to another space");
        if (!letter.authorId().equals(actor.toString())) throw error(HttpStatus.FORBIDDEN, "only the author may change a capsule");
        if (editing && stored.deleted()) throw error(HttpStatus.GONE, "capsule was deleted");
    }

    private Normalized normalize(SaveCapsuleRequest request) {
        if (request == null) throw error(HttpStatus.BAD_REQUEST, "capsule is required");
        String title = request.title() == null ? "" : request.title().trim();
        String body = request.body() == null ? "" : request.body();
        if (title.isEmpty() || title.codePointCount(0, title.length()) > 80
                || body.isEmpty() || body.codePointCount(0, body.length()) > 20000)
            throw error(HttpStatus.BAD_REQUEST, "title must contain 1..80 and body 1..20000 characters");
        CapsuleVisibility visibility;
        try { visibility = CapsuleVisibility.valueOf(request.visibility()); }
        catch (IllegalArgumentException | NullPointerException exception) { throw error(HttpStatus.BAD_REQUEST, "invalid capsule visibility"); }
        LocalDate date;
        try {
            if (request.unlockDate() == null || !request.unlockDate().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
                throw new DateTimeParseException("ISO date required", "", 0);
            date = LocalDate.parse(request.unlockDate());
            if (date.getYear() < 1) throw new DateTimeParseException("positive year required", request.unlockDate(), 0);
        } catch (DateTimeParseException exception) { throw error(HttpStatus.BAD_REQUEST, "invalid Gregorian ISO date"); }
        return new Normalized(title, body, date, visibility);
    }

    private LocalDate today() { return LocalDate.now(clock.withZone(SHANGHAI)); }

    private CapsuleView map(ResultSet rs, LocalDate today) throws SQLException {
        LocalDate date = rs.getObject("unlock_date", LocalDate.class);
        return new CapsuleView(rs.getObject("id", UUID.class).toString(), rs.getObject("couple_id", UUID.class).toString(),
                rs.getObject("author_id", UUID.class).toString(), rs.getString("title"), rs.getString("body"), date.toString(),
                CapsuleVisibility.valueOf(rs.getString("visibility")), !today.isBefore(date), rs.getTimestamp("updated_at").toInstant());
    }

    private static ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
    private record Normalized(String title, String body, LocalDate date, CapsuleVisibility visibility) { }
    private record Stored(CapsuleView view, boolean deleted) { }
}
