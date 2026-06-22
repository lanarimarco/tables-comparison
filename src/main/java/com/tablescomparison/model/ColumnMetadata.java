package com.tablescomparison.model;

/**
 * Metadata for a single database column, obtained from {@link java.sql.DatabaseMetaData#getColumns}.
 *
 * @param position  ordinal position (1-based)
 * @param name      column name
 * @param typeName  SQL type name as reported by the driver (e.g. "VARCHAR", "INTEGER")
 * @param jdbcType  JDBC type constant from {@link java.sql.Types}
 * @param size      column size (precision for numerics, length for strings)
 * @param nullable  true if the column allows NULL values
 */
public record ColumnMetadata(
        int position,
        String name,
        String typeName,
        int jdbcType,
        int size,
        boolean nullable) {
}
