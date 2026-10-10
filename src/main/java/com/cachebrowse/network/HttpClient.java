package com.cachebrowse.network;

import java.net.URI;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Java 21 HttpClient wrapper with redirection, timeouts, headers, and response measurement.
 */
public class HttpClient {
    private static final Logger LOG = LoggerFactory.getLogger(HttpClient.class);

    private static final java.net.http.HttpClient CLIENT = java.net.http.HttpClient.newBuilder()
            .version(java.net.http.HttpClient.Version.HTTP_2)
            .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36 CacheBrowse/1.0";

    /**
     * Synchronous HTTP execution.
     */
    public static HttpResponse execute(HttpRequest request) throws Exception {
        long start = System.nanoTime();
        URI uri = URI.create(request.getUrl());

        var builder = java.net.http.HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(request.getTimeoutMs()))
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5");

        if (request.getHeaders() != null) {
            for (Map.Entry<String, String> header : request.getHeaders().entrySet()) {
                builder.header(header.getKey(), header.getValue());
            }
        }

        if ("POST".equalsIgnoreCase(request.getMethod())) {
            byte[] body = request.getBody() != null ? request.getBody() : new byte[0];
            builder.POST(BodyPublishers.ofByteArray(body));
        } else {
            builder.GET();
        }

        var javaResponse = CLIENT.send(builder.build(), BodyHandlers.ofByteArray());
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        return new HttpResponse(
                javaResponse.statusCode(),
                javaResponse.body(),
                javaResponse.headers().map(),
                elapsedMs
        );
    }

    /**
     * Backward-compatible helper for simple GET.
     */
    public static java.net.http.HttpResponse<byte[]> get(String url, long timeoutMs) throws Exception {
        URI uri = URI.create(url);
        var req = java.net.http.HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(timeoutMs > 0 ? timeoutMs : 15000))
                .header("User-Agent", DEFAULT_USER_AGENT)
                .GET()
                .build();
        return CLIENT.send(req, BodyHandlers.ofByteArray());
    }

    /**
     * Asynchronous HTTP execution returning CompletableFuture.
     */
    public static CompletableFuture<HttpResponse> executeAsync(HttpRequest request) {
        long start = System.nanoTime();
        try {
            URI uri = URI.create(request.getUrl());
            var builder = java.net.http.HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMillis(request.getTimeoutMs()))
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.5");

            if (request.getHeaders() != null) {
                for (Map.Entry<String, String> header : request.getHeaders().entrySet()) {
                    builder.header(header.getKey(), header.getValue());
                }
            }

            if ("POST".equalsIgnoreCase(request.getMethod())) {
                byte[] body = request.getBody() != null ? request.getBody() : new byte[0];
                builder.POST(BodyPublishers.ofByteArray(body));
            } else {
                builder.GET();
            }

            return CLIENT.sendAsync(builder.build(), BodyHandlers.ofByteArray())
                    .orTimeout(request.getTimeoutMs(), TimeUnit.MILLISECONDS)
                    .thenApply(res -> {
                        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
                        return new HttpResponse(
                                res.statusCode(),
                                res.body(),
                                res.headers().map(),
                                elapsedMs
                        );
                    });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public static CompletableFuture<java.net.http.HttpResponse<byte[]>> getAsync(String url, long timeoutMs) {
        URI uri = URI.create(url);
        var req = java.net.http.HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(timeoutMs > 0 ? timeoutMs : 15000))
                .header("User-Agent", DEFAULT_USER_AGENT)
                .GET()
                .build();
        return CLIENT.sendAsync(req, BodyHandlers.ofByteArray())
                .orTimeout(timeoutMs > 0 ? timeoutMs : 15000, TimeUnit.MILLISECONDS);
    }
}