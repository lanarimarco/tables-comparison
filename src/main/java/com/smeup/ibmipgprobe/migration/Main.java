package com.smeup.ibmipgprobe.migration;

import com.smeup.ibmipgprobe.migration.comparator.TableComparator;
import com.smeup.ibmipgprobe.migration.model.ComparisonRequest;
import com.smeup.ibmipgprobe.migration.model.ConfigLoader;
import com.smeup.ibmipgprobe.migration.reporter.ConsoleReporter;

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
        new ConsoleReporter().report(request, results);
    }
}
