package com.search.index;

import com.search.model.Suggestion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One node of the Trie.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code children} - outgoing edges keyed by character.</li>
 *   <li>{@code isEnd} - true when a complete word terminates here.</li>
 *   <li>{@code frequency} - the word's own frequency (meaningful only when {@code isEnd}).</li>
 *   <li>{@code cachedTopK} - optional precomputed top-K suggestions passing through this node,
 *       filled by {@link Trie#precomputeTopK(int)} so autocomplete lookups become O(L).</li>
 * </ul>
 */
public class TrieNode {

    final Map<Character, TrieNode> children = new HashMap<>();
    boolean isEnd = false;
    int frequency = 0;
    // populated lazily by precomputeTopK; null means "not cached"
    List<Suggestion> cachedTopK = null;

    public Map<Character, TrieNode> getChildren() { return children; }
    public boolean isEnd() { return isEnd; }
    public int getFrequency() { return frequency; }

    /** Returns the precomputed top-K cache or an empty list if not built. */
    List<Suggestion> getCachedTopK() {
        return cachedTopK == null ? List.of() : cachedTopK;
    }

    /** Merge helper used during precomputeTopK: keep the top-K suggestions by frequency. */
    static List<Suggestion> mergeKeepTopK(List<Suggestion> a, List<Suggestion> b, int k) {
        List<Suggestion> merged = new ArrayList<>(a.size() + b.size());
        merged.addAll(a);
        merged.addAll(b);
        merged.sort(null); // Suggestion is ordered: freq desc, then alpha asc
        if (merged.size() > k) {
            merged = new ArrayList<>(merged.subList(0, k));
        }
        return merged;
    }
}
