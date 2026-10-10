package com.cachebrowse.cache;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LRUCache}.
 */
public class LRUCacheTest {

    @Test
    @DisplayName("Basic put/get and eviction behavior")
    void testEviction() {
        int capacity = 2;
        LRUCache cache = new LRUCache(capacity);
        byte[] body1 = "body1".getBytes();
        byte[] body2 = "body2".getBytes();
        CacheEntry e1 = new CacheEntry("key1", body1, 60_000L, "text/plain");
        CacheEntry e2 = new CacheEntry("key2", body2, 60_000L, "text/plain");
        cache.put(e1.getKey(), e1);
        cache.put(e2.getKey(), e2);

        assertEquals(2, cache.size());
        assertArrayEquals(body1, cache.get("key1").getResponseBody());
        assertArrayEquals(body2, cache.get("key2").getResponseBody());

        // Adding a third entry should evict the least recently used (key1)
        byte[] body3 = "body3".getBytes();
        CacheEntry e3 = new CacheEntry("key3", body3, 60_000L, "text/plain");
        cache.put(e3.getKey(), e3);
        assertEquals(2, cache.size());
        assertNull(cache.get("key1"));
        assertNotNull(cache.get("key2"));
        assertNotNull(cache.get("key3"));
    }

    @Test
    @DisplayName("Thread‑safety under concurrent access")
    void testConcurrentAccess() throws InterruptedException {
        int capacity = 5;
        LRUCache cache = new LRUCache(capacity);
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(20);

        for (int i = 0; i < 20; i++) {
            final int idx = i;
            executor.submit(() -> {
            String key = "k" + (idx % capacity);
            byte[] body = ("value" + idx).getBytes();
            CacheEntry entry = new CacheEntry(key, body, 60_000L, "text/plain");
                cache.put(entry.getKey(), entry);
                cache.get(entry.getKey());
                latch.countDown();
            });
        }

        latch.await();
        executor.shutdownNow();
        // After all operations, size should not exceed capacity
        assertTrue(cache.size() <= capacity);
    }
}