package com.tablescomparison;

import com.tablescomparison.comparator.TableComparator;
import com.tablescomparison.model.ComparisonRequest;
import com.tablescomparison.model.ConfigLoader;
import com.tablescomparison.reporter.ConsoleReporter;

/**
 * Entry point — datasource configurations and table names are loaded from .env file.
 */
public class Main {

    public static void main(String[] args) {
        var source1 = ConfigLoader.loadSource1();
        var source2 = ConfigLoader.loadSource2();
        var tableNames = ConfigLoader.loadTableNames();
        var tableSchemas = ConfigLoader.loadTableSchemas();

        var maxRows = ConfigLoader.loadMaxRows();
        var threadPoolSize = ConfigLoader.loadThreadPoolSize();
        var fetchSize = ConfigLoader.loadFetchSize();
        var queryTimeoutSeconds = ConfigLoader.loadQueryTimeoutSeconds();
        var request = new ComparisonRequest(tableNames, source1, source2, tableSchemas, maxRows, threadPoolSize, fetchSize, queryTimeoutSeconds);

        var results = new TableComparator().compareAll(request);
        new ConsoleReporter().report(results);
    }
}
