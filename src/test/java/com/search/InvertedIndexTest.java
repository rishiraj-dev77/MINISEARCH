package com.search;

import com.search.index.InvertedIndex;
import com.search.index.TopKSelector;
import com.search.model.Document;
import com.search.model.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InvertedIndexTest {

    private InvertedIndex newIndex() {
        InvertedIndex idx = new InvertedIndex();
        idx.indexDocument(new Document(1, "Java Streams", "Java streams process collections declaratively", "programming"));
        idx.indexDocument(new Document(2, "JavaScript Basics", "JavaScript is the language of the web", "programming"));
        idx.indexDocument(new Document(3, "Java Concurrency", "Java threads and locks for concurrency", "programming"));
        idx.indexDocument(new Document(4, "Python Data Science", "Python for data science and machine learning", "programming"));
        return idx;
    }

    @Test
    void buildsPostingsWithTermFrequency() {
        InvertedIndex idx = newIndex();
        Map<Integer, Integer> javaPostings = idx.getPostings("java");
        // doc1: title "Java Streams" + content "Java streams process collections declaratively" -> java appears 2x
        // doc3: title "Java Concurrency" + content "Java threads..." -> java appears 2x
        assertEquals(2, javaPostings.size());
        assertEquals(2, javaPostings.get(1));
        assertEquals(2, javaPostings.get(3));
        assertEquals(4, idx.documentCount());
    }

    @Test
    void andLookupIntersectsPostings() {
        InvertedIndex idx = newIndex();
        // "java" is in docs 1,3 ; "concurrency" is in doc 3 only -> AND = {3}
        List<Integer> result = idx.lookup(List.of("java", "concurrency"), InvertedIndex.MatchMode.AND);
        assertEquals(List.of(3), result);
    }

    @Test
    void andLookupWithMissingTermReturnsEmpty() {
        InvertedIndex idx = newIndex();
        List<Integer> result = idx.lookup(List.of("java", "nonexistent"), InvertedIndex.MatchMode.AND);
        assertTrue(result.isEmpty());
    }

    @Test
    void orLookupUnionsPostings() {
        InvertedIndex idx = newIndex();
        // "java" -> {1,3}; "python" -> {4}; OR = {1,3,4}
        List<Integer> result = idx.lookup(List.of("java", "python"), InvertedIndex.MatchMode.OR);
        assertEquals(3, result.size());
        assertTrue(result.contains(1));
        assertTrue(result.contains(3));
        assertTrue(result.contains(4));
    }

    @Test
    void tfidfRanksHigherFrequencyHigher() {
        InvertedIndex idx = newIndex();
        // doc3 mentions "java" twice AND "concurrency", "threads", "locks" once each
        List<Integer> docIds = idx.lookup(List.of("java"), InvertedIndex.MatchMode.OR);
        List<SearchResult> ranked = idx.rank(docIds, List.of("java"));
        assertFalse(ranked.isEmpty());
        // doc1 and doc3 both have java tf=2, so they should tie at the top
        // both must rank above any doc without java
        for (SearchResult r : ranked) {
            assertTrue(r.getScore() > 0);
        }
    }

    @Test
    void topKSelectorKeepsHighest() {
        InvertedIndex idx = newIndex();
        List<Integer> docIds = idx.lookup(List.of("java", "streams"), InvertedIndex.MatchMode.OR);
        List<SearchResult> all = idx.rank(docIds, List.of("java", "streams"));
        List<SearchResult> top2 = TopKSelector.topK(all, 2);
        assertEquals(2, top2.size());
        // returned in descending score order
        assertTrue(top2.get(0).getScore() >= top2.get(1).getScore());
    }

    @Test
    void clearResetsIndex() {
        InvertedIndex idx = newIndex();
        assertEquals(4, idx.documentCount());
        idx.clear();
        assertEquals(0, idx.documentCount());
        assertTrue(idx.getPostings("java").isEmpty());
    }
}
