package com.cachebrowse.cache;

import com.cachebrowse.statistics.CacheStatistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Unit tests for the new cache implementation.
 */
public class CacheManagerTest {

    private CacheStatistics stats;
    private CacheManager manager;

    @BeforeEach
    void setup() {
        stats = new CacheStatistics();
        // Use a capacity of 3 for most tests.
        manager = new CacheManager(3, stats);
    }

    @Test
    @DisplayName("Put and Get – basic hit")
    void testPutGet() {
        String key = "http://example.com/page1";
        byte[] body = "Hello".getBytes();
        CacheEntry entry = new CacheEntry(key, body, 60000, "text/plain");
        manager.put(key, entry);

        CacheEntry result = manager.get(key);
        assertNotNull(result, "Cache should return the stored entry");
        assertArrayEquals(body, result.getResponseBody());
        // Hit recorded
        assertEquals(1, stats.getCacheHits(), "Hit count should be 1");
    }

    @Test
    @DisplayName("Cache miss when key absent")
    void testMiss() {
        CacheEntry result = manager.get("non-existent-key");
        assertNull(result);
        assertEquals(1, stats.getCacheMisses(), "Miss count should be 1");
    }

    @Test
    @DisplayName("LRU eviction on capacity exceed")
    void testEviction() {
        for (int i = 0; i < 4; i++) {
            String key = "key" + i;
            byte[] body = ("body" + i).getBytes();
            manager.put(key, new CacheEntry(key, body, 60000, "text/plain"));
        }
        // Capacity is 3; first entry should have been evicted.
        assertNull(manager.get("key0"), "First key should be evicted");
        assertEquals(1, manager.getEvictions(), "Eviction count should be 1");
    }

    @Test
    @DisplayName("TTL expiration removes entry")
    void testTtlExpiration() throws InterruptedException {
        String key = "temp";
        byte[] body = "tmp".getBytes();
        // TTL 100 ms
        manager.put(key, new CacheEntry(key, body, 100, "text/plain"));
        Thread.sleep(200);
        assertNull(manager.get(key), "Expired key should return null");
        assertEquals(1, stats.getExpiredEntries(), "Expired entries count should be 1");
    }

    @Test
    @DisplayName("Thread safety under concurrent access")
    void testThreadSafety() throws InterruptedException {
        int threadCount = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount * 50);
        for (int t = 0; t < threadCount; t++) {
            final int tid = t;
            pool.submit(() -> {
                for (int i = 0; i < 50; i++) {
                    String key = "t" + tid + "-k" + i;
                    byte[] body = ("data" + i).getBytes();
                    manager.put(key, new CacheEntry(key, body, 60000, "text/plain"));
                    if (i % 2 == 0) {
                        manager.get(key);
                    }
                }
                latch.countDown();
            });
        }
        latch.await();
        pool.shutdownNow();
        // After all operations, cache size should not exceed capacity
        assertTrue(manager.size() <= manager.capacity(), "Cache size within capacity");
    }
}
