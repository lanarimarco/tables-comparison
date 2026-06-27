package com.smeup.ibmipgprobe;

/**
 * Connection configuration for a single datasource.
 *
 * @param name            human-readable label used in output (e.g. "AS400 / ges_mu274")
 * @param jdbcUrl         JDBC URL (e.g. "jdbc:postgresql://host:5432/mydb")
 * @param username        database username
 * @param password        database password
 * @param driverClassName fully-qualified JDBC driver class; may be null for auto-detection
 */
public record DataSourceConfig(
        String name,
        String jdbcUrl,
        String username,
        String password,
        String driverClassName) {

    public DataSourceConfig {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        if (jdbcUrl == null || jdbcUrl.isBlank()) throw new IllegalArgumentException("jdbcUrl must not be blank");
    }
}
