package com.cachebrowse.model;

import java.time.Instant;

/**
 * Model representing a visited page in browsing history.
 */
public class HistoryEntry {
    private String id;
    private String userId;
    private String url;
    private String title;
    private Instant visitedAt;

    public HistoryEntry() {
        this.visitedAt = Instant.now();
    }

    public HistoryEntry(String id, String userId, String url, String title, Instant visitedAt) {
        this.id = id;
        this.userId = userId;
        this.url = url;
        this.title = title;
        this.visitedAt = visitedAt != null ? visitedAt : Instant.now();
    }

    public HistoryEntry(String userId, String url, String title) {
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

    public Instant getVisitedAt() {
        return visitedAt;
    }

    public void setVisitedAt(Instant visitedAt) {
        this.visitedAt = visitedAt;
    }

    @Override
    public String toString() {
        return (title != null && !title.isBlank() ? title + " - " : "") + url + " (" + visitedAt + ")";
    }
}
