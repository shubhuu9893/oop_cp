package com.cachebrowse.model;

import java.time.Instant;

/**
 * Model representing a bookmarked URL.
 */
public class Bookmark {
    private String id;
    private String userId;
    private String url;
    private String title;
    private Instant createdAt;

    public Bookmark() {
        this.createdAt = Instant.now();
    }

    public Bookmark(String id, String userId, String url, String title, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.url = url;
        this.title = title;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public Bookmark(String userId, String url, String title) {
        this(java.util.UUID.randomUUID().toString(), userId, url, title, Instant.now());
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return (title != null && !title.isBlank() ? title + " - " : "") + url;
    }
}
