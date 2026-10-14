package com.cachebrowse.database;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reusable HTTP Client for Cloudflare D1 Database API.
 * Handles parameterized queries, statement execution, and JSON response parsing.
 */
public class D1Client {
    private static final Logger LOG = LoggerFactory.getLogger(D1Client.class);
    private static final String D1_API_URL_TEMPLATE = "https://api.cloudflare.com/client/v4/accounts/%s/d1/database/%s/query";

    private final D1Config config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Duration requestTimeout;
    private volatile boolean available = true;
    private volatile String lastFailure = "not_checked";

    public D1Client(D1Config config) {
        this(config, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build(), Duration.ofSeconds(5));
    }

    public D1Client(D1Config config, HttpClient httpClient, Duration requestTimeout) {
        this.config = config != null ? config : new D1Config();
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
        this.requestTimeout = requestTimeout != null ? requestTimeout : Duration.ofSeconds(15);
    }

    /**
     * Executes a SELECT / query SQL statement with parameters and returns the list of rows.
     */
    public List<Map<String, Object>> query(String sql, List<Object> params) {
        if (!isConfigured()) {
            LOG.debug("D1 not configured; query skipped: {}", sql);
            return Collections.emptyList();
        }

        try {
            JsonNode root = sendD1Request(sql, params);
            if (root == null || !root.path("success").asBoolean(false)) {
                logErrors(root);
                if (root != null) lastFailure = classifyCloudflareError(root);
                return Collections.emptyList();
            }

            JsonNode resultArray = root.path("result");
            if (resultArray.isArray() && !resultArray.isEmpty()) {
                JsonNode firstResult = resultArray.get(0);
                if (firstResult.has("success") && !firstResult.path("success").asBoolean(true)) {
                    lastFailure = "sql_error";
                    return Collections.emptyList();
                }
                JsonNode rows = firstResult.path("results");
                if (rows.isArray()) {
                    List<Map<String, Object>> list = new ArrayList<>();
                    for (JsonNode rowNode : rows) {
                        Map<String, Object> map = new HashMap<>();
                        Iterator<Map.Entry<String, JsonNode>> fields = rowNode.fields();
                        while (fields.hasNext()) {
                            Map.Entry<String, JsonNode> entry = fields.next();
                            map.put(entry.getKey(), jsonNodeToObject(entry.getValue()));
                        }
                        list.add(map);
                    }
                    return list;
                }
                lastFailure = "invalid_api_response";
            }
        } catch (Exception e) {
            recordTransportFailure(e);
            LOG.error("D1 query failed ({})", lastFailure);
        }
        return Collections.emptyList();
    }

    /**
     * Executes an INSERT, UPDATE, DELETE, or DDL SQL statement with parameters.
     * Returns the number of changed rows, or 1 if successful without change count metadata, or 0 on failure.
     */
    public int execute(String sql, List<Object> params) {
        try {
            return executeChecked(sql, params);
        } catch (Exception e) {
            LOG.warn("D1 statement failed ({})", lastFailure);
            return 0;
        }
    }

