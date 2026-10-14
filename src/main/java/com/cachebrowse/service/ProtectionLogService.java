package com.cachebrowse.service;

import com.cachebrowse.database.repository.D1ProtectionEventRepository;
import com.cachebrowse.model.ProtectionAction;
import com.cachebrowse.model.ProtectionEvent;
import com.cachebrowse.model.ProtectionEventType;
import com.cachebrowse.model.ProtectionStats;
import com.cachebrowse.network.UrlSanitizer;
import com.cachebrowse.statistics.CacheStatistics;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service managing real-time and session-scoped protection activity logs.
 * Provides in-memory bounded storage, query filtering, SSE broadcast,
 * event deduplication, and export.
 */
public class ProtectionLogService {
    private static final Logger LOG = LoggerFactory.getLogger(ProtectionLogService.class);
    private static final int MAX_MEMORY_EVENTS = 5000;
    private static final int MAX_QUERY_LIMIT = 500;
    private static final long DEDUP_WINDOW_MS = 1000L;
    private static final int DEDUP_CLEANUP_THRESHOLD = 2000;

    private final CacheStatistics statistics;
    private final D1ProtectionEventRepository d1Repository;
    private final ObjectMapper objectMapper;

    private final ConcurrentLinkedDeque<ProtectionEvent> events = new ConcurrentLinkedDeque<>();
    private final Map<String, Long> dedupCache = new ConcurrentHashMap<>();
    private final Set<Consumer<ProtectionEvent>> eventListeners = ConcurrentHashMap.newKeySet();

    private volatile boolean diagnosticLoggingEnabled = false;

    public ProtectionLogService(CacheStatistics statistics) {
        this(statistics, null, new ObjectMapper());
    }

