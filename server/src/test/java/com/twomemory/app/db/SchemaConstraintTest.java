package com.twomemory.app.db;

import org.flywaydb.core.Flyway;
import org.testcontainers.containers.PostgreSQLContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaConstraintTest {

    private static final String TEST_SCHEMA = "moon_letter_test";
    private static Connection connection;
    private static PostgreSQLContainer<?> postgresContainer;

    @BeforeAll
    static void migrateDatabase() throws SQLException {
        String configuredUrl = System.getenv("TEST_DB_URL");
        String url;
        String user;
        String password;
        if (configuredUrl == null || configuredUrl.isBlank()) {
            postgresContainer = new PostgreSQLContainer<>("postgres:18-alpine");
            postgresContainer.start();
            url = postgresContainer.getJdbcUrl();
            user = postgresContainer.getUsername();
            password = postgresContainer.getPassword();
        } else {
            url = configuredUrl;
            user = System.getenv().getOrDefault("TEST_DB_USER", System.getProperty("user.name"));
            password = System.getenv().getOrDefault("TEST_DB_PASSWORD", "");
        }
        Flyway.configure()
                .dataSource(url, user, password)
                .schemas(TEST_SCHEMA)
                .defaultSchema(TEST_SCHEMA)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load()
                .clean();
        Flyway.configure()
                .dataSource(url, user, password)
                .schemas(TEST_SCHEMA)
                .defaultSchema(TEST_SCHEMA)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        String schemaUrl = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + TEST_SCHEMA;
        connection = DriverManager.getConnection(schemaUrl, user, password);
        seedReferenceRows();
    }

    @AfterAll
    static void closeDatabase() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
        if (postgresContainer != null) {
            postgresContainer.stop();
        }
    }

    @Test
    void activeSpaceRejectsThirdMember() {
        assertThatThrownBy(() -> execute("""
                INSERT INTO couple_member(couple_id, user_id, joined_at)
                VALUES ('00000000-0000-0000-0000-000000000010',
                        '00000000-0000-0000-0000-000000000003', now())
                """))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void lunarAnniversaryAcceptsMonthEightDayFifteen() throws SQLException {
        execute("""
                INSERT INTO couple_space(id, status, created_at, updated_at)
                VALUES ('00000000-0000-0000-0000-000000000020', 'ACTIVE', now(), now())
                """);
        execute("""
                INSERT INTO anniversary(
                    id, couple_id, title, calendar_type, month, day,
                    lunar_leap_month, created_at, updated_at
                ) VALUES (
                    '00000000-0000-0000-0000-000000000011',
                    '00000000-0000-0000-0000-000000000020',
                    '中秋节', 'LUNAR', 8, 15, false, now(), now()
                )
                """);
    }

    @Test
    void mediaRejectsNonPositiveSize() {
        assertThatThrownBy(() -> execute("""
                INSERT INTO media_asset(
                    id, couple_id, owner_id, kind, status, object_key,
                    mime_type, byte_size, sha256, created_at, updated_at
                ) VALUES (
                    '00000000-0000-0000-0000-000000000020',
                    '00000000-0000-0000-0000-000000000030',
                    '00000000-0000-0000-0000-000000000004',
                    'IMAGE', 'LOCAL_PENDING', 'x', 'image/jpeg', 0,
                    repeat('0', 64), now(), now()
                )
                """))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void revisionNumberIsUniquePerEntry() throws SQLException {
        execute("""
                INSERT INTO entry(
                    id, couple_id, mode, state, author_id,
                    occurred_at, occurred_timezone, created_at, updated_at
                ) VALUES (
                    '00000000-0000-0000-0000-000000000031',
                    '00000000-0000-0000-0000-000000000040',
                    'PERSONAL', 'DRAFT', '00000000-0000-0000-0000-000000000005',
                    now(), 'Asia/Shanghai', now(), now()
                )
                """);
        execute("""
                INSERT INTO entry_revision(
                    id, entry_id, revision_no, base_revision_no,
                    edited_by, snapshot, created_at
                ) VALUES (
                    '00000000-0000-0000-0000-000000000030',
                    '00000000-0000-0000-0000-000000000031',
                    0, 0, '00000000-0000-0000-0000-000000000001', '{}', now()
                )
                """);
        assertThatThrownBy(() -> execute("""
                INSERT INTO entry_revision(
                    id, entry_id, revision_no, base_revision_no,
                    edited_by, snapshot, created_at
                ) VALUES (
                    '00000000-0000-0000-0000-000000000032',
                    '00000000-0000-0000-0000-000000000031',
                    0, 0, '00000000-0000-0000-0000-000000000005', '{}', now()
                )
                """))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void locationRejectsOutOfRangeCoordinates() {
        assertThatThrownBy(() -> execute("""
                INSERT INTO location_snapshot(
                    id, couple_id, created_by, latitude, longitude,
                    city_name, country_code, source, captured_at
                ) VALUES (
                    '00000000-0000-0000-0000-000000000040',
                    '00000000-0000-0000-0000-000000000050',
                    '00000000-0000-0000-0000-000000000006',
                    91.0, 120.0, '杭州', 'CN', 'SINGLE_GPS_REQUEST', now()
                )
                """))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void perCoupleChangeSequenceIsUnique() throws SQLException {
        execute("""
                INSERT INTO couple_space(id, status, created_at, updated_at)
                VALUES ('00000000-0000-0000-0000-000000000060', 'ACTIVE', now(), now())
                """);
        execute("""
                INSERT INTO sync_change(couple_id, space_sequence, entity_type, entity_id,
                                        operation, payload, created_at)
                VALUES ('00000000-0000-0000-0000-000000000060', 1, 'ENTRY',
                        '00000000-0000-0000-0000-000000000061', 'CREATE', '{}', now())
                """);
        assertThatThrownBy(() -> execute("""
                INSERT INTO sync_change(couple_id, space_sequence, entity_type, entity_id,
                                        operation, payload, created_at)
                VALUES ('00000000-0000-0000-0000-000000000060', 1, 'ENTRY',
                        '00000000-0000-0000-0000-000000000062', 'CREATE', '{}', now())
                """))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute("""
                INSERT INTO sync_change(couple_id, space_sequence, entity_type, entity_id,
                                        operation, payload, created_at)
                VALUES ('00000000-0000-0000-0000-000000000060', NULL, 'ENTRY',
                        '00000000-0000-0000-0000-000000000063', 'CREATE', '{}', now())
                """))
                .isInstanceOf(SQLException.class);
    }

    private static void execute(String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static void seedReferenceRows() throws SQLException {
        execute("""
                INSERT INTO app_user(id, status) VALUES
                    ('00000000-0000-0000-0000-000000000001', 'ACTIVE'),
                    ('00000000-0000-0000-0000-000000000002', 'ACTIVE'),
                    ('00000000-0000-0000-0000-000000000003', 'ACTIVE'),
                    ('00000000-0000-0000-0000-000000000004', 'ACTIVE'),
                    ('00000000-0000-0000-0000-000000000005', 'ACTIVE'),
                    ('00000000-0000-0000-0000-000000000006', 'ACTIVE')
                """);
        execute("""
                INSERT INTO couple_space(id, status) VALUES
                    ('00000000-0000-0000-0000-000000000010', 'ACTIVE'),
                    ('00000000-0000-0000-0000-000000000030', 'ACTIVE'),
                    ('00000000-0000-0000-0000-000000000040', 'ACTIVE'),
                    ('00000000-0000-0000-0000-000000000050', 'ACTIVE')
                """);
        execute("""
                INSERT INTO couple_member(couple_id, user_id) VALUES
                    ('00000000-0000-0000-0000-000000000010', '00000000-0000-0000-0000-000000000001'),
                    ('00000000-0000-0000-0000-000000000010', '00000000-0000-0000-0000-000000000002')
                """);
    }
}
