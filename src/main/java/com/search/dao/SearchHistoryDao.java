package com.search.dao;

import com.search.model.QueryStats;
import com.search.util.DbConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC DAO for {@code search_history} and {@code query_frequency}.
 *
 * <p>Search history is written through here, but the service hands inserts to a
 * background worker so the search response path stays fast (see
 * {@link com.search.service.SearchService}). The reading side powers the
 * trending endpoints using GROUP BY / time windows / HAVING.
 */
public class SearchHistoryDao {

    /**
     * Append one search to {@code search_history} and bump {@code query_frequency}
     * (upsert). Both writes happen in one connection; a failure rolls back both.
     */
    public void record(String query, int resultCount) throws SQLException {
        try (Connection conn = DbConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO search_history (query, result_count, searched_at) VALUES (?, ?, ?)")) {
                    ps.setString(1, query);
                    ps.setInt(2, resultCount);
                    ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
                    ps.executeUpdate();
                }
                upsertFrequency(conn, query);
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
        }
    }

    private void upsertFrequency(Connection conn, String query) throws SQLException {
        // Portable upsert (H2/MySQL/Postgres): read current freq, then update or insert.
        // Done inside the caller's transaction alongside the history insert.
        Integer current = null;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT freq FROM query_frequency WHERE query = ?")) {
            ps.setString(1, query);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) current = rs.getInt(1);
            }
        }
        if (current != null) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE query_frequency SET freq = freq + 1, last_searched = ? WHERE query = ?")) {
                ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
                ps.setString(2, query);
                ps.executeUpdate();
            }
        } else {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO query_frequency (query, freq, last_searched) VALUES (?, 1, ?)")) {
                ps.setString(1, query);
                ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
                ps.executeUpdate();
            }
        }
    }

    /** Top queries of all time: GROUP BY + COUNT, ordered desc. */
    public List<QueryStats> trendingAllTime(int limit) throws SQLException {
        String sql = """
                SELECT query, COUNT(*) AS cnt
                FROM search_history
                GROUP BY query
                ORDER BY cnt DESC
                LIMIT ?
                """;
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            return readStats(ps.executeQuery());
        }
    }

    /** Top queries in the last {@code hours} hours (24h demo case). */
    public List<QueryStats> trendingSince(int hours, int limit) throws SQLException {
        // Compute the cutoff in Java so the query is portable across H2/MySQL/PostgreSQL
        // (their INTERVAL syntax differs). A simple >= comparison on an indexed
        // searched_at column is fast and index-friendly.
        LocalDateTime cutoff = LocalDateTime.now().minusHours(hours);
        String sql = """
                SELECT query, COUNT(*) AS cnt
                FROM search_history
                WHERE searched_at >= ?
                GROUP BY query
                ORDER BY cnt DESC
                LIMIT ?
                """;
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(cutoff));
            ps.setInt(2, limit);
            return readStats(ps.executeQuery());
        }
    }

    /** Queries that returned no results - candidates to improve the index. */
    public List<QueryStats> zeroResultQueries(int limit) throws SQLException {
        String sql = """
                SELECT query, COUNT(*) AS cnt
                FROM search_history
                WHERE result_count = 0
                GROUP BY query
                ORDER BY cnt DESC
                LIMIT ?
                """;
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            return readStats(ps.executeQuery());
        }
    }

    /** Reads from the pre-aggregated {@code query_frequency} table (fast, single source). */
    public List<QueryStats> topQueriesFromFrequencyTable(int limit) throws SQLException {
        String sql = "SELECT query, freq FROM query_frequency ORDER BY freq DESC LIMIT ?";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            return readStats(ps.executeQuery());
        }
    }

    /** Category distribution of documents (GROUP BY on the documents table). */
    public List<QueryStats> categoryCounts() throws SQLException {
        String sql = "SELECT category, COUNT(*) AS cnt FROM documents GROUP BY category ORDER BY cnt DESC";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            return readStats(ps.executeQuery());
        }
    }

    private List<QueryStats> readStats(ResultSet rs) throws SQLException {
        List<QueryStats> out = new ArrayList<>();
        while (rs.next()) {
            out.add(new QueryStats(rs.getString(1), rs.getLong(2)));
        }
        return out;
    }
}
