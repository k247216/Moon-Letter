package com.twomemory.app.entry;

import com.twomemory.app.TestDatabaseConfig;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class PrivateKeepsakesMigrationTest {
    @Test void v14PreservesExistingRowsAndDefaultsThemSharedWithoutDeletingFeedHistory() {
        String schema="private_upgrade_"+UUID.randomUUID().toString().replace("-", "");
        migrate(schema,"13");
        JdbcTemplate db=new JdbcTemplate(new DriverManagerDataSource(TestDatabaseConfig.applicationUrl()+"?currentSchema="+schema,
                TestDatabaseConfig.user(),TestDatabaseConfig.password()));
        UUID space=UUID.randomUUID(), author=UUID.randomUUID(), entry=UUID.randomUUID(), card=UUID.randomUUID();
        db.update("INSERT INTO app_user(id,status) VALUES (?,'ACTIVE')",author);
        db.update("INSERT INTO couple_space(id,status) VALUES (?,'ACTIVE')",space);
        db.update("INSERT INTO entry(id,couple_id,author_id,mode,state,title,occurred_at,occurred_timezone) VALUES (?,?,?,'PERSONAL','PUBLISHED','keep-me',now(),'UTC')",entry,space,author);
        db.update("INSERT INTO occasion(id,couple_id,author_id,kind,title,body,date,repeats_yearly) VALUES (?,?,?,'BIRTHDAY','keep-card','keep-body','2000-01-01',true)",card,space,author);
        db.update("INSERT INTO couple_sync_state(couple_id,last_space_sequence) VALUES (?,1)",space);
        db.update("INSERT INTO sync_change(couple_id,space_sequence,entity_type,entity_id,operation,payload) VALUES (?,1,'ENTRY',?,'PUBLISH','{\"title\":\"keep-me\"}')",space,entry);
        migrate(schema,"14");
        assertThat(db.queryForObject("SELECT visibility FROM entry WHERE id=?",String.class,entry)).isEqualTo("SHARED");
        assertThat(db.queryForObject("SELECT title FROM entry WHERE id=?",String.class,entry)).isEqualTo("keep-me");
        assertThat(db.queryForObject("SELECT visibility FROM occasion WHERE id=?",String.class,card)).isEqualTo("SHARED");
        assertThat(db.queryForObject("SELECT body FROM occasion WHERE id=?",String.class,card)).isEqualTo("keep-body");
        assertThat(db.queryForObject("SELECT count(*) FROM sync_change WHERE audience_user_id IS NULL",Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT payload->>'title' FROM sync_change",String.class)).isEqualTo("keep-me");
    }
    void migrate(String schema,String target) {
        Flyway.configure().dataSource(TestDatabaseConfig.applicationUrl(),TestDatabaseConfig.user(),TestDatabaseConfig.password())
                .schemas(schema).defaultSchema(schema).target(target).load().migrate();
    }
}
