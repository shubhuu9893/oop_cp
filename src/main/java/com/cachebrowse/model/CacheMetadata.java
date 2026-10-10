package com.cachebrowse.model;

import java.time.Instant;

/**
 * Model representing cached response metadata for database persistence.
 */
public class CacheMetadata {
    private String id;
    private String url;
    private Instant createdAt;
    private Instant expiresAt;
    private long sizeBytes;
    private int hitCount;
    private String contentType;

    public CacheMetadata() {
        this.createdAt = Instant.now();
    }

    public CacheMetadata(String id, String url, Instant createdAt, Instant expiresAt, long sizeBytes, int hitCount, String contentType) {
        this.id = id;
        this.url = url;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.expiresAt = expiresAt;
        this.sizeBytes = sizeBytes;
        this.hitCount = hitCount;
        this.contentType = contentType;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public int getHitCount() {
        return hitCount;
    }

    public void setHitCount(int hitCount) {
        this.hitCount = hitCount;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
}
