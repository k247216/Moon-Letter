package com.twomemory.app.occasion;

import com.twomemory.app.TestDatabaseConfig;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OccasionMigrationTest {
    @Test void upgradePreservesGregorianIdsDatesAndOldRowsButSkipsInvalidAndLunarDates() {
        String schema = "occasion_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        Flyway.configure().dataSource(TestDatabaseConfig.applicationUrl(), TestDatabaseConfig.user(), TestDatabaseConfig.password())
                .schemas(schema).defaultSchema(schema).target("12").load().migrate();
        String url = TestDatabaseConfig.applicationUrl() + "?currentSchema=" + schema;
        JdbcTemplate db = new JdbcTemplate(new DriverManagerDataSource(url, TestDatabaseConfig.user(), TestDatabaseConfig.password()));
        UUID space = UUID.randomUUID(), author = UUID.randomUUID(), former = UUID.randomUUID();
        db.update("INSERT INTO couple_space(id,status) VALUES (?,'ACTIVE')", space);
        for (UUID user : new UUID[]{author, former}) db.update("INSERT INTO app_user(id,status) VALUES (?,'ACTIVE')", user);
        db.update("INSERT INTO couple_member(couple_id,user_id) VALUES (?,?)", space, author);
        db.update("INSERT INTO couple_member(couple_id,user_id,left_at) VALUES (?,?,now())", space, former);
        UUID valid = UUID.randomUUID(), leap = UUID.randomUUID(), yearless = UUID.randomUUID();
        insert(db, valid, space, "GREGORIAN", 10, 6, 2021, false);
        insert(db, leap, space, "GREGORIAN", 2, 29, 2024, true);
        insert(db, yearless, space, "GREGORIAN", 2, 29, null, true);
        insert(db, UUID.randomUUID(), space, "GREGORIAN", 2, 29, 2023, true);
        insert(db, UUID.randomUUID(), space, "GREGORIAN", 4, 31, 2026, true);
        insert(db, UUID.randomUUID(), space, "LUNAR", 8, 15, 2026, true);
        Flyway.configure().dataSource(TestDatabaseConfig.applicationUrl(), TestDatabaseConfig.user(), TestDatabaseConfig.password())
                .schemas(schema).defaultSchema(schema).load().migrate();
        assertThat(db.queryForObject("SELECT count(*) FROM occasion", Integer.class)).isEqualTo(3);
        assertThat(db.queryForObject("SELECT date::text FROM occasion WHERE id=?", String.class, valid)).isEqualTo("2021-10-06");
        assertThat(db.queryForObject("SELECT date::text FROM occasion WHERE id=?", String.class, leap)).isEqualTo("2024-02-29");
        assertThat(db.queryForObject("SELECT date::text FROM occasion WHERE id=?", String.class, yearless)).isEqualTo("2000-02-29");
        assertThat(db.queryForObject("SELECT author_id FROM occasion WHERE id=?", UUID.class, valid)).isEqualTo(author);
        assertThat(db.queryForObject("SELECT repeats_yearly FROM occasion WHERE id=?", Boolean.class, valid)).isFalse();
        assertThat(db.queryForObject("SELECT count(*) FROM anniversary", Integer.class)).isEqualTo(6);
    }

    private void insert(JdbcTemplate db, UUID id, UUID space, String calendar, int month, int day, Integer year, boolean repeat) {
        db.update("""
                INSERT INTO anniversary(id,couple_id,title,calendar_type,month,day,start_year,repeats_yearly)
                VALUES (?,?,'Our anniversary',?::calendar_type,?,?,?,?)
                """, id, space, calendar, month, day, year, repeat);
    }
}
