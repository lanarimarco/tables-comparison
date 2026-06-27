# ibmi-pg-probe

A Java 21 / Maven toolkit for discovering and diagnosing migration issues from IBM i DB2 to PostgreSQL.

## Toolkits

| Package | Entry point | Purpose |
|---------|------------|---------|
| `com.smeup.ibmipgprobe.migration` | `migration.Main` | Compare table contents across two datasources |
| `com.smeup.ibmipgprobe.reload` | `reload.SetllIssueBenchmark` | Benchmark SETLL-equivalent query patterns (UNION vs UNION ALL vs row-value) |

All toolkits share `SOURCE1_*` / `SOURCE2_*` datasource config from `.env`. Each toolkit uses its own prefix for additional settings (`COMPARE_*`, `SETLL_ISSUE_*`).

---

## Migration comparison toolkit

### Features

| Step | What is checked | Result on mismatch |
|------|-----------------|--------------------|
| 1 — Record count | Total rows in each table | Immediately flagged; steps 2 & 3 skipped |
| 2 — Metadata | Column count, column names, JDBC types | Flagged; step 3 skipped |
| 3 — Row data | Every row, ordered by primary key | Individual differences listed |

### Run

```bash
mvn compile exec:java -Dexec.mainClass=com.smeup.ibmipgprobe.migration.Main
```

### Project structure

```
src/main/java/com/smeup/ibmipgprobe/
├── DataSourceConfig.java              # Shared: connection parameters for one datasource
├── SharedConfig.java                  # Shared: loads SOURCE1/SOURCE2 from .env
├── migration/
│   ├── Main.java                      # Entry point — loads config from .env
│   ├── model/
│   │   ├── ConfigLoader.java          # Reads COMPARE_* and TABLE_* from .env
│   │   ├── ComparisonRequest.java     # List of tables + two DataSourceConfigs + options
│   │   ├── ColumnMetadata.java        # Column descriptor (name, type, position…)
│   │   ├── DifferenceDetail.java      # A single identified difference with a category
│   │   └── TableComparisonResult.java # Sealed interface: Equal | Different | Interrupted | Error
│   ├── comparator/
│   │   └── TableComparator.java       # Core comparison logic
│   └── reporter/
│       ├── ComparisonReporter.java    # Reporter interface
│       └── ConsoleReporter.java       # Prints a formatted report to stdout
└── reload/
    └── SetllIssueBenchmark.java       # SETLL-pattern JDBC benchmark
```

---

## Quick start

### 1. Configure datasources

Copy `.env.template` to `.env` and fill in your credentials:

```bash
cp .env.template .env
```

Edit `.env`:

```env
# Shared datasource config
SOURCE1_NAME=AS400 / ges_mu274
SOURCE1_JDBC_URL=jdbc:as400://host;libraries=MYLIB;naming=system
SOURCE1_USERNAME=user
SOURCE1_PASSWORD=password
SOURCE1_DRIVER_CLASS=com.ibm.as400.access.AS400JDBCDriver

SOURCE2_NAME=PG / bigdata
SOURCE2_JDBC_URL=jdbc:postgresql://host:5432/mydb?currentSchema="MYSCHEMA"
SOURCE2_USERNAME=user
SOURCE2_PASSWORD=password
SOURCE2_DRIVER_CLASS=org.postgresql.Driver

# Migration comparison toolkit
TABLE_NAMES=TABLE1,TABLE2,TABLE3
TABLE_SCHEMAS=SCHEMA1,SCHEMA2
COMPARE_MAX_ROWS=10000

# Setll Issue Benchmark
SETLL_ISSUE_ITERATIONS=5
```

**Bundled JDBC drivers:** PostgreSQL (`postgresql 42.7.11`) and IBM AS400/iSeries (`jt400 10.4`) are already included as dependencies.

### 2. Run the migration comparison

```bash
mvn compile exec:java -Dexec.mainClass=com.smeup.ibmipgprobe.migration.Main
```

### 3. Run the SETLL issue benchmark

```bash
mvn compile exec:java -Dexec.mainClass=com.smeup.ibmipgprobe.reload.SetllIssueBenchmark
```

---

## Sample comparison output

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
    [RECORD_COUNT        ] Record count differs: AS400=10000, PG=9999
----------------------------------------------------------------

  Summary: 2 table(s) — ✓ 1 equal  ✗ 1 different  ⚡ 0 interrupted  ⚠ 0 error(s)
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

## Requirements

- Java 21+
- Maven 3.8+

## Running tests

```bash
mvn test
```

Tests use an in-memory H2 database — no external infrastructure required.
