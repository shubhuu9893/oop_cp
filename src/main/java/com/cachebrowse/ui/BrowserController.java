package com.cachebrowse.ui;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import javafx.collections.ListChangeListener;

import com.cachebrowse.adblock.AdBlocker;
import com.cachebrowse.adblock.TrackerBlocker;
import com.cachebrowse.bookmark.BookmarkManager;
import com.cachebrowse.cache.CacheManager;
import com.cachebrowse.history.HistoryManager;
import com.cachebrowse.model.BrowserSettings;
import com.cachebrowse.statistics.CacheStatistics;

import javafx.concurrent.Worker;
import javafx.application.Platform;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import com.cachebrowse.cache.CacheEntry;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

public class BrowserController {

    private final Stage stage;
    private final CacheManager cacheManager;
    private final CacheStatistics statistics;
    private final BrowserSettings settings;
    private final HistoryManager historyManager;
    private final BookmarkManager bookmarkManager;
    private final AdBlocker adBlocker;
    private final TrackerBlocker trackerBlocker;
    // Executor used for network requests – keeps the FX thread responsive.
    private final ExecutorService networkExecutor = Executors.newFixedThreadPool(4);

    private final TabPane tabPane = new TabPane();

    private final Label statusLabel =
            new Label("CacheBrowse Ready");

    private final Label statsLabel =
            new Label("Requests: 0 | Hits: 0 | Misses: 0");

    private TextField addressBar;

    public BrowserController(
            Stage stage,
            CacheManager cacheManager,
            CacheStatistics statistics,
            BrowserSettings settings,
            HistoryManager historyManager,
            BookmarkManager bookmarkManager,
            AdBlocker adBlocker,
            TrackerBlocker trackerBlocker) {

        this.stage = stage;
        this.cacheManager = cacheManager;
        this.statistics = statistics;
        this.settings = settings;
        this.historyManager = historyManager;
        this.bookmarkManager = bookmarkManager;
        this.adBlocker = adBlocker;
        this.trackerBlocker = trackerBlocker;
    }

