package com.cachebrowse.database.repository;

import com.cachebrowse.database.D1Client;
import com.cachebrowse.model.Bookmark;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cloudflare D1 implementation of BookmarkRepository.
 */
public class D1BookmarkRepository implements BookmarkRepository {
    private static final Logger LOG = LoggerFactory.getLogger(D1BookmarkRepository.class);

    private final D1Client d1Client;
    private final List<Bookmark> localFallback = new CopyOnWriteArrayList<>();

    public D1BookmarkRepository(D1Client d1Client) {
        this.d1Client = d1Client;
    }

    @Override
    public void addBookmark(Bookmark bookmark) {
        if (bookmark == null || bookmark.getUrl() == null || bookmark.getUrl().isBlank()) {
            return;
        }

        String userId = (bookmark.getUserId() != null && !bookmark.getUserId().isBlank())
                ? bookmark.getUserId()
                : com.cachebrowse.auth.UserSession.ANONYMOUS_USER_ID;

        // Avoid duplicate in local
        localFallback.removeIf(b -> (b.getUserId() == null || userId.equals(b.getUserId()))
                && bookmark.getUrl().equalsIgnoreCase(b.getUrl()));
        localFallback.add(bookmark);

        if (d1Client != null && d1Client.isConfigured()) {
            try {
                String sql = "INSERT OR REPLACE INTO bookmarks (id, user_id, url, title, created_at) VALUES (?, ?, ?, ?, ?)";
                List<Object> params = List.of(
                        bookmark.getId() != null ? bookmark.getId() : java.util.UUID.randomUUID().toString(),
                        userId,
                        bookmark.getUrl(),
                        bookmark.getTitle() != null ? bookmark.getTitle() : "",
                        bookmark.getCreatedAt() != null ? bookmark.getCreatedAt().toString() : Instant.now().toString()
                );
                d1Client.executeChecked(sql, params);
            } catch (Exception e) {
                LOG.warn("[Bookmark] Failed to persist bookmark to D1 for user {}", userId);
                throw new IllegalStateException("Bookmark is only available in temporary memory because D1 persistence failed", e);
            }
        }
    }

    @Override
    public void updateBookmark(String userId, String id, String url, String title) {
        if (id == null || url == null || url.isBlank()) return;
        String effectiveUserId = (userId != null && !userId.isBlank())
                ? userId : com.cachebrowse.auth.UserSession.ANONYMOUS_USER_ID;
        boolean found = false;
        for (Bookmark bookmark : localFallback) {
            if (id.equals(bookmark.getId()) && effectiveUserId.equals(bookmark.getUserId())) {
                bookmark.setUrl(url);
                bookmark.setTitle(title);
                found = true;
                break;
            }
        }
        if (d1Client != null && d1Client.isConfigured()) {
            try {
                d1Client.execute("UPDATE bookmarks SET url = ?, title = ? WHERE id = ? AND user_id = ?",
                        List.of(url, title == null ? "" : title, id, effectiveUserId));
                return;
            } catch (Exception e) {
                LOG.warn("Failed to update bookmark in D1 for user {}: {}", effectiveUserId, e.getMessage());
                throw new IllegalStateException("Bookmark could not be persisted to D1", e);
            }
        }
        if (!found) throw new IllegalArgumentException("Bookmark not found");
    }

    @Override
    public void removeBookmark(String userId, String id) {
        if (id == null) return;
        String effectiveUserId = (userId != null && !userId.isBlank()) ? userId : com.cachebrowse.auth.UserSession.ANONYMOUS_USER_ID;
        localFallback.removeIf(b -> id.equals(b.getId()) && effectiveUserId.equals(b.getUserId()));

        if (d1Client != null && d1Client.isConfigured()) {
            try {
                String sql = "DELETE FROM bookmarks WHERE user_id = ? AND id = ?";
                d1Client.executeChecked(sql, List.of(effectiveUserId, id));
            } catch (Exception e) {
                LOG.warn("Failed to delete bookmark from D1");
                throw new IllegalStateException("Bookmark deletion was not persisted", e);
            }
        }
    }

