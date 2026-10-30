package com.search;

import com.search.index.Trie;
import com.search.model.Suggestion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrieTest {

    @Test
    void insertsAndSearchesPrefix() {
        Trie trie = new Trie();
        trie.insert("java", 5);
        trie.insert("javascript", 3);
        trie.insert("java streams", 2);
        trie.insert("python", 10);

        assertTrue(trie.contains("java"));
        assertTrue(trie.contains("javascript"));
        assertFalse(trie.contains("jav"));
        assertEquals(4, trie.size());
    }

    @Test
    void suggestionsOrderedByFrequencyThenAlpha() {
        Trie trie = new Trie();
        trie.insert("java", 5);
        trie.insert("javascript", 3);
        trie.insert("java streams", 2);
        trie.insert("javadoc", 3); // same freq as javascript -> alpha tiebreak

        List<Suggestion> s = trie.getSuggestions("java", 10);
        // java (5) first, then javascript (3) before javadoc (3) by alpha, then java streams (2)
        assertEquals("java", s.get(0).getWord());
        assertEquals(5, s.get(0).getFrequency());
        assertEquals("javadoc", s.get(1).getWord());
        assertEquals("javascript", s.get(2).getWord());
        assertEquals("java streams", s.get(3).getWord());
    }

    @Test
    void topKTruncatesResults() {
        Trie trie = new Trie();
        trie.insert("a1", 1);
        trie.insert("a2", 2);
        trie.insert("a3", 3);
        trie.insert("a4", 4);
        trie.insert("a5", 5);

        List<Suggestion> s = trie.getSuggestions("a", 2);
        assertEquals(2, s.size());
        assertEquals("a5", s.get(0).getWord());
        assertEquals("a4", s.get(1).getWord());
    }

    @Test
    void emptyOrMissingPrefix() {
        Trie trie = new Trie();
        trie.insert("hello", 1);
        // empty prefix returns from root -> all words
        assertEquals(1, trie.getSuggestions("", 10).size());
        // missing prefix -> empty
        assertTrue(trie.getSuggestions("xyz", 10).isEmpty());
        // k <= 0 -> empty
        assertTrue(trie.getSuggestions("h", 0).isEmpty());
    }

    @Test
    void frequenciesAccumulate() {
        Trie trie = new Trie();
        trie.insert("java", 1);
        trie.insert("java", 1);
        trie.insert("java", 3);
        List<Suggestion> s = trie.getSuggestions("java", 1);
        assertEquals(1, s.size());
        assertEquals(5, s.get(0).getFrequency());
    }

    @Test
    void precomputedCacheMatchesDfs() {
        Trie trie = new Trie();
        trie.insert("java", 5);
        trie.insert("javascript", 3);
        trie.insert("java streams", 2);
        trie.insert("python", 10);
        trie.precomputeTopK(5);

        List<Suggestion> dfs = trie.getSuggestions("java", 5);
        List<Suggestion> cached = trie.getCachedSuggestions("java", 5);
        assertEquals(dfs, cached);
    }

    @Test
    void clearResetsTrie() {
        Trie trie = new Trie();
        trie.insert("java", 5);
        trie.precomputeTopK(5);
        assertEquals(1, trie.size());
        trie.clear();
        assertEquals(0, trie.size());
        assertTrue(trie.getSuggestions("java", 5).isEmpty());
    }
}
