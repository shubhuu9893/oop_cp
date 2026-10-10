package com.cachebrowse.model;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates the complete result of a browser navigation request.
 */
public class BrowserResponse {
    private final String url;
    private final int statusCode;
    private final byte[] body;
    private final String contentType;
    private final boolean fromCache;
    private final boolean blocked;
    private final String blockedReason;
    private final long responseTimeMs;
    private final Map<String, List<String>> headers;
    private final String errorMessage;

    private BrowserResponse(Builder builder) {
        this.url = builder.url;
        this.statusCode = builder.statusCode;
        this.body = builder.body;
        this.contentType = builder.contentType;
        this.fromCache = builder.fromCache;
        this.blocked = builder.blocked;
        this.blockedReason = builder.blockedReason;
        this.responseTimeMs = builder.responseTimeMs;
        this.headers = builder.headers != null ? builder.headers : Collections.emptyMap();
        this.errorMessage = builder.errorMessage;
    }

    public static BrowserResponse blocked(String url, String reason) {
        String html = "<!DOCTYPE html><html><head><title>Request Blocked</title>" +
                "<style>body{font-family:sans-serif;background:#0f172a;color:#f8fafc;padding:40px;text-align:center;}" +
                ".card{background:#1e293b;border-radius:8px;padding:30px;max-width:500px;margin:auto;box-shadow:0 4px 6px rgba(0,0,0,0.3);}" +
                "h2{color:#ef4444;margin-top:0;}</style></head><body>" +
                "<div class='card'><h2>Blocked by " + reason + "</h2>" +
                "<p>The URL <code>" + url + "</code> was blocked by CacheBrowse protection rules.</p>" +
                "</div></body></html>";
        return new Builder(url)
                .statusCode(403)
                .body(html.getBytes(StandardCharsets.UTF_8))
                .contentType("text/html; charset=UTF-8")
                .blocked(true)
                .blockedReason(reason)
                .responseTimeMs(0)
                .build();
    }

    public static BrowserResponse error(String url, String errorMessage) {
        String html = "<!DOCTYPE html><html><head><title>Network Error</title>" +
                "<style>body{font-family:sans-serif;background:#0f172a;color:#f8fafc;padding:40px;text-align:center;}" +
                ".card{background:#1e293b;border-radius:8px;padding:30px;max-width:500px;margin:auto;box-shadow:0 4px 6px rgba(0,0,0,0.3);}" +
                "h2{color:#f59e0b;margin-top:0;}</style></head><body>" +
                "<div class='card'><h2>Unable to load page</h2>" +
                "<p><strong>URL:</strong> <code>" + url + "</code></p>" +
                "<p><strong>Error:</strong> " + errorMessage + "</p>" +
                "</div></body></html>";
        return new Builder(url)
                .statusCode(500)
                .body(html.getBytes(StandardCharsets.UTF_8))
                .contentType("text/html; charset=UTF-8")
                .errorMessage(errorMessage)
                .responseTimeMs(0)
                .build();
    }

    public String getUrl() {
        return url;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public byte[] getBody() {
        return body;
    }

    public String getBodyAsString() {
        return body != null ? new String(body, StandardCharsets.UTF_8) : "";
    }

    public String getContentType() {
        return contentType;
    }

    public boolean isFromCache() {
        return fromCache;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public String getBlockedReason() {
        return blockedReason;
    }

    public long getResponseTimeMs() {
        return responseTimeMs;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 400 && errorMessage == null && !blocked;
    }

    public static class Builder {
        private final String url;
        private int statusCode = 200;
        private byte[] body = new byte[0];
        private String contentType = "text/html";
        private boolean fromCache = false;
        private boolean blocked = false;
        private String blockedReason;
        private long responseTimeMs = 0;
        private Map<String, List<String>> headers;
        private String errorMessage;

        public Builder(String url) {
            this.url = url;
        }

        public Builder statusCode(int statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        public Builder body(byte[] body) {
            this.body = body != null ? body : new byte[0];
            return this;
        }

        public Builder contentType(String contentType) {
            this.contentType = contentType != null ? contentType : "text/html";
            return this;
        }

        public Builder fromCache(boolean fromCache) {
            this.fromCache = fromCache;
            return this;
        }

        public Builder blocked(boolean blocked) {
            this.blocked = blocked;
            return this;
        }

        public Builder blockedReason(String blockedReason) {
            this.blockedReason = blockedReason;
            return this;
        }

        public Builder responseTimeMs(long responseTimeMs) {
            this.responseTimeMs = responseTimeMs;
            return this;
        }

        public Builder headers(Map<String, List<String>> headers) {
            this.headers = headers;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public BrowserResponse build() {
            return new BrowserResponse(this);
        }
    }
}
