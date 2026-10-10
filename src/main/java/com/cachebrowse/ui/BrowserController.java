package com.cachebrowse.ui;

import com.cachebrowse.adblock.AdBlocker;
import com.cachebrowse.adblock.CosmeticFilter;
import com.cachebrowse.adblock.TrackerBlocker;
import com.cachebrowse.auth.UserSession;
import com.cachebrowse.cache.CacheManager;
import com.cachebrowse.model.BrowserSettings;
import com.cachebrowse.service.*;
import com.cachebrowse.model.Bookmark;
import com.cachebrowse.service.SearchService;
import com.cachebrowse.statistics.CacheStatistics;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import com.cachebrowse.model.SearchResult;

/**
 * Main browser controller wiring all backend services to the JavaFX UI.
 * All network / database operations run off the FX thread using networkExecutor.
 */
public class BrowserController {

    // --- Core components ---
    private final Stage stage;
    private final CacheManager cacheManager;
    private final CacheStatistics statistics;
    private final BrowserSettings settings;
    private final AdBlocker adBlocker;
    private final TrackerBlocker trackerBlocker;

    // --- Services ---
    private final BrowserService browserService;
    private final HistoryService historyService;
    private final BookmarkService bookmarkService;
    private final SettingsService settingsService;
    private final StatisticsService statisticsService;
    private final AuthenticationService authenticationService;
    private final SearchService searchService;
    private final UserSession userSession;

