package com.cachebrowse.database.repository;

import com.cachebrowse.database.D1Client;
import com.cachebrowse.statistics.CacheStatistics;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cloudflare D1 implementation of StatisticsRepository.
 */
public class D1StatisticsRepository implements StatisticsRepository {
    private static final Logger LOG = LoggerFactory.getLogger(D1StatisticsRepository.class);

    private final D1Client d1Client;

    public D1StatisticsRepository(D1Client d1Client) {
        this.d1Client = d1Client;
    }

    @Override
    public void saveStatistics(String userId, CacheStatistics statistics) {
        if (statistics == null) return;
        String effectiveUserId = (userId != null && !userId.isBlank())
                ? userId
                : com.cachebrowse.auth.UserSession.ANONYMOUS_USER_ID;

        if (d1Client != null && d1Client.isConfigured()) {
            try {
                String sql = "INSERT OR REPLACE INTO statistics " +
                        "(user_id, total_requests, cache_hits, cache_misses, ads_blocked, trackers_blocked, network_requests, avg_response_time, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                List<Object> params = List.of(
                        effectiveUserId,
                        statistics.getTotalRequests(),
                        statistics.getCacheHits(),
                        statistics.getCacheMisses(),
                        statistics.getAdsBlocked(),
                        statistics.getTrackersBlocked(),
                        statistics.getNetworkRequests(),
                        statistics.getAverageResponseTime(),
                        Instant.now().toString()
                );
                d1Client.executeChecked(sql, params);
            } catch (Exception e) {
                LOG.warn("[Statistics] Failed to persist statistics to D1 for user {}: {}", effectiveUserId, e.getMessage());
            }
        }
    }

    @Override
    public void loadStatistics(String userId, CacheStatistics statistics) {
        if (statistics == null) return;
        String effectiveUserId = (userId != null && !userId.isBlank()) ? userId : com.cachebrowse.auth.UserSession.ANONYMOUS_USER_ID;

        if (d1Client != null && d1Client.isConfigured()) {
            try {
                String sql = "SELECT total_requests, cache_hits, cache_misses, ads_blocked, trackers_blocked, network_requests, avg_response_time " +
                        "FROM statistics WHERE user_id = ? LIMIT 1";
                List<Map<String, Object>> rows = d1Client.query(sql, List.of(effectiveUserId));
                if (!rows.isEmpty()) {
                    Map<String, Object> row = rows.get(0);
                    if (row.get("total_requests") != null) statistics.setTotalRequests(((Number) row.get("total_requests")).longValue());
                    if (row.get("cache_hits") != null) statistics.setCacheHits(((Number) row.get("cache_hits")).longValue());
                    if (row.get("cache_misses") != null) statistics.setCacheMisses(((Number) row.get("cache_misses")).longValue());
                    if (row.get("ads_blocked") != null) statistics.setAdsBlocked(((Number) row.get("ads_blocked")).longValue());
                    if (row.get("trackers_blocked") != null) statistics.setTrackersBlocked(((Number) row.get("trackers_blocked")).longValue());
                    if (row.get("network_requests") != null) statistics.setNetworkRequests(((Number) row.get("network_requests")).longValue());
                    LOG.info("Loaded statistics from D1 for user {}: {}", effectiveUserId, row);
                }
            } catch (Exception e) {
                LOG.warn("Failed to load statistics from D1: {}", e.getMessage());
            }
        }
    }
}
