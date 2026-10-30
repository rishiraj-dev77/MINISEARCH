package com.search.model;

import java.util.Objects;

/**
 * Aggregate stats for a query (used by trending endpoints).
 * {@code count} = how many times the query was searched.
 */
public class QueryStats {
    private final String query;
    private final long count;

    public QueryStats(String query, long count) {
        this.query = query;
        this.count = count;
    }

    public String getQuery() { return query; }
    public long getCount() { return count; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QueryStats q)) return false;
        return count == q.count && Objects.equals(query, q.query);
    }

    @Override
    public int hashCode() { return Objects.hash(query, count); }

    @Override
    public String toString() {
        return query + " -> " + count + " searches";
    }
}
