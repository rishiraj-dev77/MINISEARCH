package com.search.cache;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A bounded LRU cache built from a {@link HashMap} + doubly linked list.
 *
 * <p>Both {@link #get} and {@link #put} are O(1):
 * <ul>
 *   <li>The map gives the node in O(1).</li>
 *   <li>The doubly linked list lets us unlink a node and re-insert it at the head in O(1)
 *       (we already hold a direct reference to the node).</li>
 * </ul>
 * The list is ordered most-recently-used (head) to least-recently-used (tail).
 * When capacity is exceeded, the tail node is evicted.
 *
 * <p>Thread-safe via a single {@link ReentrantLock}. Contention here is acceptable because
 * cache hits are fast (no I/O); the lock is only held briefly. Swap for striped locks
 * or a ConcurrentHashMap variant if profiling shows contention.
 *
 * @param <K> key type
 * @param <V> value type
 */
public class LRUCache<K, V> {

    private static final class Node<K, V> {
        K key;
        V value;
        Node<K, V> prev;
        Node<K, V> next;

        Node(K key, V value) { this.key = key; this.value = value; }
    }

    private final Map<K, Node<K, V>> map = new HashMap<>();
    private final Node<K, V> head = new Node<>(null, null); // most-recently-used sentinel
    private final Node<K, V> tail = new Node<>(null, null); // least-recently-used sentinel
    private final int capacity;
    private final ReentrantLock lock = new ReentrantLock();

    public LRUCache(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        this.capacity = capacity;
        head.next = tail;
        tail.prev = head;
    }

    /** @return value for {@code key}, or null if absent (also moves it to MRU position). */
    public V get(K key) {
        lock.lock();
        try {
            Node<K, V> node = map.get(key);
            if (node == null) return null;
            moveToFront(node);
            return node.value;
        } finally {
            lock.unlock();
        }
    }

    /** Insert or update {@code key}; evict LRU if over capacity. */
    public void put(K key, V value) {
        lock.lock();
        try {
            Node<K, V> node = map.get(key);
            if (node != null) {
                node.value = value;
                moveToFront(node);
                return;
            }
            Node<K, V> fresh = new Node<>(key, value);
            map.put(key, fresh);
            addToFront(fresh);
            if (map.size() > capacity) {
                evictTail();
            }
        } finally {
            lock.unlock();
        }
    }

    /** Remove a key explicitly (e.g. manual invalidation). */
    public void remove(K key) {
        lock.lock();
        try {
            Node<K, V> node = map.remove(key);
            if (node != null) unlink(node);
        } finally {
            lock.unlock();
        }
    }

    /** Drop everything (e.g. when a new document invalidates all cached search results). */
    public void clear() {
        lock.lock();
        try {
            map.clear();
            head.next = tail;
            tail.prev = head;
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return map.size();
        } finally {
            lock.unlock();
        }
    }

    public boolean containsKey(K key) {
        lock.lock();
        try {
            return map.containsKey(key);
        } finally {
            lock.unlock();
        }
    }

    // ---- doubly linked list maintenance ----

    private void addToFront(Node<K, V> node) {
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }

    private void unlink(Node<K, V> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
        node.prev = node.next = null;
    }

    private void moveToFront(Node<K, V> node) {
        unlink(node);
        addToFront(node);
    }

    private void evictTail() {
        Node<K, V> lru = tail.prev;
        if (lru == head) return;
        unlink(lru);
        map.remove(lru.key);
    }
}
