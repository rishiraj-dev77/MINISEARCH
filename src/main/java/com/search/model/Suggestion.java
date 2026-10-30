package com.search.model;

import java.util.Objects;

/**
 * An autocomplete suggestion returned by the Trie.
 * Ordered by descending frequency, then alphabetically.
 */
public class Suggestion implements Comparable<Suggestion> {
    private final String word;
    private final int frequency;

    public Suggestion(String word, int frequency) {
        this.word = word;
        this.frequency = frequency;
    }

    public String getWord() { return word; }
    public int getFrequency() { return frequency; }

    @Override
    public int compareTo(Suggestion o) {
        // higher frequency first; tie-break alphabetically
        int c = Integer.compare(o.frequency, this.frequency);
        return c != 0 ? c : this.word.compareTo(o.word);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Suggestion s)) return false;
        return frequency == s.frequency && Objects.equals(word, s.word);
    }

    @Override
    public int hashCode() { return Objects.hash(word, frequency); }

    @Override
    public String toString() {
        return word + " (" + frequency + ")";
    }
}
