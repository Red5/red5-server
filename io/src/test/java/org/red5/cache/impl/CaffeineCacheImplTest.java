/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.cache.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.red5.cache.ICacheable;

import com.github.benmanes.caffeine.cache.stats.CacheStats;

/**
 * Test for CaffeineCacheImpl
 *
 * @author The Red5 Project
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class CaffeineCacheImplTest {

    private CaffeineCacheImpl cache;

    @Before
    public void setUp() {
        cache = new CaffeineCacheImpl();
        cache.setMaxEntries(100);
        cache.setExpireAfterAccessSeconds(60);
        cache.setRecordStats(true);
        cache.init();
    }

    @After
    public void tearDown() {
        if (cache != null) {
            cache.destroy();
        }
    }

    @Test
    public void testPutAndGet() {
        String key = "testKey";
        String value = "testValue";
        CacheableImpl cacheable = new CacheableImpl(value);
        cacheable.setName(key);

        cache.put(key, cacheable);
        ICacheable retrieved = cache.get(key);

        assertNotNull("Retrieved object should not be null", retrieved);
        assertEquals("Retrieved object should match", key, retrieved.getName());
    }

    @Test
    public void testGetNonExistent() {
        ICacheable retrieved = cache.get("nonExistentKey");
        assertNull("Non-existent key should return null", retrieved);
    }

    @Test
    public void testPutNonCacheable() {
        String key = "stringKey";
        String value = "stringValue";

        cache.put(key, value);
        ICacheable retrieved = cache.get(key);

        assertNotNull("Retrieved object should not be null", retrieved);
        assertEquals("Retrieved name should match key", key, retrieved.getName());
    }

    @Test
    public void testOffer() {
        String key = "offerKey";
        String value = "offerValue";

        boolean offered = cache.offer(key, value);
        assertTrue("First offer should succeed", offered);

        boolean offeredAgain = cache.offer(key, value);
        assertTrue("Second offer should return true (key exists)", offeredAgain);
    }

    @Test
    public void testRemove() {
        String key = "removeKey";
        String value = "removeValue";
        CacheableImpl cacheable = new CacheableImpl(value);
        cacheable.setName(key);

        cache.put(key, cacheable);
        assertNotNull("Object should exist", cache.get(key));

        boolean removed = cache.remove(key);
        assertTrue("Remove should return true", removed);

        assertNull("Object should be removed", cache.get(key));
    }

    @Test
    public void testRemoveByCacheable() {
        String key = "removeKey2";
        String value = "removeValue2";
        CacheableImpl cacheable = new CacheableImpl(value);
        cacheable.setName(key);

        cache.put(key, cacheable);
        assertNotNull("Object should exist", cache.get(key));

        boolean removed = cache.remove(cacheable);
        assertTrue("Remove should return true", removed);

        assertNull("Object should be removed", cache.get(key));
    }

    @Test
    public void testMaxEntries() {
        cache.setMaxEntries(5);
        cache.destroy();
        cache.init();

        // Add more entries than the max
        for (int i = 0; i < 10; i++) {
            cache.put("key" + i, new CacheableImpl("value" + i));
        }

        // Cache should have evicted some entries
        long size = CaffeineCacheImpl.getCacheSize();
        assertTrue("Cache size should be <= max entries", size <= 5);
    }

    @Test
    public void testCacheStats() {
        cache.put("statKey1", new CacheableImpl("statValue1"));
        cache.put("statKey2", new CacheableImpl("statValue2"));

        // Generate some hits
        cache.get("statKey1");
        cache.get("statKey1");

        // Generate a miss
        cache.get("nonExistent");

        CacheStats stats = CaffeineCacheImpl.getStats();
        assertNotNull("Stats should not be null", stats);
        assertTrue("Should have hits", stats.hitCount() > 0);
        assertTrue("Should have misses", stats.missCount() > 0);
    }

    @Test
    public void testGetCacheHitMiss() {
        cache.put("hitKey", new CacheableImpl("hitValue"));

        // Generate hits
        cache.get("hitKey");
        cache.get("hitKey");

        // Generate misses
        cache.get("missKey1");
        cache.get("missKey2");

        long hits = CaffeineCacheImpl.getCacheHit();
        long misses = CaffeineCacheImpl.getCacheMiss();

        assertTrue("Should have cache hits", hits >= 2);
        assertTrue("Should have cache misses", misses >= 2);
    }

    @Test
    public void testGetObjectNames() {
        cache.put("name1", new CacheableImpl("value1"));
        cache.put("name2", new CacheableImpl("value2"));
        cache.put("name3", new CacheableImpl("value3"));

        int count = 0;
        var iterator = cache.getObjectNames();
        while (iterator.hasNext()) {
            String name = iterator.next();
            assertNotNull("Name should not be null", name);
            count++;
        }

        assertEquals("Should have 3 names", 3, count);
    }

    @Test
    public void testConcurrentAccess() throws InterruptedException {
        final int numThreads = 10;
        final int numOperations = 100;

        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < numOperations; j++) {
                    String key = "thread" + threadId + "_key" + j;
                    cache.put(key, new CacheableImpl("value" + j));
                    cache.get(key);
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // Verify cache is still functional
        cache.put("finalKey", new CacheableImpl("finalValue"));
        assertNotNull("Cache should still work after concurrent access", cache.get("finalKey"));
    }

    @Test
    public void testExpiration() throws InterruptedException {
        // Create cache with short expiration for testing
        CaffeineCacheImpl expiringCache = new CaffeineCacheImpl();
        expiringCache.setMaxEntries(100);
        expiringCache.setExpireAfterAccessSeconds(1); // 1 second
        expiringCache.setRecordStats(true);
        expiringCache.init();

        try {
            expiringCache.put("expiringKey", new CacheableImpl("expiringValue"));
            assertNotNull("Key should exist initially", expiringCache.get("expiringKey"));

            // Wait for expiration
            Thread.sleep(2000);

            // Trigger cleanup
            ICacheable retrieved = expiringCache.get("expiringKey");
            assertNull("Key should have expired", retrieved);
        } finally {
            expiringCache.destroy();
        }
    }

    @Test
    public void testDestroy() {
        cache.put("destroyKey1", new CacheableImpl("value1"));
        cache.put("destroyKey2", new CacheableImpl("value2"));

        assertNotNull("Keys should exist", cache.get("destroyKey1"));

        cache.destroy();

        // After destroy, cache should be cleared
        assertEquals("Cache size should be 0 after destroy", 0, CaffeineCacheImpl.getCacheSize());
    }
}
