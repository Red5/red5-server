/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.cache.impl;

import java.lang.ref.SoftReference;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import org.red5.cache.ICacheStore;
import org.red5.cache.ICacheable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;

/**
 * Provides an implementation of an object cache using Caffeine.
 *
 * @see <a href="https://github.com/ben-manes/caffeine">Caffeine homepage</a>
 * @author The Red5 Project
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class CaffeineCacheImpl implements ICacheStore, ApplicationContextAware {

    /** Constant <code>log</code> */
    protected static Logger log = LoggerFactory.getLogger(CaffeineCacheImpl.class);

    private static Cache<String, ICacheable> cache;

    private static ApplicationContext applicationContext;

    // Cache configuration parameters
    private int maxEntries = 1000;

    private long expireAfterAccessSeconds = 1200; // 20 minutes default

    private long expireAfterWriteSeconds = -1; // disabled by default

    private boolean recordStats = true;

    /** {@inheritDoc} */
    @SuppressWarnings("null")
    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        CaffeineCacheImpl.applicationContext = context;
    }

    /**
     * Getter for property 'applicationContext'.
     *
     * @return Value for property 'applicationContext'.
     */
    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    /**
     * <p>init.</p>
     */
    public void init() {
        log.info("Initializing Caffeine cache with maxEntries={}, expireAfterAccessSeconds={}", maxEntries, expireAfterAccessSeconds);
        try {
            // Build the cache with configuration
            Caffeine<Object, Object> builder = Caffeine.newBuilder().maximumSize(maxEntries);

            // Add expiration policies if configured
            if (expireAfterAccessSeconds > 0) {
                builder.expireAfterAccess(expireAfterAccessSeconds, TimeUnit.SECONDS);
            }
            if (expireAfterWriteSeconds > 0) {
                builder.expireAfterWrite(expireAfterWriteSeconds, TimeUnit.SECONDS);
            }

            // Enable statistics if requested
            if (recordStats) {
                builder.recordStats();
            }

            cache = builder.build();
            log.info("Caffeine cache initialized successfully");
        } catch (Exception e) {
            log.warn("Error on cache init", e);
        }
        if (log.isDebugEnabled()) {
            log.debug("Cache is null? {}", (null == cache));
        }
    }

    /** {@inheritDoc} */
    @Override
    public ICacheable get(String name) {
        ICacheable ic = null;
        try {
            ic = cache.getIfPresent(name);
        } catch (Exception e) {
            log.warn("Error getting cached item: {}", name, e);
        }
        return ic;
    }

    /** {@inheritDoc} */
    @Override
    public void put(String name, Object obj) {
        if (obj instanceof ICacheable) {
            cache.put(name, (ICacheable) obj);
        } else {
            cache.put(name, new CacheableImpl(obj));
        }
    }

    /** {@inheritDoc} */
    @Override
    public Iterator<String> getObjectNames() {
        return cache.asMap().keySet().iterator();
    }

    /** {@inheritDoc} */
    @Override
    public Iterator<SoftReference<? extends ICacheable>> getObjects() {
        // Caffeine doesn't use SoftReferences by default, but we can wrap values
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public boolean offer(String name, Object obj) {
        boolean result = false;
        try {
            result = cache.asMap().containsKey(name);
            // Put an object into the cache if not already present
            if (!result) {
                put(name, obj);
            }
            // check again
            result = cache.asMap().containsKey(name);
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.warn("Name: {} Object: {}", name, obj.getClass().getName(), e);
            }
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public boolean remove(ICacheable obj) {
        return remove(obj.getName());
    }

    /** {@inheritDoc} */
    @Override
    public boolean remove(String name) {
        cache.invalidate(name);
        return !cache.asMap().containsKey(name);
    }

    /** {@inheritDoc} */
    @Override
    public void setMaxEntries(int capacity) {
        if (log.isDebugEnabled()) {
            log.debug("Setting max entries for this cache to {}", capacity);
        }
        this.maxEntries = capacity;
    }

    /**
     * Getter for property 'expireAfterAccessSeconds'.
     *
     * @return Value for property 'expireAfterAccessSeconds'.
     */
    public long getExpireAfterAccessSeconds() {
        return expireAfterAccessSeconds;
    }

    /**
     * Setter for property 'expireAfterAccessSeconds'.
     *
     * @param expireAfterAccessSeconds
     *            Value to set for property 'expireAfterAccessSeconds'.
     */
    public void setExpireAfterAccessSeconds(long expireAfterAccessSeconds) {
        this.expireAfterAccessSeconds = expireAfterAccessSeconds;
    }

    /**
     * Getter for property 'expireAfterWriteSeconds'.
     *
     * @return Value for property 'expireAfterWriteSeconds'.
     */
    public long getExpireAfterWriteSeconds() {
        return expireAfterWriteSeconds;
    }

    /**
     * Setter for property 'expireAfterWriteSeconds'.
     *
     * @param expireAfterWriteSeconds
     *            Value to set for property 'expireAfterWriteSeconds'.
     */
    public void setExpireAfterWriteSeconds(long expireAfterWriteSeconds) {
        this.expireAfterWriteSeconds = expireAfterWriteSeconds;
    }

    /**
     * Getter for property 'recordStats'.
     *
     * @return Value for property 'recordStats'.
     */
    public boolean isRecordStats() {
        return recordStats;
    }

    /**
     * Setter for property 'recordStats'.
     *
     * @param recordStats
     *            Value to set for property 'recordStats'.
     */
    public void setRecordStats(boolean recordStats) {
        this.recordStats = recordStats;
    }

    /**
     * Getter for property 'cacheHit'.
     *
     * @return Value for property 'cacheHit'.
     */
    public static long getCacheHit() {
        if (cache != null) {
            CacheStats stats = cache.stats();
            return stats.hitCount();
        }
        return 0;
    }

    /**
     * Getter for property 'cacheMiss'.
     *
     * @return Value for property 'cacheMiss'.
     */
    public static long getCacheMiss() {
        if (cache != null) {
            CacheStats stats = cache.stats();
            return stats.missCount();
        }
        return 0;
    }

    /**
     * Getter for property 'cacheSize'.
     *
     * @return Current cache size.
     */
    public static long getCacheSize() {
        if (cache != null) {
            return cache.estimatedSize();
        }
        return 0;
    }

    /**
     * Get cache statistics.
     *
     * @return CacheStats object with detailed statistics.
     */
    public static CacheStats getStats() {
        if (cache != null) {
            return cache.stats();
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public void destroy() {
        // Clean up the cache
        if (cache != null) {
            try {
                cache.invalidateAll();
                cache.cleanUp();
                log.info("Caffeine cache destroyed");
            } catch (Exception e) {
                log.warn("Error on cache shutdown", e);
            }
        }
    }
}
