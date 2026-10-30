package com.search.util;

import java.util.Set;

/**
 * Text-cleaning helpers used by the tokenizer before indexing or searching.
 * Keeps the pipeline deterministic: lowercase -> strip punctuation -> split -> drop stopwords.
 */
public final class TextUtils {

    private TextUtils() {}

    /** Common English stopwords removed during tokenization. */
    public static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "is", "are", "am", "was", "were", "be", "been", "being",
            "and", "or", "of", "in", "on", "at", "to", "for", "with", "by", "from",
            "this", "that", "these", "those", "it", "its", "as", "if", "then", "than",
            "has", "have", "had", "do", "does", "did", "but", "not", "no", "so", "can"
    );

    /** Lowercase the input, keeping only letters and digits (everything else becomes whitespace). */
    public static String normalize(String text) {
        if (text == null || text.isEmpty()) return "";
        // Replace any non-alphanumeric character with a space, then lowercase.
        // This strips punctuation consistently for both indexing and querying.
        char[] chars = text.toLowerCase().toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (!(Character.isLetterOrDigit(c))) {
                chars[i] = ' ';
            }
        }
        return new String(chars);
    }

    /** True if {@code token} is a stopword or blank. */
    public static boolean isStopWord(String token) {
        return token == null || token.isBlank() || STOP_WORDS.contains(token);
    }

    /** Strip surrounding whitespace and return "" for null. */
    public static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
