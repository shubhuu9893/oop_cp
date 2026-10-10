package com.cachebrowse.statistics;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe statistics collector for the browser, cache, ad/tracker blockers, and proxy.
 */
public class CacheStatistics {
    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong allowedRequests = new AtomicLong();
    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong cacheMisses = new AtomicLong();
    private final AtomicLong networkRequests = new AtomicLong();
    private final AtomicLong expiredEntries = new AtomicLong();
    private final AtomicLong adsBlocked = new AtomicLong();
    private final AtomicLong trackersBlocked = new AtomicLong();
    private final AtomicLong cosmeticHiddenCount = new AtomicLong();
    private final AtomicLong bytesFromCache = new AtomicLong();
    private final AtomicLong bytesFromNetwork = new AtomicLong();
    private final AtomicLong totalResponseTime = new AtomicLong();
    private final AtomicLong responseCount = new AtomicLong();
    private final AtomicLong cacheEvictions = new AtomicLong();

    public void incTotalRequests() { totalRequests.incrementAndGet(); }
    public void incAllowedRequests() { allowedRequests.incrementAndGet(); }
    public void incCacheHits() { cacheHits.incrementAndGet(); }
    public void incCacheMisses() { cacheMisses.incrementAndGet(); }
    public void incNetworkRequests() { networkRequests.incrementAndGet(); }
    public void incExpiredEntries() { expiredEntries.incrementAndGet(); }
    public void incAdsBlocked() { adsBlocked.incrementAndGet(); }
    public void incTrackersBlocked() { trackersBlocked.incrementAndGet(); }
    public void addBytesFromCache(long bytes) { if (bytes > 0) bytesFromCache.addAndGet(bytes); }
    public void addBytesFromNetwork(long bytes) { if (bytes > 0) bytesFromNetwork.addAndGet(bytes); }
    public void addResponseTime(long ms) {
        if (ms >= 0) {
            totalResponseTime.addAndGet(ms);
            responseCount.incrementAndGet();
        }
    }
    public void incEvictions() { cacheEvictions.incrementAndGet(); }
    public void recordCosmeticAdHidden() { cosmeticHiddenCount.incrementAndGet(); }

    public long getTotalRequests() {
        return totalRequests.get();
    }

    public long getAllowedRequests() {
        return allowedRequests.get();
    }

    public long getBlockedRequests() {
        return adsBlocked.get() + trackersBlocked.get();
    }

    public long getCacheHits() {
        return cacheHits.get();
    }

    public long getCacheMisses() {
        return cacheMisses.get();
    }

    public long getNetworkRequests() {
        return networkRequests.get();
    }

    /**
     * Return the hit ratio as a percentage. If no requests have been
     * processed, returns 0.0 safely without division by zero.
     */
    public double getHitRatio() {
        long total = totalRequests.get();
        if (total == 0) {
            long sum = cacheHits.get() + cacheMisses.get();
            if (sum == 0) return 0.0;
            return ((double) cacheHits.get() / sum) * 100.0;
        }
        return ((double) cacheHits.get() / total) * 100.0;
    }

    public long getEvictions() {
        return cacheEvictions.get();
    }

    public long getExpiredEntries() {
        return expiredEntries.get();
    }

    public long getAdsBlocked() {
        return adsBlocked.get();
    }

    public long getTrackersBlocked() {
        return trackersBlocked.get();
    }

    public long getCosmeticHiddenCount() {
        return cosmeticHiddenCount.get();
    }

    public void setCosmeticHiddenCount(long val) { cosmeticHiddenCount.set(val); }

    public void resetCosmeticHiddenCount() { cosmeticHiddenCount.set(0); }

    public long getBytesFromCache() {
        return bytesFromCache.get();
    }

    public long getBytesFromNetwork() {
        return bytesFromNetwork.get();
    }

    /**
     * Average response time in milliseconds. If no responses have been
     * recorded, returns 0.0.
     */
    public double getAverageResponseTime() {
        long count = responseCount.get();
        if (count == 0) return 0.0;
        return ((double) totalResponseTime.get() / count);
    }

    public void setTotalRequests(long val) { totalRequests.set(val); }
    public void setAllowedRequests(long val) { allowedRequests.set(val); }
    public void setCacheHits(long val) { cacheHits.set(val); }
    public void setCacheMisses(long val) { cacheMisses.set(val); }
    public void setNetworkRequests(long val) { networkRequests.set(val); }
    public void setAdsBlocked(long val) { adsBlocked.set(val); }
    public void setTrackersBlocked(long val) { trackersBlocked.set(val); }

    public void reset() {
        totalRequests.set(0);
        allowedRequests.set(0);
        cacheHits.set(0);
        cacheMisses.set(0);
        networkRequests.set(0);
        expiredEntries.set(0);
        adsBlocked.set(0);
        trackersBlocked.set(0);
        cosmeticHiddenCount.set(0);
        bytesFromCache.set(0);
        bytesFromNetwork.set(0);
        totalResponseTime.set(0);
        responseCount.set(0);
        cacheEvictions.set(0);
    }
}
