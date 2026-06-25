package com.tablescomparison.reporter;

import com.tablescomparison.model.ComparisonRequest;
import com.tablescomparison.model.TableComparisonResult;

import java.util.List;

/** Strategy interface for reporting comparison results. */
public interface ComparisonReporter {
    void report(ComparisonRequest request, List<TableComparisonResult> results);
}
