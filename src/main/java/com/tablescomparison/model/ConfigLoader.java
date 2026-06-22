package com.tablescomparison.model;

import io.github.cdimascio.dotenv.Dotenv;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Loads application configuration from .env file.
 */
public class ConfigLoader {

    private static final Dotenv dotenv = Dotenv.configure()
            .directory(Paths.get(".").toAbsolutePath().toString())
            .ignoreIfMissing()
            .load();

    private ConfigLoader() {
        // Utility class
    }

    public static DataSourceConfig loadSource1() {
        return new DataSourceConfig(
                getEnv("SOURCE1_NAME"),
                getEnv("SOURCE1_JDBC_URL"),
                getEnv("SOURCE1_USERNAME"),
                getEnv("SOURCE1_PASSWORD"),
                getEnv("SOURCE1_DRIVER_CLASS")
        );
    }

    public static DataSourceConfig loadSource2() {
        return new DataSourceConfig(
                getEnv("SOURCE2_NAME"),
                getEnv("SOURCE2_JDBC_URL"),
                getEnv("SOURCE2_USERNAME"),
                getEnv("SOURCE2_PASSWORD"),
                getEnv("SOURCE2_DRIVER_CLASS")
        );
    }

    public static List<String> loadTableNames() {
        String tableNames = getEnv("TABLE_NAMES");
        return Arrays.asList(tableNames.split(","));
    }

    /**
     * Loads table schemas from TABLE_SCHEMAS environment variable.
     * Format: SCHEMA1,SCHEMA2,SCHEMA3,...
     * Example: X274DATSUP,X274DATGRU,SMEUP_DEM
     * Returns empty list if TABLE_SCHEMAS is not set.
     */
    public static List<String> loadTableSchemas() {
        String tableSchemasEnv = dotenv.get("TABLE_SCHEMAS");

        if (tableSchemasEnv == null || tableSchemasEnv.isBlank()) {
            return List.of();  // Return empty list if not configured
        }

        return Arrays.stream(tableSchemasEnv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    public static long loadMaxRows() {
        String value = dotenv.get("COMPARE_MAX_ROWS");
        if (value == null || value.isBlank()) return 0L;
        long parsed = Long.parseLong(value.trim());
        if (parsed < 0) throw new IllegalArgumentException("COMPARE_MAX_ROWS must be >= 0");
        return parsed;
    }

    private static String getEnv(String key) {
        String value = dotenv.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Environment variable '" + key + "' is not set or is blank in .env file");
        }
        return value;
    }
}
