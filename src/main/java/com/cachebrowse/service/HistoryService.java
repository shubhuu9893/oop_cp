package com.cachebrowse.service;

import com.cachebrowse.database.repository.HistoryRepository;
import com.cachebrowse.model.HistoryEntry;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service managing browsing history with asynchronous persistence.
 */
public class HistoryService {
    private final HistoryRepository historyRepository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "HistoryService-Worker");
        t.setDaemon(true);
        return t;
    });

    public HistoryService(HistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    public void addHistory(String userId, String url, String title) {
        if (url == null || url.isBlank()) return;
        HistoryEntry entry = new HistoryEntry(userId, url, title);
        await(executor.submit(() -> {
            if (historyRepository != null) {
                historyRepository.addHistory(entry);
            }
        }));
    }

    public CompletableFuture<List<HistoryEntry>> getHistoryAsync(String userId, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            if (historyRepository != null) {
                return historyRepository.getHistory(userId, limit);
            }
            return Collections.emptyList();
        }, executor);
    }

    public List<HistoryEntry> getHistory(String userId, int limit) {
        if (historyRepository != null) {
            return historyRepository.getHistory(userId, limit);
        }
        return Collections.emptyList();
    }

    public void deleteHistory(String userId, String id) {
        await(executor.submit(() -> {
            if (historyRepository != null) {
                historyRepository.deleteHistory(userId, id);
            }
        }));
    }

    public void clearHistory(String userId) {
        await(executor.submit(() -> {
            if (historyRepository != null) {
                historyRepository.clearHistory(userId);
            }
        }));
    }

    private static void await(java.util.concurrent.Future<?> operation) {
        try { operation.get(); }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("History operation was interrupted", e);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("History operation failed", cause);
        }
    }

    public void shutdown() {
        executor.shutdown();
    }
}
