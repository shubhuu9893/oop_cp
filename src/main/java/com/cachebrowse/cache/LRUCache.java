package com.cachebrowse.cache;

import java.util.HashMap;
import java.util.function.Consumer;

/**
 * A simple LRU cache implemented with a HashMap and doubly‑linked list.
 */
public class LRUCache implements Cache<String, CacheEntry> {
    private final int capacity;
    private final HashMap<String, Node> map = new HashMap<>();

    // head is most recently used, tail is least
    private Node head, tail;

    // Count evictions performed when capacity is exceeded.
    private int evictions = 0;

    /** Optional callback invoked whenever an entry is evicted. */
    private Consumer<CacheEntry> evictionListener;

    public LRUCache(int capacity) {
        this.capacity = capacity;
    }

    /** Set a listener that will be notified when an entry is evicted.
     *  The listener may be {@code null} to disable callbacks.
     */
    public void setEvictionListener(Consumer<CacheEntry> listener) {
        this.evictionListener = listener;
    }

    @Override
    public synchronized CacheEntry get(String key) {
        Node node = map.get(key);
        if (node == null) return null;
        moveToHead(node);
        return node.value;
    }

    @Override
    public synchronized void put(String key, CacheEntry value) {
        Node node = map.get(key);
        if (node != null) {
            node.value = value;
            moveToHead(node);
        } else {
            node = new Node(value);
            map.put(key, node);
            addToHead(node);
                if (map.size() > capacity) {
        // Notify eviction listener before removal
        if (evictionListener != null && tail != null) {
            try {
                evictionListener.accept(tail.value);
            } catch (Exception e) {
                // Swallow exceptions to avoid corrupting cache state.
            }
        }
        removeTail();
        evictions++;
                }
        }
    }

    @Override
    public synchronized boolean contains(String key) { return map.containsKey(key); }

    @Override
    public synchronized void remove(String key) { deleteNode(map.remove(key)); }

    @Override
    public synchronized void clear() {
        head = null;
        tail = null;
        map.clear();
    }

     @Override
     public int size() { return map.size(); }

     @Override
     public int capacity() { return capacity; }

     /** Return number of evictions performed */
     public int getEvictions() { return evictions; }

    private void moveToHead(Node node) {
        deleteNode(node);
        addToHead(node);
    }

    private void addToHead(Node node) {
        node.next = head;
        if (head != null) head.prev = node;
        head = node;
        if (tail == null) tail = head;
    }

    private void deleteNode(Node node) {
        if (node == null) return;
        if (node.prev != null) node.prev.next = node.next;
        else head = node.next;
        if (node.next != null) node.next.prev = node.prev;
        else tail = node.prev;
    }

    private void removeTail() {
        if (tail == null) return;
        map.remove(tail.value.getKey());
        deleteNode(tail);
    }

    /** Node for doubly linked list */
    private static class Node {
        CacheEntry value;
        Node prev, next;
        Node(CacheEntry v) { this.value = v; }
    }
}