package com.tablescomparison.model;

import java.util.List;

/**
 * Input request: the list of tables to compare and the two datasources that contain them.
 *
 * @param tables  table names to compare (case handled per JDBC metadata conventions)
 * @param source1 first datasource
 * @param source2 second datasource
 * @param tableSchemas optional list of schemas to try as fallback when metadata retrieval fails
 * @param maxRows maximum rows to scan per table; 0 means no limit
 * @param threadPoolSize number of tables compared in parallel
 * @param fetchSize number of rows fetched per round-trip to the database
 */
public record ComparisonRequest(
        List<String> tables,
        DataSourceConfig source1,
        DataSourceConfig source2,
        List<String> tableSchemas,
        long maxRows,
        int threadPoolSize,
        int fetchSize) {

    public ComparisonRequest {
        if (tables == null || tables.isEmpty()) throw new IllegalArgumentException("tables must not be empty");
        if (source1 == null) throw new IllegalArgumentException("source1 must not be null");
        if (source2 == null) throw new IllegalArgumentException("source2 must not be null");
        if (tableSchemas == null) throw new IllegalArgumentException("tableSchemas must not be null");
        if (maxRows < 0) throw new IllegalArgumentException("maxRows must be >= 0");
        if (threadPoolSize < 1) throw new IllegalArgumentException("threadPoolSize must be >= 1");
        if (fetchSize < 1) throw new IllegalArgumentException("fetchSize must be >= 1");
        tables = List.copyOf(tables);
        tableSchemas = List.copyOf(tableSchemas);
    }
}
