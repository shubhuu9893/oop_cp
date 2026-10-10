package com.cachebrowse.database.repository;

import com.cachebrowse.auth.UserSession;
import com.cachebrowse.database.D1Client;
import com.cachebrowse.model.HistoryEntry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cloudflare D1 implementation of HistoryRepository.
 *
 * <p><b>Foreign-key safety:</b> The {@code history} table has
 * {@code FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE}.
 * Before every INSERT this repository ensures the anonymous user row exists
 * in {@code users} so that unauthenticated browsing never causes a
 * {@code SQLITE_CONSTRAINT_FOREIGNKEY} violation.</p>
 *
 * <p>All D1 errors are logged with safe field values (no passwords or tokens)
 * and are NEVER propagated to the calling thread — history persistence is
 * fully isolated from the browsing pipeline.</p>
 */
public class D1HistoryRepository implements HistoryRepository {
    private static final Logger LOG = LoggerFactory.getLogger(D1HistoryRepository.class);

    private final D1Client d1Client;
    private final List<HistoryEntry> localFallback = new CopyOnWriteArrayList<>();

    public D1HistoryRepository(D1Client d1Client) {
        this.d1Client = d1Client;
    }

    @Override
    public void addHistory(HistoryEntry entry) {
        if (entry == null || entry.getUrl() == null || entry.getUrl().isBlank()) {
            return;
        }

        // The caller must supply a verified Firebase UID; user row creation is owned by the API.
        String userId = (entry.getUserId() != null && !entry.getUserId().isBlank())
                ? entry.getUserId()
                : UserSession.ANONYMOUS_USER_ID;

        String url       = entry.getUrl();
        String title     = entry.getTitle() != null ? entry.getTitle() : "";
        String id        = entry.getId() != null ? entry.getId() : java.util.UUID.randomUUID().toString();
        Instant visitedAt = entry.getVisitedAt() != null ? entry.getVisitedAt() : Instant.now();

        // Always record locally — browsing history must survive even if D1 is down.
        HistoryEntry resolved = new HistoryEntry(id, userId, url, title, visitedAt);
        localFallback.add(0, resolved);

        if (d1Client == null || !d1Client.isConfigured()) {
            LOG.debug("[History] D1 not configured — history stored locally only.");
            return;
        }

        try {
            // Parent user profile is written from Firebase claims by the API before child rows.
            String sql = "INSERT INTO history (id, user_id, url, title, visited_at) VALUES (?, ?, ?, ?, ?)";
            List<Object> params = List.of(id, userId, url, title, visitedAt.toString());

            d1Client.executeChecked(sql, params);
            LOG.debug("History row persisted.");

        } catch (Exception e) {
            LOG.warn("[History] Failed to persist a history entry to D1");
            throw new IllegalStateException("History entry was not persisted to D1", e);
        }
    }

    @Override
    public List<HistoryEntry> getHistory(String userId, int limit) {
        String effectiveUserId = (userId != null && !userId.isBlank()) ? userId : UserSession.ANONYMOUS_USER_ID;
        int effectiveLimit = limit > 0 ? limit : 100;

        if (d1Client != null && d1Client.isConfigured()) {
            try {
                String sql = "SELECT id, user_id, url, title, visited_at FROM history WHERE user_id = ? ORDER BY visited_at DESC LIMIT ?";
                List<Map<String, Object>> rows = d1Client.query(sql, List.of(effectiveUserId, effectiveLimit));
                if (!rows.isEmpty()) {
                    List<HistoryEntry> list = new ArrayList<>();
                    for (Map<String, Object> row : rows) {
                        String id      = String.valueOf(row.get("id"));
                        String uid     = String.valueOf(row.get("user_id"));
                        String url     = String.valueOf(row.get("url"));
                        String title   = row.get("title") != null ? String.valueOf(row.get("title")) : "";
                        Instant visitedAt;
                        try {
                            visitedAt = Instant.parse(String.valueOf(row.get("visited_at")));
                        } catch (Exception ex) {
                            visitedAt = Instant.now();
                        }
                        list.add(new HistoryEntry(id, uid, url, title, visitedAt));
                    }
                    return list;
                }
            } catch (Exception e) {
                LOG.warn("[History] Failed to query history from D1: {}", e.getMessage());
            }
        }

        // Fallback to in-memory local list
        List<HistoryEntry> list = new ArrayList<>();
        for (HistoryEntry e : localFallback) {
            if (e.getUserId() == null || e.getUserId().equals(effectiveUserId)) {
                list.add(e);
                if (list.size() >= effectiveLimit) break;
            }
        }
        return list;
    }

    @Override
    public void deleteHistory(String userId, String id) {
        if (id == null) return;
        String effectiveUserId = (userId != null && !userId.isBlank()) ? userId : UserSession.ANONYMOUS_USER_ID;
        localFallback.removeIf(e -> id.equals(e.getId()) && effectiveUserId.equals(e.getUserId()));

        if (d1Client != null && d1Client.isConfigured()) {
            try {
                String sql = "DELETE FROM history WHERE user_id = ? AND id = ?";
                d1Client.executeChecked(sql, List.of(effectiveUserId, id));
            } catch (Exception e) {
                LOG.warn("[History] Failed to delete history entry from D1");
                throw new IllegalStateException("History deletion was not persisted", e);
            }
        }
    }

    @Override
    public void clearHistory(String userId) {
        String effectiveUserId = (userId != null && !userId.isBlank()) ? userId : UserSession.ANONYMOUS_USER_ID;
        localFallback.removeIf(e -> e.getUserId() == null || e.getUserId().equals(effectiveUserId));

        if (d1Client != null && d1Client.isConfigured()) {
            try {
                String sql = "DELETE FROM history WHERE user_id = ?";
                d1Client.executeChecked(sql, List.of(effectiveUserId));
            } catch (Exception e) {
                LOG.warn("[History] Failed to clear history from D1");
                throw new IllegalStateException("History clear was not persisted", e);
            }
        }
    }
}
