package com.search.service;

import com.search.index.Trie;
import com.search.model.Suggestion;

import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Autocomplete backed by a {@link Trie}, guarded by a {@link ReentrantReadWriteLock}.
 *
 * <p>Why a read-write lock: autocomplete is read-heavy (many suggestions per insert).
 * A plain synchronized lock would serialize all reads; the RW lock lets concurrent
 * readers proceed while writers (new queries / reindex) take an exclusive lock.
 *
 * <p>The Trie stores both indexed document terms and the queries users actually search.
 * Query frequency is bumped on every search so the trie reflects what people look for,
 * not just what the corpus contains.
 */
public class AutocompleteService {

    private final Trie trie = new Trie();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final int defaultK;
    private volatile boolean cacheBuilt = false;

    public AutocompleteService(int defaultK) {
        this.defaultK = defaultK;
    }

    /** Add a corpus term with its document frequency. */
    public void registerTerm(String word, int frequency) {
        if (word == null || word.isBlank()) return;
        lock.writeLock().lock();
        try {
            trie.insert(word, frequency);
            cacheBuilt = false; // any write invalidates the top-K cache
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Bump a searched query's frequency by 1 (called by SearchService after a search). */
    public void registerQuery(String query) {
        if (query == null || query.isBlank()) return;
        String normalized = com.search.util.Tokenizer.normalizeWord(query);
        if (normalized.isBlank()) return;
        lock.writeLock().lock();
        try {
            trie.insert(normalized, 1);
            cacheBuilt = false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Top-K suggestions for a prefix (uses cached top-K if built). */
    public List<Suggestion> suggest(String prefix, int k) {
        if (prefix == null || prefix.isBlank()) return List.of();
        String normalized = com.search.util.Tokenizer.normalizeWord(prefix);
        int limit = k <= 0 ? defaultK : k;

        lock.readLock().lock();
        try {
            // read path: try the O(L) cached lookup first
            if (cacheBuilt) {
                return trie.getCachedSuggestions(normalized, limit);
            }
            return trie.getSuggestions(normalized, limit);
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Default top-K suggestion count. */
    public List<Suggestion> suggest(String prefix) {
        return suggest(prefix, defaultK);
    }

    /** Rebuild the per-node top-K cache so suggestions become O(L). Call after bulk inserts. */
    public void buildTopKCache() {
        lock.writeLock().lock();
        try {
            trie.precomputeTopK(defaultK);
            cacheBuilt = true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Number of distinct words/terms in the trie. */
    public int size() {
        lock.readLock().lock();
        try {
            return trie.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Drop everything (used by a full reindex). */
    public void clear() {
        lock.writeLock().lock();
        try {
            trie.clear();
            cacheBuilt = false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getDefaultK() { return defaultK; }
}
