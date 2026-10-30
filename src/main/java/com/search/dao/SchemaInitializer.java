package com.search.dao;

import com.search.util.DbConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates the schema (documents, search_history, query_frequency) on startup.
 * Idempotent: uses {@code IF NOT EXISTS} so it is safe to re-run.
 *
 * <p>Mirrors the schema in the README. Indexes on {@code search_history(query)} and
 * {@code search_history(searched_at)} back the trending and time-windowed queries.
 */
public final class SchemaInitializer {

    private SchemaInitializer() {}

    public static void init() throws SQLException {
        try (Connection conn = DbConnection.getConnection();
             Statement st = conn.createStatement()) {

            st.execute("""
                CREATE TABLE IF NOT EXISTS documents (
                  id INT PRIMARY KEY AUTO_INCREMENT,
                  title VARCHAR(255) NOT NULL,
                  content CLOB,
                  category VARCHAR(100),
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS search_history (
                  id INT PRIMARY KEY AUTO_INCREMENT,
                  query VARCHAR(255) NOT NULL,
                  result_count INT,
                  searched_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_query ON search_history(query)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_time ON search_history(searched_at)");

            st.execute("""
                CREATE TABLE IF NOT EXISTS query_frequency (
                  query VARCHAR(255) PRIMARY KEY,
                  freq INT DEFAULT 1,
                  last_searched TIMESTAMP
                )
            """);
        }
    }
}
