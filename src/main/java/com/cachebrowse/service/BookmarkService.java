package com.cachebrowse.service;

import com.cachebrowse.database.repository.BookmarkRepository;
import com.cachebrowse.model.Bookmark;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service managing user bookmarks.
 */
public class BookmarkService {
    private final BookmarkRepository bookmarkRepository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BookmarkService-Worker");
        t.setDaemon(true);
        return t;
    });

    public BookmarkService(BookmarkRepository bookmarkRepository) {
        this.bookmarkRepository = bookmarkRepository;
    }

    public void addBookmark(String userId, String url, String title) {
        if (url == null || url.isBlank()) return;
        Bookmark bookmark = new Bookmark(userId, url, title);
        await(executor.submit(() -> {
            if (bookmarkRepository != null) {
                bookmarkRepository.addBookmark(bookmark);
            }
        }));
    }

    public void updateBookmark(String userId, String id, String url, String title) {
        if (bookmarkRepository == null) throw new IllegalStateException("Bookmark storage is unavailable");
        try {
            executor.submit(() -> bookmarkRepository.updateBookmark(userId, id, url, title)).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Bookmark update was interrupted", e);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Bookmark update failed", cause);
        }
    }

    public void removeBookmark(String userId, String id) {
        await(executor.submit(() -> {
            if (bookmarkRepository != null) {
                bookmarkRepository.removeBookmark(userId, id);
            }
        }));
    }

    public void removeBookmarkByUrl(String userId, String url) {
        executor.submit(() -> {
            if (bookmarkRepository != null) {
                bookmarkRepository.removeBookmarkByUrl(userId, url);
            }
        });
    }

    public boolean isBookmarked(String userId, String url) {
        if (bookmarkRepository != null) {
            return bookmarkRepository.isBookmarked(userId, url);
        }
        return false;
    }

    public CompletableFuture<List<Bookmark>> getBookmarksAsync(String userId) {
        return CompletableFuture.supplyAsync(() -> {
            if (bookmarkRepository != null) {
                return bookmarkRepository.getBookmarks(userId);
            }
            return Collections.emptyList();
        }, executor);
    }

    public List<Bookmark> getBookmarks(String userId) {
        if (bookmarkRepository != null) {
            return bookmarkRepository.getBookmarks(userId);
        }
        return Collections.emptyList();
    }

    public void clearBookmarks(String userId) {
        await(executor.submit(() -> {
            if (bookmarkRepository != null) {
                bookmarkRepository.clearBookmarks(userId);
            }
        }));
    }

    private static void await(java.util.concurrent.Future<?> operation) {
        try {
            operation.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Bookmark operation was interrupted", e);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Bookmark operation failed", cause);
        }
    }

    public void shutdown() {
        executor.shutdown();
    }
}
