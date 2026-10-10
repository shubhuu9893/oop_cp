package com.cachebrowse.database.repository;

import com.cachebrowse.statistics.CacheStatistics;

/**
 * Repository interface for persisting and loading browser usage statistics.
 */
public interface StatisticsRepository {
    void saveStatistics(String userId, CacheStatistics statistics);
    void loadStatistics(String userId, CacheStatistics statistics);
}
