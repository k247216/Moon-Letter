package com.twomemory.app.capsule;

import com.twomemory.app.TestDatabaseConfig;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CapsuleMigrationTest {
    @Test void v15PreservesLegacyCapsuleAndEntryWithoutImportingTheirBody() {
        String schema = "capsule_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        Flyway.configure().dataSource(TestDatabaseConfig.applicationUrl(), TestDatabaseConfig.user(), TestDatabaseConfig.password())
                .schemas(schema).defaultSchema(schema).target("14").load().migrate();
        JdbcTemplate db = new JdbcTemplate(new DriverManagerDataSource(TestDatabaseConfig.applicationUrl() + "?currentSchema=" + schema,
                TestDatabaseConfig.user(), TestDatabaseConfig.password()));
        UUID space = UUID.randomUUID(), author = UUID.randomUUID(), entry = UUID.randomUUID(), legacy = UUID.randomUUID();
        db.update("INSERT INTO couple_space(id,status) VALUES (?,'ACTIVE')", space);
        db.update("INSERT INTO app_user(id,status) VALUES (?,'ACTIVE')", author);
        db.update("""
                INSERT INTO entry(id,couple_id,mode,state,author_id,title,occurred_at,occurred_timezone)
                VALUES (?,?,'PERSONAL','CAPSULE_LOCKED',?,'legacy title',now(),'Asia/Shanghai')
                """, entry, space, author);
        db.update("INSERT INTO time_capsule(id,entry_id,locked_by,unlock_at,status) VALUES (?,?,?,'2030-01-02T00:00:00+08','SEALED')", legacy, entry, author);
        var before = db.queryForMap("SELECT * FROM time_capsule WHERE id=?", legacy);
        var oldEntry = db.queryForMap("SELECT * FROM entry WHERE id=?", entry);
        Flyway.configure().dataSource(TestDatabaseConfig.applicationUrl(), TestDatabaseConfig.user(), TestDatabaseConfig.password())
                .schemas(schema).defaultSchema(schema).load().migrate();
        assertThat(db.queryForMap("SELECT * FROM time_capsule WHERE id=?", legacy)).isEqualTo(before);
        assertThat(db.queryForMap("SELECT * FROM entry WHERE id=?", entry)).isEqualTo(oldEntry);
        assertThat(db.queryForObject("SELECT count(*) FROM capsule_letter", Integer.class)).isZero();
        for (String date : new String[]{"0001-01-01 BC", "infinity", "10000-01-01"}) {
            assertThatThrownBy(() -> db.update("""
                    INSERT INTO capsule_letter(id,couple_id,author_id,title,body,unlock_date,visibility)
                    VALUES (?,?,?,'Letter','Body',?::date,'SHARED')
                    """, UUID.randomUUID(), space, author, date)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }
    }
}
