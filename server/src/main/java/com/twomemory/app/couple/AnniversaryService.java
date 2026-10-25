package com.twomemory.app.couple;

import com.twomemory.app.auth.SpaceAccessPolicy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Month;
import java.util.Locale;
import java.util.UUID;

@Service
public class AnniversaryService {

    private final JdbcTemplate jdbcTemplate;
    private final SpaceAccessPolicy accessPolicy;

    public AnniversaryService(JdbcTemplate jdbcTemplate, SpaceAccessPolicy accessPolicy) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessPolicy = accessPolicy;
    }

    public AnniversaryView read(UUID actorId, UUID coupleId) {
        accessPolicy.requireMember(actorId, coupleId);
        return jdbcTemplate.query("""
                SELECT id, couple_id, title, calendar_type::text AS calendar_type,
                       month, day, lunar_leap_month, start_year, repeats_yearly
                FROM anniversary
                WHERE couple_id = ?
                ORDER BY updated_at DESC, id DESC
                LIMIT 1
                """, this::mapAnniversary, coupleId).stream().findFirst().orElse(null);
    }

    @Transactional
    public AnniversaryView save(UUID actorId, UUID coupleId, SaveAnniversaryRequest request) {
        accessPolicy.requireMember(actorId, coupleId);
        NormalizedAnniversary anniversary = normalize(request);
        jdbcTemplate.queryForObject("SELECT id FROM couple_space WHERE id = ? FOR UPDATE", UUID.class, coupleId);
        UUID existingId = jdbcTemplate.query("""
                SELECT id FROM anniversary
                WHERE couple_id = ?
                ORDER BY updated_at DESC, id DESC
                LIMIT 1
                FOR UPDATE
                """, (rs, rowNum) -> rs.getObject("id", UUID.class), coupleId)
                .stream().findFirst().orElse(null);
        if (existingId == null) {
            jdbcTemplate.update("""
                    INSERT INTO anniversary(
                        id, couple_id, title, calendar_type, month, day,
                        lunar_leap_month, start_year, repeats_yearly, created_at, updated_at)
                    VALUES (?, ?, ?, ?::calendar_type, ?, ?, ?, ?, ?, now(), now())
                    """, UUID.randomUUID(), coupleId, anniversary.title(), anniversary.calendarType().name(),
                    anniversary.month(), anniversary.day(), anniversary.lunarLeapMonth(),
                    anniversary.startYear(), anniversary.repeatsYearly());
        } else {
            jdbcTemplate.update("""
                    UPDATE anniversary
                    SET title = ?, calendar_type = ?::calendar_type, month = ?, day = ?,
                        lunar_leap_month = ?, start_year = ?, repeats_yearly = ?, updated_at = now()
                    WHERE id = ?
                    """, anniversary.title(), anniversary.calendarType().name(), anniversary.month(),
                    anniversary.day(), anniversary.lunarLeapMonth(), anniversary.startYear(),
                    anniversary.repeatsYearly(), existingId);
        }
        return read(actorId, coupleId);
    }

    static NormalizedAnniversary normalize(SaveAnniversaryRequest request) {
        if (request == null) {
            throw new ValidationException("anniversary is required");
        }
        if (request.title() == null) {
            throw new ValidationException("anniversary title is required");
        }
        String title = request.title().trim();
        if (title.isEmpty() || title.codePointCount(0, title.length()) > 80) {
            throw new ValidationException("anniversary title must contain 1 to 80 Unicode code points");
        }
        CalendarType calendarType = parseCalendarType(request.calendarType());
        if (request.month() < 1 || request.month() > 12 || request.day() < 1) {
            throw new ValidationException("anniversary date is invalid");
        }
        int maxDay = calendarType == CalendarType.LUNAR
                ? 30
                : Month.of(request.month()).maxLength();
        if (request.day() > maxDay) {
            throw new ValidationException("anniversary date is invalid");
        }
        if (request.startYear() != null && (request.startYear() < 1 || request.startYear() > 32767)) {
            throw new ValidationException("anniversary start year is invalid");
        }
        boolean lunarLeapMonth = Boolean.TRUE.equals(request.lunarLeapMonth());
        if (calendarType == CalendarType.GREGORIAN && lunarLeapMonth) {
            throw new ValidationException("lunar leap month requires a lunar anniversary");
        }
        return new NormalizedAnniversary(
                title,
                calendarType,
                request.month(),
                request.day(),
                lunarLeapMonth,
                request.startYear(),
                !Boolean.FALSE.equals(request.repeatsYearly()));
    }

    private static CalendarType parseCalendarType(String raw) {
        if (raw == null) {
            throw new ValidationException("calendar type is required");
        }
        try {
            return CalendarType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ValidationException("calendar type must be GREGORIAN or LUNAR");
        }
    }

    private AnniversaryView mapAnniversary(ResultSet rs, int rowNum) throws SQLException {
        return new AnniversaryView(
                rs.getObject("id", UUID.class),
                rs.getObject("couple_id", UUID.class),
                rs.getString("title"),
                CalendarType.valueOf(rs.getString("calendar_type")),
                rs.getInt("month"),
                rs.getInt("day"),
                rs.getBoolean("lunar_leap_month"),
                rs.getObject("start_year", Integer.class),
                rs.getBoolean("repeats_yearly"));
    }

    record NormalizedAnniversary(
            String title,
            CalendarType calendarType,
            int month,
            int day,
            boolean lunarLeapMonth,
            Integer startYear,
            boolean repeatsYearly) {
    }
}
