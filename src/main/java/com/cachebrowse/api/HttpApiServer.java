package com.cachebrowse.api;

import com.cachebrowse.adblock.AdBlocker;
import com.cachebrowse.adblock.TrackerBlocker;
import com.cachebrowse.auth.UserSession;
import com.cachebrowse.cache.CacheManager;
import com.cachebrowse.model.BrowserSettings;
import com.cachebrowse.model.HistoryEntry;
import com.cachebrowse.model.Bookmark;
import com.cachebrowse.service.AuthenticationService;
import com.cachebrowse.service.BookmarkService;
import com.cachebrowse.service.HistoryService;
import com.cachebrowse.service.NetworkService;
import com.cachebrowse.service.ProtectionService;
import com.cachebrowse.service.SettingsService;
import com.cachebrowse.statistics.CacheStatistics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Loopback-only REST API exposing Java application services to the React local web UI.
 *
 * Routes:
 *   GET/POST  /api/auth/user        — current user info
 *   POST      /api/auth/login       — sign in
 *   POST      /api/auth/register    — sign up
 *   POST      /api/auth/logout      — sign out
 *   GET       /api/history          — get history
 *   DELETE    /api/history          — clear all history
 *   DELETE    /api/history/{id}     — delete one entry
 *   GET       /api/bookmarks        — get bookmarks
 *   POST      /api/bookmarks        — add bookmark
 *   DELETE    /api/bookmarks        — clear all
 *   DELETE    /api/bookmarks/{id}   — remove one
 *   GET       /api/bookmarks/check  — isBookmarked?url=
 *   GET       /api/statistics       — get stats snapshot
 *   POST      /api/statistics/reset — reset stats
 *   GET/PUT   /api/settings         — get / update settings
 *   POST      /api/cache/clear      — clear cache
 *   GET/PUT   /api/protection/status / adblock / tracker
 *   POST      /api/search           — resolve direct URL or search query
 *   GET       /api/browser/status   — report that page navigation belongs to the host browser
 */
public class HttpApiServer {

    private static final Logger LOG = LoggerFactory.getLogger(HttpApiServer.class);
    private static final int DEFAULT_PORT = 8080;

    private final HttpServer server;
    private final AuthenticationService authService;
    private final HistoryService historyService;
    private final BookmarkService bookmarkService;
    private final SettingsService settingsService;
    private final CacheStatistics statistics;
    private final CacheManager cacheManager;
    private final AdBlocker adBlocker;
    private final TrackerBlocker trackerBlocker;
    private final BrowserSettings settings;
    private final UserSession userSession;
    private final ProtectionService protectionService;
    private volatile com.cachebrowse.database.DatabaseManager databaseManager;
    private volatile com.cachebrowse.auth.IdentityVerifier identityService =
            com.cachebrowse.auth.FirebaseIdentityService.fromEnvironment();
    private final NetworkService networkService = new NetworkService();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile boolean persistenceConfigured;
    private volatile boolean persistenceAvailable;
    private volatile String persistenceIssue;
    // Deprecated JavaFX integration hooks kept so the legacy standalone source still compiles.
    private Runnable onNavigateBack;
    private Runnable onNavigateForward;
    private Runnable onNavigateReload;
    private java.util.function.Consumer<String> onNavigateTo;

    public HttpApiServer(
            AuthenticationService authService,
            HistoryService historyService,
            BookmarkService bookmarkService,
            SettingsService settingsService,
            CacheStatistics statistics,
            CacheManager cacheManager,
            AdBlocker adBlocker,
            TrackerBlocker trackerBlocker,
            BrowserSettings settings) throws IOException {
        this(authService, historyService, bookmarkService, settingsService,
             statistics, cacheManager, adBlocker, trackerBlocker, settings, DEFAULT_PORT);
    }

    /** Constructor that accepts a custom port and binds only to loopback. */
    public HttpApiServer(
            AuthenticationService authService,
            HistoryService historyService,
            BookmarkService bookmarkService,
            SettingsService settingsService,
            CacheStatistics statistics,
            CacheManager cacheManager,
            AdBlocker adBlocker,
            TrackerBlocker trackerBlocker,
            BrowserSettings settings,
            int port) throws IOException {

        this.authService      = authService;
        this.historyService   = historyService;
        this.bookmarkService  = bookmarkService;
        this.settingsService  = settingsService;
        this.statistics       = statistics;
        this.cacheManager     = cacheManager;
        this.adBlocker        = adBlocker;
        this.trackerBlocker   = trackerBlocker;
        this.settings         = settings;
        this.userSession      = authService != null ? authService.getUserSession() : UserSession.getInstance();
        this.protectionService = new ProtectionService(adBlocker, trackerBlocker, statistics);
        this.persistenceConfigured = new com.cachebrowse.database.D1Config().isConfigured();
        this.persistenceIssue = persistenceConfigured ? "initialization_pending" : "missing_credentials";

        // The API is local desktop IPC over HTTP; never expose it to the LAN.
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 64);
        this.server.setExecutor(Executors.newFixedThreadPool(16, r -> {
            Thread t = new Thread(r, "ApiServer-Worker");
            t.setDaemon(true);
            return t;
        }));

