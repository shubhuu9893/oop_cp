package com.cachebrowse.database.repository;

import com.cachebrowse.database.D1Client;
import com.cachebrowse.model.BrowserSettings;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cloudflare D1 implementation of SettingsRepository.
 */
public class D1SettingsRepository implements SettingsRepository {
    private static final Logger LOG = LoggerFactory.getLogger(D1SettingsRepository.class);

    private final D1Client d1Client;
    private final Map<String, BrowserSettings> localCache = new ConcurrentHashMap<>();

    public D1SettingsRepository(D1Client d1Client) {
        this.d1Client = d1Client;
    }

    @Override
    public void saveSettings(BrowserSettings settings) {
        if (settings == null) return;
        String userId = (settings.getUserId() != null && !settings.getUserId().isBlank())
                ? settings.getUserId()
                : com.cachebrowse.auth.UserSession.ANONYMOUS_USER_ID;
        localCache.put(userId, settings);

        if (d1Client != null && d1Client.isConfigured()) {
            try {
                String sql = "INSERT OR REPLACE INTO browser_settings " +
                        "(user_id, cache_enabled, cache_capacity, cache_ttl, ad_block_enabled, tracker_block_enabled, home_page, default_search_engine, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                List<Object> params = List.of(
                        userId,
                        settings.isCacheEnabled() ? 1 : 0,
                        settings.getCacheCapacity(),
                        settings.getCacheTtl(),
                        settings.isAdBlockingEnabled() ? 1 : 0,
                        settings.isTrackerBlockingEnabled() ? 1 : 0,
                        settings.getHomePage(),
                        settings.getDefaultSearchEngine(),
                        Instant.now().toString()
                );
                d1Client.executeChecked(sql, params);
            } catch (Exception e) {
                LOG.warn("[Settings] Failed to persist settings to D1 for user {}", userId);
                throw new IllegalStateException("Settings were not persisted to D1", e);
            }
        }
    }

    @Override
    public Optional<BrowserSettings> getSettings(String userId) {
        String effectiveUserId = (userId != null && !userId.isBlank()) ? userId : com.cachebrowse.auth.UserSession.ANONYMOUS_USER_ID;

        if (d1Client != null && d1Client.isConfigured()) {
            try {
                String sql = "SELECT user_id, cache_enabled, cache_capacity, cache_ttl, ad_block_enabled, tracker_block_enabled, home_page, default_search_engine " +
                        "FROM browser_settings WHERE user_id = ? LIMIT 1";
                List<Map<String, Object>> rows = d1Client.query(sql, List.of(effectiveUserId));
                if (!rows.isEmpty()) {
                    Map<String, Object> row = rows.get(0);
                    BrowserSettings s = new BrowserSettings(effectiveUserId);
                    if (row.get("cache_enabled") != null) {
                        s.setCacheEnabled(((Number) row.get("cache_enabled")).intValue() == 1);
                    }
                    if (row.get("cache_capacity") != null) {
                        s.setCacheCapacity(((Number) row.get("cache_capacity")).intValue());
                    }
                    if (row.get("cache_ttl") != null) {
                        s.setCacheTtl(((Number) row.get("cache_ttl")).longValue());
                    }
                    if (row.get("ad_block_enabled") != null) {
                        s.setAdBlockingEnabled(((Number) row.get("ad_block_enabled")).intValue() == 1);
                    }
                    if (row.get("tracker_block_enabled") != null) {
                        s.setTrackerBlockingEnabled(((Number) row.get("tracker_block_enabled")).intValue() == 1);
                    }
                    if (row.get("home_page") != null) {
                        s.setHomePage(String.valueOf(row.get("home_page")));
                    }
                    if (row.get("default_search_engine") != null) {
                        s.setDefaultSearchEngine(String.valueOf(row.get("default_search_engine")));
                    }
                    localCache.put(effectiveUserId, s);
                    return Optional.of(s);
                }
            } catch (Exception e) {
                LOG.warn("Failed to query settings from D1: {}", e.getMessage());
            }
        }

        return Optional.ofNullable(localCache.get(effectiveUserId));
    }
}
