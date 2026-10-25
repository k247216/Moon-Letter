package com.twomemory.app.export;

import com.twomemory.app.TestDatabaseConfig;
import com.twomemory.app.entry.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class ExportRangeIntegrityTest {
    @DynamicPropertySource static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", TestDatabaseConfig::applicationUrl);
        r.add("spring.datasource.username", TestDatabaseConfig::user);
        r.add("spring.datasource.password", TestDatabaseConfig::password);
    }
    @Autowired JdbcTemplate db;
    @Autowired EntryService entries;
    @Autowired ExportService export;
    @Autowired ExportController controller;
    UUID actor, space;
    @BeforeEach void seed() {
        actor=UUID.randomUUID(); space=UUID.randomUUID();
        db.update("INSERT INTO app_user(id,status) VALUES (?,'ACTIVE')",actor);
        db.update("INSERT INTO couple_space(id,status) VALUES (?,'ACTIVE')",space);
        db.update("INSERT INTO couple_member(couple_id,user_id) VALUES (?,?)",space,actor);
    }
    UUID record(String date, String title) {
        var entry=entries.createDraft(actor,new CreateEntryCommand(actor,space,EntryMode.PERSONAL,title,
                Instant.parse(date),"Asia/Shanghai",List.of(),UUID.randomUUID(),EntryVisibility.SHARED));
        entries.publish(entry.id(),actor,0);
        return entry.id();
    }
    @Test void allRecordsExportCannotSilentlyOmitPreEpochOrFutureMemories() {
        var past=record("1969-12-31T12:00:00Z","过去");
        var future=record("2099-01-01T12:00:00Z","未来");
        var result=export.exportCouple(actor,space,null,null);
        assertThat(result.get("entryCount")).isEqualTo(2);
        assertThat(result.toString()).contains(past.toString(),future.toString());
    }
    @Test void explicitBoundsExcludeOutsideRecordsAndRejectReversedRange() {
        record("1969-12-31T12:00:00Z","界外过去");
        var inside=record("2025-05-09T12:00:00Z","界内回忆");
        record("2099-01-01T12:00:00Z","界外未来");
        var from=Instant.parse("2025-01-01T00:00:00Z");
        var to=Instant.parse("2026-01-01T00:00:00Z");
        var result=export.exportCouple(actor,space,from,to);
        assertThat(result.get("entryCount")).isEqualTo(1);
        assertThat(result.toString()).contains(inside.toString()).doesNotContain("界外");
        assertThatThrownBy(() -> export.exportCouple(actor,space,to,from)).isInstanceOf(EntryValidationException.class);
    }
    @Test void malformedApiDateIsValidationFailureRatherThanAnInternalError() {
        assertThatThrownBy(() -> controller.export(null,space,"not-an-instant",null))
                .isInstanceOf(EntryValidationException.class);
    }
}
