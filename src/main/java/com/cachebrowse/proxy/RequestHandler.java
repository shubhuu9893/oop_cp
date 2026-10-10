package com.cachebrowse.proxy;

import com.cachebrowse.network.HttpRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Parses raw HTTP requests from a client input stream.
 */
public class RequestHandler {

    public static class ParsedRequest {
        private final String method;
        private final String uri;
        private final String httpVersion;
        private final Map<String, String> headers;

        public ParsedRequest(String method, String uri, String httpVersion, Map<String, String> headers) {
            this.method = method;
            this.uri = uri;
            this.httpVersion = httpVersion;
            this.headers = headers;
        }

        public String getMethod() { return method; }
        public String getUri() { return uri; }
        public String getHttpVersion() { return httpVersion; }
        public Map<String, String> getHeaders() { return headers; }

        public String getTargetUrl() {
            if (uri == null) return "";
            if (uri.startsWith("http://") || uri.startsWith("https://")) {
                return uri;
            }
            String host = headers.get("Host");
            if (host != null) {
                return "http://" + host + uri;
            }
            return uri;
        }

        public HttpRequest toHttpRequest() {
            return new HttpRequest(getTargetUrl(), method, headers, null, 15000);
        }
    }

    public static ParsedRequest parse(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.US_ASCII));
        String requestLine = reader.readLine();
        if (requestLine == null || requestLine.isBlank()) {
            return null;
        }

        String[] parts = requestLine.split(" ");
        if (parts.length < 2) {
            return null;
        }

        String method = parts[0];
        String uri = parts[1];
        String httpVersion = parts.length > 2 ? parts[2] : "HTTP/1.1";

        Map<String, String> headers = new HashMap<>();
        String headerLine;
        while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
            int colon = headerLine.indexOf(':');
            if (colon > 0) {
                String key = headerLine.substring(0, colon).trim();
                String value = headerLine.substring(colon + 1).trim();
                headers.put(key, value);
            }
        }

        return new ParsedRequest(method, uri, httpVersion, headers);
    }
}