package com.tablescomparison.model;

import java.util.List;

/**
 * Input request: the list of tables to compare and the two datasources that contain them.
 *
 * @param tables  table names to compare (case handled per JDBC metadata conventions)
 * @param source1 first datasource
 * @param source2 second datasource
 * @param tableSchemas optional list of schemas to try as fallback when metadata retrieval fails
 */
public record ComparisonRequest(
        List<String> tables,
        DataSourceConfig source1,
        DataSourceConfig source2,
        List<String> tableSchemas) {

    public ComparisonRequest {
        if (tables == null || tables.isEmpty()) throw new IllegalArgumentException("tables must not be empty");
        if (source1 == null) throw new IllegalArgumentException("source1 must not be null");
        if (source2 == null) throw new IllegalArgumentException("source2 must not be null");
        if (tableSchemas == null) throw new IllegalArgumentException("tableSchemas must not be null");
        tables = List.copyOf(tables);
        tableSchemas = List.copyOf(tableSchemas);
    }
}
