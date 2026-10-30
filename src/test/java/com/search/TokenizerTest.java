package com.search;

import com.search.util.Tokenizer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TokenizerTest {

    @Test
    void lowercasesAndStripsPunctuation() {
        List<String> tokens = Tokenizer.tokenize("Hello, WORLD! Java-Streams...");
        assertEquals(List.of("hello", "world", "java", "streams"), tokens);
    }

    @Test
    void removesStopWords() {
        List<String> tokens = Tokenizer.tokenize("The quick brown fox is running with the dogs");
        assertFalse(tokens.contains("the"));
        assertFalse(tokens.contains("is"));
        assertFalse(tokens.contains("with"));
        assertTrue(tokens.contains("quick"));
        assertTrue(tokens.contains("fox"));
        assertTrue(tokens.contains("running"));
    }

    @Test
    void handlesEmptyAndNull() {
        assertTrue(Tokenizer.tokenize(null).isEmpty());
        assertTrue(Tokenizer.tokenize("").isEmpty());
        assertTrue(Tokenizer.tokenize("   ").isEmpty());
        // only stopwords -> empty
        assertTrue(Tokenizer.tokenize("the is a").isEmpty());
    }

    @Test
    void preservesOrder() {
        List<String> tokens = Tokenizer.tokenize("java python data science");
        assertEquals(List.of("java", "python", "data", "science"), tokens);
    }

    @Test
    void normalizeWordLowercasesAndCleans() {
        assertEquals("java", Tokenizer.normalizeWord("Java!"));
        assertEquals("running", Tokenizer.normalizeWord("RUNNING"));
        assertEquals("", Tokenizer.normalizeWord(null));
    }
}
