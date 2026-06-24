package com.tablescomparison.comparator;

import com.tablescomparison.model.ColumnMetadata;
import com.tablescomparison.model.ComparisonRequest;
import com.tablescomparison.model.DataSourceConfig;
import com.tablescomparison.model.DifferenceDetail;
import com.tablescomparison.model.TableComparisonResult;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * Compares tables between two datasources following a three-step strategy:
 * <ol>
 *   <li>Record count — if counts differ the table is immediately flagged as different.</li>
 *   <li>Metadata — column count, names, and JDBC types are compared; a mismatch stops further
 *       analysis for that table.</li>
 *   <li>Row data — rows are fetched ordered by unique index columns (or all columns when none exist)
 *       and compared with a merge-join algorithm.</li>
 * </ol>
 */
public class TableComparator {

    private static final Logger log = LoggerFactory.getLogger(TableComparator.class);
    // DECIMAL and NUMERIC are interchangeable; compare precision and scale, not the type code
    private static final Set<Integer> NUMERIC_FAMILY = Set.of(Types.DECIMAL, Types.NUMERIC);

    // All character types are interchangeable; compare length, not the type code
    private static final Set<Integer> CHARACTER_FAMILY = Set.of(
            Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR,
            Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR);

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Runs the comparison for every table in the request and returns one result per table.
     * Tables are compared in parallel using a fixed thread pool sized by {@code request.threadPoolSize()}.
     * DataSources are created from the supplied configs and closed when done.
     */
    public List<TableComparisonResult> compareAll(ComparisonRequest request) {
        int poolSize = request.threadPoolSize();
        try (var ds1 = createDataSource(request.source1(), poolSize);
             var ds2 = createDataSource(request.source2(), poolSize)) {
            return runParallel(
                    request.tables(), ds1, ds2,
                    request.source1().name(), request.source2().name(),
                    request.tableSchemas(), request.maxRows(), poolSize, request.fetchSize(),
                    request.queryTimeoutSeconds());
        }
    }

    /**
     * Runs the comparison using already-constructed {@link DataSource} instances.
     * Useful for testing or when callers manage connection pools themselves.
     */
    public List<TableComparisonResult> compareAll(
            List<String> tables, DataSource ds1, DataSource ds2,
            String name1, String name2) {
        return compareAll(tables, ds1, ds2, name1, name2, List.of());
    }

    /**
     * Runs the comparison using already-constructed {@link DataSource} instances with table schemas.
     */
    public List<TableComparisonResult> compareAll(
            List<String> tables, DataSource ds1, DataSource ds2,
            String name1, String name2, List<String> tableSchemas) {
        return runParallel(tables, ds1, ds2, name1, name2, tableSchemas, 0L, 1, 1000, 0);
    }

