package com.search.index;

import com.search.model.Document;
import com.search.model.SearchResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An inverted index: {@code word -> {docId -> term frequency}}.
 *
 * <p>Backed by {@link ConcurrentHashMap} so concurrent index reads (searches) and
 * writes (indexing) are safe without an external lock for the common case.
 * Document metadata is kept in a separate {@code docId -> Document} map so the
 * index only stores integer frequencies.
 *
 * <p>Scoring uses TF-IDF: {@code score(doc, query) = sum over query terms of
 * tf * log(N / (1 + df))}. IDF uses {@code 1 + df} to avoid division-by-zero
 * and to keep unseen terms from dominating.
 */
public class InvertedIndex {

    /** word -> (docId -> term frequency) */
    private final Map<String, Map<Integer, Integer>> postings = new ConcurrentHashMap<>();

    /** docId -> document (used to resolve hits to {@link Document} objects) */
    private final Map<Integer, Document> documents = new ConcurrentHashMap<>();

    /** word -> number of documents containing it (document frequency, for IDF) */
    private final Map<String, Integer> documentFrequency = new ConcurrentHashMap<>();

    public void indexDocument(Document doc) {
        if (doc == null) return;
        documents.put(doc.getId(), doc);

        // tokenize once; collapse duplicates into term frequencies
        List<String> tokens = com.search.util.Tokenizer.tokenize(
                doc.getTitle() + " " + doc.getContent());
        Map<String, Integer> tf = new HashMap<>();
        for (String t : tokens) {
            tf.merge(t, 1, Integer::sum);
        }

        // write each term's posting. Use ConcurrentHashMap.merge for atomic increments,
        // safe even if two threads index different documents concurrently.
        for (Map.Entry<String, Integer> e : tf.entrySet()) {
            String term = e.getKey();
            postings.computeIfAbsent(term, k -> new ConcurrentHashMap<>())
                    .merge(doc.getId(), e.getValue(), Integer::sum);
            documentFrequency.merge(term, 1, Integer::sum);
        }
    }

    /** Index many documents (e.g. from a bulk DB load). */
    public void indexAll(List<Document> docs) {
        for (Document d : docs) indexDocument(d);
    }

    /** Posting list for {@code word}: docId -> tf, or empty map. */
    public Map<Integer, Integer> getPostings(String word) {
        return postings.getOrDefault(word, Collections.emptyMap());
    }

    public Document getDocument(int docId) { return documents.get(docId); }
    public int documentCount() { return documents.size(); }
    public int documentFrequency(String word) {
        return documentFrequency.getOrDefault(word, 0);
    }
    public int vocabularySize() { return postings.size(); }

    /** Drop all indexed state (used by a full reindex). Not great for huge indexes,
     *  but reindex is a rare admin operation. */
    public void clear() {
        postings.clear();
        documents.clear();
        documentFrequency.clear();
    }

    /**
     * Find documents matching {@code queryTokens} with the given mode.
     * @param queryTokens already-tokenized query terms
     * @param mode "AND" = intersection of posting lists, "OR" = union
     * @return docIds satisfying the mode
     */
    public List<Integer> lookup(List<String> queryTokens, MatchMode mode) {
        if (queryTokens.isEmpty()) return List.of();

        // Collect posting lists sorted ascending by size -> intersect smallest-first
        // for the AND case (two-pointer merge is O(m+n)).
        List<Map<Integer, Integer>> lists = new ArrayList<>();
        for (String t : queryTokens) {
            Map<Integer, Integer> p = getPostings(t);
            if (p.isEmpty() && mode == MatchMode.AND) {
                return List.of(); // AND: a missing term means no results
            }
            lists.add(p);
        }
        if (lists.isEmpty()) return List.of();

        lists.sort((a, b) -> Integer.compare(a.size(), b.size()));

        Map<Integer, Integer> acc = new HashMap<>(lists.get(0));
        for (int i = 1; i < lists.size(); i++) {
            acc = (mode == MatchMode.AND)
                    ? intersect(acc, lists.get(i))
                    : union(acc, lists.get(i));
        }
        return new ArrayList<>(acc.keySet());
    }

    /** Two-pointer merge of two sorted docId lists. Requires sorted input. */
    private static Map<Integer, Integer> intersect(Map<Integer, Integer> a, Map<Integer, Integer> b) {
        // iterate over the smaller map, probe the larger
        Map<Integer, Integer> small = a.size() < b.size() ? a : b;
        Map<Integer, Integer> large = a.size() < b.size() ? b : a;
        Map<Integer, Integer> out = new HashMap<>();
        for (Map.Entry<Integer, Integer> e : small.entrySet()) {
            Integer other = large.get(e.getKey());
            if (other != null) {
                out.put(e.getKey(), e.getValue() + other);
            }
        }
        return out;
    }

    private static Map<Integer, Integer> union(Map<Integer, Integer> a, Map<Integer, Integer> b) {
        Map<Integer, Integer> out = new HashMap<>(a);
        b.forEach((k, v) -> out.merge(k, v, Integer::sum));
        return out;
    }

    /**
     * Score {@code docIds} against {@code queryTokens} using TF-IDF.
     * Score = sum over query terms of (tf in doc) * log(N / (1 + df)).
     */
    public List<SearchResult> rank(List<Integer> docIds, List<String> queryTokens) {
        int n = documentCount();
        // precompute idf per distinct query term
        Map<String, Double> idf = new HashMap<>();
        for (String t : queryTokens) {
            int df = documentFrequency(t);
            idf.put(t, Math.log((double) n / (1.0 + df)));
        }

        List<SearchResult> results = new ArrayList<>(docIds.size());
        for (int docId : docIds) {
            double score = 0.0;
            for (String t : queryTokens) {
                int tf = getPostings(t).getOrDefault(docId, 0);
                if (tf > 0) {
                    score += tf * idf.get(t);
                }
            }
            Document d = getDocument(docId);
            if (d != null) {
                results.add(new SearchResult(d, score));
            }
        }
        return results;
    }

    /** Query combination mode for {@link #lookup}. */
    public enum MatchMode { AND, OR }
}
