package com.cachebrowse.database.repository;

import com.cachebrowse.auth.UserSession;
import com.cachebrowse.database.D1Client;
import com.cachebrowse.database.D1Config;
import com.cachebrowse.model.Bookmark;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class D1BookmarkRepositoryIsolationTest {
    @Test
    void fallbackBookmarksAreScopedToTheirOwnerIncludingLegacyOwnerlessWrites() {
        D1BookmarkRepository repository = new D1BookmarkRepository(
                new D1Client(new D1Config("", "", ""), null, Duration.ofMillis(10)));
        repository.addBookmark(new Bookmark("user-a", "https://a.example/", "A"));
        repository.addBookmark(new Bookmark("user-b", "https://b.example/", "B"));
        Bookmark legacy = new Bookmark(null, "https://legacy.example/", "Legacy");
        repository.addBookmark(legacy);

        assertEquals(List.of("https://a.example/"), repository.getBookmarks("user-a").stream().map(Bookmark::getUrl).toList());
        assertEquals(List.of("https://b.example/"), repository.getBookmarks("user-b").stream().map(Bookmark::getUrl).toList());
        assertTrue(repository.getBookmarks("user-c").isEmpty());
        assertTrue(repository.isBookmarked(UserSession.ANONYMOUS_USER_ID, "https://legacy.example/"));
        assertFalse(repository.isBookmarked("user-a", "https://legacy.example/"));
        assertEquals(UserSession.ANONYMOUS_USER_ID, legacy.getUserId());
    }
}
