package com.smeup.ibmipgprobe.migration.model;

import com.smeup.ibmipgprobe.DataSourceConfig;
import com.smeup.ibmipgprobe.SharedConfig;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Loads migration comparison configuration from .env. SOURCE1/SOURCE2 are delegated to {@link SharedConfig}. */
public class ConfigLoader {

    private ConfigLoader() {}

    public static DataSourceConfig loadSource1() {
        return SharedConfig.loadSource1();
    }

    public static DataSourceConfig loadSource2() {
        return SharedConfig.loadSource2();
    }

    public static List<String> loadTableNames() {
        return Arrays.asList(SharedConfig.getEnv("TABLE_NAMES").split(","));
    }

    public static List<String> loadTableSchemas() {
        String value = SharedConfig.getOptionalEnv("TABLE_SCHEMAS", null);
        if (value == null) return List.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    public static long loadMaxRows() {
        String value = SharedConfig.getOptionalEnv("COMPARE_MAX_ROWS", null);
        if (value == null) return 0L;
        long parsed = Long.parseLong(value.trim());
        if (parsed < 0) throw new IllegalArgumentException("COMPARE_MAX_ROWS must be >= 0");
        return parsed;
    }

    public static int loadThreadPoolSize() {
        String value = SharedConfig.getOptionalEnv("COMPARE_THREAD_POOL_SIZE", null);
        if (value == null) return 5;
        int parsed = Integer.parseInt(value.trim());
        if (parsed < 1) throw new IllegalArgumentException("COMPARE_THREAD_POOL_SIZE must be >= 1");
        return parsed;
    }

    public static int loadFetchSize() {
        String value = SharedConfig.getOptionalEnv("COMPARE_FETCH_SIZE", null);
        if (value == null) return 1000;
        int parsed = Integer.parseInt(value.trim());
        if (parsed < 1) throw new IllegalArgumentException("COMPARE_FETCH_SIZE must be >= 1");
        return parsed;
    }

    public static int loadQueryTimeoutSeconds() {
        String value = SharedConfig.getOptionalEnv("COMPARE_QUERY_TIMEOUT_SECONDS", null);
        if (value == null) return 0;
        int parsed = Integer.parseInt(value.trim());
        if (parsed < 0) throw new IllegalArgumentException("COMPARE_QUERY_TIMEOUT_SECONDS must be >= 0");
        return parsed;
    }
}
