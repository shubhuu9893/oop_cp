package com.cachebrowse.statistics;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread‑safe statistics collector for the proxy.
 */
public class CacheStatistics {
    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong cacheMisses = new AtomicLong();
    private final AtomicLong expiredEntries = new AtomicLong();
    private final AtomicLong adsBlocked = new AtomicLong();
    private final AtomicLong trackersBlocked = new AtomicLong();
    private final AtomicLong bytesFromCache = new AtomicLong();
    private final AtomicLong bytesFromNetwork = new AtomicLong();
    private final AtomicLong totalResponseTime = new AtomicLong();
    private final AtomicLong cacheEvictions = new AtomicLong();

    public void incTotalRequests() { totalRequests.incrementAndGet(); }
    public void incCacheHits() { cacheHits.incrementAndGet(); }
    public void incCacheMisses() { cacheMisses.incrementAndGet(); }
    public void incExpiredEntries() { expiredEntries.incrementAndGet(); }
    public void incAdsBlocked() { adsBlocked.incrementAndGet(); }
    public void incTrackersBlocked() { trackersBlocked.incrementAndGet(); }
    public void addBytesFromCache(long bytes) { bytesFromCache.addAndGet(bytes); }
    public void addBytesFromNetwork(long bytes) { bytesFromNetwork.addAndGet(bytes); }
    public void addResponseTime(long ms) { totalResponseTime.addAndGet(ms); }
    public void incEvictions() { cacheEvictions.incrementAndGet(); }

    /**
     * Return the total number of requests processed by the proxy.
     */
    public long getTotalRequests() {
        return totalRequests.get();
    }

    /**
     * Return the number of cache hits.
     */
    public long getCacheHits() {
        return cacheHits.get();
    }

    /**
     * Return the number of cache misses.
     */
    public long getCacheMisses() {
        return cacheMisses.get();
    }

    /**
     * Return the hit ratio as a percentage. If no requests have been
     * processed, returns 0.0.
     */
    public double getHitRatio() {
        long total = totalRequests.get();
        if (total == 0) return 0.0;
        return ((double) cacheHits.get() / total) * 100.0;
    }

    /**
     * Return the number of evictions performed by the LRU cache.
     */
    public long getEvictions() {
        return cacheEvictions.get();
    }

    /**
     * Return the number of expired entries removed from the cache.
     */
    public long getExpiredEntries() {
        return expiredEntries.get();
    }

    /**
     * Number of ads blocked by the proxy.
     */
    public long getAdsBlocked() { return adsBlocked.get(); }

    /**
     * Number of trackers blocked by the proxy.
     */
    public long getTrackersBlocked() { return trackersBlocked.get(); }

    /**
     * Average response time in milliseconds. If no responses have been
     * recorded, returns 0.0.
     */
    public double getAverageResponseTime() {
        long total = totalRequests.get();
        if (total == 0) return 0.0;
        return ((double) totalResponseTime.get() / total);
    }
}