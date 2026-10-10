package com.cachebrowse.database.repository;

import com.cachebrowse.model.HistoryEntry;
import java.util.List;

/**
 * Repository interface for managing browsing history.
 */
public interface HistoryRepository {
    void addHistory(HistoryEntry entry);
    List<HistoryEntry> getHistory(String userId, int limit);
    void deleteHistory(String userId, String id);
    void clearHistory(String userId);
}
