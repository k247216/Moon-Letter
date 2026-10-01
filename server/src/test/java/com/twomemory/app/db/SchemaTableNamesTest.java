package com.twomemory.app.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The open-day runbook counts rows in named tables to prove that writing one
 * real record produces exactly one entry and exactly the expected change rows.
 * Those names come from the migrations, so a silent rename would make the
 * runbook's before/after comparison impossible — that is a contract, and
 * contracts belong in a test rather than in prose.
 */
class SchemaTableNamesTest {

    private static final String TEST_SCHEMA = "moon_letter_names_test";
    private static PostgreSQLContainer<?> postgresContainer;
    private static Connection connection;

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
    }

    @AfterAll
    static void closeDatabase() throws SQLException {
        if (connection != null) {
            connection.close();
        }
        if (postgresContainer != null) {
            postgresContainer.stop();
        }
    }

    @Test
    void theTablesTheRunbookCountsExistUnderThoseNames() throws SQLException {
        Set<String> names = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT table_name FROM information_schema.tables WHERE table_schema = '"
                             + TEST_SCHEMA + "'")) {
            while (rows.next()) {
                names.add(rows.getString(1));
            }
        }

        assertThat(names).contains(
                "entry", "entry_block", "comment", "sync_change",
                "media_asset", "couple_space", "couple_member", "device_session",
                "session_admin_audit");
        // The change feed and the media table have been renamed once already;
        // the runbook must not survive that by silently counting zero rows.
        assertThat(names).doesNotContain("change_feed", "space_change", "media_object");
    }
}
