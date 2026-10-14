package com.cachebrowse.network;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Utility to sanitize URLs and remove sensitive tokens, passwords, and authorization data
 * before logging or storing protection events.
 */
public final class UrlSanitizer {
    private static final Pattern SENSITIVE_KEY_PATTERN = Pattern.compile(
            "(?i).*(token|auth|password|passwd|secret|key|access_token|api_key|session|jwt|code|credential|private).*"
    );

    private static final Set<String> EXACT_SENSITIVE_KEYS = Set.of(
            "token", "auth", "password", "pwd", "passwd", "secret", "key", "access_token",
            "api_key", "apikey", "session", "session_id", "sessionid", "jwt", "id_token",
            "refresh_token", "code", "client_secret", "signature", "sig"
    );

    private UrlSanitizer() {}

    /**
     * Extracts the lowercase hostname from a URL, returning an empty string if invalid or absent.
     */
    public static String extractHost(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) return "";
        try {
            URI uri = URI.create(rawUrl.trim());
            String host = uri.getHost();
            if (host != null && !host.isBlank()) {
                return host.toLowerCase(Locale.ROOT);
            }
        } catch (Exception ignored) {}

        // Fallback extraction
        String clean = rawUrl.trim();
        int schemeEnd = clean.indexOf("://");
        int start = schemeEnd != -1 ? schemeEnd + 3 : 0;
        int end = clean.indexOf('/', start);
        if (end == -1) end = clean.indexOf('?', start);
        if (end == -1) end = clean.indexOf('#', start);
        if (end == -1) end = clean.length();
        int colon = clean.indexOf(':', start);
        if (colon != -1 && colon < end) end = colon;
        if (start < end) {
            String candidate = clean.substring(start, end).toLowerCase(Locale.ROOT);
            if (!candidate.contains("@")) return candidate;
            int at = candidate.lastIndexOf('@');
            return candidate.substring(at + 1);
        }
        return "";
    }

    /**
     * Sanitizes a URL by stripping embedded user credentials (user:pass@host)
     * and redacting values of sensitive query parameters.
     */
    public static String sanitizeUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) return "";
        String trimmed = rawUrl.trim();

        // Check for URI parsing
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();
            String path = uri.getPath();
            String query = uri.getRawQuery();

            if (scheme != null && host != null) {
                StringBuilder sb = new StringBuilder();
                sb.append(scheme).append("://").append(host.toLowerCase(Locale.ROOT));
                if (port != -1 && port != 80 && port != 443) {
                    sb.append(':').append(port);
                }
                if (path != null && !path.isEmpty()) {
                    sb.append(path);
                } else {
                    sb.append('/');
                }
                if (query != null && !query.isEmpty()) {
                    sb.append('?').append(sanitizeQuery(query));
                }
                return sb.toString();
            }
        } catch (Exception ignored) {}

        // If URI parsing fails, strip credentials and sanitize query string manually
        int qIdx = trimmed.indexOf('?');
        if (qIdx == -1) {
            return stripCredentials(trimmed);
        }
        String base = stripCredentials(trimmed.substring(0, qIdx));
        String query = trimmed.substring(qIdx + 1);
        int hashIdx = query.indexOf('#');
        String fragment = "";
        if (hashIdx != -1) {
            fragment = query.substring(hashIdx);
            query = query.substring(0, hashIdx);
        }
        return base + "?" + sanitizeQuery(query) + fragment;
    }

    private static String stripCredentials(String url) {
        int schemeIdx = url.indexOf("://");
        if (schemeIdx == -1) return url;
        int atIdx = url.indexOf('@', schemeIdx + 3);
        int slashIdx = url.indexOf('/', schemeIdx + 3);
        if (atIdx != -1 && (slashIdx == -1 || atIdx < slashIdx)) {
            return url.substring(0, schemeIdx + 3) + url.substring(atIdx + 1);
        }
        return url;
    }

    private static String sanitizeQuery(String query) {
        if (query == null || query.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        String[] pairs = query.split("&");
        for (int i = 0; i < pairs.length; i++) {
            if (i > 0) out.append('&');
            String pair = pairs[i];
            int eq = pair.indexOf('=');
            if (eq == -1) {
                out.append(pair);
            } else {
                String key = pair.substring(0, eq);
                String val = pair.substring(eq + 1);
                String decodedKey = decode(key).toLowerCase(Locale.ROOT);
                if (isSensitiveParam(decodedKey)) {
                    out.append(key).append("=[REDACTED]");
                } else {
                    out.append(key).append('=').append(val);
                }
            }
        }
        return out.toString();
    }

    private static boolean isSensitiveParam(String key) {
        if (EXACT_SENSITIVE_KEYS.contains(key)) return true;
        return SENSITIVE_KEY_PATTERN.matcher(key).matches();
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }
}
