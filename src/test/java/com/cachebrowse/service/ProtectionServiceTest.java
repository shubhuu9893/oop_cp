package com.cachebrowse.service;

import com.cachebrowse.adblock.AdBlocker;
import com.cachebrowse.adblock.TrackerBlocker;
import com.cachebrowse.statistics.CacheStatistics;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class ProtectionServiceTest {
    @TempDir Path temporaryDirectory;

    @Test
    void allowlistRulesMatchSubdomainsAndPersist() {
        Path file = temporaryDirectory.resolve("allowlist.txt");
        ProtectionService service = new ProtectionService(new AdBlocker(), new TrackerBlocker(),
                new CacheStatistics(), file);
        service.addAllowlistRule("example.com");
        assertTrue(service.isAllowed("https://shop.example.com/page"));
        assertThrows(IllegalArgumentException.class, () -> service.addAllowlistRule("https://other.example"));

        ProtectionService restored = new ProtectionService(new AdBlocker(), new TrackerBlocker(),
                new CacheStatistics(), file);
        assertTrue(restored.isAllowed("https://example.com"));
    }

    @Test
    void onlyExplicitChromiumBlockEventsChangeProtectionCounters() {
        CacheStatistics stats = new CacheStatistics();
        ProtectionService service = new ProtectionService(new AdBlocker(), new TrackerBlocker(), stats,
                temporaryDirectory.resolve("allowlist.txt"));

        assertEquals(0, stats.getBlockedRequests());
        service.recordBlockedRequest("ad");
        service.recordBlockedRequest("tracker");
        assertEquals(1, stats.getAdsBlocked());
        assertEquals(1, stats.getTrackersBlocked());
        assertEquals(2, stats.getBlockedRequests());
        assertThrows(IllegalArgumentException.class, () -> service.recordBlockedRequest("other"));
    }
}