        registerRoutes();
    }

    // ── Route registration ────────────────────────────────────────────────────

    private void registerRoutes() {
        server.createContext("/api/auth",        this::handleAuth);
        server.createContext("/api/history",     this::handleHistory);
        server.createContext("/api/bookmarks",   this::handleBookmarks);
        server.createContext("/api/statistics",  this::handleStatistics);
        server.createContext("/api/settings",    this::handleSettings);
        server.createContext("/api/cache",       this::handleCache);
        server.createContext("/api/protection",  this::handleProtection);
        server.createContext("/api/browser",     this::handleBrowser);
        server.createContext("/api/search",      this::handleSearch);
        server.createContext("/api/health",      this::handleHealth);
        server.createContext("/api/database",    this::handleDatabase);
        server.createContext("/api/admin",       this::handleAdmin);
        // CORS preflight catch-all
        server.createContext("/api",             this::handleCors);
    }

    public void start() {
        server.start();
        LOG.info("CacheBrowse API server started on http://127.0.0.1:{}",
                server.getAddress().getPort());
    }

    public void stop() {
        server.stop(1);
        LOG.info("CacheBrowse API server stopped.");
    }

    public int getPort() { return server.getAddress().getPort(); }
    public void setPersistenceAvailable(boolean available) {
        this.persistenceAvailable = persistenceConfigured && available;
        if (this.persistenceAvailable) persistenceIssue = "none";
        else if (persistenceConfigured && "initialization_pending".equals(persistenceIssue)) persistenceIssue = "initialization_failed";
    }
    public void setPersistenceIssue(String issue) {
        if (issue != null && issue.matches("[a-z0-9_]+")) persistenceIssue = issue;
    }
    public void setDatabaseManager(com.cachebrowse.database.DatabaseManager manager) {
        this.databaseManager = manager;
        this.persistenceConfigured = manager != null && manager.getConfig().isConfigured();
        if (!persistenceConfigured) persistenceIssue = "missing_credentials";
        else setPersistenceAvailable(manager.isInitialized());
    }
    public void setIdentityService(com.cachebrowse.auth.IdentityVerifier service) { this.identityService = service; }

    @Deprecated(forRemoval = false)
    public void setOnNavigateTo(java.util.function.Consumer<String> callback) { this.onNavigateTo = callback; }
    @Deprecated(forRemoval = false)
    public void setOnNavigateBack(Runnable callback) { this.onNavigateBack = callback; }
    @Deprecated(forRemoval = false)
    public void setOnNavigateForward(Runnable callback) { this.onNavigateForward = callback; }
    @Deprecated(forRemoval = false)
    public void setOnNavigateReload(Runnable callback) { this.onNavigateReload = callback; }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private void handleAuth(HttpExchange ex) throws IOException {
        addCors(ex);
        if ("OPTIONS".equals(ex.getRequestMethod())) { respond(ex, 204, ""); return; }

        String path   = ex.getRequestURI().getPath();   // /api/auth/...
        String method = ex.getRequestMethod();
        String subPath = path.substring("/api/auth".length());

        try {
            if ("/user".equals(subPath) && "GET".equals(method)) {
                var principal = authenticate(ex);
                if (principal == null) return;
                respond(ex, 200, json("\"loggedIn\":true", "\"uid\":\"" + esc(principal.uid()) + "\"",
                        "\"email\":\"" + esc(principal.email()) + "\"", "\"displayName\":\"" + esc(principal.displayName()) + "\"",
                        "\"provider\":\"" + esc(principal.provider()) + "\"", "\"admin\":" + identityService.isAdmin(principal)));
                return;
            }
            respond(ex, 404, "{\"error\":\"Not found\"}");
        } catch (Exception e) {
            LOG.warn("Auth handler failed ({})", e.getClass().getSimpleName());
            respond(ex, 500, "{\"error\":\"Authentication request failed\"}");
        }
    }

    private void handleHistory(HttpExchange ex) throws IOException {
        addCors(ex);
        if ("OPTIONS".equals(ex.getRequestMethod())) { respond(ex, 204, ""); return; }
        String path   = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        try {
            var principal = authenticate(ex);
            if (principal == null) return;
            ensureVerifiedUser(principal);
            String uid = principal.uid();
            if ("/api/history/visit".equals(path) && "POST".equals(method)) {
                JsonNode body = readJson(ex);
                String url = text(body, "url");
                String title = text(body, "title");
                if (url == null || !networkService.isValidUrl(url)) {
                    respond(ex, 400, "{\"error\":\"A valid HTTP or HTTPS URL is required\"}"); return;
                }
                historyService.addHistory(uid, url, title == null ? url : title);
                refreshPersistenceState();
                respond(ex, 202, "{\"accepted\":true}");
                return;
            }
            if ("/api/history".equals(path)) {
                if ("GET".equals(method)) {
                    String qs    = ex.getRequestURI().getQuery();
                    int limit    = 100;
                    if (qs != null) {
                        for (String parameter : qs.split("&")) {
                            if (!parameter.startsWith("limit=")) continue;
                            try { limit = Integer.parseInt(parameter.substring("limit=".length())); }
                            catch (NumberFormatException e) {
                                throw new IllegalArgumentException("limit must be an integer");
                            }
                        }
                    }
                    if (limit < 1 || limit > 500) {
                        throw new IllegalArgumentException("limit must be between 1 and 500");
                    }
                    List<HistoryEntry> entries = historyService.getHistory(uid, limit);
                    refreshPersistenceState();
                    respond(ex, 200, historyListJson(entries));
                    return;
                }
                if ("DELETE".equals(method)) {
                    historyService.clearHistory(uid);
                    refreshPersistenceState();
                    respond(ex, 200, "{\"success\":true}");
                    return;
                }
            }
            // /api/history/{id}
            if (path.startsWith("/api/history/") && "DELETE".equals(method)) {
                String id = path.substring("/api/history/".length());
                historyService.deleteHistory(uid, id);
                refreshPersistenceState();
                respond(ex, 200, "{\"success\":true}");
                return;
            }
            respond(ex, 404, "{\"error\":\"Not found\"}");
        } catch (IllegalArgumentException e) {
            respond(ex, 400, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
        } catch (Exception e) {
            LOG.warn("History handler error: {}", e.getMessage());
            respond(ex, 500, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    private void handleBookmarks(HttpExchange ex) throws IOException {
        addCors(ex);
        if ("OPTIONS".equals(ex.getRequestMethod())) { respond(ex, 204, ""); return; }
        String path   = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        try {
            var principal = authenticate(ex);
            if (principal == null) return;
            ensureVerifiedUser(principal);
            String uid = principal.uid();
            if ("/api/bookmarks".equals(path)) {
                if ("GET".equals(method)) {
                    List<Bookmark> bks = bookmarkService.getBookmarks(uid);
                    refreshPersistenceState();
                    respond(ex, 200, bookmarkListJson(bks));
                    return;
                }
                if ("POST".equals(method)) {
                    JsonNode body = readJson(ex);
                    String url   = text(body, "url");
                    String title = text(body, "title");
                    if (!networkService.isValidUrl(url)) {
                        throw new IllegalArgumentException("A valid HTTP or HTTPS bookmark URL is required");
                    }
                    if (title == null || title.isBlank()) title = url;
                    bookmarkService.addBookmark(uid, url, title);
                    refreshPersistenceState();
                    respond(ex, 202, "{\"accepted\":true,\"persistenceAvailable\":" + persistenceAvailable + "}");
                    return;
                }
                if ("PUT".equals(method)) {
                    JsonNode body = readJson(ex);
                    String id = text(body, "id");
                    String url = text(body, "url");
                    String title = text(body, "title");
                    if (id == null || id.isBlank()) throw new IllegalArgumentException("Bookmark id is required");
                    if (!networkService.isValidUrl(url)) throw new IllegalArgumentException("A valid HTTP or HTTPS bookmark URL is required");
                    if (title == null || title.isBlank()) title = url;
                    bookmarkService.updateBookmark(uid, id, url, title);
                    refreshPersistenceState();
                    respond(ex, 200, "{\"success\":true,\"persistenceAvailable\":" + persistenceAvailable + "}");
                    return;
                }
                if ("DELETE".equals(method)) {
                    bookmarkService.clearBookmarks(uid);
                    refreshPersistenceState();
                    respond(ex, 200, "{\"success\":true}");
                    return;
                }
            }
            if (path.equals("/api/bookmarks/check") && "GET".equals(method)) {
                String qs  = ex.getRequestURI().getQuery();
                String url = "";
                if (qs != null && qs.contains("url=")) {
                    url = java.net.URLDecoder.decode(qs.replaceAll(".*url=([^&]*).*", "$1"), StandardCharsets.UTF_8);
                }
                boolean bm = bookmarkService.isBookmarked(uid, url);
                respond(ex, 200, "{\"bookmarked\":" + bm + "}");
                return;
            }
            if (path.startsWith("/api/bookmarks/") && "DELETE".equals(method)) {
                String id = path.substring("/api/bookmarks/".length());
                bookmarkService.removeBookmark(uid, id);
                refreshPersistenceState();
                respond(ex, 200, "{\"success\":true}");
                return;
            }
            respond(ex, 404, "{\"error\":\"Not found\"}");
        } catch (IllegalArgumentException e) {
            respond(ex, 400, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
        } catch (Exception e) {
            LOG.warn("Bookmarks handler error: {}", e.getMessage());
            respond(ex, 500, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    private void handleStatistics(HttpExchange ex) throws IOException {
        addCors(ex);
        if ("OPTIONS".equals(ex.getRequestMethod())) { respond(ex, 204, ""); return; }
        String path   = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();

        try {
            if ("/api/statistics".equals(path) && "GET".equals(method)) {
                respond(ex, 200, statsJson());
                return;
            }
            if ("/api/statistics/reset".equals(path) && "POST".equals(method)) {
                statistics.reset();
                respond(ex, 200, "{\"success\":true}");
                return;
            }
            respond(ex, 404, "{\"error\":\"Not found\"}");
        } catch (Exception e) {
            respond(ex, 500, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    private void handleSettings(HttpExchange ex) throws IOException {
        addCors(ex);
        if ("OPTIONS".equals(ex.getRequestMethod())) { respond(ex, 204, ""); return; }
        String method = ex.getRequestMethod();

        try {
            var principal = authenticate(ex);
            if (principal == null) return;
            ensureVerifiedUser(principal);
            if ("GET".equals(method)) {
                BrowserSettings userSettings = new BrowserSettings(principal.uid());
                if (databaseManager != null && databaseManager.getSettingsRepository() != null) {
                    userSettings = databaseManager.getSettingsRepository().getSettings(principal.uid()).orElse(userSettings);
                }
                respond(ex, 200, settingsJson(userSettings));
                return;
            }
            if ("PUT".equals(method)) {
                JsonNode body = readJson(ex);
                String hp = text(body, "homePage");
                String se = text(body, "defaultSearchEngine");
                Boolean ce = optionalBoolean(body, "cacheEnabled");
                Boolean ab = optionalBoolean(body, "adBlockingEnabled");
                Boolean tb = optionalBoolean(body, "trackerBlockingEnabled");
                Integer cap = optionalInteger(body, "cacheCapacity");
                Long ttl = optionalLong(body, "cacheTtl");

                if (se != null && !networkService.isValidSearchEngineTemplate(se)) {
                    throw new IllegalArgumentException("defaultSearchEngine must be an HTTP(S) URL with one %s placeholder");
                }
                if (hp != null && !hp.equals("cachebrowse://home")
                        && !networkService.isValidUrl(networkService.resolveUrl(hp,
                                se == null ? settings.getDefaultSearchEngine() : se))) {
                    throw new IllegalArgumentException("homePage must be cachebrowse://home or a valid HTTP(S) URL");
                }
                if (cap != null && (cap < 1 || cap > 1000)) {
                    throw new IllegalArgumentException("cacheCapacity must be between 1 and 1000");
                }
                if (ttl != null && (ttl < 1_000 || ttl > 2_592_000_000L)) {
                    throw new IllegalArgumentException("cacheTtl must be between 1 second and 30 days");
                }

                BrowserSettings scoped = databaseManager != null && databaseManager.getSettingsRepository() != null
                        ? databaseManager.getSettingsRepository().getSettings(principal.uid()).orElse(new BrowserSettings(principal.uid()))
                        : new BrowserSettings(principal.uid());
                if (hp != null) scoped.setHomePage(hp);
                if (se != null) scoped.setDefaultSearchEngine(se);
                if (ce != null) scoped.setCacheEnabled(ce);
                if (ab != null) scoped.setAdBlockingEnabled(ab);
                if (tb != null) scoped.setTrackerBlockingEnabled(tb);
                if (cap != null) scoped.setCacheCapacity(cap);
                if (ttl != null) scoped.setCacheTtl(ttl);

                // Browser-engine toggles are runtime-wide for this local installation.
                if (ab != null) { settings.setAdBlockingEnabled(ab); adBlocker.setEnabled(ab); }
                if (tb != null) { settings.setTrackerBlockingEnabled(tb); trackerBlocker.setEnabled(tb); }
                if (cacheManager != null && cap != null) cacheManager.setCapacity(cap);

                if (settingsService != null) settingsService.saveSettingsForUser(principal.uid(), scoped);
                refreshPersistenceState();
                respond(ex, 200, "{\"success\":true,\"persistenceAvailable\":" + persistenceAvailable + "}");
                return;
            }
            respond(ex, 404, "{\"error\":\"Not found\"}");
        } catch (IllegalArgumentException e) {
            respond(ex, 400, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
        } catch (Exception e) {
            respond(ex, 500, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    private void handleCache(HttpExchange ex) throws IOException {
        addCors(ex);
        if ("OPTIONS".equals(ex.getRequestMethod())) { respond(ex, 204, ""); return; }
        try {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();
            if ("GET".equals(method) && (path.endsWith("/status") || path.endsWith("/statistics"))) {
                respond(ex, 200, cacheJson());
            } else if ("POST".equals(method) && (path.endsWith("/clear") || path.endsWith("/cleanup"))) {
                if (path.endsWith("/clear") && cacheManager != null) cacheManager.clear();
                int removed = path.endsWith("/cleanup") && cacheManager != null
                        ? cacheManager.cleanupExpired() : 0;
                respond(ex, 200, "{\"success\":true,\"removedEntries\":" + removed + "}");
            } else {
                respond(ex, 405, "{\"error\":\"Method not allowed\"}");
            }
        } catch (Exception e) {
            respond(ex, 500, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    private void handleProtection(HttpExchange ex) throws IOException {
        addCors(ex);
        if ("OPTIONS".equals(ex.getRequestMethod())) { respond(ex, 204, ""); return; }
        String path   = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();

        try {
            if (path.endsWith("/allowlist") && "GET".equals(method)) {
                respond(ex, 200, objectMapper.writeValueAsString(protectionService.getAllowlist()));
                return;
            }
            if (path.endsWith("/status") && "GET".equals(method)) {
                respond(ex, 200, protectionJson());
                return;
            }
            if (path.endsWith("/statistics") && "GET".equals(method)) {
                respond(ex, 200, "{\"adsBlocked\":" + statistics.getAdsBlocked()
                        + ",\"trackersBlocked\":" + statistics.getTrackersBlocked()
                        + ",\"totalBlocked\":" + statistics.getBlockedRequests() + "}");
                return;
            }
            if (path.endsWith("/rules") && "GET".equals(method)) {
                respond(ex, 200, protectionRulesJson());
                return;
            }
            if (path.endsWith("/blocked") && "POST".equals(method)) {
                protectionService.recordBlockedRequest(text(readJson(ex), "category"));
                respond(ex, 202, "{\"recorded\":true}");
                return;
            }
            if (path.endsWith("/allowlist") && "POST".equals(method)) {
                if (authenticate(ex) == null) return;
                protectionService.addAllowlistRule(text(readJson(ex), "domain"));
                respond(ex, 201, objectMapper.writeValueAsString(protectionService.getAllowlist()));
                return;
            }
            if (path.endsWith("/allowlist") && "DELETE".equals(method)) {
                if (authenticate(ex) == null) return;
                String domain = text(readJson(ex), "domain");
                if (domain == null || domain.isBlank()) throw new IllegalArgumentException("domain is required");
                protectionService.removeAllowlistRule(domain);
                respond(ex, 200, objectMapper.writeValueAsString(protectionService.getAllowlist()));
                return;
            }
            if (path.endsWith("/blocklist") && "POST".equals(method)) {
                var principal = authenticate(ex);
                if (principal == null) return;
                if (!identityService.isAdmin(principal)) { respond(ex, 403, "{\"error\":\"Administrator access required\"}"); return; }
                JsonNode body = readJson(ex);
                protectionService.addBlocklistRule(text(body, "category"), text(body, "rule"));
                respond(ex, 201, protectionRulesJson());
                return;
            }
            if (path.endsWith("/blocklist") && "DELETE".equals(method)) {
                var principal = authenticate(ex);
                if (principal == null) return;
                if (!identityService.isAdmin(principal)) { respond(ex, 403, "{\"error\":\"Administrator access required\"}"); return; }
                JsonNode body = readJson(ex);
                String category = text(body, "category");
                String rule = text(body, "rule");
                if (rule == null || rule.isBlank()) throw new IllegalArgumentException("rule is required");
                protectionService.removeBlocklistRule(category, rule);
                respond(ex, 200, protectionRulesJson());
                return;
            }
            if (path.endsWith("/adblock") && "PUT".equals(method)) {
                var principal = authenticate(ex);
                if (principal == null) return;
                Boolean enabled = optionalBoolean(readJson(ex), "enabled");
                if (enabled == null) throw new IllegalArgumentException("enabled must be a JSON boolean");
                boolean val = enabled;
                adBlocker.setEnabled(val);
                settings.setAdBlockingEnabled(val);
                if (settingsService != null) settingsService.saveSettingsForUser(principal.uid(), settings);
                respond(ex, 200, "{\"success\":true,\"adBlock\":" + val + "}");
                return;
            }
            if (path.endsWith("/tracker") && "PUT".equals(method)) {
                var principal = authenticate(ex);
                if (principal == null) return;
                Boolean enabled = optionalBoolean(readJson(ex), "enabled");
                if (enabled == null) throw new IllegalArgumentException("enabled must be a JSON boolean");
                boolean val = enabled;
                trackerBlocker.setEnabled(val);
                settings.setTrackerBlockingEnabled(val);
                if (settingsService != null) settingsService.saveSettingsForUser(principal.uid(), settings);
                respond(ex, 200, "{\"success\":true,\"trackerBlock\":" + val + "}");
                return;
            }
            respond(ex, 404, "{\"error\":\"Not found\"}");
        } catch (IllegalArgumentException e) {
            respond(ex, 400, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
        } catch (Exception e) {
            respond(ex, 500, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    private void handleBrowser(HttpExchange ex) throws IOException {
        addCors(ex);
        if ("OPTIONS".equals(ex.getRequestMethod())) { respond(ex, 204, ""); return; }
        String path = ex.getRequestURI().getPath();
        if ("/api/browser/status".equals(path) && "GET".equals(ex.getRequestMethod())) {
            respond(ex, 200, "{\"ready\":true,\"scope\":\"java-backend\",\"engine\":\"host-browser\",\"navigationOwner\":\"user-browser\",\"navigationAvailable\":false}");
            return;
        }
        respond(ex, 410, "{\"error\":\"The Java backend does not control navigation in the user's browser.\"}");
    }

    private void handleAdmin(HttpExchange ex) throws IOException {
        addCors(ex);
        if ("OPTIONS".equals(ex.getRequestMethod())) { respond(ex, 204, ""); return; }
        var principal = authenticate(ex);
        if (principal == null) return;
        if (!identityService.isAdmin(principal)) {
            respond(ex, 403, "{\"error\":\"Administrator access required\"}"); return;
        }
        try {
            String path = ex.getRequestURI().getPath();
            if ("GET".equals(ex.getRequestMethod()) && "/api/admin/users".equals(path)) {
                Map<String, String> query = queryParameters(ex.getRequestURI().getRawQuery());
                int limit = 50;
                if (query.containsKey("limit")) limit = Integer.parseInt(query.get("limit"));
                if (limit < 1 || limit > 1000) throw new IllegalArgumentException("limit must be between 1 and 1000");
                var page = identityService.listUsers(limit, query.get("pageToken"));
                respond(ex, 200, objectMapper.writeValueAsString(Map.of(
                        "users", page.users(), "nextPageToken", page.nextPageToken() == null ? "" : page.nextPageToken())));
                return;
            }
            if ("GET".equals(ex.getRequestMethod()) && "/api/admin/summary".equals(path)) {
                long count = 0;
                String token = null;
                do {
                    var page = identityService.listUsers(1000, token);
                    count += page.users().size();
                    token = page.nextPageToken();
                } while (token != null && !token.isBlank());
                respond(ex, 200, "{\"totalUsers\":" + count + "}");
                return;
            }
            if ("GET".equals(ex.getRequestMethod()) && path.startsWith("/api/admin/users/")) {
                String uid = path.substring("/api/admin/users/".length());
                if (uid.isBlank() || uid.contains("/")) throw new IllegalArgumentException("Invalid user id");
                var user = identityService.getUser(java.net.URLDecoder.decode(uid, StandardCharsets.UTF_8));
                if (user.isEmpty()) { respond(ex, 404, "{\"error\":\"User not found\"}"); return; }
                respond(ex, 200, objectMapper.writeValueAsString(user.get()));
                return;
            }
            respond(ex, 404, "{\"error\":\"Not found\"}");
        } catch (IllegalArgumentException error) {
            respond(ex, 400, "{\"error\":\"Invalid admin request\"}");
        } catch (Exception error) {
            LOG.warn("Admin API failed ({})", error.getClass().getSimpleName());
            respond(ex, 502, "{\"error\":\"Firebase user directory is unavailable\"}");
        }
    }

    private com.cachebrowse.auth.FirebaseIdentityService.Principal authenticate(HttpExchange ex) throws IOException {
        com.cachebrowse.auth.IdentityVerifier verifier = identityService;
        String header = ex.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ") || header.length() <= 7) {
            respond(ex, 401, "{\"error\":\"Authentication required\"}"); return null;
        }
        if (verifier == null) {
            respond(ex, 503, "{\"error\":\"Firebase Admin verification is not configured\"}"); return null;
        }
        try { return verifier.verify(header.substring(7)); }
        catch (Exception error) {
            LOG.info("Firebase token rejected ({})", error.getClass().getSimpleName());
            respond(ex, 401, "{\"error\":\"Invalid or expired authentication token\"}"); return null;
        }
    }

    private void ensureVerifiedUser(com.cachebrowse.auth.FirebaseIdentityService.Principal principal) {
        var manager = databaseManager;
        if (manager == null || manager.getUserRepository() == null) return;
        String email = principal.email() == null ? "" : principal.email();
        manager.getUserRepository().createUser(new com.cachebrowse.model.User(principal.uid(), email));
    }

    private Map<String, String> queryParameters(String query) {
        java.util.Map<String, String> values = new java.util.HashMap<>();
        if (query == null || query.isBlank()) return values;
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            values.put(java.net.URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    parts.length == 1 ? "" : java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        return values;
    }

    private void handleSearch(HttpExchange ex) throws IOException {
        addCors(ex);
        if ("OPTIONS".equals(ex.getRequestMethod())) { respond(ex, 204, ""); return; }
        if (!"POST".equals(ex.getRequestMethod())) {
            respond(ex, 405, "{\"error\":\"Method not allowed\"}"); return;
        }
        try {
            String query = text(readJson(ex), "query");
            if (query == null || query.isBlank()) {
                respond(ex, 400, "{\"error\":\"query must not be empty\"}"); return;
            }
            String target = networkService.resolveUrl(query, settings.getDefaultSearchEngine());
            if (target == null || target.isBlank() || !networkService.isValidUrl(target)) {
                respond(ex, 400, "{\"error\":\"Input or configured search engine is invalid\"}"); return;
            }
            String kind = query.trim().matches("(?i)^https?://.*") ? "url"
                    : target.contains(java.net.URLEncoder.encode(query.trim(), StandardCharsets.UTF_8))
                    ? "search" : "domain";
            respond(ex, 200, "{\"query\":\"" + esc(query.trim()) + "\",\"url\":\"" + esc(target)
                    + "\",\"kind\":\"" + kind + "\"}");
        } catch (IllegalArgumentException exn) {
            respond(ex, 400, "{\"error\":\"Malformed search request\"}");
        } catch (Exception exn) {
            LOG.warn("Search resolution failed: {}", exn.getMessage());
            respond(ex, 502, "{\"error\":\"Unable to resolve this search right now\"}");
        }
    }

    private void handleHealth(HttpExchange ex) throws IOException {
        addCors(ex);
        if (!"GET".equals(ex.getRequestMethod())) {
            respond(ex, 405, "{\"error\":\"Method not allowed\"}"); return;
        }
        refreshPersistenceState();
        respond(ex, 200, "{\"ready\":true,\"persistenceConfigured\":" + persistenceConfigured
                + ",\"persistenceAvailable\":" + persistenceAvailable
                + ",\"authenticationConfigured\":" + (identityService != null)
                + ",\"adminConfigured\":" + (identityService != null && !com.cachebrowse.config.EnvConfig.get("CACHEBROWSE_ADMIN_UID").isBlank())
                + ",\"persistenceIssue\":\"" + esc(persistenceIssue) + "\""
                + ",\"storageMode\":\"" + (persistenceAvailable ? "cloudflare-d1" : "process-memory") + "\"}");
    }

    private void refreshPersistenceState() {
        com.cachebrowse.database.DatabaseManager manager = databaseManager;
        if (manager == null) return;
        boolean available = manager.isInitialized();
        setPersistenceAvailable(available);
        if (!available) setPersistenceIssue(manager.getDiagnostic());
    }

    private void handleDatabase(HttpExchange ex) throws IOException {
        addCors(ex);
        if ("OPTIONS".equals(ex.getRequestMethod())) { respond(ex, 204, ""); return; }
        if (!"POST".equals(ex.getRequestMethod()) || !"/api/database/check".equals(ex.getRequestURI().getPath())) {
            respond(ex, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        com.cachebrowse.database.DatabaseManager manager = databaseManager;
        if (manager == null || !manager.getConfig().isConfigured()) {
            persistenceAvailable = false;
            persistenceIssue = "missing_credentials";
            respond(ex, 503, "{\"connected\":false,\"schemaInitialized\":false,\"issue\":\"missing_credentials\"}");
            return;
        }
        boolean ready = manager.retryInitialization();
        setPersistenceAvailable(ready);
        if (!ready) setPersistenceIssue(manager.getDiagnostic());
        String body = "{\"connected\":" + ready + ",\"schemaInitialized\":" + ready
                + ",\"issue\":\"" + esc(manager.getDiagnostic()) + "\"}";
        respond(ex, ready ? 200 : 503, body);
    }

    private void handleCors(HttpExchange ex) throws IOException {
        addCors(ex);
        respond(ex, ex.getRequestMethod().equals("OPTIONS") ? 204 : 404, "");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void addCors(HttpExchange ex) {
        String origin = ex.getRequestHeaders().getFirst("Origin");
        String frontendPort = System.getenv().getOrDefault("CACHEBROWSE_FRONTEND_PORT", "5173");
        String allowed = "http://127.0.0.1:" + frontendPort;
        if (origin != null) {
            try {
                java.net.URI parsed = java.net.URI.create(origin);
                int port = parsed.getPort() == -1 ? 80 : parsed.getPort();
                if ("http".equalsIgnoreCase(parsed.getScheme())
                        && ("localhost".equalsIgnoreCase(parsed.getHost()) || "127.0.0.1".equals(parsed.getHost()))
                        && port == Integer.parseInt(frontendPort)) {
                    allowed = origin;
                }
            } catch (Exception ignored) { }
        }

        ex.getResponseHeaders().set("Access-Control-Allow-Origin",  allowed);
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
        ex.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");
        ex.getResponseHeaders().set("Access-Control-Max-Age", "86400");
    }

    private void respond(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        }
        ex.close();
    }

    private String readBody(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            byte[] body = is.readNBytes(64 * 1024 + 1);
            if (body.length > 64 * 1024) throw new IllegalArgumentException("Request body too large");
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    /** Extracts a scalar field from a JSON object, honoring escapes and primitive types. */
    private String jsonField(String json, String key) {
        if (json == null || key == null) return null;
        try {
            JsonNode node = objectMapper.readTree(json).get(key);
            return node == null || node.isNull() || !node.isValueNode() ? null : node.asText();
        } catch (Exception ignored) { return null; }
    }

    private JsonNode readJson(HttpExchange ex) throws IOException {
        byte[] bytes = ex.getRequestBody().readNBytes(64 * 1024 + 1);
        if (bytes.length > 64 * 1024) throw new IllegalArgumentException("Request body too large");
        JsonNode node;
        try { node = objectMapper.readTree(bytes); }
        catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalArgumentException("Malformed JSON", error);
        }
        if (node == null || !node.isObject()) throw new IllegalArgumentException("Expected a JSON object");
        return node;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() || !value.isValueNode() ? null : value.asText();
    }

    private Boolean optionalBoolean(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isBoolean()) throw new IllegalArgumentException(field + " must be a JSON boolean");
        return value.booleanValue();
    }

    private Integer optionalInteger(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isIntegralNumber() || !value.canConvertToInt()) throw new IllegalArgumentException(field + " must be an integer");
        return value.intValue();
    }

    private Long optionalLong(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isIntegralNumber() || !value.canConvertToLong()) throw new IllegalArgumentException(field + " must be an integer");
        return value.longValue();
    }

    private String json(String... fields) {
        return "{" + String.join(",", fields) + "}";
    }

    private String esc(String s) {
        if (s == null) return "";
        StringBuilder escaped = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) escaped.append(String.format("\\u%04x", (int) c));
                    else escaped.append(c);
                }
            }
        }
        return escaped.toString();
    }

    private String statsJson() {
        return json(
            "\"totalRequests\":"    + statistics.getTotalRequests(),
            "\"allowedRequests\":"  + statistics.getAllowedRequests(),
            "\"blockedRequests\":"  + statistics.getBlockedRequests(),
            "\"cacheHits\":"        + statistics.getCacheHits(),
            "\"cacheMisses\":"      + statistics.getCacheMisses(),
            "\"hitRatio\":"         + statistics.getHitRatio(),
            "\"networkRequests\":"  + statistics.getNetworkRequests(),
            "\"adsBlocked\":"       + statistics.getAdsBlocked(),
            "\"trackersBlocked\":"  + statistics.getTrackersBlocked(),
            "\"avgResponseTime\":"  + statistics.getAverageResponseTime(),
            "\"evictions\":"        + statistics.getEvictions(),
            "\"expiredEntries\":"   + statistics.getExpiredEntries(),
            "\"bytesFromCache\":"   + statistics.getBytesFromCache(),
            "\"bytesFromNetwork\":" + statistics.getBytesFromNetwork(),
            "\"cacheEntries\":"     + (cacheManager != null ? cacheManager.size() : 0),
            "\"cacheCapacity\":"    + (cacheManager != null ? cacheManager.capacity() : 0)
        );
    }

    private String cacheJson() {
        return json("\"source\":\"java-application-cache\"",
                "\"scope\":\"Java-managed HTTP responses; does not represent Chromium page traffic\"",
                "\"entryCount\":" + (cacheManager == null ? 0 : cacheManager.size()),
                "\"capacity\":" + (cacheManager == null ? 0 : cacheManager.capacity()),
                "\"cacheHits\":" + statistics.getCacheHits(),
                "\"cacheMisses\":" + statistics.getCacheMisses(),
                "\"evictions\":" + statistics.getEvictions(),
                "\"expiredEntries\":" + statistics.getExpiredEntries(),
                "\"browserEngineCache\":\"Managed by the host browser; not accessible to the Java application\"");
    }

    private String protectionJson() {
        return json("\"adBlock\":" + adBlocker.isEnabled(),
                "\"trackerBlock\":" + trackerBlocker.isEnabled(),
                "\"allowlistCount\":" + protectionService.getAllowlist().size(),
                "\"adsBlocked\":" + statistics.getAdsBlocked(),
                "\"trackersBlocked\":" + statistics.getTrackersBlocked());
    }

    private String protectionRulesJson() {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "adRules", protectionService.getAdRules(),
                    "trackerRules", protectionService.getTrackerRules(),
                    "allowlist", protectionService.getAllowlist()));
        } catch (Exception ex) {
            return "{\"error\":\"Could not serialize protection rules\"}";
        }
    }

    private String settingsJson() {
        return settingsJson(settings);
    }

    private String settingsJson(BrowserSettings value) {
        return json(
            "\"homePage\":\""                  + esc(value.getHomePage()) + "\"",
            "\"defaultSearchEngine\":\""       + esc(value.getDefaultSearchEngine()) + "\"",
            "\"cacheEnabled\":"                + value.isCacheEnabled(),
            "\"cacheCapacity\":"               + value.getCacheCapacity(),
            "\"cacheTtl\":"                    + value.getCacheTtl(),
            "\"adBlockingEnabled\":"           + value.isAdBlockingEnabled(),
            "\"trackerBlockingEnabled\":"      + value.isTrackerBlockingEnabled()
        );
    }

    private String historyListJson(List<HistoryEntry> entries) {
        if (entries == null || entries.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < entries.size(); i++) {
            HistoryEntry e = entries.get(i);
            if (i > 0) sb.append(",");
            sb.append("{")
              .append("\"id\":\"").append(esc(e.getId())).append("\",")
              .append("\"url\":\"").append(esc(e.getUrl())).append("\",")
              .append("\"title\":\"").append(esc(e.getTitle())).append("\",")
              .append("\"visitedAt\":\"").append(esc(String.valueOf(e.getVisitedAt()))).append("\"")
              .append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String bookmarkListJson(List<Bookmark> bks) {
        if (bks == null || bks.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < bks.size(); i++) {
            Bookmark b = bks.get(i);
            if (i > 0) sb.append(",");
            sb.append("{")
              .append("\"id\":\"").append(esc(b.getId())).append("\",")
              .append("\"url\":\"").append(esc(b.getUrl())).append("\",")
              .append("\"title\":\"").append(esc(b.getTitle())).append("\"")
              .append("}");
        }
        sb.append("]");
        return sb.toString();
    }
}
