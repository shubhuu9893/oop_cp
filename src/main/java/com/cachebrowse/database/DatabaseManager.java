package com.cachebrowse.database;

import com.cachebrowse.database.repository.*;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central Database Manager managing D1 connectivity and Repositories.
 */
public class DatabaseManager {
    private static final Logger LOG = LoggerFactory.getLogger(DatabaseManager.class);
    private static DatabaseManager instance;

    private final D1Config config;
    private final D1Client d1Client;
    private final HistoryRepository historyRepository;
    private final BookmarkRepository bookmarkRepository;
    private final SettingsRepository settingsRepository;
    private final StatisticsRepository statisticsRepository;
    private final UserRepository userRepository;
    private volatile boolean initialized;
    private volatile String diagnostic = "not_checked";

    public DatabaseManager() {
        this(new D1Config());
    }

    public DatabaseManager(D1Config config) {
        this(config, null);
    }

    public DatabaseManager(D1Config config, D1Client injectedClient) {
        this.config = config != null ? config : new D1Config();
        this.d1Client = injectedClient != null ? injectedClient : new D1Client(this.config);
        this.diagnostic = this.config.isConfigured() ? "initialization_pending" : "missing_credentials";
        this.historyRepository = new D1HistoryRepository(this.d1Client);
        this.bookmarkRepository = new D1BookmarkRepository(this.d1Client);
        this.settingsRepository = new D1SettingsRepository(this.d1Client);
        this.statisticsRepository = new D1StatisticsRepository(this.d1Client);
        this.userRepository = new D1UserRepository(this.d1Client);

        if (this.config.isConfigured()) {
            initSchema();
        }
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    /**
     * Initializes the D1 database schema if configured.
     */
    public void initSchema() {
        if (!config.isConfigured()) {
            diagnostic = "missing_credentials";
            return;
        }

        try {
            boolean connected = false;
            for (int attempt = 0; attempt < 3; attempt++) {
                if (d1Client.testConnection()) { connected = true; break; }
                String failure = d1Client.getLastFailure();
                if (!isTransient(failure) || attempt == 2) break;
                try {
                    Thread.sleep(250L * (attempt + 1));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    diagnostic = "request_interrupted";
                    throw new IllegalStateException("D1 initialization interrupted", interrupted);
                }
            }
            if (!connected) throw new IllegalStateException("D1 connectivity check failed (" + d1Client.getLastFailure() + ")");
            List<String> statements = loadSchemaStatements();
            if (statements.isEmpty()) {
                throw new IllegalStateException("D1 schema file is missing or empty");
            }
            if (!d1Client.executeBatch(statements)) {
                throw new IllegalStateException("schema_initialization_failed (" + d1Client.getLastFailure() + ")");
            }
            initialized = true;
            diagnostic = "none";
            LOG.info("D1 Database schema initialized successfully.");
        } catch (Exception e) {
            initialized = false;
            diagnostic = d1Client.getLastFailure();
            if (diagnostic == null || diagnostic.isBlank() || "none".equals(diagnostic)) diagnostic = "schema_initialization_failed";
            d1Client.markUnavailable();
            LOG.error("Failed to initialize Cloudflare D1 ({}); using non-persistent fallback mode", diagnostic);
        }
    }

    /** Rechecks the configured credentials and reruns idempotent schema creation. */
    public synchronized boolean retryInitialization() {
        initialized = false;
        d1Client.prepareForRetry();
        initSchema();
        return initialized;
    }

    public String getDiagnostic() {
        String current = d1Client.getLastFailure();
        return current == null || "not_checked".equals(current) || "none".equals(current)
                ? diagnostic : current;
    }

    private static boolean isTransient(String failure) {
        return "request_timeout".equals(failure) || "dns_error".equals(failure)
                || "connection_refused".equals(failure) || "network_error".equals(failure)
                || "cloudflare_service_error".equals(failure);
    }

    private List<String> loadSchemaStatements() {
        List<String> statements = new ArrayList<>();
        try {
            Path path = Paths.get("database", "schema.sql");
            String content = null;
            if (Files.exists(path)) {
                content = Files.readString(path);
            }
            if (content == null || content.isBlank()) {
                InputStream in = getClass().getResourceAsStream("/database/schema.sql");
                if (in != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line).append("\n");
                        }
                        content = sb.toString();
                    }
                }
            }

            if (content != null) {
                String[] raw = content.split(";");
                for (String s : raw) {
                    String clean = s.replaceAll("(?m)^--.*$", "").trim();
                    if (!clean.isEmpty()) {
                        statements.add(clean);
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Could not load schema.sql: {}", e.getMessage());
        }
        return statements;
    }

    public D1Config getConfig() {
        return config;
    }

    public boolean isInitialized() { return initialized && d1Client.isConfigured(); }

    public D1Client getD1Client() {
        return d1Client;
    }

    public HistoryRepository getHistoryRepository() {
        return historyRepository;
    }

    public BookmarkRepository getBookmarkRepository() {
        return bookmarkRepository;
    }

    public SettingsRepository getSettingsRepository() {
        return settingsRepository;
    }

    public StatisticsRepository getStatisticsRepository() {
        return statisticsRepository;
    }

    public UserRepository getUserRepository() {
        return userRepository;
    }
}
