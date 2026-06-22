# tables-comparison

A Java 21 / Maven utility that compares the content of database tables across two datasources and produces a human-readable report.

## Features

| Step | What is checked | Result on mismatch |
|------|-----------------|--------------------|
| 1 — Record count | Total rows in each table | Immediately flagged; steps 2 & 3 skipped |
| 2 — Metadata | Column count, column names, JDBC types | Flagged; step 3 skipped |
| 3 — Row data | Every row, ordered by primary key | Individual differences listed (max 100) |

## Requirements

- Java 21+
- Maven 3.8+

## Project structure

```
src/main/java/com/tablescomparison/
├── Main.java                          # Entry point — loads config from .env
├── model/
│   ├── ConfigLoader.java              # Reads .env variables into config objects
│   ├── DataSourceConfig.java          # Connection parameters for one datasource
│   ├── ComparisonRequest.java         # List of tables + two DataSourceConfigs + optional schemas
│   ├── ColumnMetadata.java            # Column descriptor (name, type, position…)
│   ├── DifferenceDetail.java          # A single identified difference with a category
│   └── TableComparisonResult.java     # Sealed interface: Equal | Different | Error
├── comparator/
│   └── TableComparator.java           # Core comparison logic
└── reporter/
    ├── ComparisonReporter.java        # Reporter interface
    └── ConsoleReporter.java           # Prints a formatted report to stdout
```

## Quick start

### 1. Configure datasources

Copy `.env.template` to `.env` and fill in your credentials:

```bash
cp .env.template .env
```

Edit `.env`:

```env
# Source 1
SOURCE1_NAME=AS400 / ges_mu274
SOURCE1_JDBC_URL=jdbc:as400://host;libraries=MYLIB;naming=system
SOURCE1_USERNAME=user
SOURCE1_PASSWORD=password
SOURCE1_DRIVER_CLASS=com.ibm.as400.access.AS400JDBCDriver

# Source 2
SOURCE2_NAME=PG / bigdata
SOURCE2_JDBC_URL=jdbc:postgresql://host:5432/mydb?currentSchema="MYSCHEMA"
SOURCE2_USERNAME=user
SOURCE2_PASSWORD=password
SOURCE2_DRIVER_CLASS=org.postgresql.Driver

# Table names (comma-separated)
TABLE_NAMES=TABLE1,TABLE2,TABLE3

# Optional: schemas to try as fallback when metadata retrieval fails (comma-separated)
TABLE_SCHEMAS=SCHEMA1,SCHEMA2

# Optional: maximum rows to scan per table (0 or unset = no limit)
COMPARE_MAX_ROWS=10000
```

**Bundled JDBC drivers:** PostgreSQL (`postgresql 42.7.11`) and IBM AS400/iSeries (`jt400 10.4`) are already included as dependencies. For other databases, add the appropriate driver to `pom.xml`.

### 2. Run

```bash
# requires Java 21+ on PATH / JAVA_HOME
mvn compile exec:java -Dexec.mainClass=com.tablescomparison.Main
```

### 3. Use programmatically

```java
var source1 = new DataSourceConfig("Production", "jdbc:postgresql://...", "user", "password", null);
var source2 = new DataSourceConfig("Staging",    "jdbc:postgresql://...", "user", "password", null);

var request = new ComparisonRequest(
    List.of("CUSTOMERS", "ORDERS", "PRODUCTS"),
    source1,
    source2,
    List.of(),  // optional schema fallback list
    0L          // maxRows: 0 means no limit
);

var results = new TableComparator().compareAll(request);
new ConsoleReporter().report(results);

// or inspect results directly
for (TableComparisonResult result : results) {
    switch (result) {
        case TableComparisonResult.Equal eq ->
            System.out.println(eq.tableName() + " is equal (" + eq.recordCount() + " rows)");
        case TableComparisonResult.Different diff ->
            diff.differences().forEach(d -> System.out.println(d.category() + ": " + d.description()));
        case TableComparisonResult.Error err ->
            System.err.println("Error on " + err.tableName() + ": " + err.message());
    }
}
```

## Sample output

```
================================================================
  TABLE COMPARISON REPORT
================================================================

  Table  : CUSTOMERS
  Status : ✓ EQUAL (1,500 records)
----------------------------------------------------------------
  Table  : ORDERS
  Status : ✗ DIFFERENT
  Details:
    [RECORD_COUNT        ] Record count differs: Production=10000, Staging=9999
----------------------------------------------------------------
  Table  : PRODUCTS
  Status : ✗ DIFFERENT
  Details:
    [ROW_DATA_MISMATCH   ] Row [ID=42] column 'PRICE': Production='9.99', Staging='10.99'
    [ONLY_IN_SOURCE1     ] Production only — {ID=99, NAME=Widget, PRICE=5.00}
----------------------------------------------------------------

  Summary: 3 table(s) — ✓ 1 equal  ✗ 2 different  ⚠ 0 error(s)
================================================================
```

## Difference categories

| Category | Description |
|----------|-------------|
| `RECORD_COUNT` | Row counts differ between the two sources |
| `COLUMN_COUNT` | Number of columns differs |
| `COLUMN_NAME` | Column name differs at the same ordinal position |
| `COLUMN_TYPE` | Column JDBC type differs (same name, different type) |
| `ONLY_IN_SOURCE1` | Row exists only in source 1 (identified by PK) |
| `ONLY_IN_SOURCE2` | Row exists only in source 2 (identified by PK) |
| `ROW_DATA_MISMATCH` | Same PK, but one or more column values differ |

## Running tests

```bash
mvn test
```

Tests use an in-memory H2 database — no external infrastructure required.
