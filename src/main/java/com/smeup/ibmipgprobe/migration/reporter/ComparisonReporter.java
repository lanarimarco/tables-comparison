package com.smeup.ibmipgprobe.migration.reporter;

import com.smeup.ibmipgprobe.migration.model.ComparisonRequest;
import com.smeup.ibmipgprobe.migration.model.TableComparisonResult;

import java.util.List;

/** Strategy interface for reporting comparison results. */
public interface ComparisonReporter {
    void report(ComparisonRequest request, List<TableComparisonResult> results);
}
