package com.cachebrowse.api;

import com.cachebrowse.adblock.AdBlocker;
import com.cachebrowse.adblock.TrackerBlocker;
import com.cachebrowse.cache.CacheManager;
import com.cachebrowse.database.DatabaseManager;
import com.cachebrowse.database.D1Config;
import com.cachebrowse.model.BrowserSettings;
import com.cachebrowse.statistics.CacheStatistics;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HttpApiServerTest {
    private java.net.http.HttpClient client;
    private HttpApiServer server;
    private CacheStatistics statistics;

    @BeforeEach
    void startServer() throws Exception {
        client = java.net.http.HttpClient.newHttpClient();
        statistics = new CacheStatistics();
        server = new HttpApiServer(null, null, null, null, statistics,
                new CacheManager(10, statistics), new AdBlocker(), new TrackerBlocker(),
                new BrowserSettings(), 0);
        server.setIdentityService(new com.cachebrowse.auth.IdentityVerifier() {
            public com.cachebrowse.auth.FirebaseIdentityService.Principal verify(String token) {
                if (!token.equals("valid-user")) throw new IllegalArgumentException();
                return new com.cachebrowse.auth.FirebaseIdentityService.Principal("ordinary-user", "user@example.test", "Test", true, "password");
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop();
    }

    @Test
    void exposesRealSearchResolutionAndExplicitBrowserOwner() throws Exception {
        HttpResponse<String> search = post("/api/search", "{\"query\":\"machine learning\"}");
        assertEquals(200, search.statusCode());
        assertTrue(search.body().contains("html.duckduckgo.com/html/?q=machine+learning"));

        HttpResponse<String> browser = get("/api/browser/status");
        assertEquals(200, browser.statusCode());
        assertTrue(browser.body().contains("\"navigationOwner\":\"user-browser\""));
        assertTrue(browser.body().contains("\"navigationAvailable\":false"));

        HttpResponse<String> noSearch = post("/api/search", "{\"query\":\" \"}");
        assertEquals(400, noSearch.statusCode());
    }

    @Test
    void recordsProtectionOnlyWhenBrowserReportsCancelledRequests() throws Exception {
        assertEquals(0, statistics.getBlockedRequests());
        HttpResponse<String> record = post("/api/protection/blocked", "{\"category\":\"ad\"}");
        assertEquals(202, record.statusCode());
        assertEquals(1, statistics.getAdsBlocked());

        HttpResponse<String> status = get("/api/protection/statistics");
        assertTrue(status.body().contains("\"adsBlocked\":1"));
        assertTrue(status.body().contains("\"totalBlocked\":1"));
    }

    @Test
    void reportsApplicationCacheSeparatelyFromChromiumCache() throws Exception {
        HttpResponse<String> response = get("/api/cache/status");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("java-application-cache"));
        assertTrue(response.body().contains("does not represent Chromium page traffic"));
    }

    @Test
    void databaseDiagnosticDoesNotClaimConnectivityWithoutCredentials() throws Exception {
        server.setDatabaseManager(new DatabaseManager(new D1Config("", "", "")));
        HttpResponse<String> response = post("/api/database/check", "{}");
        assertEquals(503, response.statusCode());
        assertTrue(response.body().contains("\"connected\":false"));
        assertTrue(response.body().contains("\"issue\":\"missing_credentials\""));
    }

    @Test
    void rejectsMalformedSettingsAndNonBooleanProtectionValues() throws Exception {
        HttpResponse<String> badEngine = put("/api/settings", "{\"defaultSearchEngine\":\"file:///tmp?q=%s\"}");
        assertEquals(400, badEngine.statusCode());

        HttpResponse<String> badCapacity = put("/api/settings", "{\"cacheCapacity\":0}");
        assertEquals(400, badCapacity.statusCode());

        HttpResponse<String> badToggle = put("/api/protection/adblock", "{\"enabled\":\"false\"}");
        assertEquals(400, badToggle.statusCode());

        HttpResponse<String> badBookmark = post("/api/bookmarks", "{\"url\":\"javascript:alert(1)\"}");
        assertEquals(400, badBookmark.statusCode());

        HttpResponse<String> malformedHistory = post("/api/history/visit", "not-json");
        assertEquals(400, malformedHistory.statusCode());
        assertEquals(400, get("/api/history?limit=501").statusCode());
    }

    @Test
    void protectedUserApiRequiresVerifiedBearerAndExposesNoAdminPortal() throws Exception {
        HttpResponse<String> anonymous = getWithoutAuth("/api/auth/user");
        assertEquals(401, anonymous.statusCode());
        assertEquals(401, getWithoutAuth("/api/history").statusCode());
        assertEquals(401, getWithoutAuth("/api/settings").statusCode());
        assertEquals(404, post("/api/auth/login", "{\"email\":\"nobody@example.test\",\"password\":\"not-a-real-password\"}").statusCode());
        HttpResponse<String> identity = getAs("/api/auth/user", "valid-user");
        assertEquals(200, identity.statusCode());
        assertTrue(identity.body().contains("\"uid\":\"ordinary-user\""));
        assertFalse(identity.body().contains("admin"));
        HttpResponse<String> health = get("/api/health");
        assertEquals(200, health.statusCode());
        assertFalse(health.body().contains("adminConfigured"));
        assertEquals(404, getAs("/api/admin/summary", "valid-user").statusCode());
        assertEquals(404, getWithoutAuth("/api/admin/users").statusCode());
        assertEquals(404, post("/api/protection/blocklist", "{\"category\":\"ad\",\"rule\":\"example.test\"}").statusCode());
    }

    @Test
    void protectedBookmarkApiFailsClosedWhenFirebaseServerCredentialsAreUnavailable() throws Exception {
        server.setIdentityService(null);
        HttpResponse<String> response = getAs("/api/bookmarks", "valid-user");
        assertEquals(503, response.statusCode());
        assertTrue(response.body().contains("Firebase ID-token verification is unavailable"));
    }

    private HttpResponse<String> get(String route) throws Exception {
        return getAs(route, "valid-user");
    }
    private HttpResponse<String> getAs(String route, String token) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(route)).header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> getWithoutAuth(String route) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(route)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String route, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(route))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer valid-user")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(String route, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(route))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer valid-user")
                .PUT(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String route) {
        return URI.create("http://127.0.0.1:" + server.getPort() + route);
    }
}
