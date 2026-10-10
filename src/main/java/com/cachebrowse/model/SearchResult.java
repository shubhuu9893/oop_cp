package com.cachebrowse.model;

/**
 * Simple DTO representing a search hit from bookmarks or history.
 */
public class SearchResult {
    public enum Type { BOOKMARK, HISTORY }

    private final Type type;
    private final String url;
    private final String title;

    public SearchResult(Type type, String url, String title) {
        this.type = type;
        this.url = url;
        this.title = title;
    }

    public Type getType() { return type; }
    public String getUrl() { return url; }
    public String getTitle() { return title; }

    @Override
    public String toString() {
        return String.format("[%s] %s – %s", type, title, url);
    }
}