    public ProtectionLogService(CacheStatistics statistics, D1ProtectionEventRepository d1Repository, ObjectMapper objectMapper) {
        this.statistics = statistics;
        this.d1Repository = d1Repository;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public boolean isDiagnosticLoggingEnabled() {
        return diagnosticLoggingEnabled;
    }

    public void setDiagnosticLoggingEnabled(boolean diagnosticLoggingEnabled) {
        this.diagnosticLoggingEnabled = diagnosticLoggingEnabled;
        LOG.info("[ProtectionLog] Diagnostic logging set to: {}", diagnosticLoggingEnabled);
    }

    /**
     * Registers a listener callback for live event delivery (e.g., SSE streams).
     * @return a Runnable to unsubscribe.
     */
    public Runnable addListener(Consumer<ProtectionEvent> listener) {
        if (listener != null) {
            eventListeners.add(listener);
            return () -> eventListeners.remove(listener);
        }
        return () -> {};
    }

    /**
     * Records a protection decision into memory, updates statistics, and broadcasts to listeners.
     */
    public ProtectionEvent recordEvent(ProtectionEvent rawEvent) {
        if (rawEvent == null) return null;

        // Skip non-diagnostic allowed events when diagnostic logging is disabled
        if (rawEvent.getAction() == ProtectionAction.ALLOWED && !diagnosticLoggingEnabled) {
            return null;
        }

        // Sanitize URLs to prevent logging passwords, auth tokens, and session secrets
        String cleanResourceUrl = UrlSanitizer.sanitizeUrl(rawEvent.getResourceUrl());
        String cleanPageUrl = UrlSanitizer.sanitizeUrl(rawEvent.getPageUrl());
        String resourceHost = rawEvent.getResourceHostname() != null && !rawEvent.getResourceHostname().isBlank()
                ? rawEvent.getResourceHostname().toLowerCase(Locale.ROOT)
                : UrlSanitizer.extractHost(cleanResourceUrl);
        String pageHost = rawEvent.getPageHostname() != null && !rawEvent.getPageHostname().isBlank()
                ? rawEvent.getPageHostname().toLowerCase(Locale.ROOT)
                : UrlSanitizer.extractHost(cleanPageUrl);

        // Deduplication check
        long now = System.currentTimeMillis();
        String dedupKey = buildDedupKey(rawEvent.getSessionId(), rawEvent.getTabId(), cleanResourceUrl, rawEvent.getAction());
        Long prev = dedupCache.put(dedupKey, now);
        if (prev != null && (now - prev) < DEDUP_WINDOW_MS) {
            // Already processed within deduplication window
            return null;
        }
        cleanupDedupCacheIfFull(now);

        ProtectionEvent event = new ProtectionEvent(
                rawEvent.getEventId(),
                rawEvent.getTimestamp() != null ? rawEvent.getTimestamp() : Instant.now().toString(),
                rawEvent.getSessionId() != null ? rawEvent.getSessionId() : "default-session",
                rawEvent.getTabId() != null ? rawEvent.getTabId() : "default-tab",
                cleanPageUrl,
                pageHost,
                cleanResourceUrl,
                resourceHost,
                rawEvent.getEventType() != null ? rawEvent.getEventType() : ProtectionEventType.AD,
                rawEvent.getAction() != null ? rawEvent.getAction() : ProtectionAction.BLOCKED,
                rawEvent.getRule(),
                rawEvent.getReason(),
                rawEvent.getResourceType() != null ? rawEvent.getResourceType() : "other"
        );

        // Update statistics for real decisions
        if (statistics != null) {
            if (event.getAction() == ProtectionAction.BLOCKED) {
                if (event.getEventType() == ProtectionEventType.AD) {
                    statistics.incAdsBlocked();
                } else if (event.getEventType() == ProtectionEventType.TRACKER) {
                    statistics.incTrackersBlocked();
                }
            } else if (event.getAction() == ProtectionAction.ALLOWED) {
                statistics.incAllowedRequests();
            }
        }

        // Store into in-memory bounded buffer (newest at front)
        events.addFirst(event);
        while (events.size() > MAX_MEMORY_EVENTS) {
            events.pollLast();
        }

        // Broadcast to live stream listeners
        for (Consumer<ProtectionEvent> listener : eventListeners) {
            try {
                listener.accept(event);
            } catch (Exception ex) {
                LOG.debug("[ProtectionLog] Listener notification failed: {}", ex.getMessage());
            }
        }

        // Optional asynchronous D1 persistence
        if (d1Repository != null && event.getAction() == ProtectionAction.BLOCKED) {
            d1Repository.queueForBatchInsert(event);
        }

        return event;
    }

    /**
     * Query events with filtering, search query, and pagination.
     */
    public List<ProtectionEvent> getEvents(String sessionId, String tabId, ProtectionEventType type,
                                           ProtectionAction action, String query, int limit, int offset) {
        int effectiveLimit = (limit <= 0) ? 50 : Math.min(limit, MAX_QUERY_LIMIT);
        int effectiveOffset = Math.max(0, offset);

        String cleanQuery = (query != null && !query.isBlank()) ? query.trim().toLowerCase(Locale.ROOT) : null;

        List<ProtectionEvent> matches = new ArrayList<>();
        int currentIdx = 0;

        for (ProtectionEvent ev : events) {
            if (sessionId != null && !sessionId.isBlank() && !sessionId.equalsIgnoreCase(ev.getSessionId())) {
                continue;
            }
            if (tabId != null && !tabId.isBlank() && !tabId.equalsIgnoreCase(ev.getTabId())) {
                continue;
            }
            if (type != null && ev.getEventType() != type) {
                continue;
            }
            if (action != null && ev.getAction() != action) {
                continue;
            }
            if (cleanQuery != null) {
                boolean hit = (ev.getResourceUrl() != null && ev.getResourceUrl().toLowerCase(Locale.ROOT).contains(cleanQuery))
                        || (ev.getResourceHostname() != null && ev.getResourceHostname().contains(cleanQuery))
                        || (ev.getPageUrl() != null && ev.getPageUrl().toLowerCase(Locale.ROOT).contains(cleanQuery))
                        || (ev.getPageHostname() != null && ev.getPageHostname().contains(cleanQuery))
                        || (ev.getRule() != null && ev.getRule().toLowerCase(Locale.ROOT).contains(cleanQuery))
                        || (ev.getReason() != null && ev.getReason().toLowerCase(Locale.ROOT).contains(cleanQuery))
                        || (ev.getResourceType() != null && ev.getResourceType().toLowerCase(Locale.ROOT).contains(cleanQuery));
                if (!hit) continue;
            }

            if (currentIdx >= effectiveOffset && matches.size() < effectiveLimit) {
                matches.add(ev);
            }
            currentIdx++;
            if (matches.size() >= effectiveLimit) break;
        }

        return matches;
    }

    /**
     * Total number of matching events for given filters.
     */
    public int countEvents(String sessionId, String tabId, ProtectionEventType type,
                           ProtectionAction action, String query) {
        String cleanQuery = (query != null && !query.isBlank()) ? query.trim().toLowerCase(Locale.ROOT) : null;
        int count = 0;
        for (ProtectionEvent ev : events) {
            if (sessionId != null && !sessionId.isBlank() && !sessionId.equalsIgnoreCase(ev.getSessionId())) continue;
            if (tabId != null && !tabId.isBlank() && !tabId.equalsIgnoreCase(ev.getTabId())) continue;
            if (type != null && ev.getEventType() != type) continue;
            if (action != null && ev.getAction() != action) continue;
            if (cleanQuery != null) {
                boolean hit = (ev.getResourceUrl() != null && ev.getResourceUrl().toLowerCase(Locale.ROOT).contains(cleanQuery))
                        || (ev.getResourceHostname() != null && ev.getResourceHostname().contains(cleanQuery))
                        || (ev.getPageUrl() != null && ev.getPageUrl().toLowerCase(Locale.ROOT).contains(cleanQuery))
                        || (ev.getPageHostname() != null && ev.getPageHostname().contains(cleanQuery))
                        || (ev.getRule() != null && ev.getRule().toLowerCase(Locale.ROOT).contains(cleanQuery))
                        || (ev.getReason() != null && ev.getReason().toLowerCase(Locale.ROOT).contains(cleanQuery))
                        || (ev.getResourceType() != null && ev.getResourceType().toLowerCase(Locale.ROOT).contains(cleanQuery));
                if (!hit) continue;
            }
            count++;
        }
        return count;
    }

    /**
     * Calculates statistics for all, session, and tab scopes.
     */
    public ProtectionStats getStats(String sessionId, String tabId) {
        ProtectionStats stats = new ProtectionStats();
        stats.setSessionId(sessionId);
        stats.setTabId(tabId);
        stats.setDiagnosticLogging(diagnosticLoggingEnabled);

        long totalAds = statistics != null ? statistics.getAdsBlocked() : 0;
        long totalTrackers = statistics != null ? statistics.getTrackersBlocked() : 0;
        long totalAllowed = statistics != null ? statistics.getAllowedRequests() : 0;

        long sessAds = 0;
        long sessTrackers = 0;
        long sessAllowed = 0;

        long tabAds = 0;
        long tabTrackers = 0;
        long tabAllowed = 0;

        for (ProtectionEvent ev : events) {
            boolean isSess = sessionId != null && !sessionId.isBlank() && sessionId.equalsIgnoreCase(ev.getSessionId());
            boolean isTab = isSess && (tabId != null && !tabId.isBlank() && tabId.equalsIgnoreCase(ev.getTabId()));

            if (ev.getAction() == ProtectionAction.BLOCKED) {
                if (ev.getEventType() == ProtectionEventType.AD) {
                    if (isSess) sessAds++;
                    if (isTab) tabAds++;
                } else if (ev.getEventType() == ProtectionEventType.TRACKER) {
                    if (isSess) sessTrackers++;
                    if (isTab) tabTrackers++;
                }
            } else if (ev.getAction() == ProtectionAction.ALLOWED) {
                if (isSess) sessAllowed++;
                if (isTab) tabAllowed++;
            }
        }

        stats.setTotalAdsBlocked(totalAds);
        stats.setTotalTrackersBlocked(totalTrackers);
        stats.setTotalBlocked(totalAds + totalTrackers);
        stats.setTotalAllowed(totalAllowed);

        stats.setSessionAdsBlocked(sessAds);
        stats.setSessionTrackersBlocked(sessTrackers);
        stats.setSessionBlocked(sessAds + sessTrackers);
        stats.setSessionAllowed(sessAllowed);

        stats.setTabAdsBlocked(tabAds);
        stats.setTabTrackersBlocked(tabTrackers);
        stats.setTabBlocked(tabAds + tabTrackers);
        stats.setTabAllowed(tabAllowed);

        return stats;
    }

    /**
     * Clears all recorded events for the given session ID only.
     * Does NOT touch history, bookmarks, or other sessions.
     */
    public int clearSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return 0;
        int removed = 0;
        Iterator<ProtectionEvent> it = events.iterator();
        while (it.hasNext()) {
            ProtectionEvent ev = it.next();
            if (sessionId.equalsIgnoreCase(ev.getSessionId())) {
                it.remove();
                removed++;
            }
        }
        if (d1Repository != null) {
            d1Repository.deleteBySession(sessionId);
        }
        LOG.info("[ProtectionLog] Cleared {} events for session {}", removed, sessionId);
        return removed;
    }

