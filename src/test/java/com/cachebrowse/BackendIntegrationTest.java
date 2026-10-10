package com.cachebrowse;

import com.cachebrowse.adblock.AdBlocker;
import com.cachebrowse.adblock.TrackerBlocker;
import com.cachebrowse.auth.UserSession;
import com.cachebrowse.cache.CacheEntry;
import com.cachebrowse.cache.CacheManager;
import com.cachebrowse.database.DatabaseManager;
import com.cachebrowse.model.Bookmark;
import com.cachebrowse.model.BrowserSettings;
import com.cachebrowse.model.HistoryEntry;
import com.cachebrowse.model.SearchResult;
import com.cachebrowse.network.HttpClient;
import com.cachebrowse.network.HttpRequest;
import com.cachebrowse.network.HttpResponse;
import com.cachebrowse.service.*;
import com.cachebrowse.statistics.CacheStatistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class BackendIntegrationTest {

    private BrowserSettings settings;
    private CacheStatistics statistics;
    private CacheManager cacheManager;
    private AdBlocker adBlocker;
    private TrackerBlocker trackerBlocker;
    private DatabaseManager db;
    private HistoryService historyService;
    private BookmarkService bookmarkService;
    private SettingsService settingsService;
    private StatisticsService statisticsService;
    private AuthenticationService authService;
    private NetworkService networkService;
    private CacheService cacheService;
    private SearchService searchService;
    private BrowserService browserService;

    @BeforeEach
    void setup() {
        settings = new BrowserSettings();
        statistics = new CacheStatistics();
        cacheManager = new CacheManager(100, statistics);
        adBlocker = new AdBlocker();
        trackerBlocker = new TrackerBlocker();

        adBlocker.setEnabled(true);
        trackerBlocker.setEnabled(true);

        // Keep automated integration fixtures offline even when the developer's root .env
        // contains production D1 credentials; remote D1 writes require an explicit manual run.
        db = new DatabaseManager(new com.cachebrowse.database.D1Config("", "", ""));
        UserSession userSession = UserSession.getInstance();
        authService = new AuthenticationService(db.getUserRepository(), userSession);

        historyService = new HistoryService(db.getHistoryRepository());
        bookmarkService = new BookmarkService(db.getBookmarkRepository());
        settingsService = new SettingsService(db.getSettingsRepository(), settings);
        statisticsService = new StatisticsService(statistics, db.getStatisticsRepository());
        networkService = new NetworkService();
        cacheService = new CacheService(cacheManager);

        browserService = new BrowserService(
                networkService, cacheService, adBlocker, trackerBlocker,
                historyService, bookmarkService, settingsService, statisticsService, authService
        );

        searchService = new SearchService(
                db.getBookmarkRepository(), db.getHistoryRepository(), userSession
        );
    }

    @Test
    @DisplayName("Test 1: URL & Search Detection")
    void testUrlAndSearchDetection() {
        // Pure domain
        String res1 = networkService.resolveUrl("example.com", settings.getDefaultSearchEngine());
        assertEquals("https://example.com", res1);

        // Subdomain and path
        String res2 = networkService.resolveUrl("github.com/shubham", settings.getDefaultSearchEngine());
        assertEquals("https://github.com/shubham", res2);

        // Explicit HTTP
        String res3 = networkService.resolveUrl("http://example.org", settings.getDefaultSearchEngine());
        assertEquals("http://example.org", res3);

        // Search query with spaces
        String res4 = networkService.resolveUrl("java tutorial", settings.getDefaultSearchEngine());
        assertTrue(res4.contains("q=java+tutorial") || res4.contains("q=java%20tutorial"),
                "Search query should be properly encoded: " + res4);
    }

    @Test
    @DisplayName("Test 2: Ad and Tracker Blocking")
    void testAdAndTrackerBlocking() {
        // Ads
        assertTrue(adBlocker.isAdBlocked("https://doubleclick.net/ad"));
        assertTrue(adBlocker.isAdBlocked("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"));
        assertFalse(adBlocker.isAdBlocked("https://example.com/index.html"));

        // Trackers
        assertTrue(trackerBlocker.isTrackerBlocked("https://google-analytics.com/analytics.js"));
        assertTrue(trackerBlocker.isTrackerBlocked("https://pixel.facebook.com/tr/"));
        assertFalse(trackerBlocker.isTrackerBlocked("https://example.com/logo.png"));

        // Toggle disable
        adBlocker.setEnabled(false);
        assertFalse(adBlocker.isAdBlocked("https://doubleclick.net/ad"));
        trackerBlocker.setEnabled(false);
        assertFalse(trackerBlocker.isTrackerBlocked("https://google-analytics.com/analytics.js"));
    }

    @Test
    @DisplayName("Test 3: Cache Management & Statistics")
    void testCacheFlow() {
        String testUrl = "https://example.com/cached-page";
        byte[] payload = "<html>Test Content</html>".getBytes();

        // 1. Initial request -> Cache MISS
        assertNull(cacheManager.get(testUrl));
        assertEquals(1, statistics.getCacheMisses());
        assertEquals(0, statistics.getCacheHits());

        // 2. Put into cache
        cacheManager.put(testUrl, new CacheEntry(testUrl, payload, 60000, "text/html", 200));

        // 3. Subsequent request -> Cache HIT
        CacheEntry hit = cacheManager.get(testUrl);
        assertNotNull(hit);
        assertArrayEquals(payload, hit.getResponseBody());
        assertEquals(1, statistics.getCacheHits());
        assertEquals(1, statistics.getCacheMisses());
        assertEquals(50.0, statistics.getHitRatio(), 0.01);
    }

    @Test
    @DisplayName("Test 4: History and Bookmarks Persistence")
    void testHistoryAndBookmarks() throws Exception {
        String userId = "test-user-" + System.currentTimeMillis();
        String url = "https://example.com/test-page";
        String title = "Example Test Page";

        // History
        historyService.addHistory(userId, url, title);
        Thread.sleep(100); // allow executor task to persist
        List<HistoryEntry> history = historyService.getHistory(userId, 10);
        assertFalse(history.isEmpty());
        assertEquals(url, history.get(0).getUrl());

        // Bookmarks
        bookmarkService.addBookmark(userId, url, title);
        Thread.sleep(100);
        assertTrue(bookmarkService.isBookmarked(userId, url));
        List<Bookmark> bookmarks = bookmarkService.getBookmarks(userId);
        assertFalse(bookmarks.isEmpty());
        assertEquals(title, bookmarks.get(0).getTitle());
    }

    @Test
    @DisplayName("Test 5: Settings and Dynamic Update")
    void testSettings() {
        settings.setCacheCapacity(250);
        settings.setCacheTtl(120000);
        settings.setAdBlockingEnabled(false);
        settings.setTrackerBlockingEnabled(false);

        settingsService.saveSettings(settings);

        assertEquals(250, settingsService.getSettings().getCacheCapacity());
        assertFalse(settingsService.getSettings().isAdBlockingEnabled());
    }

    @Test
    @DisplayName("Test 6: Authentication and User Session")
    void testAuthentication() {
        if (authService.isFirebaseConfigured()) {
            // Do not create real Firebase users as a side effect of an automated test.
            assertFalse(authService.isLoggedIn());
            return;
        }
        String email = "dev" + System.currentTimeMillis() + "@cachebrowse.org";
        boolean registered = authService.register(email, "pass123");
        assertFalse(registered, "Authentication must not silently create a local account when Firebase is unavailable");
        assertFalse(authService.login(email, "pass123"));
        assertFalse(authService.isLoggedIn());
    }

    @Test
    @DisplayName("Test 7: External Internet Navigation (example.com)")
    void testExternalInternetNavigation() {
        try {
            HttpResponse res = HttpClient.execute(new HttpRequest("https://example.com", "GET", null, null, 15000));
            assertNotNull(res);
            assertEquals(200, res.getStatusCode());
            assertTrue(res.getBody().length > 0);
            String html = new String(res.getBody());
            assertTrue(html.contains("Example Domain"), "Should contain 'Example Domain'");
        } catch (Exception e) {
            // In restricted test environments without network access, log rather than failing
            System.out.println("Internet access restricted in current runner: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Test 8: Expanded Ad & Tracker Domain and Pattern Detection")
    void testExpandedAdAndTrackerRules() {
        // Broad ad networks
        assertTrue(adBlocker.isAdBlocked("https://googleadservices.com/pagead/aclk?sa=L"));
        assertTrue(adBlocker.isAdBlocked("https://securepubads.g.doubleclick.net/gampad/ads"));
        assertTrue(adBlocker.isAdBlocked("https://criteo.com/delivery/ajs.php"));
        assertTrue(adBlocker.isAdBlocked("https://static.criteo.net/js/ld/ld.js"));
        assertTrue(adBlocker.isAdBlocked("https://taboola.com/feed"));
        assertTrue(adBlocker.isAdBlocked("https://outbrain.com/widget"));
        assertTrue(adBlocker.isAdBlocked("https://amazon-adsystem.com/aax2/apstag.js"));
        assertTrue(adBlocker.isAdBlocked("https://example.com/pagead/test"));

        // Normal legitimate domains must NOT be blocked
        assertFalse(adBlocker.isAdBlocked("https://youtube.com/watch?v=123"));
        assertFalse(adBlocker.isAdBlocked("https://google.com/search?q=test"));
        assertFalse(adBlocker.isAdBlocked("https://github.com/repository"));

        // Comprehensive trackers
        assertTrue(trackerBlocker.isTrackerBlocked("https://clarity.ms/s/0.7.20/clarity.js"));
        assertTrue(trackerBlocker.isTrackerBlocked("https://cdn.segment.com/analytics.js/v1/xyz"));
        assertTrue(trackerBlocker.isTrackerBlocked("https://connect.facebook.net/en_US/fbevents.js"));
        assertTrue(trackerBlocker.isTrackerBlocked("https://bam.nr-data.net/1/xyz"));

        // Check allowed vs blocked counters
        statistics.incAllowedRequests();
        statistics.incAdsBlocked();
        statistics.incTrackersBlocked();
        assertEquals(1, statistics.getAllowedRequests());
        assertEquals(2, statistics.getBlockedRequests());
    }
}
