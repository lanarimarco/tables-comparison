package com.smeup.ibmipgprobe.migration.model;

import java.util.List;

/**
 * Result of comparing a single table across two datasources.
 *
 * <p>Use pattern matching on this sealed interface to handle all four outcomes:
 * <pre>{@code
 * switch (result) {
 *     case TableComparisonResult.Equal       eq   -> ...
 *     case TableComparisonResult.Different   d    -> ...
 *     case TableComparisonResult.Interrupted i    -> ...
 *     case TableComparisonResult.Error       err  -> ...
 * }
 * }</pre>
 */
public sealed interface TableComparisonResult
        permits TableComparisonResult.Equal,
                TableComparisonResult.Different,
                TableComparisonResult.Interrupted,
                TableComparisonResult.Error {

    String tableName();

    /** The two sources agree on the table content. */
    record Equal(String tableName, long recordCount) implements TableComparisonResult {}

    /** At least one difference was detected. */
    record Different(String tableName, List<DifferenceDetail> differences, String rowQuery)
            implements TableComparisonResult {
        public Different {
            differences = List.copyOf(differences);
        }
    }

    /** Row scan was skipped or stopped because the table exceeds COMPARE_MAX_ROWS. */
    record Interrupted(String tableName, long compareMaxRows, long totalRowCount, String rowQuery)
            implements TableComparisonResult {}

    /** The comparison could not be completed due to an exception. */
    record Error(String tableName, String message, Exception cause)
            implements TableComparisonResult {}
}
