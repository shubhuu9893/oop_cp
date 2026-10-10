package com.cachebrowse.service;

import com.cachebrowse.cache.CacheEntry;
import com.cachebrowse.cache.CacheManager;

/**
 * Service managing cache operations.
 */
public class CacheService {
    private final CacheManager cacheManager;

    public CacheService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public CacheEntry get(String url) {
        if (cacheManager == null || url == null) return null;
        return cacheManager.get(url);
    }

    public void put(String url, byte[] body, long ttlMillis, String contentType, int statusCode) {
        if (cacheManager == null || url == null || body == null) return;
        CacheEntry entry = new CacheEntry(url, body, ttlMillis, contentType, statusCode);
        cacheManager.put(url, entry);
    }

    public void remove(String url) {
        if (cacheManager != null) {
            cacheManager.remove(url);
        }
    }

    public void clear() {
        if (cacheManager != null) {
            cacheManager.clear();
        }
    }

    public boolean contains(String url) {
        return cacheManager != null && cacheManager.contains(url);
    }

    public int size() {
        return cacheManager != null ? cacheManager.size() : 0;
    }

    public int capacity() {
        return cacheManager != null ? cacheManager.capacity() : 0;
    }

    public void setCapacity(int capacity) {
        if (cacheManager != null) {
            cacheManager.setCapacity(capacity);
        }
    }

    public long getEvictions() {
        return cacheManager != null ? cacheManager.getEvictions() : 0;
    }

    public CacheManager getCacheManager() {
        return cacheManager;
    }
}
