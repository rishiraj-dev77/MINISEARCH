package com.search.dao;

import com.search.model.Document;
import com.search.util.DbConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC DAO for the {@code documents} table.
 *
 * <p>Uses try-with-resources and parameterized queries throughout (no string
 * interpolation of user values), which prevents SQL injection. Plain JDBC keeps
 * the SQL explicit - useful for the trending/window-function work later.
 */
public class DocumentDao {

    /**
     * Insert a document and assign the DB-generated id back onto a returned copy.
     * Returns the inserted document with its real id.
     */
    public Document insert(Document doc) throws SQLException {
        String sql = "INSERT INTO documents (title, content, category) VALUES (?, ?, ?)";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, doc.getTitle());
            ps.setString(2, doc.getContent());
            ps.setString(3, doc.getCategory());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return new Document(keys.getInt(1), doc.getTitle(),
                            doc.getContent(), doc.getCategory(), doc.getCreatedAt());
                }
            }
        }
        return doc;
    }

    /** Bulk insert; returns documents with assigned ids. */
    public List<Document> insertAll(List<Document> docs) throws SQLException {
        List<Document> inserted = new ArrayList<>(docs.size());
        for (Document d : docs) inserted.add(insert(d));
        return inserted;
    }

    public List<Document> findAll() throws SQLException {
        String sql = "SELECT id, title, content, category, created_at FROM documents";
        List<Document> out = new ArrayList<>();
        try (Connection conn = DbConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                out.add(map(rs));
            }
        }
        return out;
    }

    public Document findById(int id) throws SQLException {
        String sql = "SELECT id, title, content, category, created_at FROM documents WHERE id = ?";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        }
        return null;
    }

    public int count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM documents";
        try (Connection conn = DbConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static Document map(ResultSet rs) throws SQLException {
        return new Document(
                rs.getInt("id"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("category"),
                rs.getTimestamp("created_at") == null ? null
                        : rs.getTimestamp("created_at").toLocalDateTime());
    }
}
