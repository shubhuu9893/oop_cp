package com.cachebrowse.cache;

import java.util.HashMap;
import java.util.function.Consumer;

/**
 * A custom LRU (Least Recently Used) cache implemented with HashMap and Doubly-Linked List.
 * Provides O(1) time complexity for get(), put(), remove(), and contains().
 * Thread-safe with synchronized operations.
 */
public class LRUCache implements Cache<String, CacheEntry> {
    private int capacity;
    private final HashMap<String, Node> map = new HashMap<>();

    // Head is most recently used, tail is least recently used
    private Node head, tail;

    // Counter for evictions performed when capacity is exceeded
    private int evictions = 0;

    /** Optional callback invoked whenever an entry is evicted. */
    private Consumer<CacheEntry> evictionListener;

    public LRUCache(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    /**
     * Set a listener that will be notified when an entry is evicted.
     */
    public synchronized void setEvictionListener(Consumer<CacheEntry> listener) {
        this.evictionListener = listener;
    }

    @Override
    public synchronized CacheEntry get(String key) {
        if (key == null) return null;
        Node node = map.get(key);
        if (node == null) return null;
        moveToHead(node);
        return node.value;
    }

    @Override
    public synchronized void put(String key, CacheEntry value) {
        if (key == null || value == null) return;

        Node node = map.get(key);
        if (node != null) {
            node.value = value;
            moveToHead(node);
        } else {
            node = new Node(value);
            map.put(key, node);
            addToHead(node);

            while (map.size() > capacity && tail != null) {
                if (evictionListener != null) {
                    try {
                        evictionListener.accept(tail.value);
                    } catch (Exception ignored) {
                    }
                }
                removeTail();
                evictions++;
            }
        }
    }

    @Override
    public synchronized boolean contains(String key) {
        if (key == null) return false;
        return map.containsKey(key);
    }

    @Override
    public synchronized void remove(String key) {
        if (key == null) return;
        Node node = map.remove(key);
        if (node != null) {
            deleteNode(node);
        }
    }

    @Override
    public synchronized void clear() {
        head = null;
        tail = null;
        map.clear();
    }

    @Override
    public synchronized int size() {
        return map.size();
    }

    /** Removes expired entries without treating maintenance as browser requests. */
    public synchronized int removeExpired() {
        int removed = 0;
        Node node = tail;
        while (node != null) {
            Node previous = node.prev;
            if (node.value.isExpired()) {
                map.remove(node.value.getKey());
                deleteNode(node);
                removed++;
            }
            node = previous;
        }
        return removed;
    }

    @Override
    public synchronized int capacity() {
        return capacity;
    }

    public synchronized void setCapacity(int newCapacity) {
        if (newCapacity > 0) {
            this.capacity = newCapacity;
            while (map.size() > capacity && tail != null) {
                if (evictionListener != null) {
                    try {
                        evictionListener.accept(tail.value);
                    } catch (Exception ignored) {
                    }
                }
                removeTail();
                evictions++;
            }
        }
    }

    public synchronized int getEvictions() {
        return evictions;
    }

    private void moveToHead(Node node) {
        deleteNode(node);
        addToHead(node);
    }

    private void addToHead(Node node) {
        node.next = head;
        node.prev = null;
        if (head != null) {
            head.prev = node;
        }
        head = node;
        if (tail == null) {
            tail = head;
        }
    }

    private void deleteNode(Node node) {
        if (node == null) return;
        if (node.prev != null) {
            node.prev.next = node.next;
        } else {
            head = node.next;
        }
        if (node.next != null) {
            node.next.prev = node.prev;
        } else {
            tail = node.prev;
        }
        node.prev = null;
        node.next = null;
    }

    private void removeTail() {
        if (tail == null) return;
        map.remove(tail.value.getKey());
        deleteNode(tail);
    }

    private static class Node {
        CacheEntry value;
        Node prev, next;
        Node(CacheEntry v) { this.value = v; }
    }
}
