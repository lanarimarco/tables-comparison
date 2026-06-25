package com.tablescomparison.model;

/**
 * A single identified difference between the two datasources for a given table.
 *
 * @param category    the kind of difference
 * @param description human-readable explanation
 * @param reproQuery  optional SQL to reproduce the difference in isolation (may be null)
 */
public record DifferenceDetail(Category category, String description, String reproQuery) {

    public DifferenceDetail(Category category, String description) {
        this(category, description, null);
    }

    public enum Category {
        /** The total number of rows differs between the two sources. */
        RECORD_COUNT,
        /** The number of columns differs. */
        COLUMN_COUNT,
        /** A column name differs at the same ordinal position. */
        COLUMN_NAME,
        /** A column SQL type differs (same name, different type). */
        COLUMN_TYPE,
        /** A row exists only in source 1. */
        ONLY_IN_SOURCE1,
        /** A row exists only in source 2. */
        ONLY_IN_SOURCE2,
        /** A row with the same primary key has different column values. */
        ROW_DATA_MISMATCH
    }
}
