package com.cachebrowse.service;

import com.cachebrowse.database.repository.BookmarkRepository;
import com.cachebrowse.database.repository.HistoryRepository;
import com.cachebrowse.model.Bookmark;
import com.cachebrowse.model.SearchResult;
import com.cachebrowse.model.HistoryEntry;
import com.cachebrowse.auth.UserSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service to search through bookmarks and history for a given query string.
 */
public class SearchService {
    private final BookmarkRepository bookmarkRepository;
    private final HistoryRepository historyRepository;
    private final UserSession userSession;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SearchService-Worker");
        t.setDaemon(true);
        return t;
    });

    public SearchService(BookmarkRepository bookmarkRepository,
                         HistoryRepository historyRepository,
                         UserSession userSession) {
        this.bookmarkRepository = bookmarkRepository;
        this.historyRepository = historyRepository;
        this.userSession = userSession;
    }

    public CompletableFuture<List<SearchResult>> searchAsync(String query) {
        return CompletableFuture.supplyAsync(() -> search(query), executor);
    }

    private List<SearchResult> search(String query) {
        String lower = query == null ? "" : query.toLowerCase(Locale.ROOT);
        List<SearchResult> results = new ArrayList<>();
        if (userSession == null) return results;
        String userId = userSession.getUserId();

        // Bookmarks
        if (bookmarkRepository != null) {
            for (Bookmark b : bookmarkRepository.getBookmarks(userId)) {
                if (b.getTitle().toLowerCase(Locale.ROOT).contains(lower)
                        || b.getUrl().toLowerCase(Locale.ROOT).contains(lower)) {
                    results.add(new SearchResult(SearchResult.Type.BOOKMARK, b.getUrl(), b.getTitle()));
                }
            }
        }

        // History
        if (historyRepository != null) {
            for (HistoryEntry h : historyRepository.getHistory(userId, Integer.MAX_VALUE)) {
                if (h.getTitle().toLowerCase(Locale.ROOT).contains(lower)
                        || h.getUrl().toLowerCase(Locale.ROOT).contains(lower)) {
                    results.add(new SearchResult(SearchResult.Type.HISTORY, h.getUrl(), h.getTitle()));
                }
            }
        }
        return results;
    }

    public void shutdown() {
        executor.shutdown();
    }
}