    @Override
    public void removeBookmarkByUrl(String userId, String url) {
        if (url == null) return;
        String effectiveUserId = (userId != null && !userId.isBlank()) ? userId : com.cachebrowse.auth.UserSession.ANONYMOUS_USER_ID;
        localFallback.removeIf(b -> (b.getUserId() == null || b.getUserId().equals(effectiveUserId)) && url.equalsIgnoreCase(b.getUrl()));

        if (d1Client != null && d1Client.isConfigured()) {
            try {
                String sql = "DELETE FROM bookmarks WHERE user_id = ? AND url = ?";
                d1Client.executeChecked(sql, List.of(effectiveUserId, url));
            } catch (Exception e) {
                LOG.warn("Failed to delete bookmark by url from D1: {}", e.getMessage());
            }
        }
    }

    @Override
    public boolean isBookmarked(String userId, String url) {
        if (url == null || url.isBlank()) return false;
        String effectiveUserId = (userId != null && !userId.isBlank()) ? userId : com.cachebrowse.auth.UserSession.ANONYMOUS_USER_ID;

        if (d1Client != null && d1Client.isConfigured()) {
            try {
                String sql = "SELECT id FROM bookmarks WHERE user_id = ? AND url = ? LIMIT 1";
                List<Map<String, Object>> rows = d1Client.query(sql, List.of(effectiveUserId, url));
                if (!rows.isEmpty()) return true;
            } catch (Exception e) {
                LOG.debug("Error checking isBookmarked in D1: {}", e.getMessage());
            }
        }

        return localFallback.stream()
                .anyMatch(b -> (b.getUserId() == null || b.getUserId().equals(effectiveUserId)) && url.equalsIgnoreCase(b.getUrl()));
    }

    @Override
    public List<Bookmark> getBookmarks(String userId) {
        String effectiveUserId = (userId != null && !userId.isBlank()) ? userId : com.cachebrowse.auth.UserSession.ANONYMOUS_USER_ID;

        if (d1Client != null && d1Client.isConfigured()) {
            try {
                String sql = "SELECT id, user_id, url, title, created_at FROM bookmarks WHERE user_id = ? ORDER BY created_at DESC";
                List<Map<String, Object>> rows = d1Client.query(sql, List.of(effectiveUserId));
                if (!rows.isEmpty()) {
                    List<Bookmark> list = new ArrayList<>();
                    for (Map<String, Object> row : rows) {
                        String id = String.valueOf(row.get("id"));
                        String uid = String.valueOf(row.get("user_id"));
                        String url = String.valueOf(row.get("url"));
                        String title = row.get("title") != null ? String.valueOf(row.get("title")) : "";
                        Instant createdAt;
                        try {
                            createdAt = Instant.parse(String.valueOf(row.get("created_at")));
                        } catch (Exception ex) {
                            createdAt = Instant.now();
                        }
                        list.add(new Bookmark(id, uid, url, title, createdAt));
                    }
                    return list;
                }
            } catch (Exception e) {
                LOG.warn("Failed to query bookmarks from D1: {}", e.getMessage());
            }
        }

        List<Bookmark> list = new ArrayList<>();
        for (Bookmark b : localFallback) {
            if (b.getUserId() == null || b.getUserId().equals(effectiveUserId)) {
                list.add(b);
            }
        }
        return list;
    }

    @Override
    public void clearBookmarks(String userId) {
        String effectiveUserId = (userId != null && !userId.isBlank()) ? userId : com.cachebrowse.auth.UserSession.ANONYMOUS_USER_ID;
        localFallback.removeIf(b -> b.getUserId() == null || b.getUserId().equals(effectiveUserId));

        if (d1Client != null && d1Client.isConfigured()) {
            try {
                String sql = "DELETE FROM bookmarks WHERE user_id = ?";
                d1Client.executeChecked(sql, List.of(effectiveUserId));
            } catch (Exception e) {
                LOG.warn("Failed to clear bookmarks in D1: {}", e.getMessage());
            }
        }
    }
}
