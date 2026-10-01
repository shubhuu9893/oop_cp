package com.cachebrowse.cache;

import java.time.Instant;

/**
 * Represents a cache entry.
 */
public class CacheEntry {
    private final String key;
    private final byte[] responseBody;
    private final Instant createdAt;
    private final long ttlMillis;
    private final String contentType;
    private final long size; // in bytes

    /** Expiration instant calculated from createdAt + ttl */
    private final java.time.Instant expirationTime;

    /** HTTP status code for the cached response. Defaults to 200. */
    private final int statusCode;

    /**
     * Convenience constructor assuming a 200 status code.
     */
    public CacheEntry(String key, byte[] responseBody, long ttlMillis,
                      String contentType) {
        this(key, responseBody, ttlMillis, contentType, 200);
    }

    /** Full constructor with status code. */
    public CacheEntry(String key, byte[] responseBody, long ttlMillis,
                      String contentType, int statusCode) {
        this.key = key;
        this.responseBody = responseBody;
        this.createdAt = Instant.now();
        this.ttlMillis = ttlMillis;
        this.contentType = contentType;
        this.size = responseBody != null ? responseBody.length : 0;
        this.statusCode = statusCode;
        this.expirationTime = createdAt.plusMillis(ttlMillis);
    }

    /**
     * Returns {@code true} if the entry has exceeded its TTL.
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expirationTime);
    }

    // Getters
    public String getKey() { return key; }
    public byte[] getResponseBody() { return responseBody; }
    public Instant getCreatedAt() { return createdAt; }
    public long getTtlMillis() { return ttlMillis; }
    public String getContentType() { return contentType; }
    public long getSize() { return size; }

    /** Return the HTTP status code for the cached response. */
    public int getStatusCode() { return statusCode; }

    /** Return the instant at which this entry will expire. */
    public java.time.Instant getExpirationTime() { return expirationTime; }
}