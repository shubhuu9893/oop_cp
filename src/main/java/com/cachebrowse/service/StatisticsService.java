package com.cachebrowse.service;

import com.cachebrowse.database.repository.StatisticsRepository;
import com.cachebrowse.statistics.CacheStatistics;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;

/**
 * Service managing real-time and persisted statistics.
 */
public class StatisticsService {
    private final CacheStatistics statistics;
    private final StatisticsRepository statisticsRepository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "StatisticsService-Worker");
        t.setDaemon(true);
        return t;
    });

    public StatisticsService(CacheStatistics statistics, StatisticsRepository statisticsRepository) {
        this.statistics = statistics != null ? statistics : new CacheStatistics();
        this.statisticsRepository = statisticsRepository;
    }

    public CacheStatistics getStatistics() {
        return statistics;
    }

    public void saveStatistics(String userId) {
        if (statisticsRepository == null) return;
        executor.submit(() -> statisticsRepository.saveStatistics(userId, statistics));
    }

    public CompletableFuture<Void> loadStatistics(String userId) {
        if (statisticsRepository == null) return CompletableFuture.completedFuture(null);
        return CompletableFuture.runAsync(() -> statisticsRepository.loadStatistics(userId, statistics), executor);
    }

    public void reset() {
        statistics.reset();
    }

    public void shutdown() {
        executor.shutdown();
    }
}
