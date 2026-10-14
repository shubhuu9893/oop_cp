package com.cachebrowse.database.repository;

import com.cachebrowse.database.D1Client;
import com.cachebrowse.model.ProtectionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cloudflare D1 repository for asynchronously persisting real blocked protection events.
 * Batches inserts on a background thread so browsing is never blocked.
 */
public class D1ProtectionEventRepository {
    private static final Logger LOG = LoggerFactory.getLogger(D1ProtectionEventRepository.class);
    private static final int BATCH_SIZE = 25;
    private static final int MAX_QUEUE_SIZE = 1000;

    private final D1Client d1Client;
    private final ConcurrentLinkedQueue<ProtectionEvent> batchQueue = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService scheduler;

    public D1ProtectionEventRepository(D1Client d1Client) {
        this.d1Client = d1Client;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "d1-protection-log-flusher");
            t.setDaemon(true);
            return t;
        });
        this.scheduler.scheduleWithFixedDelay(this::flushBatch, 5, 5, TimeUnit.SECONDS);
    }

    public void queueForBatchInsert(ProtectionEvent event) {
        if (event == null || d1Client == null || !d1Client.isConfigured() || !d1Client.isAvailable()) {
            return;
        }
        if (batchQueue.size() >= MAX_QUEUE_SIZE) {
            batchQueue.poll(); // Evict oldest from queue to enforce bounds
        }
        batchQueue.offer(event);
    }

    public void flushBatch() {
        if (batchQueue.isEmpty() || d1Client == null || !d1Client.isConfigured() || !d1Client.isAvailable()) {
            return;
        }

        List<ProtectionEvent> batch = new ArrayList<>(BATCH_SIZE);
        for (int i = 0; i < BATCH_SIZE; i++) {
            ProtectionEvent ev = batchQueue.poll();
            if (ev == null) break;
            batch.add(ev);
        }

        if (batch.isEmpty()) return;

        List<String> statements = new ArrayList<>();
        for (ProtectionEvent ev : batch) {
            String sql = String.format(
                    "INSERT INTO protection_events (id, session_id, tab_id, page_url, page_hostname, resource_url, resource_hostname, event_type, action, rule, reason, resource_type, created_at) " +
                    "VALUES ('%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s')",
                    escapeSql(ev.getEventId()),
                    escapeSql(ev.getSessionId()),
                    escapeSql(ev.getTabId()),
                    escapeSql(ev.getPageUrl()),
                    escapeSql(ev.getPageHostname()),
                    escapeSql(ev.getResourceUrl()),
                    escapeSql(ev.getResourceHostname()),
                    ev.getEventType() != null ? ev.getEventType().name() : "AD",
                    ev.getAction() != null ? ev.getAction().name() : "BLOCKED",
                    escapeSql(ev.getRule()),
                    escapeSql(ev.getReason()),
                    escapeSql(ev.getResourceType()),
                    escapeSql(ev.getTimestamp())
            );
            statements.add(sql);
        }

        try {
            boolean ok = d1Client.executeBatch(statements);
            if (ok) {
                LOG.debug("[D1Protection] Successfully persisted batch of {} events", batch.size());
            } else {
                LOG.debug("[D1Protection] Batch persist returned false; D1 may be offline");
            }
        } catch (Exception ex) {
            LOG.debug("[D1Protection] Asynchronous batch persist skipped: {}", ex.getMessage());
        }
    }

    public void deleteBySession(String sessionId) {
        if (sessionId == null || sessionId.isBlank() || d1Client == null || !d1Client.isConfigured() || !d1Client.isAvailable()) {
            return;
        }
        try {
            String sql = "DELETE FROM protection_events WHERE session_id = ?";
            d1Client.execute(sql, List.of(sessionId));
        } catch (Exception ex) {
            LOG.debug("[D1Protection] Delete by session skipped: {}", ex.getMessage());
        }
    }

    public void shutdown() {
        try {
            flushBatch();
            scheduler.shutdown();
        } catch (Exception ignored) {}
    }

    private static String escapeSql(String val) {
        if (val == null) return "";
        return val.replace("'", "''");
    }
}
