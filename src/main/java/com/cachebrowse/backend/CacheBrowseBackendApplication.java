package com.cachebrowse.backend;

import com.cachebrowse.adblock.AdBlocker;
import com.cachebrowse.adblock.TrackerBlocker;
import com.cachebrowse.api.HttpApiServer;
import com.cachebrowse.auth.UserSession;
import com.cachebrowse.cache.CacheManager;
import com.cachebrowse.database.DatabaseManager;
import com.cachebrowse.model.BrowserSettings;
import com.cachebrowse.service.*;
import com.cachebrowse.statistics.CacheStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

/**
 * CacheBrowse Backend Application entry point.
 *
 * Starts the Java application services and a loopback-only HTTP REST API.
 * Chromium navigation, page cache, and request interception are owned by Electron.
 *
 * NO JavaFX code is referenced here. This class is a pure Java main class.
 *
 * Start with:
 *   mvn exec:java
 */
public class CacheBrowseBackendApplication {

    private static final Logger LOG = LoggerFactory.getLogger(CacheBrowseBackendApplication.class);

    public static final int DEFAULT_API_PORT = 8080;

    public static void main(String[] args) throws Exception {
        LOG.info("=======================================================");
        LOG.info("  CacheBrowse Backend  —  starting up");
        LOG.info("=======================================================");

        // ── Core domain objects ──────────────────────────────────────────────
        BrowserSettings  settings      = new BrowserSettings();
        CacheStatistics  statistics    = new CacheStatistics();
        CacheManager     cacheManager  = new CacheManager(settings.getCacheCapacity(), statistics);
        AdBlocker        adBlocker     = new AdBlocker();
        TrackerBlocker   trackerBlocker= new TrackerBlocker();

        adBlocker.setEnabled(settings.isAdBlockingEnabled());
        trackerBlocker.setEnabled(settings.isTrackerBlockingEnabled());

        // ── Database layer (Cloudflare D1 — graceful if unconfigured) ────────
        DatabaseManager db = new DatabaseManager();

        // REST authentication is stateless: Firebase ID tokens are verified per request.
        // Do not load or refresh the legacy process-global JavaFX session here.
        UserSession userSession = UserSession.getInstance();

        // ── Services ──────────────────────────────────────────────────────────
        HistoryService    historyService   = new HistoryService(db.getHistoryRepository());
        BookmarkService   bookmarkService  = new BookmarkService(db.getBookmarkRepository());
        SettingsService   settingsService  = new SettingsService(db.getSettingsRepository(), settings);
        StatisticsService statsService     = new StatisticsService(statistics, db.getStatisticsRepository());
        CacheService      cacheService     = new CacheService(cacheManager);
        SearchService     searchService    = new SearchService(
                db.getBookmarkRepository(), db.getHistoryRepository(), UserSession.getInstance());

        // Finish the one-time D1 reads before advertising the API as ready, so
        // persisted protection/search settings are active on the first tab.
        settingsService.ready().join();
        adBlocker.setEnabled(settings.isAdBlockingEnabled());
        trackerBlocker.setEnabled(settings.isTrackerBlockingEnabled());

        // ── REST API server ───────────────────────────────────────────────────
        int apiPort = configuredPort("CACHEBROWSE_API_PORT", DEFAULT_API_PORT);
        HttpApiServer apiServer = new HttpApiServer(
                null, historyService, bookmarkService,
                settingsService, statistics, cacheManager,
                adBlocker, trackerBlocker, settings,
                apiPort);
        apiServer.setDatabaseManager(db);
        apiServer.setPersistenceAvailable(db.isInitialized());
        if (!db.isInitialized() && db.getConfig().isConfigured())
            apiServer.setPersistenceIssue(db.getD1Client().getLastFailure());

        apiServer.start();
        LOG.info("REST API server started on http://127.0.0.1:{}", apiPort);
        LOG.info("React frontend:  http://localhost:5173");
        LOG.info("API base URL:    http://127.0.0.1:{}/api", apiPort);
        LOG.info("=======================================================");
        LOG.info("  Backend ready — waiting for requests");
        LOG.info("  Press Ctrl+C to stop");
        LOG.info("=======================================================");

        // ── Shutdown hook ─────────────────────────────────────────────────────
        final HttpApiServer finalApi = apiServer;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutting down CacheBrowse backend…");
            finalApi.stop();
            historyService.shutdown();
            bookmarkService.shutdown();
            settingsService.shutdown();
            statsService.shutdown();
            searchService.shutdown();
            LOG.info("Shutdown complete.");
        }, "ShutdownHook"));

        // ── Block main thread indefinitely (servers run on daemon threads) ────
        new CountDownLatch(1).await();
    }

    private static int configuredPort(String key, int fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) return fallback;
        try {
            int port = Integer.parseInt(value);
            return port > 0 && port <= 65535 ? port : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
