package com.cachebrowse.network;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Representation of an outgoing HTTP request.
 */
public class HttpRequest {
    private final String url;
    private final String method;
    private final Map<String, String> headers;
    private final byte[] body;
    private final long timeoutMs;

    public HttpRequest(String url) {
        this(url, "GET", Collections.emptyMap(), null, 15000);
    }

    public HttpRequest(String url, String method, Map<String, String> headers, byte[] body, long timeoutMs) {
        this.url = url;
        this.method = method != null ? method.toUpperCase() : "GET";
        this.headers = headers != null ? new HashMap<>(headers) : new HashMap<>();
        this.body = body;
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : 15000;
    }

    public String getUrl() {
        return url;
    }

    public String getMethod() {
        return method;
    }

    public Map<String, String> getHeaders() {
        return Collections.unmodifiableMap(headers);
    }

    public byte[] getBody() {
        return body;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }
}