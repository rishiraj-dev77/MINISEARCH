package com.search.index;

import com.search.model.SearchResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Selects the top-K highest-scoring results using a size-K min-heap.
 *
 * <p>Keeping only K elements in the heap means each insert is O(log K) and the
 * total selection is O(n log K), which beats O(n log n) full sort when K is much
 * smaller than n (the common search case: n = matches, K = 10).
 */
public final class TopKSelector {

    private TopKSelector() {}

    /**
     * @param results scored results (possibly large)
     * @param k       number to keep
     * @return the top-K by descending score, in descending order
     */
    public static List<SearchResult> topK(List<SearchResult> results, int k) {
        if (k <= 0 || results.isEmpty()) return List.of();
        if (results.size() <= k) {
            List<SearchResult> copy = new ArrayList<>(results);
            copy.sort(Comparator.comparingDouble(SearchResult::getScore).reversed());
            return copy;
        }

        // Min-heap by score: the smallest score sits on top and gets evicted.
        PriorityQueue<SearchResult> minHeap = new PriorityQueue<>(
                Comparator.comparingDouble(SearchResult::getScore)
                        .thenComparing(r -> r.getDocument().getTitle()));
        for (SearchResult r : results) {
            if (minHeap.size() < k) {
                minHeap.offer(r);
            } else if (r.getScore() > minHeap.peek().getScore()
                    || (r.getScore() == minHeap.peek().getScore()
                        && r.getDocument().getTitle().compareTo(minHeap.peek().getDocument().getTitle()) < 0)) {
                minHeap.poll();
                minHeap.offer(r);
            }
        }

        List<SearchResult> out = new ArrayList<>(minHeap);
        out.sort(Comparator.comparingDouble(SearchResult::getScore).reversed());
        return out;
    }
}
