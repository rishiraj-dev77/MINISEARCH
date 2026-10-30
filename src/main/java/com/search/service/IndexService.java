package com.search.service;

import com.search.dao.DocumentDao;
import com.search.index.InvertedIndex;
import com.search.model.Document;
import com.search.util.Tokenizer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/**
 * Owns the {@link InvertedIndex} and drives the indexing pipeline:
 * tokenize -> inverted index -> autocomplete trie.
 *
 * <p>The inverted index is internally thread-safe (ConcurrentHashMap), so per-document
 * {@link #indexDocument} calls are lock-free. A {@link ReentrantReadWriteLock} guards
 * full rebuilds so a search reading the index sees a consistent snapshot, and so the
 * cache-invalidation hook fires exactly once per rebuild.
 */
public class IndexService {

    private final InvertedIndex index = new InvertedIndex();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final AutocompleteService autocomplete;
    private final DocumentDao documentDao;
    // fired when the index changes (so SearchService can drop stale cached results)
    private Consumer<Void> cacheInvalidationListener = v -> {};

    public IndexService(AutocompleteService autocomplete, DocumentDao documentDao) {
        this.autocomplete = autocomplete;
        this.documentDao = documentDao;
    }

    /** Register a callback that drops cached search results when the index changes. */
    public void setCacheInvalidationListener(Consumer<Void> listener) {
        this.cacheInvalidationListener = listener == null ? v -> {} : listener;
    }

    /**
     * Index one document into the inverted index and seed the autocomplete trie
     * with the document's terms (weighted by term frequency across the corpus).
     */
    public void indexDocument(Document doc) {
        index.indexDocument(doc);
        seedAutocompleteFrom(doc);
    }

    /** Index many documents (e.g. a bulk DB load). */
    public void indexAll(List<Document> docs) {
        for (Document d : docs) {
            index.indexDocument(d);
        }
        // seed the trie once with aggregate term frequencies for efficiency
        Map<String, Integer> aggregate = aggregateTermFrequencies(docs);
        aggregate.forEach(autocomplete::registerTerm);
        autocomplete.buildTopKCache();
    }

    /**
     * Full rebuild: clear index + trie, reload everything from the DB, reindex.
     * Used by Main's "reindex" command and after bulk inserts.
     */
    public List<Document> reindex() throws java.sql.SQLException {
        lock.writeLock().lock();
        try {
            // clear the in-memory structures; the DB is the source of truth
            clear();
            List<Document> docs = documentDao.findAll();
            indexAll(docs);
            cacheInvalidationListener.accept(null);
            return docs;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Expose the index for search reads (e.g. {@link SearchService}). */
    public InvertedIndex getIndex() {
        return index;
    }

    public void clear() {
        index.clear();
        autocomplete.clear();
    }

    // ---- internals ----

    private void seedAutocompleteFrom(Document doc) {
        List<String> tokens = Tokenizer.tokenize(doc.getTitle() + " " + doc.getContent());
        Map<String, Integer> tf = new HashMap<>();
        for (String t : tokens) tf.merge(t, 1, Integer::sum);
        tf.forEach(autocomplete::registerTerm);
    }

    private Map<String, Integer> aggregateTermFrequencies(List<Document> docs) {
        Map<String, Integer> agg = new HashMap<>();
        for (Document d : docs) {
            for (String t : Tokenizer.tokenize(d.getTitle() + " " + d.getContent())) {
                agg.merge(t, 1, Integer::sum);
            }
        }
        return agg;
    }
}