    /**
     * Exports recorded events for a session in JSON or CSV format.
     */
    public String exportEvents(String sessionId, String format) {
        List<ProtectionEvent> sessionEvents = getEvents(sessionId, null, null, null, null, MAX_MEMORY_EVENTS, 0);
        if ("csv".equalsIgnoreCase(format)) {
            return exportCsv(sessionEvents);
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(sessionEvents);
        } catch (Exception ex) {
            LOG.error("[ProtectionLog] JSON export failed: {}", ex.getMessage());
            return "[]";
        }
    }

    private String exportCsv(List<ProtectionEvent> list) {
        StringBuilder sb = new StringBuilder();
        sb.append("eventId,timestamp,sessionId,tabId,pageUrl,pageHostname,resourceUrl,resourceHostname,eventType,action,rule,reason,resourceType\n");
        for (ProtectionEvent ev : list) {
            sb.append(escapeCsv(ev.getEventId())).append(',')
              .append(escapeCsv(ev.getTimestamp())).append(',')
              .append(escapeCsv(ev.getSessionId())).append(',')
              .append(escapeCsv(ev.getTabId())).append(',')
              .append(escapeCsv(ev.getPageUrl())).append(',')
              .append(escapeCsv(ev.getPageHostname())).append(',')
              .append(escapeCsv(ev.getResourceUrl())).append(',')
              .append(escapeCsv(ev.getResourceHostname())).append(',')
              .append(ev.getEventType() != null ? ev.getEventType().name() : "").append(',')
              .append(ev.getAction() != null ? ev.getAction().name() : "").append(',')
              .append(escapeCsv(ev.getRule())).append(',')
              .append(escapeCsv(ev.getReason())).append(',')
              .append(escapeCsv(ev.getResourceType())).append('\n');
        }
        return sb.toString();
    }

    private static String escapeCsv(String val) {
        if (val == null) return "\"\"";
        return "\"" + val.replace("\"", "\"\"") + "\"";
    }

    private static String buildDedupKey(String sessionId, String tabId, String url, ProtectionAction action) {
        return (sessionId != null ? sessionId : "") + "|" +
                (tabId != null ? tabId : "") + "|" +
                (url != null ? url : "") + "|" +
                (action != null ? action.name() : "");
    }

    private void cleanupDedupCacheIfFull(long now) {
        if (dedupCache.size() > DEDUP_CLEANUP_THRESHOLD) {
            dedupCache.entrySet().removeIf(entry -> (now - entry.getValue()) > (DEDUP_WINDOW_MS * 5));
        }
    }
}
