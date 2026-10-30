package com.search;

import com.search.dao.DocumentDao;
import com.search.dao.SchemaInitializer;
import com.search.dao.SearchHistoryDao;
import com.search.index.InvertedIndex;
import com.search.model.Document;
import com.search.model.SearchResult;
import com.search.service.AutocompleteService;
import com.search.service.IndexService;
import com.search.service.SearchService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SearchServiceTest {

    private static IndexService indexService;
    private static SearchService searchService;
    private static AutocompleteService autocomplete;
    private static DocumentDao documentDao;

    @BeforeAll
    static void setup() throws Exception {
        SchemaInitializer.init();
        documentDao = new DocumentDao();
        // clear and seed a known corpus for deterministic assertions
        try (var conn = com.search.util.DbConnection.getConnection();
             var st = conn.createStatement()) {
            st.execute("DELETE FROM documents");
            st.execute("DELETE FROM search_history");
            st.execute("DELETE FROM query_frequency");
        }
        documentDao.insertAll(List.of(
                new Document("Java Streams Tutorial", "Java streams process collections declaratively", "programming"),
                new Document("JavaScript Basics", "JavaScript is the language of the web", "programming"),
                new Document("Java Concurrency Guide", "Java threads locks and executors for concurrency", "programming"),
                new Document("Python for Data Science", "Python libraries for data science and machine learning", "programming"),
                new Document("Healthy Eating Habits", "Eat more vegetables fruits and whole grains for health", "health")
        ));

        autocomplete = new AutocompleteService(5);
        indexService = new IndexService(autocomplete, documentDao);
        searchService = new SearchService(indexService, autocomplete, new SearchHistoryDao(), 64);
        indexService.reindex();
        autocomplete.buildTopKCache();
    }

    @AfterAll
    static void tearDown() {
        if (searchService != null) searchService.close();
    }

    @Test
    void searchReturnsRankedResults() {
        List<SearchResult> results = searchService.search("java", 10);
        assertFalse(results.isEmpty());
        // docs mentioning java must be returned
        assertTrue(results.stream().anyMatch(r -> r.getDocument().getTitle().contains("Java Streams")));
        assertTrue(results.stream().anyMatch(r -> r.getDocument().getTitle().contains("Java Concurrency")));
        // results are ordered by descending score
        for (int i = 1; i < results.size(); i++) {
            assertTrue(results.get(i - 1).getScore() >= results.get(i).getScore(),
                    "results should be in descending score order");
        }
    }

    @Test
    void andMatchRequiresAllTerms() {
        List<SearchResult> results = searchService.search("java concurrency", 10, InvertedIndex.MatchMode.AND);
        // only "Java Concurrency Guide" has both java and concurrency
        assertEquals(1, results.size());
        assertTrue(results.get(0).getDocument().getTitle().contains("Java Concurrency"));
    }

    @Test
    void orMatchReturnsAnyTerm() {
        List<SearchResult> results = searchService.search("python health", 10, InvertedIndex.MatchMode.OR);
        // python doc + health doc
        assertTrue(results.size() >= 2);
    }

    @Test
    void emptyAndUnknownQueriesReturnEmpty() {
        assertTrue(searchService.search("", 10).isEmpty());
        assertTrue(searchService.search("   ", 10).isEmpty());
        assertTrue(searchService.search("zzzznonexistent", 10).isEmpty());
    }

    @Test
    void cacheRepeatsQueriesWithoutRecompute() {
        searchService.clearCache();
        searchService.search("java streams");
        long missesAfterFirst = searchService.getCacheMisses();
        searchService.search("java streams");
        searchService.search("java streams");
        // second and third calls should be cache hits
        assertEquals(missesAfterFirst, searchService.getCacheMisses(),
                "repeated query should not cause additional cache misses");
        assertTrue(searchService.getCacheHits() >= 2);
    }

    @Test
    void topKTruncatesResults() {
        List<SearchResult> results = searchService.search("java", 2);
        assertEquals(2, results.size());
    }

    @Test
    void reindexInvalidatesCache() throws Exception {
        searchService.clearCache();
        searchService.search("java", 10);
        assertTrue(searchService.getCacheSize() > 0);
        indexService.reindex();
        assertEquals(0, searchService.getCacheSize(), "cache should be cleared on reindex");
    }

    @Test
    void autocompleteSuggestsByFrequency() {
        // "java" appears in multiple docs and was registered; suggestions for "jav" should include java terms
        List<com.search.model.Suggestion> s = autocomplete.suggest("jav", 10);
        assertFalse(s.isEmpty());
        assertTrue(s.stream().anyMatch(x -> x.getWord().equals("java")));
    }
}
