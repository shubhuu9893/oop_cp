package com.cachebrowse.service;

import com.cachebrowse.network.HttpClient;
import com.cachebrowse.network.HttpRequest;
import com.cachebrowse.network.HttpResponse;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

/**
 * Service for network operations, URL validation, and search normalization.
 */
public class NetworkService {

    public boolean isValidSearchEngineTemplate(String template) {
        if (template == null || template.isBlank()) return false;
        int marker = template.indexOf("%s");
        if (marker < 0 || marker != template.lastIndexOf("%s")) return false;
        try {
            URI uri = URI.create(template.replace("%s", "cachebrowse-query"));
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /**
     * Normalizes a raw input string into either an absolute HTTP/HTTPS URL or a search engine query URL.
     */
    public String resolveUrl(String input, String searchEngineTemplate) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String trimmed = input.trim();

        if (trimmed.chars().anyMatch(Character::isISOControl)) return "";
        if (trimmed.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) {
            try {
                URI uri = URI.create(trimmed);
                String scheme = uri.getScheme();
                return (("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                        && uri.getHost() != null && uri.getUserInfo() == null) ? uri.toASCIIString() : "";
            } catch (IllegalArgumentException ex) {
                return "";
            }
        }

        // A single host/path input is treated as a direct navigation. Inputs
        // containing whitespace or invalid host syntax are search terms.
        if (!trimmed.chars().anyMatch(Character::isWhitespace)) {
            try {
                URI candidate = URI.create("https://" + trimmed);
                String host = candidate.getHost();
                if (host != null && (host.equalsIgnoreCase("localhost") || host.contains("."))) {
                    return candidate.toASCIIString();
                }
            } catch (IllegalArgumentException ignored) {
                // Route malformed host-like input through the configured search provider.
            }
        }

        // Search query
        String template = (searchEngineTemplate != null && !searchEngineTemplate.isBlank())
                ? searchEngineTemplate
                : "https://html.duckduckgo.com/html/?q=%s";

        if (!isValidSearchEngineTemplate(template)) return "";
        try {
            URI provider = URI.create(template.replace("%s", "query"));
            if (!("https".equalsIgnoreCase(provider.getScheme()) || "http".equalsIgnoreCase(provider.getScheme()))
                    || provider.getHost() == null) return "";
        } catch (IllegalArgumentException ex) {
            return "";
        }

        String encoded = URLEncoder.encode(trimmed, StandardCharsets.UTF_8);
        return template.replace("%s", encoded);
    }

    public boolean isValidUrl(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (Exception e) {
            return false;
        }
    }

    public CompletableFuture<HttpResponse> fetchAsync(String url, long timeoutMs) {
        HttpRequest req = new HttpRequest(url, "GET", Collections.emptyMap(), null, timeoutMs);
        return HttpClient.executeAsync(req);
    }
}
