package com.cachebrowse.cache;

import com.cachebrowse.statistics.CacheStatistics;

/**
 * High-level CacheManager managing LRUCache instance, TTL validation,
 * and reporting statistics to CacheStatistics.
 */
public class CacheManager {
    private final LRUCache lruCache;
    private final CacheStatistics statistics;

    public CacheManager(int capacity) {
        this(capacity, null);
    }

    public CacheManager(int capacity, CacheStatistics statistics) {
        this.lruCache = new LRUCache(capacity);
        this.statistics = statistics;
        if (statistics != null) {
            lruCache.setEvictionListener(entry -> statistics.incEvictions());
        }
    }

    public void put(String key, CacheEntry entry) {
        if (key == null || entry == null) return;
        lruCache.put(key, entry);
    }

    /**
     * Retrieves an entry from the cache. Expired entries are removed and counted as misses.
     */
    public CacheEntry get(String key) {
        if (statistics != null) {
            statistics.incTotalRequests();
        }
        if (key == null) {
            if (statistics != null) statistics.incCacheMisses();
            return null;
        }

        CacheEntry entry = lruCache.get(key);
        if (entry == null) {
            if (statistics != null) {
                statistics.incCacheMisses();
            }
            return null;
        }

        if (entry.isExpired()) {
            lruCache.remove(key);
            if (statistics != null) {
                statistics.incExpiredEntries();
                statistics.incCacheMisses();
            }
            return null;
        }

        if (statistics != null) {
            statistics.incCacheHits();
            statistics.addBytesFromCache(entry.getSize());
        }
        return entry;
    }

    public boolean contains(String key) {
        return lruCache.contains(key);
    }

    public void remove(String key) {
        lruCache.remove(key);
    }

    public void clear() {
        lruCache.clear();
    }

    public int cleanupExpired() {
        int removed = lruCache.removeExpired();
        if (statistics != null) {
            for (int i = 0; i < removed; i++) statistics.incExpiredEntries();
        }
        return removed;
    }

    public int size() {
        return lruCache.size();
    }

    public int capacity() {
        return lruCache.capacity();
    }

    public void setCapacity(int capacity) {
        lruCache.setCapacity(capacity);
    }

    public long getEvictions() {
        return lruCache.getEvictions();
    }

    public LRUCache getUnderlyingCache() {
        return lruCache;
    }
}
