package com.tablescomparison.model;

import java.util.List;

/**
 * Result of comparing a single table across two datasources.
 *
 * <p>Use pattern matching on this sealed interface to handle all three outcomes:
 * <pre>{@code
 * switch (result) {
 *     case TableComparisonResult.Equal eq     -> ...
 *     case TableComparisonResult.Different d  -> ...
 *     case TableComparisonResult.Error err    -> ...
 * }
 * }</pre>
 */
public sealed interface TableComparisonResult
        permits TableComparisonResult.Equal,
                TableComparisonResult.Different,
                TableComparisonResult.Error {

    String tableName();

    /** The two sources agree on the table content. */
    record Equal(String tableName, long recordCount) implements TableComparisonResult {}

    /** At least one difference was detected. */
    record Different(String tableName, List<DifferenceDetail> differences)
            implements TableComparisonResult {
        public Different {
            differences = List.copyOf(differences);
        }
    }

    /** The comparison could not be completed due to an exception. */
    record Error(String tableName, String message, Exception cause)
            implements TableComparisonResult {}
}
