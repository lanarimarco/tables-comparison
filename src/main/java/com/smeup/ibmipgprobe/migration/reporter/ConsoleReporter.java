package com.smeup.ibmipgprobe.migration.reporter;

import com.smeup.ibmipgprobe.migration.model.ComparisonRequest;
import com.smeup.ibmipgprobe.migration.model.TableComparisonResult;

import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    public void report(ComparisonRequest request, List<TableComparisonResult> results) {
        out.println(HEADER);
        out.println("  TABLE COMPARISON REPORT");
        out.println("  " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        out.println(SEPARATOR);
        printConfig(request);
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

    private void printConfig(ComparisonRequest req) {
        var s1 = req.source1();
        var s2 = req.source2();
        out.println("  Source 1 : " + s1.name());
        out.println("    URL    : " + s1.jdbcUrl());
        out.println("    User   : " + s1.username());
        if (s1.driverClassName() != null) out.println("    Driver : " + s1.driverClassName());
        out.println("  Source 2 : " + s2.name());
        out.println("    URL    : " + s2.jdbcUrl());
        out.println("    User   : " + s2.username());
        if (s2.driverClassName() != null) out.println("    Driver : " + s2.driverClassName());
        out.println("  Tables   : " + String.join(", ", req.tables()));
        if (!req.tableSchemas().isEmpty())
            out.println("  Schemas  : " + String.join(", ", req.tableSchemas()));
        out.println("  Max rows : " + (req.maxRows() == 0 ? "unlimited" : "%,d".formatted(req.maxRows())));
        out.println("  Threads  : " + req.threadPoolSize());
        out.println("  Fetch sz : " + req.fetchSize());
        out.println("  Timeout  : " + (req.queryTimeoutSeconds() == 0 ? "none" : req.queryTimeoutSeconds() + "s"));
    }
}
