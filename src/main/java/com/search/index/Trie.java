package com.search.index;

import com.search.model.Suggestion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * A frequency-aware Trie for autocomplete.
 *
 * <p>Two retrieval modes:
 * <ol>
 *   <li>{@link #getSuggestions(String, int)} - DFS from the prefix node, collect all words,
 *       keep top-K via a min-heap. O(L + N log K) where N = words under the prefix.</li>
 *   <li>{@link #getCachedSuggestions(String, int)} - after {@link #precomputeTopK(int)}, each
 *       node carries its top-K, so lookup is O(L). Trade-off is more memory.</li>
 * </ol>
 *
 * <p>Not thread-safe by design; concurrency is handled by the service layer with a
 * {@link java.util.concurrent.locks.ReentrantReadWriteLock} so reads stay fast and
 * the data structure stays simple.
 */
public class Trie {

    private final TrieNode root = new TrieNode();

    public TrieNode getRoot() { return root; }

    /**
     * Insert (or re-frequency) a word.
     * Frequencies accumulate, so calling {@code insert("java", 1)} twice makes "java" freq 2.
     */
    public void insert(String word, int freq) {
        if (word == null || word.isEmpty()) return;
        TrieNode node = root;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            node = node.children.computeIfAbsent(c, k -> new TrieNode());
        }
        node.isEnd = true;
        node.frequency += freq;
    }

    /** Convenience: insert with frequency 1 (e.g. a single new query). */
    public void insert(String word) { insert(word, 1); }

    /** Return the node at the end of {@code prefix}, or null if the prefix is absent. */
    public TrieNode searchPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) return root;
        TrieNode node = root;
        for (int i = 0; i < prefix.length(); i++) {
            node = node.children.get(prefix.charAt(i));
            if (node == null) return null;
        }
        return node;
    }

    /** True if {@code word} was inserted. */
    public boolean contains(String word) {
        TrieNode node = searchPrefix(word);
        return node != null && node.isEnd;
    }

    /**
     * Top-K suggestions for {@code prefix} using DFS + min-heap.
     * Min-heap keeps the K largest frequencies seen so far in O(N log K),
     * better than fully sorting all words under the prefix.
     */
    public List<Suggestion> getSuggestions(String prefix, int k) {
        if (k <= 0) return List.of();
        TrieNode start = searchPrefix(prefix);
        if (start == null) return List.of();

        // Min-heap: smallest frequency on top so we can evict it in O(log K).
        PriorityQueue<Suggestion> minHeap = new PriorityQueue<>(
                Comparator.comparingInt(Suggestion::getFrequency)
                        .thenComparing(Suggestion::getWord).reversed()
                        .reversed() // net effect: min-heap by frequency, ties by reverse-alpha
        );

        // DFS collecting complete words. StringBuilder avoids string concat allocations.
        dfsCollect(start, new StringBuilder(prefix), minHeap, k);

        // Drain the heap sorted descending by frequency.
        List<Suggestion> result = new ArrayList<>(minHeap);
        result.sort(null); // Suggestion natural order = freq desc, then alpha asc
        return result;
    }

    private void dfsCollect(TrieNode node, StringBuilder prefix,
                            PriorityQueue<Suggestion> minHeap, int k) {
        if (node.isEnd && node.frequency > 0) {
            Suggestion s = new Suggestion(prefix.toString(), node.frequency);
            if (minHeap.size() < k) {
                minHeap.offer(s);
            } else if (s.getFrequency() > minHeap.peek().getFrequency()
                    || (s.getFrequency() == minHeap.peek().getFrequency()
                        && s.getWord().compareTo(minHeap.peek().getWord()) < 0)) {
                minHeap.poll();
                minHeap.offer(s);
            }
        }
        for (Map.Entry<Character, TrieNode> e : node.children.entrySet()) {
            prefix.append(e.getKey());
            dfsCollect(e.getValue(), prefix, minHeap, k);
            prefix.deleteCharAt(prefix.length() - 1);
        }
    }

    /**
     * Precompute top-K at every node so autocomplete becomes O(L).
     * Each node's cache = top-K of (its own word if it ends here) merged with all children's caches.
     * Call once after a bulk insert; rebuild after frequent updates.
     */
    public void precomputeTopK(int k) {
        precompute(root, k, "");
    }

    private List<Suggestion> precompute(TrieNode node, int k, String prefix) {
        List<Suggestion> acc = new ArrayList<>();
        if (node.isEnd && node.frequency > 0) {
            acc.add(new Suggestion(prefix, node.frequency));
        }
        for (Map.Entry<Character, TrieNode> e : node.children.entrySet()) {
            List<Suggestion> childTop = precompute(e.getValue(), k, prefix + e.getKey());
            acc = TrieNode.mergeKeepTopK(acc, childTop, k);
        }
        node.cachedTopK = acc;
        return acc;
    }

    /**
     * O(L) autocomplete using the precomputed cache.
     * Falls back to {@link #getSuggestions} if the cache wasn't built.
     */
    public List<Suggestion> getCachedSuggestions(String prefix, int k) {
        TrieNode start = searchPrefix(prefix);
        if (start == null) return List.of();
        if (start.cachedTopK == null) {
            return getSuggestions(prefix, k);
        }
        List<Suggestion> all = start.cachedTopK;
        if (all.size() <= k) return new ArrayList<>(all);
        return new ArrayList<>(all.subList(0, k));
    }

    /** Total number of words stored (for stats / README). */
    public int size() {
        return countWords(root);
    }

    /** Empty the trie (used by reindex). O(V) where V = total nodes. */
    public void clear() {
        root.children.clear();
        root.isEnd = false;
        root.frequency = 0;
        root.cachedTopK = null;
    }

    private int countWords(TrieNode node) {
        int count = node.isEnd ? 1 : 0;
        for (TrieNode child : node.children.values()) {
            count += countWords(child);
        }
        return count;
    }
}
