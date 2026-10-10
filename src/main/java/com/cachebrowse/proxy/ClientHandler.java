package com.cachebrowse.proxy;

import com.cachebrowse.adblock.AdBlocker;
import com.cachebrowse.adblock.TrackerBlocker;
import com.cachebrowse.cache.CacheEntry;
import com.cachebrowse.cache.CacheManager;
import com.cachebrowse.model.BrowserSettings;
import com.cachebrowse.network.HttpClient;
import com.cachebrowse.network.HttpRequest;
import com.cachebrowse.network.HttpResponse;
import com.cachebrowse.statistics.CacheStatistics;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles a single incoming proxy client socket connection.
 * Supports standard HTTP requests (GET, POST, etc.) and HTTPS tunnels via HTTP CONNECT.
 *
 * Key performance decisions:
 * - Pipe buffer is 64 KB (was 8 KB) to reduce read/write cycles for video streaming.
 * - Streaming media (video/*, audio/*, manifest, DASH) is NOT cached to prevent
 *   memory exhaustion and GC pressure during video playback.
 * - Stats callbacks are never fired per-request to prevent Platform.runLater() flooding.
 */
public class ClientHandler implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(ClientHandler.class);

    // 64 KB pipe buffer for smooth video streaming (was 8 KB — too small, caused frame drops)
    private static final int PIPE_BUFFER_SIZE = 65536;

    // 512 KB: maximum response size eligible for caching.
    // Video segments are 1–10 MB each — caching them causes memory exhaustion and GC stalls.
    private static final int MAX_CACHEABLE_RESPONSE_BYTES = 524_288;

    private final Socket socket;
    private final CacheManager cacheManager;
    private final CacheStatistics statistics;
    private final BrowserSettings settings;
    private final AdBlocker adBlocker;
    private final TrackerBlocker trackerBlocker;
    private final Runnable onStatsUpdated;

    public ClientHandler(Socket socket, CacheManager cacheManager, CacheStatistics statistics,
                         BrowserSettings settings, AdBlocker adBlocker, TrackerBlocker trackerBlocker,
                         Runnable onStatsUpdated) {
        this.socket = socket;
        this.cacheManager = cacheManager;
        this.statistics = statistics;
        this.settings = settings != null ? settings : new BrowserSettings();
        this.adBlocker = adBlocker != null ? adBlocker : new AdBlocker();
        this.trackerBlocker = trackerBlocker != null ? trackerBlocker : new TrackerBlocker();
        this.onStatsUpdated = onStatsUpdated;
    }

    public ClientHandler(Socket socket) {
        this(socket, null, null, null, null, null, null);
    }

    @Override
    public void run() {
        try (socket; InputStream in = socket.getInputStream(); OutputStream out = socket.getOutputStream()) {
            RequestHandler.ParsedRequest request = RequestHandler.parse(in);
            if (request == null) return;

            if (statistics != null) statistics.incTotalRequests();

            String method = request.getMethod();

            // ── HTTPS Tunnels ──────────────────────────────────────────────────
            if ("CONNECT".equalsIgnoreCase(method)) {
                handleConnect(request, in, out);
                return;
            }

            // ── Standard HTTP ─────────────────────────────────────────────────
            String url = request.getTargetUrl();
            if (url == null || url.isBlank()) {
                ResponseHandler.writeErrorResponse(out, 400, "Bad Request: Missing URL");
                return;
            }

            // 1. Ad block check
            if (settings.isAdBlockingEnabled() && adBlocker.isAdBlocked(url)) {
                if (statistics != null) statistics.incAdsBlocked();
                // No triggerStatsCallback() — would flood Platform.runLater() per-request
                LOG.info("[Proxy] Blocked Ad request: {}", url);
                ResponseHandler.writeBlockedResponse(out, "Ad Blocker");
                return;
            }

            // 2. Tracker block check
            if (settings.isTrackerBlockingEnabled() && trackerBlocker.isTrackerBlocked(url)) {
                if (statistics != null) statistics.incTrackersBlocked();
                // No triggerStatsCallback()
                LOG.info("[Proxy] Blocked Tracker request: {}", url);
                ResponseHandler.writeBlockedResponse(out, "Tracker Blocker");
                return;
            }

            if (statistics != null) statistics.incAllowedRequests();

            // 3. Cache lookup
            if (cacheManager != null && settings.isCacheEnabled()) {
                CacheEntry cached = cacheManager.get(url);
                if (cached != null) {
                    // No triggerStatsCallback() — cache HITs are very frequent
                    ResponseHandler.writeResponse(out, cached.getStatusCode(), "OK",
                            cached.getContentType(), cached.getResponseBody(), null);
                    return;
                }
            }

            // 4. Cache MISS → forward to origin
            if (statistics != null) statistics.incNetworkRequests();

            long start = System.nanoTime();
            try {
                HttpRequest httpReq = request.toHttpRequest();
                HttpResponse httpRes = HttpClient.execute(httpReq);

                long elapsedMs = (System.nanoTime() - start) / 1_000_000;
                if (statistics != null) {
                    statistics.addResponseTime(elapsedMs);
                    statistics.addBytesFromNetwork(httpRes.getBody().length);
                    // No triggerStatsCallback() — fires per-resource, up to 200× per page
                }

                // 5. Store in cache only if eligible (not streaming media)
                if (cacheManager != null && settings.isCacheEnabled()
                        && httpRes.isSuccess() && isCacheable(httpRes)) {
                    cacheManager.put(url, new CacheEntry(
                            url,
                            httpRes.getBody(),
                            settings.getCacheTtl(),
                            httpRes.getContentType(),
                            httpRes.getStatusCode()
                    ));
                }

                // 6. Return response
                ResponseHandler.writeResponse(out, httpRes.getStatusCode(), "OK",
                        httpRes.getContentType(), httpRes.getBody(), httpRes.getHeaders());

            } catch (Exception ex) {
                LOG.warn("Proxy forwarding failed for {}: {}", url, ex.getMessage());
                ResponseHandler.writeErrorResponse(out, 502, "Bad Gateway: " + ex.getMessage());
            }

        } catch (Exception e) {
            LOG.debug("Client handler exception: {}", e.getMessage());
        }
    }

    /**
     * Whether a response should be stored in the LRU cache.
     *
     * Video/audio streaming responses are excluded because:
     * - Segments are 1–10 MB each — caching them exhausts the LRU and causes GC storms.
     * - GC storms during playback stall the JavaFX rendering thread → WebView flickers.
     * - Segments have no reuse value (played once, then discarded by the player).
     * - Range requests (206 Partial Content) cannot be correctly served from cache.
     */
    private static boolean isCacheable(HttpResponse response) {
        if (response == null || response.getBody() == null) return false;

        // Skip large responses (video segments, large binaries)
        if (response.getBody().length > MAX_CACHEABLE_RESPONSE_BYTES) return false;

        String ct = response.getContentType();
        if (ct == null) return true;
        String low = ct.toLowerCase();

        return !low.startsWith("video/")
            && !low.startsWith("audio/")
            && !low.contains("application/octet-stream")
            && !low.contains("application/x-mpegurl")        // HLS
            && !low.contains("application/vnd.apple.mpegurl")
            && !low.contains("application/dash+xml")          // DASH
            && !low.contains("video/mp4")
            && !low.contains("video/webm")
            && !low.contains("video/x-flv");
    }

    /**
     * Handles HTTPS CONNECT tunnels.
     * Checks host against ad/tracker lists; if blocked, returns 403 and closes.
     * Otherwise establishes a bidirectional byte pipe to the remote server.
     */
    private void handleConnect(RequestHandler.ParsedRequest request,
                                InputStream clientIn, OutputStream clientOut) {
        String target = request.getUri();
        if (target == null || target.isBlank()) {
            try { ResponseHandler.writeErrorResponse(clientOut, 400, "No target"); } catch (Exception ignored) {}
            return;
        }

        String host = target;
        int    port = 443;
        int colonIdx = target.indexOf(':');
        if (colonIdx != -1) {
            host = target.substring(0, colonIdx);
            try { port = Integer.parseInt(target.substring(colonIdx + 1)); } catch (NumberFormatException ignored) {}
        }

        String checkUrl = "https://" + target;

        // Ad block
        if (settings.isAdBlockingEnabled()
                && (adBlocker.isAdBlocked(checkUrl) || adBlocker.isAdBlocked(host))) {
            if (statistics != null) statistics.incAdsBlocked();
            // No triggerStatsCallback() — CONNECT blocks fire 50+ times per page on ad-heavy sites
            LOG.info("[Proxy CONNECT] Blocked Ad host: {}", host);
            try { ResponseHandler.writeBlockedResponse(clientOut, "Ad Blocker"); } catch (Exception ignored) {}
            return;
        }

        // Tracker block
        if (settings.isTrackerBlockingEnabled()
                && (trackerBlocker.isTrackerBlocked(checkUrl) || trackerBlocker.isTrackerBlocked(host))) {
            if (statistics != null) statistics.incTrackersBlocked();
            // No triggerStatsCallback()
            LOG.info("[Proxy CONNECT] Blocked Tracker host: {}", host);
            try { ResponseHandler.writeBlockedResponse(clientOut, "Tracker Blocker"); } catch (Exception ignored) {}
            return;
        }

        if (statistics != null) statistics.incAllowedRequests();

        // Establish bidirectional tunnel
        try (Socket remoteSocket = new Socket(host, port)) {
            clientOut.write("HTTP/1.1 200 Connection Established\r\n\r\n"
                    .getBytes(StandardCharsets.US_ASCII));
            clientOut.flush();

            InputStream  remoteIn  = remoteSocket.getInputStream();
            OutputStream remoteOut = remoteSocket.getOutputStream();

            Thread t = new Thread(() -> {
                pipe(clientIn, remoteOut);
                try { remoteSocket.shutdownOutput(); } catch (Exception ignored) {}
            }, "ProxyPipe-ClientToRemote");
            t.setDaemon(true);
            t.start();

            pipe(remoteIn, clientOut);
            try { socket.shutdownOutput(); }    catch (Exception ignored) {}
            try { t.join(3000); }               catch (InterruptedException ignored) {}

        } catch (Exception e) {
            LOG.debug("CONNECT tunnel failed for {}: {}", target, e.getMessage());
            try { ResponseHandler.writeErrorResponse(clientOut, 502, "Bad Gateway: " + e.getMessage()); }
            catch (Exception ignored) {}
        }
    }

    /**
     * Copies bytes from {@code in} to {@code out} until EOF or error.
     * Buffer is 64 KB (previously 8 KB) to handle video streaming efficiently.
     */
    private static void pipe(InputStream in, OutputStream out) {
        try {
            byte[] buf = new byte[PIPE_BUFFER_SIZE];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                out.flush();
            }
        } catch (Exception ignored) {}
    }

    private void triggerStatsCallback() {
        if (onStatsUpdated != null) {
            try { onStatsUpdated.run(); } catch (Exception ignored) {}
        }
    }
}