    private final ExecutorService networkExecutor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "BrowserController-Network");
        t.setDaemon(true);
        return t;
    });

    // --- UI components ---
    private final TabPane tabPane = new TabPane();
    private final Label statusLabel = new Label("● Ready");
    private final Label statsLabel = new Label("Requests: 0 | Hits: 0 | Misses: 0 | Ads: 0 | Trackers: 0");
    private TextField addressBar;
    private Button loginBtn;
    private Button adBlockToggle;
    private Button trackerToggle;
    private Button shieldBtn;

    // --- Per-tab New Tab UI state -------------------------------------------
    private final Map<Tab, NewTabPane> tabNewTabUi = new HashMap<>();

    // --- Debug logging flag ---
    private final boolean debugLogging;

    /** Build and return the root browser UI node. */
    public BrowserController(
            Stage stage,
            CacheManager cacheManager,
            CacheStatistics statistics,
            BrowserSettings settings,
            AdBlocker adBlocker,
            TrackerBlocker trackerBlocker,
            BrowserService browserService,
            HistoryService historyService,
            BookmarkService bookmarkService,
            SettingsService settingsService,
            StatisticsService statisticsService,
            AuthenticationService authenticationService,
            SearchService searchService) {

        this.stage = stage;
        this.cacheManager = cacheManager;
        this.statistics = statistics;
        this.settings = settings;
        this.adBlocker = adBlocker;
        this.trackerBlocker = trackerBlocker;
        this.browserService = browserService;
        this.historyService = historyService;
        this.bookmarkService = bookmarkService;
        this.settingsService = settingsService;
        this.statisticsService = statisticsService;
        this.authenticationService = authenticationService;
        this.searchService = searchService;
        this.userSession = authenticationService != null
                ? authenticationService.getUserSession()
                : UserSession.getInstance();

        // Enable debug logging via system property
        this.debugLogging = "true".equalsIgnoreCase(System.getProperty("cachebrowse.debug"));
    }

    /** Build and return the root browser UI node. */
    public Node createView() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("browser-root");
        root.setTop(createToolbar());
        
        // Tab pane setup
        tabPane.getStyleClass().add("tab-pane");
        root.setCenter(tabPane);
        root.setBottom(createStatusBar());

        createTab(null);

        // Update address bar on tab switch
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab == null) {
                Platform.runLater(() -> {
                    addressBar.clear();
                    addressBar.setPromptText("Search the web or enter a URL");
                });
                return;
            }
            boolean isNew = tabNewTabUi.containsKey(newTab);
            if (isNew) {
                Platform.runLater(() -> {
                    addressBar.clear();
                    addressBar.setPromptText("Search the web or enter a URL");
                });
            } else {
                String loc = getTabUrl(newTab);
                Platform.runLater(() -> addressBar.setText(loc != null && !loc.isBlank() ? loc : ""));
            }
        });

        // Ensure at least one tab always exists
        tabPane.getTabs().addListener((ListChangeListener<Tab>) change -> {
            while (change.next()) {
                if (change.wasRemoved() && tabPane.getTabs().isEmpty()) {
                    createTab(null);
                }
            }
        });

        return root;
    }

    // -------------------------------------------------------------------------
    // Toolbar
    // -------------------------------------------------------------------------
    private Node createToolbar() {
        HBox toolbar = new HBox();
        toolbar.getStyleClass().add("browser-toolbar");
        
        // Navigation section
        HBox navSection = new HBox();
        navSection.getStyleClass().add("nav-section");
        
        Button back = createNavButton("←", "Back");
        Button forward = createNavButton("→", "Forward");  
        Button refresh = createNavButton("⟳", "Reload");
        Button home = createNavButton("⌂", "Home");
        
        navSection.getChildren().addAll(back, forward, refresh, home);
        
        // Address bar section  
        HBox addressSection = new HBox();
        addressSection.getStyleClass().add("address-section");
        
        // Security/Protection shield
        shieldBtn = new Button("🛡");
        shieldBtn.getStyleClass().add("shield-button");
        shieldBtn.setTooltip(new Tooltip("CacheBrowse Protection"));
        shieldBtn.setOnAction(e -> showProtectionDialog());
        
        // Address bar
        addressBar = new TextField();
        addressBar.getStyleClass().add("address-bar");
        addressBar.setPromptText("Search the web or enter a URL");
        HBox.setHgrow(addressBar, Priority.ALWAYS);
        
        addressSection.getChildren().addAll(shieldBtn, addressBar);
        
        // Actions section
        HBox actionsSection = new HBox();
        actionsSection.getStyleClass().add("actions-section");
        
        Button plus = createIconButton("+", "New Tab");
        Button historyBtn = createIconButton("History", "View History");
        Button bookmarkBtn = createIconButton("★", "Bookmark This Page");
        Button bookmarksBtn = createIconButton("⊟", "View Bookmarks");
        
        // Protection toggles
        adBlockToggle = new Button(adBlocker.isEnabled() ? "ADS ✓" : "ADS ✕");
        adBlockToggle.getStyleClass().add(adBlocker.isEnabled() ? "toggle-button-active" : "toggle-button-inactive");
        adBlockToggle.setTooltip(new Tooltip("Ad Blocking"));
        
        trackerToggle = new Button(trackerBlocker.isEnabled() ? "TRK ✓" : "TRK ✕");
        trackerToggle.getStyleClass().add(trackerBlocker.isEnabled() ? "toggle-button-active" : "toggle-button-inactive");
        trackerToggle.setTooltip(new Tooltip("Tracker Blocking"));
        
        Button dashboard = createIconButton("Dashboard", "Browser Dashboard");
        Button settingsBtn = createIconButton("⚙", "Settings");
        
        // User profile button
        loginBtn = new Button(buildLoginLabel());
        loginBtn.getStyleClass().add("profile-button");
        loginBtn.setTooltip(new Tooltip("User Profile"));
        
        actionsSection.getChildren().addAll(
            plus, historyBtn, bookmarkBtn, bookmarksBtn,
            adBlockToggle, trackerToggle,
            dashboard, settingsBtn, loginBtn
        );

        // --- Actions ---
        back.setOnAction(e -> goBack());
        forward.setOnAction(e -> goForward());
        refresh.setOnAction(e -> refresh());
        home.setOnAction(e -> resetTabsToNewTab());
        plus.setOnAction(e -> createTab(null));
        addressBar.setOnAction(e -> navigate(addressBar.getText()));

        historyBtn.setOnAction(e -> openHistory());
        bookmarkBtn.setOnAction(e -> addCurrentBookmark());
        bookmarksBtn.setOnAction(e -> openBookmarks());

        adBlockToggle.setOnAction(e -> toggleAdBlock());
        trackerToggle.setOnAction(e -> toggleTrackerBlock());

        dashboard.setOnAction(e -> new DashboardController(statistics, cacheManager).show());

        settingsBtn.setOnAction(e -> {
            new SettingsController(settings, settingsService, cacheManager).show();
            adBlocker.setEnabled(settings.isAdBlockingEnabled());
            trackerBlocker.setEnabled(settings.isTrackerBlockingEnabled());
            Platform.runLater(() -> {
                updateToggleButtons();
            });
        });

        loginBtn.setOnAction(e -> {
            new AuthDialog(authenticationService).show();
            Platform.runLater(() -> loginBtn.setText(buildLoginLabel()));
        });

        toolbar.getChildren().addAll(navSection, addressSection, actionsSection);
        return toolbar;
    }
    
    private Button createNavButton(String text, String tooltipText) {
        Button btn = new Button(text);
        btn.getStyleClass().add("nav-button");
        btn.setTooltip(new Tooltip(tooltipText));
        return btn;
    }
    
    private Button createIconButton(String text, String tooltipText) {
        Button btn = new Button(text);
        btn.getStyleClass().add("icon-button");
        btn.setTooltip(new Tooltip(tooltipText));
        return btn;
    }
    
    private void updateToggleButtons() {
        adBlockToggle.setText(adBlocker.isEnabled() ? "ADS ✓" : "ADS ✕");
        adBlockToggle.getStyleClass().clear();
        adBlockToggle.getStyleClass().add(adBlocker.isEnabled() ? "toggle-button-active" : "toggle-button-inactive");
        
        trackerToggle.setText(trackerBlocker.isEnabled() ? "TRK ✓" : "TRK ✕");
        trackerToggle.getStyleClass().clear();
        trackerToggle.getStyleClass().add(trackerBlocker.isEnabled() ? "toggle-button-active" : "toggle-button-inactive");
    }

    private void showProtectionDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("CacheBrowse Protection");
        alert.setHeaderText("Active Privacy & Ad Protection");

        StringBuilder sb = new StringBuilder();
        sb.append("Ad Blocking: ").append(adBlocker.isEnabled() ? "ON (Active)" : "OFF (Disabled)").append("\n");
        sb.append("Tracker Blocking: ").append(trackerBlocker.isEnabled() ? "ON (Active)" : "OFF (Disabled)").append("\n\n");
        sb.append("🛡 Ads Blocked: ").append(statistics.getAdsBlocked()).append("\n");
        sb.append("🔒 Trackers Blocked: ").append(statistics.getTrackersBlocked()).append("\n");
        sb.append("⛔ Total Requests Blocked: ").append(statistics.getBlockedRequests()).append("\n");
        sb.append("✓ Requests Allowed: ").append(statistics.getAllowedRequests()).append("\n");
        sb.append("⚡ Cache Hit Ratio: ").append(String.format("%.1f%%", statistics.getHitRatio())).append("\n");

        alert.setContentText(sb.toString());
        alert.showAndWait();
    }

    // -------------------------------------------------------------------------
    // Status bar
    // -------------------------------------------------------------------------
    private Node createStatusBar() {
        HBox status = new HBox(0);
        status.getStyleClass().add("status-bar");

        // Left: page status
        statusLabel.getStyleClass().addAll("status-item", "status-item-success");

        // Separator
        Label sep1 = new Label("  |  ");
        sep1.getStyleClass().add("status-item");

        // Stats label (right side, compact)
        statsLabel.getStyleClass().add("status-item");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        status.getChildren().addAll(statusLabel, sep1, spacer, statsLabel);
        return status;
    }

    // -------------------------------------------------------------------------
    // Tab management
    // -------------------------------------------------------------------------
    /**
     * Create a new browser tab.
     * When {@code url} is {@code null} the tab opens as a New Tab (modern home page).
     */
    private void createTab(String url) {
        Tab tab = new Tab("New Tab");
        tab.setClosable(true);

        if (url != null && !url.trim().isEmpty()) {
            if (debugLogging) {
                System.out.println("[WEBVIEW] CREATED tab=" + tab.getText() + " with url=" + url);
            }
            // Navigate to a webpage immediately
            WebView webView = new WebView();
            WebEngine engine = webView.getEngine();
            setupWebViewListeners(tab, engine);
            tab.setContent(webView);
            tabNewTabUi.remove(tab);
            loadUrl(engine, url);
        } else {
            if (debugLogging) {
                System.out.println("[TAB] CREATED tab=" + tab.getText() + " (New Tab UI)");
            }
            // --- Default: modern New Tab UI ----------------------------
            NewTabPane newTabUi = new NewTabPane(resolved -> {
                // User typed something in the New Tab search box — resolve then navigate
                if (resolved != null && !resolved.isBlank()) {
                    navigate(resolved);
                }
            });
            // Load bookmarks for current user and set them on the new tab UI.
            List<Bookmark> bookmarks = bookmarkService.getBookmarks(userSession.getUserId());
            if (bookmarks != null && !bookmarks.isEmpty()) {
                Platform.runLater(() -> newTabUi.setBookmarks(bookmarks));
            }

            tab.setContent(newTabUi);
            tabNewTabUi.put(tab, newTabUi);

            Platform.runLater(() -> {
                addressBar.clear();
                addressBar.setPromptText("Search or enter address");
            });
        }

        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);

        // Clean up New Tab UI map when this tab is removed (closed by user)
        tabPane.getTabs().addListener((ListChangeListener<Tab>) change -> {
            while (change.next()) {
                if (change.wasRemoved()) {
                    for (Tab removed : change.getRemoved()) {
                        tabNewTabUi.remove(removed);
                        if (debugLogging) {
                            System.out.println("[TAB] REMOVED tab=" + removed.getText());
                        }
                    }
                }
            }
        });
    }

    /**
     * Set up comprehensive loading/state listeners for a WebView-based tab.
     * Handles ALL Worker.State values so loading never stays indefinitely.
     */
    private void setupWebViewListeners(Tab tab, WebEngine engine) {
        if (debugLogging) {
            System.out.println("[WEBVIEW] setupWebViewListeners for tab=" + tab.getText());
        }
        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            String loc = engine.getLocation();
            if (debugLogging) {
                System.out.println("[STATE] " + newState + " | URL: " + loc);
            }

            switch (newState) {
                case RUNNING -> Platform.runLater(() -> {
                    if (debugLogging) {
                        System.out.println("[STATE] RUNNING: " + loc);
                    }
                    statusLabel.setText("Loading…");
                });
                case SUCCEEDED -> {
                    String title = engine.getTitle();
                    String location = engine.getLocation();
                    if (debugLogging) {
                        System.out.println("[STATE] SUCCEEDED: " + location);
                    }
                    Platform.runLater(() -> {
                        // Reset cosmetic hidden count for new page
                        statistics.resetCosmeticHiddenCount();
                        
                        tab.setText(title != null && !title.isBlank() ? title : "CacheBrowse");
                        addressBar.setText(location != null ? location : "");
                        statusLabel.setText("Page loaded");
                        updateStats();
                    });

                    // ── Inject Cosmetic Filter for on-page advertisement removal (e.g. YouTube sponsored items)
                    CosmeticFilter.apply(engine, location, adBlocker.isEnabled(), statistics, this::updateStats);

                    // Save to history asynchronously (off FX thread)
                    if (location != null && !location.isBlank() && historyService != null) {
                        String uid = userSession.getUserId();
                        String ttl = (title != null && !title.isBlank()) ? title : location;
                        networkExecutor.submit(() -> historyService.addHistory(uid, location, ttl));
                    }
                }
                case FAILED -> {
                    Throwable ex = engine.getLoadWorker().getException();
                    if (debugLogging) {
                        System.out.println("[STATE] FAILED: " + loc
                                + (ex != null ? " | Exception: " + ex.getMessage() : ""));
                    }
                    Platform.runLater(() -> {
                        statusLabel.setText("Failed to load: " + (loc != null ? loc : "unknown"));
                        tab.setText("Error");
                    });
                }
                case CANCELLED -> {
                    if (debugLogging) {
                        System.out.println("[STATE] CANCELLED: " + loc);
                    }
                    Platform.runLater(() -> statusLabel.setText("Cancelled"));
                }
                case READY, SCHEDULED -> {}
            }
        });

        // Intercept custom protocol navigation emitted by the home page HTML.
        engine.locationProperty().addListener((obs, oldLoc, newLoc) -> {
            if (newLoc != null && newLoc.startsWith("cachebrowse://navigate?")) {
                if (debugLogging) {
                    System.out.println("[NAV] Intercepted cachebrowse://: " + newLoc);
                }
                engine.getLoadWorker().cancel();
                navigate(newLoc);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Navigation
    // -------------------------------------------------------------------------
    /**
     * Main navigation entry point. Accepts raw user input (URL or search query),
     * resolves it to a full HTTP/HTTPS URL, then loads it in the current tab.
     */
    private void navigate(String input) {
        if (input == null || input.isBlank()) return;

        String url = resolveUrl(input);
        if (debugLogging) {
            System.out.println("[NAV] input=" + input);
            System.out.println("[NAV] resolved=" + url);
        }

        if (url == null || url.isBlank()) {
            if (debugLogging) {
                System.out.println("[NAV] blank URL - aborting");
            }
            return;
        }

        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab == null) {
            createTab(url);
            return;
        }

        // If this tab is still showing the New Tab UI, replace content with a WebView
        if (tabNewTabUi.containsKey(tab)) {
            if (debugLogging) {
                System.out.println("[TAB] Converting NewTab to WebView for: " + url);
            }
            tabNewTabUi.remove(tab);
            tab.setText("Loading…");

            WebView webView = new WebView();
            WebEngine engine = webView.getEngine();
            setupWebViewListeners(tab, engine);
            tab.setContent(webView);

            Platform.runLater(() -> addressBar.setText(url));
            statusLabel.setText("Loading…");
            loadUrl(engine, url);
        } else {
            // Existing webpage tab – navigate in-place
            if (tab.getContent() instanceof WebView wv) {
                if (debugLogging) {
                    System.out.println("[NAV] Loading existing WebView: " + url);
                }
                Platform.runLater(() -> addressBar.setText(url));
                loadUrl(wv.getEngine(), url);
            }
        }
    }

    /**
     * Convert raw user input into a navigable HTTP/HTTPS URL.
     * Handles the custom cachebrowse:// protocol, explicit URLs, domain names and search queries.
     */
    private String resolveUrl(String input) {
        if (input == null || input.isBlank()) return "";

        // Decode the internal navigation protocol produced by the home-page HTML
        if (input.startsWith("cachebrowse://navigate?")) {
            try {
                String query = input.substring("cachebrowse://navigate?".length());
                java.util.Map<String, String> params = new java.util.HashMap<>();
                for (String pair : query.split("&")) {
                    int idx = pair.indexOf('=');
                    if (idx > 0 && idx < pair.length() - 1) {
                        String key   = java.net.URLDecoder.decode(pair.substring(0, idx),   StandardCharsets.UTF_8);
                        String value = java.net.URLDecoder.decode(pair.substring(idx + 1),  StandardCharsets.UTF_8);
                        params.put(key, value);
                    }
                }
                if (params.containsKey("url")) {
                    // The value from the home page is the raw user input — resolve it again
                    return resolveInput(java.net.URLDecoder.decode(params.get("url"), StandardCharsets.UTF_8).trim());
                }
            } catch (Exception e) {
                System.out.println("[Browser] Failed to parse cachebrowse:// URL: " + e.getMessage());
            }
        }
        return resolveInput(input.trim());
    }

    /**
     * Resolve a plain string (URL or search query) to an absolute HTTP/HTTPS URL.
     * Never returns {@code null}; returns an empty string if input is blank.
     */
    private String resolveInput(String input) {
        if (input == null || input.isBlank()) return "";

        // Already a fully-qualified URL
        if (input.startsWith("http://") || input.startsWith("https://")) {
            return input;
        }
        // Looks like a domain name (contains a dot, no spaces, doesn't start with a dot)
        if (input.contains(".") && !input.contains(" ") && !input.startsWith(".")) {
            return "https://" + input;
        }
        // Treat everything else as a search query
        String encoded = URLEncoder.encode(input, StandardCharsets.UTF_8);
        String template = settings.getDefaultSearchEngine();
        if (template == null || template.isBlank()) {
            template = "https://www.google.com/search?q=%s";
        }
        return template.replace("%s", encoded);
    }

    /** Get the current URL associated with a tab's content. */
    private String getTabUrl(Tab tab) {
        if (tab.getContent() instanceof WebView wv) {
            return wv.getEngine().getLocation();
        }
        return null;
    }

    /** Reset the selected tab back to a fresh New Tab page. */
    private void resetTabsToNewTab() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab != null && tabNewTabUi.containsKey(tab)) {
            // Already on New Tab — just clear address bar
            Platform.runLater(() -> {
                addressBar.clear();
                addressBar.setPromptText("Search or enter address");
            });
            return;
        }
        // Replace current tab content with New Tab UI
        tabNewTabUi.remove(tab);
        NewTabPane newTabUi = new NewTabPane(resolved -> {
            if (resolved != null && !resolved.isBlank()) {
                navigate(resolved);
            }
        });
        tab.setContent(newTabUi);
        tabNewTabUi.put(tab, newTabUi);
        tab.setText("New Tab");
        Platform.runLater(() -> {
            addressBar.clear();
            addressBar.setPromptText("Search or enter address");
        });
    }

    /**
     * Core loading method: applies ad/tracker blocking, checks the cache, and
     * delegates actual page loading to the WebEngine.
     */
    private void loadUrl(WebEngine engine, String url) {
        if (debugLogging) {
            System.out.println("[LOAD] url=" + url);
        }
        statistics.incTotalRequests();

        // ── Ad block check ────────────────────────────────────────────────────
        if (settings.isAdBlockingEnabled() && adBlocker.isAdBlocked(url)) {
            if (debugLogging) {
                System.out.println("[LOAD] Ad-blocked: " + url);
            }
            statistics.incAdsBlocked();
            Platform.runLater(() -> {
                engine.loadContent(blockedHtml(url, "Ad Blocker"), "text/html");
                statusLabel.setText("Blocked by Ad Blocker");
                updateStats();
            });
            return;
        }

        // ── Tracker block check ───────────────────────────────────────────────
        if (settings.isTrackerBlockingEnabled() && trackerBlocker.isTrackerBlocked(url)) {
            if (debugLogging) {
                System.out.println("[LOAD] Tracker-blocked: " + url);
            }
            statistics.incTrackersBlocked();
            Platform.runLater(() -> {
                engine.loadContent(blockedHtml(url, "Tracker Blocker"), "text/html");
                statusLabel.setText("Blocked by Tracker Blocker");
                updateStats();
            });
            return;
        }

        statistics.incAllowedRequests();

        // ── Cache lookup ──────────────────────────────────────────────────────
        // NOTE: The proxy server (ClientHandler) already handles caching at the network layer.
        // All subresources (CSS, JS, images) are served from the proxy cache transparently.
        // The BrowserController-level cache lookup here is for the top-level HTML page URL only.
        // 
        // IMPORTANT: We do NOT use engine.loadContent() for cache HITs here.
        // Using loadContent() with raw cached bytes caused WebView blinking because:
        //   1. The cached bytes may include HTTP response headers (not just body)
        //   2. Content-type mismatches cause WebEngine to repaint repeatedly
        //   3. Malformed loadContent calls trigger Prism texture invalidations
        //
        // Instead, we always use engine.load(url) and let the proxy serve the cached
        // response transparently. The proxy cache HIT is instant (in-memory LRU).
        // This is the correct, stable architecture.

        // ── Always use engine.load(url) — proxy handles caching transparently ─
        if (debugLogging) {
            System.out.println("[LOAD] Calling WebEngine.load(): " + url);
        }
        statistics.incNetworkRequests();

        Platform.runLater(() -> {
            engine.load(url);
            updateStats();
        });
    }

    // -------------------------------------------------------------------------
    // Navigation helpers
    // -------------------------------------------------------------------------
    private void goBack() {
        WebView web = getCurrentWebView();
        if (web != null) {
            var history = web.getEngine().getHistory();
            if (history.getCurrentIndex() > 0) history.go(-1);
        }
    }

    private void goForward() {
        WebView web = getCurrentWebView();
        if (web != null) {
            var history = web.getEngine().getHistory();
            if (history.getCurrentIndex() < history.getEntries().size() - 1) history.go(1);
        }
    }

    private void refresh() {
        WebView web = getCurrentWebView();
        if (web != null) web.getEngine().reload();
    }

    private WebView getCurrentWebView() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        return (tab != null && tab.getContent() instanceof WebView wv) ? wv : null;
    }

    // -------------------------------------------------------------------------
    // Bookmarks
    // -------------------------------------------------------------------------
    private void addCurrentBookmark() {
        WebView web = getCurrentWebView();
        if (web == null) return;
        String loc   = web.getEngine().getLocation();
        String title = web.getEngine().getTitle();
        if (loc == null || loc.isBlank()) return;

        if (bookmarkService != null) {
            String uid = userSession.getUserId();
            String ttl = (title != null && !title.isBlank()) ? title : loc;
            bookmarkService.addBookmark(uid, loc, ttl);
        }
        statusLabel.setText("Bookmarked: " + loc);
    }

    private void openBookmarks() {
        if (bookmarkService != null) {
            new BookmarkDialog(bookmarkService, userSession, this::navigate).show();
        }
    }

    // -------------------------------------------------------------------------
    // History
    // -------------------------------------------------------------------------
    private void openHistory() {
        if (historyService != null) {
            new HistoryDialog(historyService, userSession, this::navigate).show();
        }
    }

    // -------------------------------------------------------------------------
    // Ad / Tracker toggle
    // -------------------------------------------------------------------------
    private void toggleAdBlock() {
        boolean nowEnabled = !adBlocker.isEnabled();
        adBlocker.setEnabled(nowEnabled);
        settings.setAdBlockingEnabled(nowEnabled);
        if (settingsService != null) {
            settingsService.saveSettings(settings);
        }
        adBlockToggle.setText(nowEnabled ? "ADS ✓" : "ADS ✕");
        adBlockToggle.getStyleClass().clear();
        adBlockToggle.getStyleClass().add(nowEnabled ? "toggle-button-active" : "toggle-button-inactive");
        statusLabel.setText("Ad Blocking " + (nowEnabled ? "enabled" : "disabled"));
        updateStats();
    }

    private void toggleTrackerBlock() {
        boolean nowEnabled = !trackerBlocker.isEnabled();
        trackerBlocker.setEnabled(nowEnabled);
        settings.setTrackerBlockingEnabled(nowEnabled);
        if (settingsService != null) {
            settingsService.saveSettings(settings);
        }
        trackerToggle.setText(nowEnabled ? "TRK ✓" : "TRK ✕");
        trackerToggle.getStyleClass().clear();
        trackerToggle.getStyleClass().add(nowEnabled ? "toggle-button-active" : "toggle-button-inactive");
        statusLabel.setText("Tracker Blocking " + (nowEnabled ? "enabled" : "disabled"));
        updateStats();
    }

    // -------------------------------------------------------------------------
    // Stats & helpers
    // -------------------------------------------------------------------------

    /**
     * Timestamp of the last time a stats UI update was scheduled.
     * Volatile so proxy background threads see the latest value without locking.
     */
    private volatile long lastStatsUpdateMs = 0;

    /**
     * Atomic flag: true when a stats update task is already queued in Platform.runLater().
     * Uses AtomicBoolean.compareAndSet() to eliminate the check-then-set race condition
     * that previously allowed multiple threads to simultaneously queue duplicate UI updates,
     * flooding the JavaFX Application Thread and causing WebView flickering.
     */
    private final AtomicBoolean statsUpdatePending = new AtomicBoolean(false);

    /**
     * Updates the stats label at most once every 500 ms.
     * <p>
     * Previously, the proxy called updateStats() for EVERY blocked request (ad/tracker)
     * and every CONNECT-level block — up to 100+ times per page on ad-heavy sites like YouTube.
     * Combined with the non-atomic pending flag, this flooded Platform.runLater() with hundreds
     * of concurrent UI update tasks, starving the JavaFX rendering thread and causing the
     * WebView content area to blink/flicker continuously.
     * </p>
     * <p>
     * Fix: Use AtomicBoolean.compareAndSet(false, true) to guarantee only ONE update task
     * is ever queued at a time. The task clears the flag when it completes so the next
     * update can proceed after the 500ms throttle expires.
     * </p>
     */
    public void updateStats() {
        long now = System.currentTimeMillis();
        if (now - lastStatsUpdateMs < 500) {
            return; // within throttle window — skip
        }
        // compareAndSet: atomically sets to true only if currently false.
        // If another thread already set it to true, this returns false and we skip.
        if (!statsUpdatePending.compareAndSet(false, true)) {
            return; // another update is already pending
        }
        lastStatsUpdateMs = now;
        Platform.runLater(() -> {
            try {
                long adsBlocked      = statistics.getAdsBlocked();
                long trackersBlocked = statistics.getTrackersBlocked();
                statsLabel.setText(
                        "Requests: "      + statistics.getTotalRequests()
                        + " | Hits: "     + statistics.getCacheHits()
                        + " | Misses: "   + statistics.getCacheMisses()
                        + " | Ratio: "    + String.format("%.1f%%", statistics.getHitRatio())
                        + " | Ads Blocked: "  + adsBlocked
                        + " | Ads Hidden: "   + cosmeticHiddenCount.get()
                        + " | Trackers: " + trackersBlocked
                        + " | Blocked: "  + (adsBlocked + trackersBlocked)
                        + " | Allowed: "  + statistics.getAllowedRequests()
                );
            } finally {
                // Always clear the flag so future updates can be scheduled
                statsUpdatePending.set(false);
            }
        });
    }

    // Track cosmetic-hidden ads separately from network-blocked ads
    private final AtomicLong cosmeticHiddenCount = new AtomicLong();

    /**
     * Record that cosmetic filtering hid an ad element.
     * This is distinct from network-blocked ads.
     */
    public void recordCosmeticAdHidden() {
        cosmeticHiddenCount.incrementAndGet();
        updateStats();
    }

    /**
     * Reset the cosmetic hidden count (e.g., on new page load).
     */
    public void resetCosmeticHiddenCount() {
        cosmeticHiddenCount.set(0);
    }

    private String buildLoginLabel() {
        if (userSession != null && userSession.isLoggedIn()) {
            String email = userSession.getEmail();
            return email != null && email.length() > 15 ? email.substring(0, 13) + "…" : (email != null ? email : "Profile");
        }
        return "Login";
    }

    /**
     * Handles an internal /search command. Results are displayed in a simple alert.
     * This is a secondary feature — normal navigation goes through resolveInput().
     */
    private void handleSearch(String query) {
        if (searchService == null || query == null || query.isBlank()) {
            return;
        }
        CompletableFuture<List<SearchResult>> future = searchService.searchAsync(query);
        future.whenComplete((results, ex) -> {
            Platform.runLater(() -> {
                if (ex != null) {
                    Alert err = new Alert(Alert.AlertType.ERROR);
                    err.setTitle("Search Error");
                    err.setHeaderText("Failed to search for " + query);
                    err.setContentText(ex.getMessage());
                    err.showAndWait();
                } else {
                    StringBuilder sb = new StringBuilder();
                    if (results == null || results.isEmpty()) {
                        sb.append("No results found for ").append(query).append(".");
                    } else {
                        sb.append("Results for '").append(query).append("':\n");
                        results.forEach(r -> sb.append(r.toString()).append('\n'));
                    }
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Search Results");
                    alert.setHeaderText(null);
                    alert.getDialogPane().setContent(new javafx.scene.control.TextArea(sb.toString()));
                    alert.showAndWait();
                }
            });
        });
    }

    private String blockedHtml(String url, String reason) {
        return "<!DOCTYPE html><html><head><title>Blocked</title>"
                + "<style>"
                + "* { margin: 0; padding: 0; box-sizing: border-box; }"
                + "body { font-family: 'Segoe UI', system-ui, sans-serif; background: #0F172A;"
                + "  display: flex; align-items: center; justify-content: center; min-height: 100vh; }"
                + ".card { background: #1E293B; border: 1px solid #334155; border-radius: 16px;"
                + "  padding: 40px 48px; max-width: 520px; width: 90%; text-align: center; }"
                + ".icon { font-size: 48px; margin-bottom: 16px; }"
                + "h2 { color: #EF4444; font-size: 22px; font-weight: 700; margin-bottom: 10px; }"
                + "p { color: #94A3B8; font-size: 14px; margin-bottom: 8px; }"
                + "code { color: #3B82F6; font-size: 12px; word-break: break-all; }"
                + ".badge { display: inline-block; background: #EF4444; color: white;"
                + "  padding: 3px 10px; border-radius: 20px; font-size: 11px; font-weight: 600;"
                + "  text-transform: uppercase; margin-bottom: 20px; }"
                + "</style></head><body>"
                + "<div class='card'>"
                + "<div class='icon'>🛡</div>"
                + "<div class='badge'>" + reason + "</div>"
                + "<h2>Request Blocked</h2>"
                + "<p>CacheBrowse prevented this request:</p>"
                + "<p><code>" + url + "</code></p>"
                + "</div></body></html>";
    }

    /** Shutdown executors cleanly when application closes. */
    public void shutdown() {
        networkExecutor.shutdown();
    }

    // ── Public API methods called by HttpApiServer via Platform.runLater ──────

    /** Navigate to a URL — called from the React API bridge. */
    public void navigateFromApi(String url) {
        if (url == null || url.isBlank()) return;
        navigate(url);
    }

    /** Go back — called from the React API bridge. */
    public void goBackFromApi() { goBack(); }

    /** Go forward — called from the React API bridge. */
    public void goForwardFromApi() { goForward(); }

    /** Reload — called from the React API bridge. */
    public void reloadFromApi() { refresh(); }
}