    /** Executes a write/DDL statement and throws unless Cloudflare confirms SQL success. */
    public int executeChecked(String sql, List<Object> params) {
        if (!isConfigured()) {
            lastFailure = config != null && config.isConfigured() ? "connection_unavailable" : "missing_credentials";
            throw new IllegalStateException("D1 statement failed (" + lastFailure + ")");
        }
        try {
            JsonNode root = sendD1Request(sql, params);
            if (root == null || !root.path("success").asBoolean(false)) {
                logErrors(root);
                if (lastFailure == null || "none".equals(lastFailure)) lastFailure = classifyCloudflareError(root);
                if ("invalid_credentials".equals(lastFailure) || "insufficient_permissions".equals(lastFailure)
                        || "account_or_database_not_found".equals(lastFailure)) markUnavailable();
                throw new IllegalStateException("D1 statement failed (" + lastFailure + ")");
            }
            JsonNode results = root.path("result");
            if (!results.isArray() || results.isEmpty()) {
                lastFailure = "invalid_api_response";
                throw new IllegalStateException("D1 statement failed (invalid_api_response)");
            }
            JsonNode first = results.get(0);
            if (first.has("success") && !first.path("success").asBoolean(true)) {
                lastFailure = "sql_error";
                throw new IllegalStateException("D1 statement failed (sql_error)");
            }
            return first.path("meta").path("changes").asInt(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            lastFailure = "request_interrupted";
            throw new IllegalStateException("D1 statement failed (request_interrupted)", e);
        } catch (IOException e) {
            recordTransportFailure(e);
            throw new IllegalStateException("D1 statement failed (" + lastFailure + ")", e);
        }
    }

    public int insert(String sql, List<Object> params) {
        return execute(sql, params);
    }

    public int update(String sql, List<Object> params) {
        return execute(sql, params);
    }

    public int delete(String sql, List<Object> params) {
        return execute(sql, params);
    }

    /**
     * Executes multiple SQL statements in sequence.
     */
    public boolean executeBatch(List<String> sqlStatements) {
        if (sqlStatements == null || sqlStatements.isEmpty()) {
            return true;
        }
        for (String sql : sqlStatements) {
            if (sql.trim().isEmpty()) continue;
            if (!executeSuccessfully(sql, Collections.emptyList())) return false;
        }
        return true;
    }

    private boolean executeSuccessfully(String sql, List<Object> params) {
        try {
            executeChecked(sql, params);
            return true;
        } catch (RuntimeException ex) {
            LOG.error("D1 schema statement failed ({})", lastFailure);
        }
        return false;
    }

    private JsonNode sendD1Request(String sql, List<Object> params) throws IOException, InterruptedException {
        String endpoint = String.format(D1_API_URL_TEMPLATE, config.getAccountId(), config.getDatabaseId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("sql", sql);
        payload.put("params", params != null ? params : Collections.emptyList());

        String jsonPayload = objectMapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(requestTimeout)
                .header("Authorization", "Bearer " + config.getApiToken())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        lastFailure = "none";

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            JsonNode root;
            try {
                root = objectMapper.readTree(response.body());
            } catch (IOException parseError) {
                lastFailure = "response_parse_error";
                throw parseError;
            }
            if (!root.path("success").asBoolean(false)) lastFailure = classifyCloudflareError(root);
            return root;
        } else {
            lastFailure = classifyHttpStatus(response.statusCode(), response.body());
            if (response.statusCode() == 401 || response.statusCode() == 403 || response.statusCode() == 404
                    || response.statusCode() >= 500) markUnavailable();
            LOG.warn("D1 API request failed ({})", lastFailure);
            try {
                return objectMapper.readTree(response.body());
            } catch (Exception e) {
                return null;
            }
        }
    }

    private void logErrors(JsonNode root) {
        if (root != null && root.has("errors")) {
            JsonNode errors = root.path("errors");
            if (errors.isArray()) {
                for (JsonNode err : errors) {
                    LOG.error("Cloudflare D1 error [code {}]: {}", err.path("code").asText(),
                            safeErrorMessage(err.path("message").asText()));
                }
            }
        }
    }

    private Object jsonNodeToObject(JsonNode node) {
        if (node.isNull()) return null;
        if (node.isBoolean()) return node.asBoolean();
        if (node.isInt()) return node.asInt();
        if (node.isLong()) return node.asLong();
        if (node.isDouble() || node.isFloat()) return node.asDouble();
        return node.asText();
    }

    public D1Config getConfig() {
        return config;
    }

    public boolean isConfigured() {
        return available && config != null && config.isConfigured();
    }

    /** Whether this client has not been marked unavailable after a failed request. */
    public boolean isAvailable() {
        return available;
    }

    /** Mark the current startup attempt unavailable; retry is explicit and bounded by DatabaseManager. */
    public void markUnavailable() {
        available = false;
    }

    public void prepareForRetry() { available = true; }

    /** Tests the D1 API response itself; unlike execute(SELECT), this does not treat zero changed rows as failure. */
    public boolean testConnection() {
        if (config == null || !config.isConfigured()) {
            lastFailure = "missing_credentials";
            return false;
        }
        try {
            JsonNode root = sendD1Request("SELECT 1 AS cachebrowse_connected", List.of());
            if (root != null && root.path("success").asBoolean(false)) {
                JsonNode results = root.path("result");
                if (results.isArray() && !results.isEmpty()) {
                    JsonNode rows = results.get(0).path("results");
                    if (rows.isArray() && !rows.isEmpty()
                            && rows.get(0).path("cachebrowse_connected").asInt(Integer.MIN_VALUE) == 1) {
                        lastFailure = "none";
                        return true;
                    }
                }
                lastFailure = "sql_result_invalid";
                return false;
            }
            if (lastFailure == null || "none".equals(lastFailure)) lastFailure = classifyCloudflareError(root);
        } catch (Exception e) {
            recordTransportFailure(e);
        }
        return false;
    }

    public String getLastFailure() { return lastFailure; }

    private void recordTransportFailure(Exception error) {
        if (error instanceof java.net.http.HttpTimeoutException) lastFailure = "request_timeout";
        else if (error instanceof javax.net.ssl.SSLException) lastFailure = "tls_error";
        else if (hasCause(error, java.net.UnknownHostException.class)) lastFailure = "dns_error";
        else if (hasCause(error, java.net.ConnectException.class)) lastFailure = "connection_refused";
        else if ("response_parse_error".equals(lastFailure)) { /* retain parse classification */ }
        else lastFailure = "network_error";
        markUnavailable();
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable current = error; current != null; current = current.getCause()) if (type.isInstance(current)) return true;
        return false;
    }

    private String classifyHttpStatus(int status, String body) {
        if (status == 401) return "invalid_credentials";
        if (status == 403) return "insufficient_permissions";
        if (status == 404) return "account_or_database_not_found";
        if (status >= 500) return "cloudflare_service_error";
        if (status == 400) {
            try { return classifyCloudflareError(objectMapper.readTree(body)); }
            catch (Exception ignored) { return "invalid_request"; }
        }
        try { return classifyCloudflareError(objectMapper.readTree(body)); }
        catch (Exception ignored) { return "http_error_" + status; }
    }

    private String classifyCloudflareError(JsonNode root) {
        if (root == null) return "invalid_api_response";
        JsonNode errors = root.path("errors");
        if (errors.isArray() && !errors.isEmpty()) {
            String message = errors.get(0).path("message").asText("").toLowerCase();
            String code = errors.get(0).path("code").asText("");
            if (message.contains("authentication") || message.contains("api token") || message.contains("invalid token")) return "invalid_credentials";
            if (message.contains("permission") || message.contains("unauthorized")) return "insufficient_permissions";
            if (message.contains("database") || message.contains("account") || "10000".equals(code)) return "account_or_database_not_found";
            if (message.contains("sql") || message.contains("syntax") || message.contains("no such table")) return "sql_error";
            return "cloudflare_api_error";
        }
        return "invalid_api_response";
    }

    private String safeErrorMessage(String message) {
        if (message == null) return "unspecified provider error";
        return message.replaceAll("(?i)(bearer\\s+)[^\\s,;]+", "$1[redacted]")
                .replaceAll("(?i)(api[_ -]?token[=: ]+)[^\\s,;]+", "$1[redacted]");
    }
}
