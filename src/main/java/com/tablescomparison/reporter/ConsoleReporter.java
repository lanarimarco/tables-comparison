package com.tablescomparison.reporter;

import com.tablescomparison.model.TableComparisonResult;

import java.io.PrintStream;
import java.util.List;

/** Prints a human-readable comparison report to a {@link PrintStream} (defaults to stdout). */
public class ConsoleReporter implements ComparisonReporter {

    private static final String SEPARATOR = "-".repeat(64);
    private static final String HEADER    = "=".repeat(64);

    private final PrintStream out;

    public ConsoleReporter() {
        this(System.out);
    }

    public ConsoleReporter(PrintStream out) {
        this.out = out;
    }

    @Override
    public void report(List<TableComparisonResult> results) {
        out.println(HEADER);
        out.println("  TABLE COMPARISON REPORT");
        out.println(HEADER);

        for (var result : results) {
            out.println();
            switch (result) {
                case TableComparisonResult.Equal eq -> {
                    out.println("  Table  : " + eq.tableName());
                    out.println("  Status : ✓ EQUAL (%,d records)".formatted(eq.recordCount()));
                }
                case TableComparisonResult.Different diff -> {
                    out.println("  Table  : " + diff.tableName());
                    out.println("  Status : ✗ DIFFERENT");
                    if (diff.rowQuery() != null) {
                        out.println("  Select : " + diff.rowQuery());
                    }
                    out.println("  Details:");
                    for (var d : diff.differences()) {
                        out.println("    [%-20s] %s".formatted(d.category(), d.description()));
                        if (d.reproQuery() != null) {
                            out.println("                           " + d.reproQuery());
                        }
                    }
                }
                case TableComparisonResult.Interrupted i -> {
                    out.println("  Table  : " + i.tableName());
                    out.println("  Status : ⚡ INTERRUPTED (compareMaxRows %,d/%,d rows)".formatted(i.compareMaxRows(), i.totalRowCount()));
                    if (i.rowQuery() != null) {
                        out.println("  Select : " + i.rowQuery());
                    }
                }
                case TableComparisonResult.Error err -> {
                    out.println("  Table  : " + err.tableName());
                    out.println("  Status : ⚠ ERROR");
                    out.println("  Message: " + err.message());
                }
            }
            out.println(SEPARATOR);
        }

        long equal       = results.stream().filter(r -> r instanceof TableComparisonResult.Equal).count();
        long diff        = results.stream().filter(r -> r instanceof TableComparisonResult.Different).count();
        long interrupted = results.stream().filter(r -> r instanceof TableComparisonResult.Interrupted).count();
        long errors      = results.stream().filter(r -> r instanceof TableComparisonResult.Error).count();

        out.println();
        out.println("  Summary: %d table(s) — ✓ %d equal  ✗ %d different  ⚡ %d interrupted  ⚠ %d error(s)"
                .formatted(results.size(), equal, diff, interrupted, errors));
        out.println(HEADER);
    }
}
