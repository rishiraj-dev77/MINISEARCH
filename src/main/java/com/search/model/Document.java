package com.search.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A document (or product) that gets indexed and searched.
 * Mirrors the {@code documents} SQL table.
 */
public class Document {
    private final int id;
    private final String title;
    private final String content;
    private final String category;
    private final LocalDateTime createdAt;

    public Document(int id, String title, String content, String category, LocalDateTime createdAt) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.category = category;
        this.createdAt = createdAt;
    }

    /** Convenience: known id, defaulted createdAt (used in tests). */
    public Document(int id, String title, String content, String category) {
        this(id, title, content, category, LocalDateTime.now());
    }

    /** Convenience constructor for documents not yet persisted (id assigned by DB). */
    public Document(String title, String content, String category) {
        this(-1, title, content, category, LocalDateTime.now());
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getCategory() { return category; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Document d)) return false;
        return id == d.id && Objects.equals(title, d.title);
    }

    @Override
    public int hashCode() { return Objects.hash(id, title); }

    @Override
    public String toString() {
        return "Document{" + "id=" + id + ", title='" + title + '\'' +
                ", category='" + category + '\'' + '}';
    }
}
