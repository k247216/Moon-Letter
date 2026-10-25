package com.twomemory.app;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boot the real application context against an explicit PostgreSQL database
 * and assert that Flyway applied the expected M1 schema.
 *
 * The URL must be supplied explicitly via TEST_DB_URL (with TEST_DB_USER /
 * TEST_DB_PASSWORD), matching the contract used by SchemaConstraintTest.
 * When TEST_DB_URL is absent the test uses the local Docker Compose database
 * URL, while its password must still come from TEST_DB_PASSWORD.
 */
@SpringBootTest
class CoupleDiaryApplicationTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestDatabaseConfig::applicationUrl);
        registry.add("spring.datasource.username", TestDatabaseConfig::user);
        registry.add("spring.datasource.password", TestDatabaseConfig::password);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads() {
        assertThat(jdbcTemplate).isNotNull();
    }

    @Test
    void flywayAppliedExpectedSchema() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables " +
                        "WHERE table_schema = current_schema() ORDER BY table_name",
                String.class);
        assertThat(tables).contains(
                "app_user",
                "user_profile",
                "couple_space",
                "couple_member",
                "entry",
                "entry_block",
                "entry_revision",
                "comment",
                "media_asset",
                "location_snapshot",
                "anniversary",
                "time_capsule",
                "idempotency_record",
                "sync_change");
    }

    @Test
    void flywayHistoryRecordsMigrations() {
        Integer applied = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = true",
                Integer.class);
        assertThat(applied).isGreaterThanOrEqualTo(5);
    }
}
