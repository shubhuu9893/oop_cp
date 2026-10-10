package com.cachebrowse.service;

import com.cachebrowse.adblock.AdBlocker;
import com.cachebrowse.adblock.TrackerBlocker;
import com.cachebrowse.auth.UserSession;
import com.cachebrowse.cache.CacheEntry;
import com.cachebrowse.model.BrowserResponse;
import com.cachebrowse.model.BrowserSettings;
import com.cachebrowse.network.HttpResponse;
import com.cachebrowse.statistics.CacheStatistics;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main Browser Service orchestrating the complete browsing, caching, blocking, and persistence pipeline.
 */
public class BrowserService {
    private static final Logger LOG = LoggerFactory.getLogger(BrowserService.class);

    private final NetworkService networkService;
    private final CacheService cacheService;
    private final AdBlocker adBlocker;
    private final TrackerBlocker trackerBlocker;
    private final HistoryService historyService;
    private final BookmarkService bookmarkService;
    private final SettingsService settingsService;
    private final StatisticsService statisticsService;
    private final AuthenticationService authenticationService;
    private final UserSession userSession;

    public BrowserService(NetworkService networkService,
                          CacheService cacheService,
                          AdBlocker adBlocker,
                          TrackerBlocker trackerBlocker,
                          HistoryService historyService,
                          BookmarkService bookmarkService,
                          SettingsService settingsService,
                          StatisticsService statisticsService,
                          AuthenticationService authenticationService) {
        this.networkService = networkService != null ? networkService : new NetworkService();
        this.cacheService = cacheService;
        this.adBlocker = adBlocker != null ? adBlocker : new AdBlocker();
        this.trackerBlocker = trackerBlocker != null ? trackerBlocker : new TrackerBlocker();
        this.historyService = historyService;
        this.bookmarkService = bookmarkService;
        this.settingsService = settingsService;
        this.statisticsService = statisticsService;
        this.authenticationService = authenticationService;
        this.userSession = authenticationService != null ? authenticationService.getUserSession() : UserSession.getInstance();
    }


