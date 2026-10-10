package com.cachebrowse.network;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Representation of an incoming HTTP response.
 */
public class HttpResponse {
    private final int statusCode;
    private final byte[] body;
    private final Map<String, List<String>> headers;
    private final String contentType;
    private final long responseTimeMs;

    public HttpResponse(int statusCode, byte[] body, Map<String, List<String>> headers, long responseTimeMs) {
        this.statusCode = statusCode;
        this.body = body != null ? body : new byte[0];
        this.headers = headers != null ? headers : Collections.emptyMap();
        this.responseTimeMs = responseTimeMs;

        String ct = "text/html; charset=UTF-8";
        if (headers != null) {
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if ("Content-Type".equalsIgnoreCase(entry.getKey()) && !entry.getValue().isEmpty()) {
                    ct = entry.getValue().get(0);
                    break;
                }
            }
        }
        this.contentType = ct;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public byte[] getBody() {
        return body;
    }

    public String getBodyAsString() {
        return new String(body, StandardCharsets.UTF_8);
    }

    public Map<String, List<String>> getHeaders() {
        return Collections.unmodifiableMap(headers);
    }

    public String getContentType() {
        return contentType;
    }

    public long getResponseTimeMs() {
        return responseTimeMs;
    }

    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 400;
    }
}
