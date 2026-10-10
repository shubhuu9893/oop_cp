package com.cachebrowse.database;

import static org.junit.jupiter.api.Assertions.*;

import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

class DatabaseManagerTest {
    @Test
    void initializesSchemaOnlyAfterValidatedConnectionProbeAndAllowsSafeRetry() {
        AtomicInteger calls = new AtomicInteger();
        D1Client client = new D1Client(new D1Config("test-account", "test-token", "test-db"),
                new StubHttpClient(request -> {
                    calls.incrementAndGet();
                    return response(request, "{\"success\":true,\"result\":[{\"success\":true,\"results\":[{\"cachebrowse_connected\":1}],\"meta\":{\"changes\":1}}]}");
                }), Duration.ofSeconds(1));

        DatabaseManager manager = new DatabaseManager(new D1Config("test-account", "test-token", "test-db"), client);
        assertTrue(manager.isInitialized());
        assertEquals("none", manager.getDiagnostic());
        assertTrue(calls.get() > 1, "probe must precede schema statements");
        assertTrue(manager.retryInitialization());
    }

    @Test
    void missingConfigurationStaysInFallbackMode() {
        DatabaseManager manager = new DatabaseManager(new D1Config("", "", ""));
        assertFalse(manager.isInitialized());
        assertEquals("missing_credentials", manager.getDiagnostic());
    }

    private static HttpResponse<String> response(HttpRequest request, String body) {
        return new HttpResponse<>() {
            @Override public int statusCode() { return 200; }
            @Override public HttpRequest request() { return request; }
            @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
            @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a, b) -> true); }
            @Override public String body() { return body; }
            @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
            @Override public URI uri() { return request.uri(); }
            @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        };
    }

    @FunctionalInterface private interface Responder { HttpResponse<String> respond(HttpRequest request); }
    private static final class StubHttpClient extends HttpClient {
        private final Responder responder;
        private StubHttpClient(Responder responder) { this.responder = responder; }
        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.of(Duration.ofSeconds(1)); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() { return null; }
        @Override public SSLParameters sslParameters() { return new SSLParameters(); }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }
        @SuppressWarnings("unchecked")
        @Override public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            return (HttpResponse<T>) responder.respond(request);
        }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) { throw new UnsupportedOperationException(); }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) { throw new UnsupportedOperationException(); }
    }
}
