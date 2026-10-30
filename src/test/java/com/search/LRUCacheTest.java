package com.search;

import com.search.cache.LRUCache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LRUCacheTest {

    @Test
    void putAndGet() {
        LRUCache<String, Integer> cache = new LRUCache<>(3);
        cache.put("a", 1);
        cache.put("b", 2);
        assertEquals(1, cache.get("a"));
        assertEquals(2, cache.get("b"));
        assertNull(cache.get("missing"));
        assertEquals(2, cache.size());
    }

    @Test
    void evictsLeastRecentlyUsed() {
        LRUCache<String, Integer> cache = new LRUCache<>(3);
        cache.put("a", 1);
        cache.put("b", 2);
        cache.put("c", 3);
        // access order: a is now most-recently-used after this get
        cache.get("a");
        // insert d -> evict LRU which is b (c was used at put, a was used at get)
        cache.put("d", 4);

        assertNull(cache.get("b"), "b should have been evicted as LRU");
        assertEquals(1, cache.get("a"));
        assertEquals(3, cache.get("c"));
        assertEquals(4, cache.get("d"));
        assertEquals(3, cache.size());
    }

    @Test
    void putExistingKeyUpdatesAndMovesToFront() {
        LRUCache<String, Integer> cache = new LRUCache<>(2);
        cache.put("a", 1);
        cache.put("b", 2);
        cache.put("a", 99); // a becomes MRU
        // now insert c -> evict b (LRU)
        cache.put("c", 3);
        assertEquals(99, cache.get("a"));
        assertNull(cache.get("b"));
        assertEquals(3, cache.get("c"));
    }

    @Test
    void removeAndClear() {
        LRUCache<String, Integer> cache = new LRUCache<>(3);
        cache.put("a", 1);
        cache.put("b", 2);
        cache.remove("a");
        assertNull(cache.get("a"));
        assertEquals(2, cache.get("b"));
        cache.clear();
        assertNull(cache.get("b"));
        assertEquals(0, cache.size());
    }

    @Test
    void rejectInvalidCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new LRUCache<>(0));
        assertThrows(IllegalArgumentException.class, () -> new LRUCache<>(-1));
    }

    @Test
    void concurrentAccessIsSafe() throws InterruptedException {
        LRUCache<Integer, Integer> cache = new LRUCache<>(100);
        for (int i = 0; i < 100; i++) cache.put(i, i);

        Thread[] threads = new Thread[8];
        for (int t = 0; t < 8; t++) {
            threads[t] = new Thread(() -> {
                for (int i = 0; i < 10000; i++) {
                    int key = i % 150;
                    cache.get(key);
                    cache.put(key, key);
                }
            });
        }
        for (Thread th : threads) th.start();
        for (Thread th : threads) th.join();

        // size should never exceed capacity regardless of interleaving
        assertTrue(cache.size() <= 100, "cache size " + cache.size() + " exceeded capacity");
        // some keys should still be retrievable
        assertNotNull(cache.get(50));
    }
}
