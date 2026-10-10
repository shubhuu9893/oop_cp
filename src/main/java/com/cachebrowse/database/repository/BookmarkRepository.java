package com.cachebrowse.database.repository;

import com.cachebrowse.model.Bookmark;
import java.util.List;

/**
 * Repository interface for managing bookmarks.
 */
public interface BookmarkRepository {
    void addBookmark(Bookmark bookmark);
    void updateBookmark(String userId, String id, String url, String title);
    void removeBookmark(String userId, String id);
    void removeBookmarkByUrl(String userId, String url);
    boolean isBookmarked(String userId, String url);
    List<Bookmark> getBookmarks(String userId);
    void clearBookmarks(String userId);
}
