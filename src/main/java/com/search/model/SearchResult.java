package com.search.model;

import java.util.Objects;

/**
 * A single ranked search hit.
 * Score is computed by the ranking strategy (term frequency or TF-IDF).
 */
public class SearchResult {
    private final Document document;
    private final double score;

    public SearchResult(Document document, double score) {
        this.document = document;
        this.score = score;
    }

    public Document getDocument() { return document; }
    public double getScore() { return score; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SearchResult r)) return false;
        return Objects.equals(document, r.document) && Double.compare(r.score, score) == 0;
    }

    @Override
    public int hashCode() { return Objects.hash(document, score); }

    @Override
    public String toString() {
        return String.format("[%.4f] %s", score, document.getTitle());
    }
}
