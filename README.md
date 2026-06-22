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
- JDBC driver for your target database (add to `pom.xml`)

## Project structure

```
src/main/java/com/tablescomparison/
├── Main.java                          # Entry point — configure datasources here
├── model/
│   ├── DataSourceConfig.java          # Connection parameters for one datasource
│   ├── ComparisonRequest.java         # List of tables + two DataSourceConfigs
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

### 1. Add your JDBC driver

Add the driver for your database to `pom.xml`. Examples:

```xml
<!-- PostgreSQL -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <version>42.7.3</version>
</dependency>

<!-- MySQL -->
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <version>9.0.0</version>
</dependency>

<!-- Oracle -->
<dependency>
    <groupId>com.oracle.database.jdbc</groupId>
    <artifactId>ojdbc11</artifactId>
    <version>23.4.0.24.05</version>
</dependency>
```

### 2. Configure datasources

Edit `Main.java`:

```java
var source1 = new DataSourceConfig(
    "Production",
    "jdbc:postgresql://prod-host:5432/mydb",
    "user", "password",
    null   // driver auto-detected from URL
);

var source2 = new DataSourceConfig(
    "Staging",
    "jdbc:postgresql://staging-host:5432/mydb",
    "user", "password",
    null
);

var request = new ComparisonRequest(
    List.of("CUSTOMERS", "ORDERS", "PRODUCTS"),
    source1,
    source2
);
```

### 3. Run

```bash
# requires Java 21+ on PATH / JAVA_HOME
mvn compile exec:java -Dexec.mainClass=com.tablescomparison.Main

# or build a fat-jar first and run directly
mvn package -DskipTests
java -jar target/tables-comparison-1.0.0-SNAPSHOT.jar
```

### 4. Use programmatically

```java
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
