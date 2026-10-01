package com.cachebrowse.cache;

public interface Cache<K, V> {
    V get(K key);
    void put(K key, V value);
    boolean contains(K key);
    void remove(K key);
    void clear();
    int size();
    int capacity();
}