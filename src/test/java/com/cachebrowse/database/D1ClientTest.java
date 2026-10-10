package com.cachebrowse.database;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
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
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

class D1ClientTest {
    @Test
    void verifiesProbeRowAndBuildsExpectedCloudflareRequest() {
        AtomicReference<HttpRequest> captured = new AtomicReference<>();
        D1Client client = client(request -> {
            captured.set(request);
            return response(request, 200, "{\"success\":true,\"result\":[{\"results\":[{\"cachebrowse_connected\":1}]}]}");
        });

        assertTrue(client.testConnection());
        assertEquals("none", client.getLastFailure());
        assertEquals(URI.create("https://api.cloudflare.com/client/v4/accounts/account-test/d1/database/database-test/query"), captured.get().uri());
        assertEquals("Bearer test-token", captured.get().headers().firstValue("Authorization").orElseThrow());
        assertTrue(captured.get().bodyPublisher().isPresent());
    }

    @Test
    void classifiesAuthenticationPermissionsAndSqlFailures() {
        D1Client unauthorized = client(request -> response(request, 401, "{\"success\":false,\"errors\":[{\"code\":10000,\"message\":\"Invalid API token\"}]}"));
        assertFalse(unauthorized.testConnection());
        assertEquals("invalid_credentials", unauthorized.getLastFailure());

        D1Client forbidden = client(request -> response(request, 403, "{\"success\":false,\"errors\":[{\"code\":1000,\"message\":\"Missing D1 permissions\"}]}"));
        assertFalse(forbidden.testConnection());
        assertEquals("insufficient_permissions", forbidden.getLastFailure());

        D1Client sqlError = client(request -> response(request, 200, "{\"success\":false,\"errors\":[{\"code\":7500,\"message\":\"SQL syntax error\"}]}"));
        assertThrows(IllegalStateException.class, () -> sqlError.executeChecked("SELECT * FROM missing_table", List.of()));
        assertEquals("sql_error", sqlError.getLastFailure());
    }

    @Test
    void rejectsSuccessfulHttpResponseWithMissingProbeValueAndCloudflareApiErrors() {
        D1Client invalidResult = client(request -> response(request, 200, "{\"success\":true,\"result\":[{\"results\":[]}]}"));
        assertFalse(invalidResult.testConnection());
        assertEquals("sql_result_invalid", invalidResult.getLastFailure());

        D1Client apiError = client(request -> response(request, 200, "{\"success\":false,\"errors\":[{\"code\":10000,\"message\":\"Database not found\"}]}"));
        assertFalse(apiError.testConnection());
        assertEquals("account_or_database_not_found", apiError.getLastFailure());
    }

    @Test
    void missingConfigurationDoesNotSendARequest() {
        D1Client client = new D1Client(new D1Config("", "", ""), new StubHttpClient(request -> {
            fail("HTTP should not be called when configuration is missing");
            return null;
        }), Duration.ofSeconds(1));
        assertFalse(client.testConnection());
        assertEquals("missing_credentials", client.getLastFailure());
    }

    private static D1Client client(Responder responder) {
        return new D1Client(new D1Config("account-test", "test-token", "database-test"),
                new StubHttpClient(responder), Duration.ofSeconds(1));
    }

    private static HttpResponse<String> response(HttpRequest request, int status, String body) {
        return new HttpResponse<>() {
            @Override public int statusCode() { return status; }
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
        @Override public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws IOException {
            return (HttpResponse<T>) responder.respond(request);
        }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) { throw new UnsupportedOperationException(); }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) { throw new UnsupportedOperationException(); }
    }
}
