package com.smeup.ibmipgprobe.migration;

import com.smeup.ibmipgprobe.migration.comparator.TableComparator;
import com.smeup.ibmipgprobe.migration.model.DifferenceDetail;
import com.smeup.ibmipgprobe.migration.model.TableComparisonResult;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TableComparatorTest {

    private static final String DDL_CUSTOMERS =
            "CREATE TABLE CUSTOMERS (ID INT PRIMARY KEY, NAME VARCHAR(100), EMAIL VARCHAR(200))";
    private static final String DDL_ORDERS =
            "CREATE TABLE ORDERS (ID INT PRIMARY KEY, CUSTOMER_ID INT, AMOUNT DECIMAL(10,2))";

    private String db1;
    private String db2;
    private DataSource ds1;
    private DataSource ds2;

    @BeforeEach
    void setUp() {
        db1 = "testdb_" + UUID.randomUUID().toString().replace("-", "");
        db2 = "testdb_" + UUID.randomUUID().toString().replace("-", "");
        ds1 = hikari(db1);
        ds2 = hikari(db2);
    }

    @AfterEach
    void tearDown() {
        dropAll(db1);
        dropAll(db2);
        if (ds1 instanceof HikariDataSource hds) hds.close();
        if (ds2 instanceof HikariDataSource hds) hds.close();
    }

    // -------------------------------------------------------------------------
    // Equal tables
    // -------------------------------------------------------------------------

    @Test
    void equalTables_returnsEqual() throws SQLException {
        execute(db1, DDL_CUSTOMERS,
                "INSERT INTO CUSTOMERS VALUES (1,'Alice','alice@example.com')",
                "INSERT INTO CUSTOMERS VALUES (2,'Bob','bob@example.com')");
        execute(db2, DDL_CUSTOMERS,
                "INSERT INTO CUSTOMERS VALUES (1,'Alice','alice@example.com')",
                "INSERT INTO CUSTOMERS VALUES (2,'Bob','bob@example.com')");

        var results = new TableComparator().compareAll(List.of("CUSTOMERS"), ds1, ds2, "S1", "S2");

        assertThat(results).hasSize(1);
        assertThat(results.get(0)).isInstanceOf(TableComparisonResult.Equal.class);
        var eq = (TableComparisonResult.Equal) results.get(0);
        assertThat(eq.recordCount()).isEqualTo(2);
    }

    // -------------------------------------------------------------------------
    // Record count difference
    // -------------------------------------------------------------------------

    @Test
    void differentRecordCount_flaggedImmediately() throws SQLException {
        execute(db1, DDL_CUSTOMERS,
                "INSERT INTO CUSTOMERS VALUES (1,'Alice','alice@example.com')",
                "INSERT INTO CUSTOMERS VALUES (2,'Bob','bob@example.com')");
        execute(db2, DDL_CUSTOMERS,
                "INSERT INTO CUSTOMERS VALUES (1,'Alice','alice@example.com')");

        var results = new TableComparator().compareAll(List.of("CUSTOMERS"), ds1, ds2, "S1", "S2");

        assertThat(results).hasSize(1);
        assertThat(results.get(0)).isInstanceOf(TableComparisonResult.Different.class);
        var diff = (TableComparisonResult.Different) results.get(0);
        assertThat(diff.differences()).hasSize(1);
        assertThat(diff.differences().get(0).category()).isEqualTo(DifferenceDetail.Category.RECORD_COUNT);
    }

    // -------------------------------------------------------------------------
    // Metadata differences
    // -------------------------------------------------------------------------

    @Test
    void differentColumnCount_flaggedAsMetadataDiff() throws SQLException {
        execute(db1, "CREATE TABLE CUSTOMERS (ID INT PRIMARY KEY, NAME VARCHAR(100), EMAIL VARCHAR(200))");
        execute(db2, "CREATE TABLE CUSTOMERS (ID INT PRIMARY KEY, NAME VARCHAR(100))");
        // same row count
        execute(db1, "INSERT INTO CUSTOMERS VALUES (1,'Alice','a@b.com')");
        execute(db2, "INSERT INTO CUSTOMERS(ID,NAME) VALUES (1,'Alice')");

        var results = new TableComparator().compareAll(List.of("CUSTOMERS"), ds1, ds2, "S1", "S2");

        var diff = (TableComparisonResult.Different) results.get(0);
        assertThat(diff.differences().stream().map(DifferenceDetail::category))
                .contains(DifferenceDetail.Category.COLUMN_COUNT);
    }

    @Test
    void differentColumnType_flaggedAsMetadataDiff() throws SQLException {
        execute(db1, "CREATE TABLE ORDERS (ID INT PRIMARY KEY, AMOUNT DECIMAL(10,2))");
        execute(db2, "CREATE TABLE ORDERS (ID INT PRIMARY KEY, AMOUNT VARCHAR(50))");
        // same row count — no rows needed

        var results = new TableComparator().compareAll(List.of("ORDERS"), ds1, ds2, "S1", "S2");

        var diff = (TableComparisonResult.Different) results.get(0);
        assertThat(diff.differences().stream().map(DifferenceDetail::category))
                .contains(DifferenceDetail.Category.COLUMN_TYPE);
    }

    @Test
    void charVsVarchar_sameLength_flaggedAsTypeDiff() throws SQLException {
        // CHAR is in FIXED_LENGTH_CHARACTER_FAMILY, VARCHAR in VARYING_CHARACTER_FAMILY — different families → always flagged
        execute(db1, "CREATE TABLE PRODUCTS (ID INT PRIMARY KEY, CODE CHAR(10))");
        execute(db2, "CREATE TABLE PRODUCTS (ID INT PRIMARY KEY, CODE VARCHAR(10))");

        var results = new TableComparator().compareAll(List.of("PRODUCTS"), ds1, ds2, "S1", "S2");

        var diff = (TableComparisonResult.Different) results.get(0);
        assertThat(diff.differences()).hasSize(1);
        assertThat(diff.differences().get(0).category()).isEqualTo(DifferenceDetail.Category.COLUMN_TYPE);
    }

    @Test
    void charVsVarchar_differentLength_flaggedAsTypeDiff() throws SQLException {
        // CHAR and VARCHAR are different families; reported via the generic "type differs" branch
        execute(db1, "CREATE TABLE PRODUCTS (ID INT PRIMARY KEY, CODE CHAR(10))");
        execute(db2, "CREATE TABLE PRODUCTS (ID INT PRIMARY KEY, CODE VARCHAR(20))");

        var results = new TableComparator().compareAll(List.of("PRODUCTS"), ds1, ds2, "S1", "S2");

        var diff = (TableComparisonResult.Different) results.get(0);
        assertThat(diff.differences()).hasSize(1);
        assertThat(diff.differences().get(0).category()).isEqualTo(DifferenceDetail.Category.COLUMN_TYPE);
        assertThat(diff.differences().get(0).description()).contains("type differs");
    }

    @Test
    void numericSamePrecisionScale_notFlagged() throws SQLException {
        // DECIMAL and NUMERIC are the same numeric family; same precision/scale → no diff
        execute(db1, "CREATE TABLE ORDERS (ID INT PRIMARY KEY, AMOUNT DECIMAL(10,2))");
        execute(db2, "CREATE TABLE ORDERS (ID INT PRIMARY KEY, AMOUNT NUMERIC(10,2))");

        var results = new TableComparator().compareAll(List.of("ORDERS"), ds1, ds2, "S1", "S2");

        assertThat(results.get(0)).isInstanceOf(TableComparisonResult.Equal.class);
    }

    @Test
    void numericDifferentScale_flaggedAsTypeDiff() throws SQLException {
        // Same numeric family, but scales differ → COLUMN_TYPE diff
        execute(db1, "CREATE TABLE ORDERS (ID INT PRIMARY KEY, AMOUNT DECIMAL(10,2))");
        execute(db2, "CREATE TABLE ORDERS (ID INT PRIMARY KEY, AMOUNT NUMERIC(10,4))");

        var results = new TableComparator().compareAll(List.of("ORDERS"), ds1, ds2, "S1", "S2");

        var diff = (TableComparisonResult.Different) results.get(0);
        assertThat(diff.differences()).hasSize(1);
        assertThat(diff.differences().get(0).category()).isEqualTo(DifferenceDetail.Category.COLUMN_TYPE);
        assertThat(diff.differences().get(0).description()).contains("precision/scale");
    }

    // -------------------------------------------------------------------------
    // Row data differences
    // -------------------------------------------------------------------------

    @Test
    void differentRowData_flaggedAsRowMismatch() throws SQLException {
        execute(db1, DDL_CUSTOMERS,
                "INSERT INTO CUSTOMERS VALUES (1,'Alice','alice@example.com')",
                "INSERT INTO CUSTOMERS VALUES (2,'Bob','bob@example.com')");
        execute(db2, DDL_CUSTOMERS,
                "INSERT INTO CUSTOMERS VALUES (1,'Alice','alice@example.com')",
                "INSERT INTO CUSTOMERS VALUES (2,'Bobby','bob@example.com')");  // NAME differs

        var results = new TableComparator().compareAll(List.of("CUSTOMERS"), ds1, ds2, "S1", "S2");

        var diff = (TableComparisonResult.Different) results.get(0);
        assertThat(diff.differences().stream().map(DifferenceDetail::category))
                .contains(DifferenceDetail.Category.ROW_DATA_MISMATCH);
        assertThat(diff.differences().get(0).description()).startsWith("Row (");
        assertThat(diff.rowQuery()).contains("CUSTOMERS");
    }

    @Test
    void asymmetricRows_firstDifferenceFlagged() throws SQLException {
        // Key-based compare stops at the first difference found while iterating source2.
        // ID=3 (source2 only) is encountered before the source1-only ID=2 check, so only ONLY_IN_SOURCE2 is returned.
        execute(db1, DDL_ORDERS,
                "INSERT INTO ORDERS VALUES (1,10,100.00)",
                "INSERT INTO ORDERS VALUES (2,20,200.00)");
        execute(db2, DDL_ORDERS,
                "INSERT INTO ORDERS VALUES (1,10,100.00)",
                "INSERT INTO ORDERS VALUES (3,30,300.00)");  // ID=2 missing, ID=3 extra

        var results = new TableComparator().compareAll(List.of("ORDERS"), ds1, ds2, "S1", "S2");

        var diff = (TableComparisonResult.Different) results.get(0);
        var categories = diff.differences().stream().map(DifferenceDetail::category).toList();
        assertThat(categories).contains(DifferenceDetail.Category.ONLY_IN_SOURCE2);
    }

    @Test
    void multipleTablesReported() throws SQLException {
        // CUSTOMERS — equal
        execute(db1, DDL_CUSTOMERS, "INSERT INTO CUSTOMERS VALUES (1,'Alice','a@b.com')");
        execute(db2, DDL_CUSTOMERS, "INSERT INTO CUSTOMERS VALUES (1,'Alice','a@b.com')");
        // ORDERS — different
        execute(db1, DDL_ORDERS, "INSERT INTO ORDERS VALUES (1,1,50.00)", "INSERT INTO ORDERS VALUES (2,1,75.00)");
        execute(db2, DDL_ORDERS, "INSERT INTO ORDERS VALUES (1,1,50.00)");

        var results = new TableComparator().compareAll(List.of("CUSTOMERS", "ORDERS"), ds1, ds2, "S1", "S2");

        assertThat(results).hasSize(2);
        assertThat(results.get(0)).isInstanceOf(TableComparisonResult.Equal.class);
        assertThat(results.get(1)).isInstanceOf(TableComparisonResult.Different.class);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static DataSource hikari(String dbName) {
        var hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1");
        hc.setUsername("sa");
        hc.setPassword("");
        hc.setMaximumPoolSize(5);
        return new HikariDataSource(hc);
    }

    private static void execute(String dbName, String... sqls) throws SQLException {
        String url = "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
             Statement stmt = conn.createStatement()) {
            for (String sql : sqls) {
                stmt.execute(sql);
            }
        }
    }

    private static void dropAll(String dbName) {
        try {
            execute(dbName, "DROP ALL OBJECTS");
        } catch (SQLException ignored) { /* best-effort cleanup */ }
    }
}
