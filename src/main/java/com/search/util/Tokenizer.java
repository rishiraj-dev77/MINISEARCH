package com.search.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits raw text into normalized, stopword-free tokens.
 * The same tokenizer is used for indexing and querying so that
 * "Running with the Bulls!" and "running bulls" match each other.
 */
public final class Tokenizer {

    private Tokenizer() {}

    /**
     * Tokenize text: normalize, split on whitespace, drop stopwords and blanks.
     * Order is preserved (useful for phrase search extensions).
     *
     * @param text raw input text
     * @return list of lowercase, non-stopword tokens
     */
    public static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isBlank()) return tokens;

        String normalized = TextUtils.normalize(text);
        // Manual split avoids regex overhead and keeps this allocation-light.
        int start = 0;
        int len = normalized.length();
        while (start < len) {
            // skip leading spaces
            while (start < len && normalized.charAt(start) == ' ') start++;
            if (start >= len) break;
            int end = start;
            while (end < len && normalized.charAt(end) != ' ') end++;
            String token = normalized.substring(start, end);
            if (!TextUtils.isStopWord(token)) {
                tokens.add(token);
            }
            start = end;
        }
        return tokens;
    }

    /** Single-token convenience used by the Trie prefix path. */
    public static String normalizeWord(String word) {
        if (word == null || word.isBlank()) return "";
        String n = TextUtils.normalize(word);
        return n.trim();
    }
}
