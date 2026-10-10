package com.cachebrowse.ui;

import com.cachebrowse.adblock.AdBlocker;
import com.cachebrowse.adblock.TrackerBlocker;
// No need for explicit AuthDialog import – same package
import com.cachebrowse.auth.UserSession;
import com.cachebrowse.cache.CacheManager;
import com.cachebrowse.database.DatabaseManager;
import com.cachebrowse.model.BrowserSettings;
import com.cachebrowse.proxy.ProxyServer;
import com.cachebrowse.service.*;
import com.cachebrowse.service.SearchService;
import com.cachebrowse.statistics.CacheStatistics;

import javafx.application.Application;
import javafx.application.Platform; // For graceful exit after auth dialog
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

/**
 * JavaFX application entry point.
 * Wires up all backend services, starts the local ad/tracker-blocking caching proxy,
 * and constructs the browser UI.
 */
public class BrowserApplication extends Application {

    private ProxyServer proxyServer;

    @Override
    public void start(Stage stage) {

        // --- Core domain objects ---
        BrowserSettings settings   = new BrowserSettings();
        CacheStatistics  statistics = new CacheStatistics();
        CacheManager     cacheManager = new CacheManager(settings.getCacheCapacity(), statistics);
        AdBlocker        adBlocker    = new AdBlocker();
        TrackerBlocker   trackerBlocker = new TrackerBlocker();

        // Apply settings-driven toggles
        adBlocker.setEnabled(settings.isAdBlockingEnabled());
        trackerBlocker.setEnabled(settings.isTrackerBlockingEnabled());

        // --- Database layer (Cloudflare D1 – graceful if unconfigured) ---
        DatabaseManager db = new DatabaseManager();

        // --- Auth + session ---
        UserSession userSession = UserSession.getInstance();
        AuthenticationService authService = new AuthenticationService(
                db.getUserRepository(), userSession);

        // --- Services ---
        HistoryService   historyService   = new HistoryService(db.getHistoryRepository());
        BookmarkService  bookmarkService  = new BookmarkService(db.getBookmarkRepository());
        SettingsService  settingsService  = new SettingsService(db.getSettingsRepository(), settings);
        StatisticsService statsService    = new StatisticsService(statistics, db.getStatisticsRepository());
        NetworkService   networkService   = new NetworkService();
        CacheService     cacheService     = new CacheService(cacheManager);

        BrowserService browserService = new BrowserService(
                networkService, cacheService, adBlocker, trackerBlocker,
                historyService, bookmarkService, settingsService, statsService, authService);

        // --- Search Service ---
        SearchService searchService = new SearchService(
                db.getBookmarkRepository(), db.getHistoryRepository(), UserSession.getInstance());

        // --- Browser controller ---
        BrowserController controller = new BrowserController(
                stage, cacheManager, statistics, settings,
                adBlocker, trackerBlocker,
                browserService, historyService, bookmarkService,
                settingsService, statsService, authService, searchService);

        // --- Legacy JavaFX-only proxy uses its own port; the current Electron
        // desktop runtime does not start this proxy. ---
        try {
            proxyServer = new ProxyServer(configuredProxyPort(), cacheManager, statistics, settings, adBlocker, trackerBlocker);
            proxyServer.setOnStatsUpdated(controller::updateStats);
            proxyServer.start();
        } catch (Exception e) {
            System.err.println("[BrowserApplication] Could not start local proxy server: " + e.getMessage());
        }

        // --- Start React API Bridge (HTTP on localhost:8090) ---
        com.cachebrowse.api.HttpApiServer apiServer = null;
        try {
            apiServer = new com.cachebrowse.api.HttpApiServer(
                    authService, historyService, bookmarkService,
                    settingsService, statistics, cacheManager,
                    adBlocker, trackerBlocker, settings);
            // Wire JavaFX browser navigation callbacks so React UI can control the browser
            apiServer.setOnNavigateTo(url ->
                javafx.application.Platform.runLater(() -> controller.navigateFromApi(url)));
            apiServer.setOnNavigateBack(() ->
                javafx.application.Platform.runLater(controller::goBackFromApi));
            apiServer.setOnNavigateForward(() ->
                javafx.application.Platform.runLater(controller::goForwardFromApi));
            apiServer.setOnNavigateReload(() ->
                javafx.application.Platform.runLater(controller::reloadFromApi));
            apiServer.start();
        } catch (Exception e) {
            System.err.println("[BrowserApplication] Could not start API server: " + e.getMessage());
        }
        final com.cachebrowse.api.HttpApiServer finalApiServer = apiServer;

        // --- Layout ---
        BorderPane root = new BorderPane();
        root.setCenter(controller.createView());

        Scene scene = new Scene(root, 1400, 850);

        // CSS
        var css = getClass().getResource("/styles.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }

        // Load persisted statistics
        statsService.loadStatistics(userSession.getUserId());

        stage.setTitle("CacheBrowse – Smart Caching Browser");
        stage.setMinWidth(1100);
        stage.setMinHeight(680);
        stage.setScene(scene);

        // Prompt for authentication if no session exists
        if (!authService.isLoggedIn()) {
            // Display the authentication dialog before showing the main UI.
            AuthDialog authDialog = new AuthDialog(authService);
            authDialog.show();
            if (!authService.isLoggedIn()) {
                Platform.exit();
                return;
            }
        }

        stage.show();

        // Shutdown executors and proxy on close
        stage.setOnCloseRequest(e -> {
            statsService.saveStatistics(userSession.getUserId());
            if (proxyServer != null) {
                proxyServer.stop();
            }
            if (finalApiServer != null) {
                finalApiServer.stop();
            }
            controller.shutdown();
            historyService.shutdown();
            bookmarkService.shutdown();
            settingsService.shutdown();
            statsService.shutdown();
            authService.shutdown();
            searchService.shutdown();
        });
    }

    private static int configuredProxyPort() {
        String value = System.getenv("CACHEBROWSE_PROXY_PORT");
        if (value != null && !value.isBlank()) {
            try {
                int port = Integer.parseInt(value);
                if (port > 0 && port <= 65535 && port != 8080) return port;
            } catch (NumberFormatException ignored) { }
        }
        return 8888;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
