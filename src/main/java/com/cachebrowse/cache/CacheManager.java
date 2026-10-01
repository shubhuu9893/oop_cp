package com.cachebrowse.cache;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Statistics collector used to record cache events.
import com.cachebrowse.statistics.CacheStatistics;

/**
 * High‑level cache manager that delegates to an {@link LRUCache} instance.
 * <p>
 * The original implementation only forwarded operations. For the new
 * feature set we also track hits, misses, evictions and expired entries via a
 * {@link CacheStatistics} object when provided.
 */
public class CacheManager {
    private final LRUCache lruCache;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final CacheStatistics statistics;

    /**
     * Constructor used by the application – no statistics tracking.
     */
    public CacheManager(int capacity) {
        this(capacity, null);
    }

    /**
     * Construct a manager with optional statistics collection.  When
     * {@code statistics} is non‑null cache operations will record hits,
     * misses, evictions and expired entries.
     */
    public CacheManager(int capacity, CacheStatistics statistics) {
        this.lruCache = new LRUCache(capacity);
        this.statistics = statistics;
        if (statistics != null) {
            // Record evictions via listener on the underlying LRU cache.
            lruCache.setEvictionListener(entry -> statistics.incEvictions());
        }
    }

    /** Add or update an entry in the cache. */
    public void put(String key, CacheEntry entry) {
        lruCache.put(key, entry);
        // Eviction counter handled by listener.
    }

    /** Retrieve an entry from the cache, updating statistics as
     * appropriate.  Expired entries are removed and counted as misses.
     */
    public CacheEntry get(String key) {
        if (statistics != null) statistics.incTotalRequests();
        CacheEntry entry = lruCache.get(key);
        if (entry == null) {
            if (statistics != null) statistics.incCacheMisses();
            return null;
        }
        if (entry.isExpired()) {
            // Remove expired and count
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

    public boolean contains(String key) { return lruCache.contains(key); }
    public void remove(String key) { lruCache.remove(key); }
    public void clear() { lruCache.clear(); }

    /** Current number of entries in the cache. */
    public int size() {
        return lruCache.size();
    }

    /** Capacity of the underlying LRUCache. */
    public int capacity() { return lruCache.capacity(); }

    /** Number of evictions performed by the underlying LRUCache. */
    public long getEvictions() { return lruCache.getEvictions(); }
}