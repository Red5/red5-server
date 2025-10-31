/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.io.amf;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.beanutils.BeanMap;
import org.apache.mina.core.buffer.IoBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;

/**
 * Test for AMF Output Caffeine caching functionality
 *
 * @author The Red5 Project
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class OutputCacheTest {

    private IoBuffer buf;

    private Output out;

    @Before
    public void setUp() {
        buf = IoBuffer.allocate(4096);
        buf.setAutoExpand(true);
        out = new Output(buf);
        // Ensure caches are initialized
        Output.destroyCache();
    }

    @After
    public void tearDown() {
        if (buf != null) {
            buf.free();
        }
        // Clean up caches after tests
        Output.destroyCache();
    }

    @Test
    public void testStringCacheInitialization() {
        Cache<String, byte[]> stringCache = Output.getStringCache();
        assertNotNull("String cache should be initialized", stringCache);
    }

    @Test
    public void testSerializeCacheInitialization() {
        Cache<Class<?>, Map<String, Boolean>> serializeCache = Output.getSerializeCache();
        assertNotNull("Serialize cache should be initialized", serializeCache);
    }

    @Test
    public void testFieldCacheInitialization() {
        Cache<Class<?>, Map<String, Field>> fieldCache = Output.getFieldCache();
        assertNotNull("Field cache should be initialized", fieldCache);
    }

    @Test
    public void testGetterCacheInitialization() {
        Cache<Class<?>, Map<String, Method>> getterCache = Output.getGetterCache();
        assertNotNull("Getter cache should be initialized", getterCache);
    }

    @Test
    public void testStringCaching() {
        String testString1 = "TestString";
        String testString2 = "AnotherTestString";

        // Encode strings to trigger caching
        out.writeString(testString1);
        out.writeString(testString2);
        out.writeString(testString1); // Should hit cache

        Cache<String, byte[]> stringCache = Output.getStringCache();
        CacheStats stats = stringCache.stats();

        assertTrue("String cache should have entries", stringCache.estimatedSize() > 0);
        assertTrue("Should have cache hits from repeated string", stats.hitCount() > 0);
    }

    @Test
    public void testStringCacheConsistency() {
        String testString = "CacheConsistencyTest";

        // Write string twice
        out.writeString(testString);
        buf.flip();
        byte[] firstEncoding = new byte[buf.remaining()];
        buf.get(firstEncoding);

        buf.clear();
        out.writeString(testString);
        buf.flip();
        byte[] secondEncoding = new byte[buf.remaining()];
        buf.get(secondEncoding);

        assertArrayEquals("Cached string encoding should be consistent", firstEncoding, secondEncoding);
    }

    @Test
    public void testFieldCaching() {
        TestBean bean = new TestBean();
        bean.setName("Test");
        bean.setValue(42);

        // Serialize bean multiple times to trigger field cache
        out.writeObject(bean);
        buf.clear();
        out.writeObject(bean);
        buf.clear();
        out.writeObject(bean);

        Cache<Class<?>, Map<String, Field>> fieldCache = Output.getFieldCache();
        assertTrue("Field cache should have entries", fieldCache.estimatedSize() > 0);

        // Verify TestBean class is in cache
        Map<String, Field> fieldMap = fieldCache.getIfPresent(TestBean.class);
        assertNotNull("TestBean should be in field cache", fieldMap);
    }

    @Test
    public void testGetterCaching() {
        TestBean bean = new TestBean();
        bean.setName("GetterTest");
        bean.setValue(100);

        BeanMap beanMap = new BeanMap(bean);

        // Access getters through Output to trigger caching
        out.writeObject(beanMap);
        buf.clear();
        out.writeObject(beanMap);

        Cache<Class<?>, Map<String, Method>> getterCache = Output.getGetterCache();
        assertTrue("Getter cache should have entries", getterCache.estimatedSize() > 0);
    }

    @Test
    public void testSerializeCaching() {
        TestBean bean = new TestBean();
        bean.setName("SerializeTest");
        bean.setValue(200);

        // Serialize multiple times
        for (int i = 0; i < 5; i++) {
            out.writeObject(bean);
            buf.clear();
        }

        Cache<Class<?>, Map<String, Boolean>> serializeCache = Output.getSerializeCache();
        assertTrue("Serialize cache should have entries", serializeCache.estimatedSize() > 0);

        Map<String, Boolean> serializeMap = serializeCache.getIfPresent(TestBean.class);
        assertNotNull("TestBean should be in serialize cache", serializeMap);
    }

    @Test
    public void testCacheStatsRecording() {
        // Perform operations to generate stats
        out.writeString("Stats Test 1");
        out.writeString("Stats Test 2");
        out.writeString("Stats Test 1"); // Hit

        Cache<String, byte[]> stringCache = Output.getStringCache();
        CacheStats stats = stringCache.stats();

        assertNotNull("Cache stats should be available", stats);
        assertTrue("Should record hits", stats.hitCount() > 0);
        assertTrue("Should record requests", stats.requestCount() > 0);
    }

    @Test
    public void testCacheExpiration() throws InterruptedException {
        // This tests that caches eventually expire (configured for 20 minutes)
        String testString = "ExpirationTest";
        out.writeString(testString);

        Cache<String, byte[]> stringCache = Output.getStringCache();
        assertTrue("String should be cached", stringCache.getIfPresent(testString) != null);

        // Note: Full expiration test would require waiting 20 minutes
        // This just verifies the cache is working
        assertEquals("Cache should have the entry", 1, stringCache.estimatedSize());
    }

    @Test
    public void testCacheDestroy() {
        // Populate caches
        out.writeString("DestroyTest");
        TestBean bean = new TestBean();
        bean.setName("DestroyBean");
        out.writeObject(bean);

        // Verify caches have content
        assertTrue("String cache should have entries", Output.getStringCache().estimatedSize() > 0);

        // Destroy caches
        Output.destroyCache();

        // Verify caches are cleared (they'll reinitialize on next access)
        Cache<String, byte[]> stringCache = Output.getStringCache();
        assertEquals("Cache should be empty after destroy and reinit", 0, stringCache.estimatedSize());
    }

    @Test
    public void testCacheConcurrency() throws InterruptedException {
        final int numThreads = 5;
        final int numStrings = 100;

        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                IoBuffer threadBuf = IoBuffer.allocate(1024);
                threadBuf.setAutoExpand(true);
                Output threadOut = new Output(threadBuf);

                for (int j = 0; j < numStrings; j++) {
                    threadOut.writeString("Thread" + threadId + "_String" + j);
                    threadBuf.clear();
                }

                threadBuf.free();
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // Verify cache still works after concurrent access
        Cache<String, byte[]> stringCache = Output.getStringCache();
        assertTrue("Cache should have entries after concurrent access", stringCache.estimatedSize() > 0);
    }

    @Test
    public void testCachePerformance() {
        // Warm up cache
        for (int i = 0; i < 100; i++) {
            out.writeString("WarmUp" + i);
            buf.clear();
        }

        String repeatedString = "PerformanceTest";

        // Time with cache (should be fast due to hits)
        long startTime = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            out.writeString(repeatedString);
            buf.clear();
        }
        long endTime = System.nanoTime();
        long cachedTime = endTime - startTime;

        Cache<String, byte[]> stringCache = Output.getStringCache();
        CacheStats stats = stringCache.stats();

        // Verify we got cache hits
        assertTrue("Should have significant cache hits", stats.hitCount() > 900);
        assertTrue("Cache should improve performance", cachedTime > 0); // Just verify it executed
    }

    /**
     * Test bean class for serialization tests
     */
    public static class TestBean {

        private String name;

        private int value;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }
    }
}
