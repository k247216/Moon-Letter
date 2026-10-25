package com.twomemory.app;

public final class TestDatabaseConfig {

    private static final String DEFAULT_APPLICATION_URL = "jdbc:postgresql://localhost:5432/moon_letter";
    private static final String DEFAULT_ADMIN_URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String DEFAULT_USER = "moon_letter";

    private TestDatabaseConfig() {
    }

    public static String applicationUrl() {
        return valueOrDefault("TEST_DB_URL", DEFAULT_APPLICATION_URL);
    }

    public static String adminUrl() {
        return valueOrDefault("TEST_DB_ADMIN_URL", DEFAULT_ADMIN_URL);
    }

    public static String user() {
        return valueOrDefault("TEST_DB_USER", DEFAULT_USER);
    }

    public static String password() {
        return required("TEST_DB_PASSWORD");
    }

    private static String valueOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Environment variable " + name + " must be set to run database-backed tests");
        }
        return value;
    }
}