    private List<TableComparisonResult> runParallel(
            List<String> tables, DataSource ds1, DataSource ds2,
            String name1, String name2, List<String> tableSchemas, long maxRows, int threadPoolSize, int fetchSize,
            int queryTimeoutSeconds) {

        var executor = Executors.newFixedThreadPool(threadPoolSize);
        var futures = new ArrayList<Future<TableComparisonResult>>(tables.size());
        for (String table : tables) {
            futures.add(executor.submit(
                    () -> compare(table, ds1, ds2, name1, name2, tableSchemas, maxRows, fetchSize, queryTimeoutSeconds)));
        }
        executor.shutdown();

        var results = new ArrayList<TableComparisonResult>(tables.size());
        for (var future : futures) {
            try {
                results.add(future.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Table comparison interrupted", e);
            } catch (ExecutionException e) {
                throw new RuntimeException("Table comparison failed", e.getCause());
            }
        }
        return results;
    }

    // -------------------------------------------------------------------------
    // Per-table comparison
    // -------------------------------------------------------------------------

    private TableComparisonResult compare(
            String tableName, DataSource ds1, DataSource ds2,
            String name1, String name2, List<String> tableSchemas, long maxRows, int fetchSize,
            int queryTimeoutSeconds) {

        var thread = Thread.currentThread();
        var previousName = thread.getName();
        thread.setName("compare-" + tableName);
        log.info("Comparing table '{}' between '{}' and '{}'", tableName, name1, name2);
        try {
            var diffs = new ArrayList<DifferenceDetail>();

            // Step 1 — record count
            log.info("[STEP 1/3] Comparing record counts for table '{}'", tableName);
            long count1 = getRecordCount(tableName, ds1, queryTimeoutSeconds);
            long count2 = getRecordCount(tableName, ds2, queryTimeoutSeconds);
            log.info("Record counts for table '{}': {} = {}, {} = {}", tableName, name1, count1, name2, count2);
            if (count1 != count2) {
                log.warn("Record count mismatch for table '{}': {} = {}, {} = {}", tableName, name1, count1, name2, count2);
                diffs.add(new DifferenceDetail(
                        DifferenceDetail.Category.RECORD_COUNT,
                        "Record count differs: %s=%d, %s=%d".formatted(name1, count1, name2, count2)));
                return new TableComparisonResult.Different(tableName, diffs, null);
            }

            // Step 2 — metadata
            log.info("[STEP 2/3] Comparing metadata for table '{}'", tableName);
            var cols1 = getColumnMetadata(tableName, ds1, tableSchemas);
            var cols2 = getColumnMetadata(tableName, ds2, tableSchemas);
            log.debug("Metadata retrieved for table '{}': {} columns in {}, {} columns in {}",
                    tableName, cols1.size(), name1, cols2.size(), name2);
            var metaDiffs = compareMetadata(cols1, cols2, name1, name2);
            if (!metaDiffs.isEmpty()) {
                var firstDiff = metaDiffs.getFirst();
                log.warn("Metadata differences found for table '{}': {} difference(s) — First: {} : {}",
                        tableName, metaDiffs.size(), firstDiff.category(), firstDiff.description());
                return new TableComparisonResult.Different(tableName, metaDiffs, null);
            }
            log.debug("Metadata match for table '{}': schema is identical", tableName);

            // Step 3 — row data
            log.info("[STEP 3/3] Comparing row data for table '{}'", tableName);
            var keyColumns = getUniqueIndexColumns(tableName, ds1, tableSchemas);
            log.debug("Table '{}': {} unique index column(s): {}", tableName, keyColumns.size(), keyColumns);
            var rowResult = compareRecords(tableName, ds1, ds2, keyColumns, cols1, name1, name2, count1, maxRows, fetchSize, queryTimeoutSeconds);

            if (rowResult.interrupted()) {
                return new TableComparisonResult.Interrupted(tableName, maxRows, maxRows, rowResult.query());
            } else if (!rowResult.diffs().isEmpty()) {
                log.warn("Row data differences found for table '{}': {} difference(s)", tableName, rowResult.diffs().size());
                return new TableComparisonResult.Different(tableName, rowResult.diffs(), rowResult.query());
            } else {
                log.debug("Row data match for table '{}': all rows are identical", tableName);
                return new TableComparisonResult.Equal(tableName, count1);
            }

        } catch (Exception e) {
            log.error("Error comparing table '{}': {}", tableName, e.getMessage(), e);
            return new TableComparisonResult.Error(tableName, e.getMessage(), e);
        } finally {
            thread.setName(previousName);
        }
    }

    // -------------------------------------------------------------------------
    // Step 1 — record count
    // -------------------------------------------------------------------------

    private long getRecordCount(String tableName, DataSource ds, int queryTimeoutSeconds) throws SQLException {
        try (var conn = ds.getConnection();
             var stmt = conn.createStatement()) {
            if (queryTimeoutSeconds > 0) stmt.setQueryTimeout(queryTimeoutSeconds);
            try (var rs = stmt.executeQuery("SELECT COUNT(*) FROM " + "\"" + tableName + "\"")) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Step 2 — metadata
    // -------------------------------------------------------------------------

    private List<ColumnMetadata> getColumnMetadata(String tableName, DataSource ds, List<String> tableSchemas)
            throws SQLException {
        try (var conn = ds.getConnection()) {
            var dbMeta = conn.getMetaData();
            var cols = new ArrayList<ColumnMetadata>();

            // Try with each configured schema first
            for (String schema : tableSchemas) {
                log.debug("Attempting to retrieve metadata for table '{}' with schema '{}'", tableName, schema);
                cols = readColumns(dbMeta, tableName, schema);
                if (!cols.isEmpty()) {
                    return cols;
                }
                // Try uppercase table name variant
                cols = readColumns(dbMeta, tableName.toUpperCase(), schema);
                if (!cols.isEmpty()) {
                    return cols;
                }
            }

            // Fallback: try without schema if no schemas are configured or all schema attempts failed
            log.debug("No metadata found with configured schemas, attempting without schema for table '{}'", tableName);
            cols = readColumns(dbMeta, tableName, null);
            if (cols.isEmpty()) {
                // Some drivers require the table name in upper case
                cols = readColumns(dbMeta, tableName.toUpperCase(), null);
            }

            if (cols.isEmpty()) {
                throw new SQLException("Table not found or no columns returned for: " + tableName);
            }
            return cols;
        }
    }

    private ArrayList<ColumnMetadata> readColumns(DatabaseMetaData dbMeta, String tableName, String schema)
            throws SQLException {
        var cols = new ArrayList<ColumnMetadata>();
        try (var rs = dbMeta.getColumns(null, schema, tableName, null)) {
            while (rs.next()) {
                cols.add(new ColumnMetadata(
                        rs.getInt("ORDINAL_POSITION"),
                        rs.getString("COLUMN_NAME"),
                        rs.getString("TYPE_NAME"),
                        rs.getInt("DATA_TYPE"),
                        rs.getInt("COLUMN_SIZE"),
                        rs.getInt("DECIMAL_DIGITS"),
                        rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls));
            }
        }
        return cols;
    }

    private List<DifferenceDetail> compareMetadata(
            List<ColumnMetadata> cols1, List<ColumnMetadata> cols2,
            String name1, String name2) {

        var diffs = new ArrayList<DifferenceDetail>();

        if (cols1.size() != cols2.size()) {
            diffs.add(new DifferenceDetail(
                    DifferenceDetail.Category.COLUMN_COUNT,
                    "Column count differs: %s=%d, %s=%d".formatted(name1, cols1.size(), name2, cols2.size())));
            return diffs;
        }

        for (int i = 0; i < cols1.size(); i++) {
            var c1 = cols1.get(i);
            var c2 = cols2.get(i);

            if (!c1.name().equalsIgnoreCase(c2.name())) {
                diffs.add(new DifferenceDetail(
                        DifferenceDetail.Category.COLUMN_NAME,
                        "Column at position %d name differs: %s='%s', %s='%s'"
                                .formatted(i + 1, name1, c1.name(), name2, c2.name())));
            } else {
                diffs.addAll(compareColumnType(c1, c2, name1, name2));
            }
        }
        return diffs;
    }

    private List<DifferenceDetail> compareColumnType(
            ColumnMetadata c1, ColumnMetadata c2, String name1, String name2) {

        boolean bothNumeric = NUMERIC_FAMILY.contains(c1.jdbcType()) && NUMERIC_FAMILY.contains(c2.jdbcType());
        boolean bothCharacter = CHARACTER_FAMILY.contains(c1.jdbcType()) && CHARACTER_FAMILY.contains(c2.jdbcType());

        if (bothNumeric) {
            if (c1.size() != c2.size() || c1.scale() != c2.scale()) {
                return List.of(new DifferenceDetail(
                        DifferenceDetail.Category.COLUMN_TYPE,
                        "Column '%s' numeric precision/scale differs: %s=%s(%d,%d), %s=%s(%d,%d)"
                                .formatted(c1.name(),
                                        name1, c1.typeName(), c1.size(), c1.scale(),
                                        name2, c2.typeName(), c2.size(), c2.scale())));
            }
            return List.of();
        }

        if (bothCharacter) {
            if (c1.size() != c2.size()) {
                return List.of(new DifferenceDetail(
                        DifferenceDetail.Category.COLUMN_TYPE,
                        "Column '%s' character length differs: %s=%s(%d), %s=%s(%d)"
                                .formatted(c1.name(),
                                        name1, c1.typeName(), c1.size(),
                                        name2, c2.typeName(), c2.size())));
            }
            return List.of();
        }

        if (c1.jdbcType() != c2.jdbcType()) {
            return List.of(new DifferenceDetail(
                    DifferenceDetail.Category.COLUMN_TYPE,
                    "Column '%s' type differs: %s=%s(%d), %s=%s(%d)"
                            .formatted(c1.name(),
                                    name1, c1.typeName(), c1.jdbcType(),
                                    name2, c2.typeName(), c2.jdbcType())));
        }
        return List.of();
    }

    // -------------------------------------------------------------------------
    // Step 3 — row data
    // -------------------------------------------------------------------------

    private List<String> getUniqueIndexColumns(String tableName, DataSource ds, List<String> tableSchemas)
            throws SQLException {
        try (var conn = ds.getConnection()) {
            var dbMeta = conn.getMetaData();

            for (String schema : tableSchemas) {
                log.debug("Attempting to retrieve unique index columns for table '{}' with schema '{}'", tableName, schema);
                var keys = readUniqueIndexColumns(dbMeta, tableName, schema);
                if (!keys.isEmpty()) return new ArrayList<>(keys.values());
                keys = readUniqueIndexColumns(dbMeta, tableName.toUpperCase(), schema);
                if (!keys.isEmpty()) return new ArrayList<>(keys.values());
            }

            // Fallback: try without schema if no schemas are configured or all schema attempts failed
            var keys = readUniqueIndexColumns(dbMeta, tableName, null);
            if (keys.isEmpty()) {
                keys = readUniqueIndexColumns(dbMeta, tableName.toUpperCase(), null);
            }
            return new ArrayList<>(keys.values());
        }
    }

    private TreeMap<Integer, String> readUniqueIndexColumns(DatabaseMetaData dbMeta, String tableName, String schema)
            throws SQLException {
        var byIndex = new TreeMap<String, TreeMap<Integer, String>>();
        try (var rs = dbMeta.getIndexInfo(null, schema, tableName, true, false)) {
            while (rs.next()) {
                if (rs.getShort("TYPE") == DatabaseMetaData.tableIndexStatistic) continue;
                String indexName = rs.getString("INDEX_NAME");
                if (indexName == null) continue;
                byIndex.computeIfAbsent(indexName, k -> new TreeMap<>())
                       .put(rs.getInt("ORDINAL_POSITION"), rs.getString("COLUMN_NAME"));
            }
        }
        return byIndex.isEmpty() ? new TreeMap<>() : byIndex.firstEntry().getValue();
    }

    private record RowCompareResult(String query, List<DifferenceDetail> diffs, boolean interrupted) {
        static RowCompareResult ok(String query, List<DifferenceDetail> diffs) {
            return new RowCompareResult(query, diffs, false);
        }
        static RowCompareResult interrupted(String query) {
            return new RowCompareResult(query, List.of(), true);
        }
    }

    private RowCompareResult compareRecords(
            String tableName, DataSource ds1, DataSource ds2,
            List<String> keyColumns, List<ColumnMetadata> columns,
            String name1, String name2, long totalCount, long maxRows, int fetchSize,
            int queryTimeoutSeconds) throws SQLException {

        String orderBy = buildOrderBy(keyColumns, columns);
        String query = "SELECT * FROM \"" + tableName.toUpperCase() + "\" ORDER BY " + orderBy;
        log.info("Executing query for table '{}': {}", tableName, query);

        try (var conn1 = ds1.getConnection();
             var conn2 = ds2.getConnection();
             var stmt1 = conn1.createStatement();
             var stmt2 = conn2.createStatement()) {

            stmt1.setFetchSize(fetchSize);
            stmt2.setFetchSize(fetchSize);
            if (queryTimeoutSeconds > 0) {
                stmt1.setQueryTimeout(queryTimeoutSeconds);
                stmt2.setQueryTimeout(queryTimeoutSeconds);
            }

            try (var rs1 = stmt1.executeQuery(query);
                 var rs2 = stmt2.executeQuery(query)) {

                boolean has1 = rs1.next();
                boolean has2 = rs2.next();
                long rowPosition = 0;
                long logStep = 1000L;

                while (has1 || has2) {
                    rowPosition++;
                    if (maxRows > 0 && rowPosition > maxRows) {
                        log.warn("Table '{}': row scan interrupted at {} rows (COMPARE_MAX_ROWS={})",
                                tableName, maxRows, maxRows);
                        return RowCompareResult.interrupted(query);
                    }
                    if (totalCount > 0 && rowPosition % logStep == 0) {
                        log.info("Table '{}': {}% ({}/{} rows)", tableName,
                                rowPosition * 100 / totalCount, rowPosition, totalCount);
                    }
                    if (!has1) {
                        return RowCompareResult.ok(query, List.of(new DifferenceDetail(
                                DifferenceDetail.Category.ONLY_IN_SOURCE2,
                                "Row #%d — %s only".formatted(rowPosition, name2))));
                    } else if (!has2) {
                        return RowCompareResult.ok(query, List.of(new DifferenceDetail(
                                DifferenceDetail.Category.ONLY_IN_SOURCE1,
                                "Row #%d — %s only".formatted(rowPosition, name1))));
                    } else if (!keyColumns.isEmpty()) {
                        int cmp = compareKeyColumns(rs1, rs2, keyColumns);
                        if (cmp < 0) {
                            return RowCompareResult.ok(query, List.of(new DifferenceDetail(
                                    DifferenceDetail.Category.ONLY_IN_SOURCE1,
                                    "Row #%d — %s only".formatted(rowPosition, name1))));
                        } else if (cmp > 0) {
                            return RowCompareResult.ok(query, List.of(new DifferenceDetail(
                                    DifferenceDetail.Category.ONLY_IN_SOURCE2,
                                    "Row #%d — %s only".formatted(rowPosition, name2))));
                        } else {
                            String mismatch = firstMismatchField(rs1, rs2, columns, keyColumns);
                            if (mismatch != null) {
                                return RowCompareResult.ok(query, List.of(new DifferenceDetail(
                                        DifferenceDetail.Category.ROW_DATA_MISMATCH,
                                        "Row #%d%s".formatted(rowPosition, mismatch))));
                            }
                            has1 = rs1.next();
                            has2 = rs2.next();
                        }
                    } else {
                        // No unique index — positional comparison
                        String mismatch = firstMismatchField(rs1, rs2, columns, List.of());
                        if (mismatch != null) {
                            return RowCompareResult.ok(query, List.of(new DifferenceDetail(
                                    DifferenceDetail.Category.ROW_DATA_MISMATCH,
                                    "Row #%d%s".formatted(rowPosition, mismatch))));
                        }
                        has1 = rs1.next();
                        has2 = rs2.next();
                    }
                }
            }
        }

        return RowCompareResult.ok(query, List.of());
    }

    private String buildOrderBy(List<String> keyColumns, List<ColumnMetadata> columns) {
        if (!keyColumns.isEmpty()) {
            return keyColumns.stream()
                    .map(col -> "\"" + col.toUpperCase() + "\"")
                    .collect(Collectors.joining(", "));
        }
        return columns.stream()
                .map(col -> "\"" + col.name().toUpperCase() + "\"")
                .collect(Collectors.joining(", "));
    }

    private int compareKeyColumns(ResultSet rs1, ResultSet rs2, List<String> keyColumns)
            throws SQLException {
        for (String col : keyColumns) {
            int cmp = compareValues(rs1.getObject(col), rs2.getObject(col));
            if (cmp != 0) return cmp;
        }
        return 0;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns a formatted string describing the first column that differs between the two rows,
     * or {@code null} if all non-key columns are equal.
     */
    private String firstMismatchField(ResultSet rs1, ResultSet rs2,
            List<ColumnMetadata> columns, List<String> keyColumns) throws SQLException {
        for (var col : columns) {
            if (keyColumns.contains(col.name())) continue;
            Object v1 = rs1.getObject(col.name());
            Object v2 = rs2.getObject(col.name());
            if (!Objects.equals(v1, v2)) {
                return " — '%s': src1=%s, src2=%s".formatted(col.name(), v1, v2);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private int compareValues(Object v1, Object v2) {
        if (v1 == null && v2 == null) return 0;
        if (v1 == null) return -1;
        if (v2 == null) return 1;
        if (v1 instanceof Number && v2 instanceof Number) {
            return new BigDecimal(v1.toString()).compareTo(new BigDecimal(v2.toString()));
        }
        if (v1 instanceof Comparable c) {
            try { return c.compareTo(v2); }
            catch (ClassCastException ignored) { /* fall through */ }
        }
        return v1.toString().compareTo(v2.toString());
    }

    private HikariDataSource createDataSource(DataSourceConfig config, int threadPoolSize) {
        var hc = new HikariConfig();
        hc.setJdbcUrl(config.jdbcUrl());
        hc.setUsername(config.username());
        hc.setPassword(config.password());
        if (config.driverClassName() != null && !config.driverClassName().isBlank()) {
            hc.setDriverClassName(config.driverClassName());
        }

        // AS400 driver doesn't implement isValid(), so we need a test query
        if (config.driverClassName() != null && config.driverClassName().contains("as400")) {
            hc.setConnectionTestQuery("VALUES 1");
        } else {
            hc.setConnectionTestQuery("SELECT 1");
        }

        // Each thread needs at most one connection per datasource at a time
        hc.setMaximumPoolSize(threadPoolSize);
        hc.setConnectionTimeout(30_000);
        return new HikariDataSource(hc);
    }
}
