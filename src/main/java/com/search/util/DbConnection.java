package com.search.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Thin H2 connection provider.
 *
 * Uses an in-memory database named "searchdb" kept alive for the life of the JVM
 * via {@code DB_CLOSE_DELAY=-1}, so multiple {@link #getConnection()} calls share
 * the same schema and data. Swap the URL for {@code jdbc:mysql://...} or
 * {@code jdbc:postgresql://...} to run against a real database; the DAOs use
 * plain JDBC so no other code needs to change.
 */
public final class DbConnection {

    private static final String URL =
            "jdbc:h2:mem:searchdb;DB_CLOSE_DELAY=-1;MODE=MySQL";
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    private DbConnection() {}

    /** Open a new connection. Caller is responsible for closing it (try-with-resources). */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    /** JDBC URL in use (for logging / README setup notes). */
    public static String getUrl() { return URL; }
}
