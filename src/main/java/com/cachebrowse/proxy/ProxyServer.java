package com.cachebrowse.proxy;

import com.cachebrowse.adblock.AdBlocker;
import com.cachebrowse.adblock.TrackerBlocker;
import com.cachebrowse.cache.CacheManager;
import com.cachebrowse.model.BrowserSettings;
import com.cachebrowse.statistics.CacheStatistics;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Local HTTP Proxy Server with HTTP CONNECT tunneling support.
 * Also configures the JVM ProxySelector so JavaFX WebEngine routes its HTTP/HTTPS
 * requests through this local proxy.
 */
public class ProxyServer {
    private static final Logger LOG = LoggerFactory.getLogger(ProxyServer.class);

    private int port;
    private final CacheManager cacheManager;
    private final CacheStatistics statistics;
    private final BrowserSettings settings;
    private final AdBlocker adBlocker;
    private final TrackerBlocker trackerBlocker;

    private ServerSocket serverSocket;
    private ExecutorService executor;
    private volatile boolean running = false;
    private Runnable onStatsUpdated;

    public ProxyServer(int port, CacheManager cacheManager, CacheStatistics statistics,
                       BrowserSettings settings, AdBlocker adBlocker, TrackerBlocker trackerBlocker) {
        this.port = port > 0 ? port : 8080;
        this.cacheManager = cacheManager;
        this.statistics = statistics;
        this.settings = settings != null ? settings : new BrowserSettings();
        this.adBlocker = adBlocker != null ? adBlocker : new AdBlocker();
        this.trackerBlocker = trackerBlocker != null ? trackerBlocker : new TrackerBlocker();
    }

    public ProxyServer() {
        this(8080, null, null, null, null, null);
    }

    public void setOnStatsUpdated(Runnable callback) {
        this.onStatsUpdated = callback;
    }

    public synchronized void start() throws IOException {
        if (running) return;

        // Bind to localhost on configured port or find an available ephemeral port
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException bindEx) {
            LOG.warn("Port {} in use, binding to ephemeral port...", port);
            serverSocket = new ServerSocket(0);
            this.port = serverSocket.getLocalPort();
        }

        executor = Executors.newFixedThreadPool(64, r -> {
            Thread t = new Thread(r, "ProxyServer-Worker");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1); // Slightly below normal to yield rendering thread
            return t;
        });
        running = true;
        LOG.info("ProxyServer listening on 127.0.0.1:{}", port);

        Thread acceptThread = new Thread(() -> {
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    executor.submit(new ClientHandler(
                            clientSocket,
                            cacheManager,
                            statistics,
                            settings,
                            adBlocker,
                            trackerBlocker,
                            onStatsUpdated
                    ));
                } catch (IOException e) {
                    if (!running) break;
                    LOG.error("Error accepting proxy connection: {}", e.getMessage());
                }
            }
        }, "ProxyServer-AcceptThread");
        acceptThread.setDaemon(true);
        acceptThread.start();

        // Install local ProxySelector to route JavaFX WebView network traffic through local proxy
        installProxySelector();
    }

    private void installProxySelector() {
        final Proxy localProxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", port));
        final ProxySelector previousSelector = ProxySelector.getDefault();

        ProxySelector.setDefault(new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                if (uri == null) {
                    return Collections.singletonList(Proxy.NO_PROXY);
                }
                String host = uri.getHost();
                if (host != null && (host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1"))) {
                    return Collections.singletonList(Proxy.NO_PROXY);
                }
                String scheme = uri.getScheme();
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    return Collections.singletonList(localProxy);
                }
                return previousSelector != null ? previousSelector.select(uri) : Collections.singletonList(Proxy.NO_PROXY);
            }

            @Override
            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                if (previousSelector != null) {
                    previousSelector.connectFailed(uri, sa, ioe);
                }
            }
        });
        LOG.info("Installed Local ProxySelector directing WebView traffic to port {}", port);
    }

    public synchronized void stop() {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
        LOG.info("ProxyServer stopped.");
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }
}