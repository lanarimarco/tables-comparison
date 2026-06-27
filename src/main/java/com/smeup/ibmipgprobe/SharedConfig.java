package com.smeup.ibmipgprobe;

import io.github.cdimascio.dotenv.Dotenv;

import java.nio.file.Paths;

/**
 * Shared configuration loaded from the project's .env file.
 * All toolkits read SOURCE1_* and SOURCE2_* from here;
 * toolkit-specific settings use their own prefix (e.g. COMPARE_*, SETLL_ISSUE_*).
 */
public class SharedConfig {

    private static final Dotenv dotenv = Dotenv.configure()
            .directory(Paths.get(".").toAbsolutePath().toString())
            .ignoreIfMissing()
            .load();

    private SharedConfig() {}

    public static DataSourceConfig loadSource1() {
        return new DataSourceConfig(
                getEnv("SOURCE1_NAME"),
                getEnv("SOURCE1_JDBC_URL"),
                getEnv("SOURCE1_USERNAME"),
                getEnv("SOURCE1_PASSWORD"),
                getOptionalEnv("SOURCE1_DRIVER_CLASS", null)
        );
    }

    public static DataSourceConfig loadSource2() {
        return new DataSourceConfig(
                getEnv("SOURCE2_NAME"),
                getEnv("SOURCE2_JDBC_URL"),
                getEnv("SOURCE2_USERNAME"),
                getEnv("SOURCE2_PASSWORD"),
                getOptionalEnv("SOURCE2_DRIVER_CLASS", null)
        );
    }

    /** Returns the value of {@code key}; throws if the key is missing or blank. */
    public static String getEnv(String key) {
        String value = dotenv.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Environment variable '" + key + "' is not set or blank in .env");
        }
        return value;
    }

    /** Returns the value of {@code key}, or {@code defaultValue} if the key is missing or blank. */
    public static String getOptionalEnv(String key, String defaultValue) {
        String value = dotenv.get(key);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }
}