    /**
     * Creates the complete browser UI.
     */
    public Node createView() {

        BorderPane root = new BorderPane();

        root.setTop(createToolbar());
        root.setCenter(tabPane);
        root.setBottom(createStatusBar());

        createTab(settings.getHomePage());

        // Update address bar when switching tabs.
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null && newTab.getContent() instanceof WebView)
                addressBar.setText(((WebView) newTab.getContent()).getEngine().getLocation());
        });

        // Ensure at least one tab exists – create a new tab if all are closed.
        tabPane.getTabs().addListener((ListChangeListener<Tab>) change -> {
            while (change.next()) {
                if (change.wasRemoved() && tabPane.getTabs().isEmpty()) {
                    // User closed the last remaining tab; create a new one.
                    createTab(settings.getHomePage());
                }
            }
        });

        return root;
    }

    /**
     * Browser toolbar.
     */
    private Node createToolbar() {

        VBox container = new VBox();

        HBox toolbar = new HBox(8);
        toolbar.setPadding(new Insets(10));

        Button back = new Button("←");
        Button forward = new Button("→");
        Button refresh = new Button("⟳");
        Button home = new Button("⌂");

        Button dashboard = new Button("Dashboard");
        Button bookmark = new Button("★");
        Button settingsBtn = new Button("⚙");

        addressBar = new TextField();
        addressBar.setPromptText("Enter URL or search...");
        addressBar.setPrefWidth(650);

        Button go = new Button("Go");

        // + New Tab button – adds a new browser tab and navigates to home page.
        Button plus = new Button("+");
        plus.setOnAction(e -> createTab(settings.getHomePage()));

        back.setOnAction(e -> goBack());

        forward.setOnAction(e -> goForward());

        refresh.setOnAction(e -> refresh());

        home.setOnAction(e ->
                navigate(settings.getHomePage())
        );

        go.setOnAction(e ->
                navigate(addressBar.getText())
        );

        addressBar.setOnAction(e ->
                navigate(addressBar.getText())
        );

        dashboard.setOnAction(e ->
                new DashboardController(
                        statistics,
                        cacheManager
                ).show()
        );

        settingsBtn.setOnAction(e ->
                new SettingsController(settings).show()
        );

        bookmark.setOnAction(e -> {

            WebView web = getCurrentWebView();

                if (web != null &&
                        web.getEngine().getLocation() != null) {

                    bookmarkManager.addBookmark(
                            web.getEngine().getLocation()
                    );

                    statusLabel.setText("Page saved to bookmarks");
            }
        });

        toolbar.getChildren().addAll(
                back,
                forward,
                refresh,
                home,
                addressBar,
                go,
                // Insert + button before bookmark.
                plus,
                bookmark,
                dashboard,
                settingsBtn
        );

        container.getChildren().add(toolbar);

        return container;
    }

    /**
     * Bottom status bar.
     */
    private Node createStatusBar() {

        HBox status = new HBox(20);

        status.setPadding(new Insets(8));

        status.getStyleClass().add("status-bar");

        status.getChildren().addAll(
                statusLabel,
                statsLabel
        );

        return status;
    }

    /**
     * Creates a new browser tab.
     */
    private void createTab(String url) {

        WebView webView = new WebView();

        WebEngine engine = webView.getEngine();

        Tab tab = new Tab("New Tab");

        tab.setClosable(true);

        tab.setContent(webView);

        tabPane.getTabs().add(tab);

        tabPane.getSelectionModel().select(tab);

        engine.getLoadWorker()
                .stateProperty()
                .addListener((obs, oldState, newState) -> {

                    if (newState == Worker.State.RUNNING) {

                        statusLabel.setText(
                                "Loading..."
                        );
                    }

                    if (newState == Worker.State.SUCCEEDED) {

                        String title = engine.getTitle();

                        if (title == null || title.isBlank()) {
                            title = "CacheBrowse";
                        }

                        tab.setText(title);

                        String location =
                                engine.getLocation();

                        addressBar.setText(location);

                        if (location != null &&
                                !location.isBlank()) {

                            historyManager.addHistory(
                                    location
                            );
                        }

                        statusLabel.setText(
                                "Page loaded"
                        );

                        updateStats();
                    }

                    if (newState == Worker.State.FAILED) {

                        statusLabel.setText(
                                "Failed to load page"
                        );
                    }
                });

        navigateInEngine(engine, url);
    }

    /**
     * Navigate from URL bar.
     */
    private void navigate(String input) {

        if (input == null || input.isBlank()) {
            return;
        }

        String url = input.trim();

        /*
         * If user entered a normal URL.
         */
        if (!url.startsWith("http://") &&
                !url.startsWith("https://")) {

            /*
             * Looks like a domain.
             */
            if (url.contains(".") &&
                    !url.contains(" ")) {

                url = "https://" + url;

            } else {

                /*
                 * Otherwise use configured search engine.
                 */
                String encoded =
                        URLEncoder.encode(
                                url,
                                StandardCharsets.UTF_8
                        );

                url = settings
                        .getDefaultSearchEngine()
                        .replace("%s", encoded);
            }
        }

        Tab tab =
                tabPane.getSelectionModel()
                        .getSelectedItem();

        if (tab == null) {

            createTab(url);
            return;
        }

        WebView web =
                (WebView) tab.getContent();

        navigateInEngine(
                web.getEngine(),
                url
        );
    }

    /**
     * Load URL in WebEngine.
     */
    private void navigateInEngine(
            WebEngine engine,
            String url) {

        addressBar.setText(url);

        statusLabel.setText("Loading...");
        // Delegate to custom loader that checks the cache first.
        loadUrl(engine, url);
    }

    /**
     * Loads a URL either from the in‑memory cache or via an HTTP GET request
     * using {@link com.cachebrowse.network.HttpClient}.  The method runs all
     * blocking I/O on {@code networkExecutor} so the JavaFX Application
     * Thread never stalls.
     */
    private void loadUrl(WebEngine engine, String url) {
        // Request will be counted by CacheManager.get() on cache hit or
        // manually via the network fetch below when needed.

        // Check blockers first.
        if (adBlocker.isAdBlocked(url)) {
            Platform.runLater(() -> {
                engine.loadContent("<html><body>Ad Blocked</body></html>", "text/html");
                statusLabel.setText("URL blocked by ad blocker");
            });
            statistics.incAdsBlocked();
            updateStats();
            return;
        }
        if (trackerBlocker.isTrackerBlocked(url)) {
            Platform.runLater(() -> {
                engine.loadContent("<html><body>Tracker Blocked</body></html>", "text/html");
                statusLabel.setText("URL blocked by tracker blocker");
            });
            statistics.incTrackersBlocked();
            updateStats();
            return;
        }

        CacheEntry cached = null;
        if (settings.isCacheEnabled()) {
            cached = cacheManager.get(url);
        }
                if (cached != null) {
            // Cache hit: render immediately on the FX thread.
            Platform.runLater(() -> {
                try {
                    String bodyStr = new String(cached.getResponseBody(), StandardCharsets.UTF_8);
                    engine.loadContent(bodyStr, cached.getContentType());
                    statusLabel.setText("Loaded from cache");
                } catch (Exception e) {
                    statusLabel.setText("Failed to render cached content");
                }
                updateStats();
            });
        } else {
            // Cache miss – fetch from the network asynchronously.
            CompletableFuture.supplyAsync(() -> {
                long start = System.nanoTime();
                try {
                    var response = com.cachebrowse.network.HttpClient.get(url, 15000); // 15s timeout
                    long elapsedMs = (System.nanoTime() - start) / 1_000_000;
                    statistics.addResponseTime(elapsedMs);

                     // Cache the entry if appropriate and caching is enabled.
                    if (settings.isCacheEnabled() && response.statusCode() == 200 && !response.headers().firstValue("Set-Cookie").isPresent()) {
                        byte[] body = response.body();
                        String contentType = response.headers()
                                .firstValue("Content-Type")
                                .orElse("text/html");
                        CacheEntry entry = new CacheEntry(url, body, settings.getCacheTtl(), contentType);
                        cacheManager.put(url, entry);
                    }

                    return response;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, networkExecutor).thenAcceptAsync(response -> {
                Platform.runLater(() -> {
                    try {
                        String bodyStr = new String(response.body(), StandardCharsets.UTF_8);
                        String ct = response.headers()
                                .firstValue("Content-Type")
                                .orElse("text/html");
                        engine.loadContent(bodyStr, ct);
                        statusLabel.setText("Loaded from network");
                    } catch (Exception e) {
                        statusLabel.setText("Failed to render content: " + e.getMessage());
                    }
                    updateStats();
                });
            }, CompletableFuture.delayedExecutor(0, java.util.concurrent.TimeUnit.MILLISECONDS))
            .exceptionally(ex -> {
                Platform.runLater(() -> statusLabel.setText("Network error: " + ex.getMessage()));
                return null;
            });
        }
    }

    /**
     * Back button.
     */
    private void goBack() {

        WebView web = getCurrentWebView();

        if (web != null) {

            var history =
                    web.getEngine().getHistory();

            if (history.getCurrentIndex() > 0) {

                history.go(-1);
            }
        }
    }

    /**
     * Forward button.
     */
    private void goForward() {

        WebView web = getCurrentWebView();

        if (web != null) {

            var history =
                    web.getEngine().getHistory();

            if (history.getCurrentIndex()
                    < history.getEntries().size() - 1) {

                history.go(1);
            }
        }
    }

    /**
     * Refresh current page.
     */
    private void refresh() {

        WebView web = getCurrentWebView();

        if (web != null) {

            web.getEngine().reload();
        }
    }

    /**
     * Returns currently selected WebView.
     */
    private WebView getCurrentWebView() {

        Tab tab =
                tabPane.getSelectionModel()
                        .getSelectedItem();

        if (tab == null) {
            return null;
        }

        return (WebView) tab.getContent();
    }

    /**
     * Updates statistics displayed at bottom.
     */
    private void updateStats() {

        statsLabel.setText(
                "Requests: "
                        + statistics.getTotalRequests()
                        + " | Hits: "
                        + statistics.getCacheHits()
                        + " | Misses: "
                        + statistics.getCacheMisses()
                        + " | Hit Ratio: "
                        + String.format(
                                "%.1f",
                                statistics.getHitRatio()
                        )
                        + "%"
        );
    }
}