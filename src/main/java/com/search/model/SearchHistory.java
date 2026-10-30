package com.search.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * One row from the {@code search_history} table.
 * Used for trending and zero-result analysis.
 */
public class SearchHistory {
    private final int id;
    private final String query;
    private final int resultCount;
    private final LocalDateTime searchedAt;

    public SearchHistory(int id, String query, int resultCount, LocalDateTime searchedAt) {
        this.id = id;
        this.query = query;
        this.resultCount = resultCount;
        this.searchedAt = searchedAt;
    }

    public int getId() { return id; }
    public String getQuery() { return query; }
    public int getResultCount() { return resultCount; }
    public LocalDateTime getSearchedAt() { return searchedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SearchHistory h)) return false;
        return id == h.id && Objects.equals(query, h.query);
    }

    @Override
    public int hashCode() { return Objects.hash(id, query); }

    @Override
    public String toString() {
        return "SearchHistory{" + "id=" + id + ", query='" + query + '\'' +
                ", resultCount=" + resultCount + ", searchedAt=" + searchedAt + '}';
    }
}