     /**
      * Generates the internal homepage HTML.  This is a minimal page containing
      * branding, a central search/address bar and placeholders for statistics.
      * It intentionally avoids any external requests so that opening the home
      * page does not trigger network traffic.  The content is kept simple to
      * reduce maintenance overhead; if more dynamic features are required they
      */
     /**
     * Generates a modern, glass‑morphic home page for the CacheBrowser.
     * <p>
     * The HTML is intentionally minimal – it only contains a large central
     * search bar that forwards user input to the Java side via a custom
     * {@code cachebrowse://navigate?url=...} protocol.  This keeps all
     * navigation logic in {@link com.cachebrowse.ui.BrowserController},
     * preserving ad‑block, tracker‑block and history integration.
     */
    private String generateHomepageContent() {
        return "<!DOCTYPE html>" +
                "<html><head><meta charset='UTF-8'><title>CacheBrowse Home</title>" +
                "<style>body{margin:0;font-family:sans-serif;background:#121212;color:#e0e0e0;display:flex;flex-direction:column;height:100vh;}" +
                ".header{padding:20px;text-align:center;}" +
                "h1{font-size:2rem;margin:0;}" +
                " .search-container{flex-grow:1;display:flex;align-items:center;justify-content:center;}" +
                " .search-bar{width:80%;max-width:600px;display:flex;align-items:center;background:rgba(255,255,255,0.08);border-radius:32px;border:1px solid rgba(255,255,255,0.12);backdrop-filter:blur(10px);padding:8px 12px;transition:border-color 0.3s ease,box-shadow 0.3s ease;}" +
                " .search-bar:hover{border-color:rgba(255,255,255,0.24);} .search-bar:focus-within{box-shadow:0 0 4px #5e9cec;}" +
                " .input{flex-grow:1;border:none;background:none;color:#fff;font-size:16px;padding:8px;} .input:focus{outline:none;}" +
                " .icon{cursor:pointer;margin-right:8px;display:flex;align-items:center;justify-content:center;}" +
                " @media (max-width:600px){ .search-bar{width:90%;}}" +
                "</style></head>" +
                "<body>" +
                " <div class='header'><h1>CacheBrowse</h1></div>" +
                " <div class='search-container'>" +
                "  <div class='search-bar' id='searchBar'>" +
                "   <div class='icon' id='searchIcon'>🔍</div>" +
                "   <input type='text' id='searchInput' class='input' placeholder='Search the web or enter a URL' autocomplete='off'/>" +
                "  </div>" +
                " </div>" +
                "<script>document.getElementById('searchBar').addEventListener('keydown', function(e){if(e.key==='Enter'){submit();}});document.getElementById('searchIcon').addEventListener('click', function(){submit();});function submit(){var val=document.getElementById('searchInput').value.trim();if(val.length===0)return;window.location.href='cachebrowse://navigate?url='+encodeURIComponent(val);}</script>" +
                "<div class='footer'><p><a href='https://github.com/yourrepo'>GitHub Repo</a></p></div>" +
                "</body></html>";
    }
    /**
     * Executes a navigation / search request asynchronously following the target architecture pipeline.
     */
    public CompletableFuture<BrowserResponse> requestAsync(String rawInput) {
        BrowserSettings settings = settingsService != null ? settingsService.getSettings() : new BrowserSettings();
        CacheStatistics stats = statisticsService != null ? statisticsService.getStatistics() : null;

        // Special handling for the internal homepage.
        if (rawInput.equals(settings.getHomePage()) && rawInput.startsWith("cachebrowse://")) {
            byte[] body = generateHomepageContent().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return CompletableFuture.completedFuture(
                    new BrowserResponse.Builder(rawInput)
                            .statusCode(200)
                            .body(body)
                            .contentType("text/html; charset=UTF-8")
                            .fromCache(false)
                            .responseTimeMs(0)
                            .build());
        }

        // 1. Resolve and normalize URL or search query
        String resolvedUrl = networkService.resolveUrl(rawInput, settings.getDefaultSearchEngine());
        if (resolvedUrl == null || resolvedUrl.isBlank()) {
            return CompletableFuture.completedFuture(
                    BrowserResponse.error(rawInput, "Invalid or empty URL/search query")
            );
        }

        // 2. Ad Blocker Check
        if (settings.isAdBlockingEnabled() && adBlocker.isAdBlocked(resolvedUrl)) {
            if (stats != null) {
                stats.incAdsBlocked();
            }
            return CompletableFuture.completedFuture(
                    BrowserResponse.blocked(resolvedUrl, "Ad Blocker")
            );
        }

        // 3. Tracker Blocker Check
        if (settings.isTrackerBlockingEnabled() && trackerBlocker.isTrackerBlocked(resolvedUrl)) {
            if (stats != null) {
                stats.incTrackersBlocked();
            }
            return CompletableFuture.completedFuture(
                    BrowserResponse.blocked(resolvedUrl, "Tracker Blocker")
            );
        }

        // 4. Cache Lookup
        CacheEntry cached = null;
        if (settings.isCacheEnabled() && cacheService != null) {
            cached = cacheService.get(resolvedUrl);
        }

        if (cached != null) {
            // CACHE HIT
            BrowserResponse hitResponse = new BrowserResponse.Builder(resolvedUrl)
                    .statusCode(cached.getStatusCode())
                    .body(cached.getResponseBody())
                    .contentType(cached.getContentType())
                    .fromCache(true)
                    .responseTimeMs(1)
                    .build();

            // Record history
            recordHistory(resolvedUrl, "Cached Page");
            return CompletableFuture.completedFuture(hitResponse);
        }

        // 5. CACHE MISS -> Fetch from Internet
        if (stats != null) {
            stats.incNetworkRequests();
        }

        return networkService.fetchAsync(resolvedUrl, 15000)
                .thenApply(httpRes -> {
                    if (stats != null) {
                        stats.addResponseTime(httpRes.getResponseTimeMs());
                        stats.addBytesFromNetwork(httpRes.getBody().length);
                    }

                    // Store in cache if enabled and successful
                    if (settings.isCacheEnabled() && cacheService != null && httpRes.isSuccess()) {
                        cacheService.put(
                                resolvedUrl,
                                httpRes.getBody(),
                                settings.getCacheTtl(),
                                httpRes.getContentType(),
                                httpRes.getStatusCode()
                        );
                    }

                    // Record history
                    recordHistory(resolvedUrl, resolvedUrl);

                    return new BrowserResponse.Builder(resolvedUrl)
                            .statusCode(httpRes.getStatusCode())
                            .body(httpRes.getBody())
                            .contentType(httpRes.getContentType())
                            .headers(httpRes.getHeaders())
                            .fromCache(false)
                            .responseTimeMs(httpRes.getResponseTimeMs())
                            .build();
                })
                .exceptionally(ex -> {
                    LOG.error("Failed to load URL {}: {}", resolvedUrl, ex.getMessage());
                    return BrowserResponse.error(resolvedUrl, ex.getMessage());
                });
    }

    private void recordHistory(String url, String title) {
        if (historyService != null) {
            String userId = userSession != null ? userSession.getUserId() : com.cachebrowse.auth.UserSession.ANONYMOUS_USER_ID;
            historyService.addHistory(userId, url, title);
        }
    }

    public NetworkService getNetworkService() { return networkService; }
    public CacheService getCacheService() { return cacheService; }
    public AdBlocker getAdBlocker() { return adBlocker; }
    public TrackerBlocker getTrackerBlocker() { return trackerBlocker; }
    public HistoryService getHistoryService() { return historyService; }
    public BookmarkService getBookmarkService() { return bookmarkService; }
    public SettingsService getSettingsService() { return settingsService; }
    public StatisticsService getStatisticsService() { return statisticsService; }
    public AuthenticationService getAuthenticationService() { return authenticationService; }
}
