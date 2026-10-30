package com.search.service;

import com.search.cache.LRUCache;
import com.search.dao.SearchHistoryDao;
import com.search.index.InvertedIndex;
import com.search.index.TopKSelector;
import com.search.model.SearchResult;
import com.search.util.Tokenizer;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Search orchestration: tokenize -> cache check -> inverted index lookup -> TF-IDF rank
 * -> top-K -> cache write -> async history write -> autocomplete frequency bump.
 *
 * <p>Why async history: the DB insert (search_history + query_frequency) is the only
 * I/O on the hot path and would add latency to every search. We hand it to a single
 * background worker fed by a bounded queue, so the search response returns as soon as
 * results are ready. Counters ({@link LongAdder}/{@link AtomicInteger}) track cache
 * hits/misses for the benchmark and README without contention.
 */
public class SearchService implements AutoCloseable {

    /** Cache stores a canonical top-N so requests with smaller K reuse it. */
    private static final int CACHE_TOP = 50;

    private final IndexService indexService;
    private final AutocompleteService autocomplete;
    private final SearchHistoryDao historyDao;

    /** query|mode -> cached top results */
    private final LRUCache<String, List<SearchResult>> cache;

    // async history writer
    private final LinkedBlockingQueue<HistoryTask> historyQueue = new LinkedBlockingQueue<>(10_000);
    private final ExecutorService historyWriter = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "search-history-writer");
        t.setDaemon(true);
        return t;
    });

    // contention-free counters for stats / benchmark
    private final LongAdder cacheHits = new LongAdder();
    private final LongAdder cacheMisses = new LongAdder();
    private final LongAdder searches = new LongAdder();
    private final AtomicInteger historyDropped = new AtomicInteger();

    public SearchService(IndexService indexService,
                          AutocompleteService autocomplete,
                          SearchHistoryDao historyDao,
                          int cacheCapacity) {
        this.indexService = indexService;
        this.autocomplete = autocomplete;
        this.historyDao = historyDao;
        this.cache = new LRUCache<>(cacheCapacity);
        // wire cache invalidation from the index side
        this.indexService.setCacheInvalidationListener(v -> cache.clear());
        // start the worker that drains the history queue
        this.historyWriter.submit(this::drainHistoryLoop);
    }

    /** AND search (default semantics). */
    public List<SearchResult> search(String query, int k) {
        return search(query, k, InvertedIndex.MatchMode.AND);
    }

    /**
     * Run a search.
     * @param query raw user query
     * @param k     number of results to return
     * @param mode  AND = all terms present, OR = any term present
     */
    public List<SearchResult> search(String query, int k, InvertedIndex.MatchMode mode) {
        if (query == null || query.isBlank() || k <= 0) return List.of();
        searches.increment();

        List<String> tokens = Tokenizer.tokenize(query);
        if (tokens.isEmpty()) return List.of();

        String cacheKey = tokens.toString() + "|" + mode;

        // 1. cache check
        List<SearchResult> cached = cache.get(cacheKey);
        if (cached != null) {
            cacheHits.increment();
            recordHistoryAsync(query, cached.size());
            autocomplete.registerQuery(query);
            return slice(cached, k);
        }
        cacheMisses.increment();

        // 2. inverted index lookup (intersection or union of posting lists)
        InvertedIndex index = indexService.getIndex();
        List<Integer> docIds = index.lookup(tokens, mode);
        if (docIds.isEmpty()) {
            recordHistoryAsync(query, 0);
            return List.of();
        }

        // 3. TF-IDF ranking over the matching doc ids
        List<SearchResult> ranked = index.rank(docIds, tokens);

        // 4. top-K via min-heap (and cache the canonical top-N)
        List<SearchResult> top = TopKSelector.topK(ranked, Math.max(k, CACHE_TOP));
        cache.put(cacheKey, top);

        // 5. async history + autocomplete frequency bump
        recordHistoryAsync(query, top.size());
        autocomplete.registerQuery(query);

        return slice(top, k);
    }

    /** Convenience: default top-10. */
    public List<SearchResult> search(String query) {
        return search(query, 10);
    }

    // ---- stats ----

    public long getSearches() { return searches.sum(); }
    public long getCacheHits() { return cacheHits.sum(); }
    public long getCacheMisses() { return cacheMisses.sum(); }
    public int getCacheSize() { return cache.size(); }
    public int getHistoryQueueDepth() { return historyQueue.size(); }
    public int getHistoryDropped() { return historyDropped.get(); }

    /** Drop all cached results (benchmark / manual invalidation). */
    public void clearCache() { cache.clear(); }

    // ---- shutdown ----

    @Override
    public void close() {
        historyWriter.shutdown();
        try {
            // flush remaining history before exit
            historyWriter.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- internals ----

    private void recordHistoryAsync(String query, int resultCount) {
        HistoryTask task = new HistoryTask(query, resultCount);
        if (!historyQueue.offer(task)) {
            // queue full (backpressure) - drop and count rather than block the search path
            historyDropped.incrementAndGet();
        }
    }

    /** Worker loop: pull from the queue and write to the DB. */
    private void drainHistoryLoop() {
        while (true) {
            try {
                HistoryTask task = historyQueue.poll(250, TimeUnit.MILLISECONDS);
                if (task != null) {
                    historyDao.record(task.query, task.resultCount);
                }
            } catch (InterruptedException e) {
                // drain remaining tasks then exit
                drainRemaining();
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ex) {
                // a single bad row should not kill the writer
                System.err.println("[search-history-writer] error: " + ex.getMessage());
            }
        }
    }

    private void drainRemaining() {
        HistoryTask t;
        while ((t = historyQueue.poll()) != null) {
            try { historyDao.record(t.query, t.resultCount); }
            catch (Exception ignored) {}
        }
    }

    private static List<SearchResult> slice(List<SearchResult> list, int k) {
        return list.size() <= k ? list : new java.util.ArrayList<>(list.subList(0, k));
    }

    private record HistoryTask(String query, int resultCount) {}
}
