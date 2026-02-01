# Red5 Server Cache Usage Examples

This document provides practical examples of using the Caffeine-based caching system in Red5 Server applications.

## Table of Contents

1. [Basic Cache Configuration](#basic-cache-configuration)
2. [Programmatic Cache Access](#programmatic-cache-access)
3. [Custom Application Caching](#custom-application-caching)
4. [Cache Statistics](#cache-statistics)
5. [Advanced Configurations](#advanced-configurations)
6. [Best Practices](#best-practices)

## Basic Cache Configuration

### Enable Caching in red5-common.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://www.springframework.org/schema/beans
                           http://www.springframework.org/schema/beans/spring-beans.xsd">

    <!-- Enable Caffeine cache -->
    <bean id="object.cache" class="org.red5.cache.impl.CaffeineCacheImpl"
          init-method="init" destroy-method="destroy">
        <property name="maxEntries" value="1000"/>
        <property name="expireAfterAccessSeconds" value="1200"/>
        <property name="recordStats" value="true"/>
    </bean>

</beans>
```

### Disable Caching (Development)

```xml
<!-- No caching - useful for debugging -->
<bean id="object.cache" class="org.red5.cache.impl.NoCacheImpl"/>
```

## Programmatic Cache Access

### Accessing Cache from Application

```java
import org.red5.cache.ICacheStore;
import org.red5.cache.ICacheable;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class MyApplication extends MultiThreadedApplicationAdapter
        implements ApplicationContextAware {

    private ICacheStore cache;

    @Override
    public void setApplicationContext(ApplicationContext context)
            throws BeansException {
        // Get the cache bean from Spring context
        this.cache = (ICacheStore) context.getBean("object.cache");
    }

    @Override
    public boolean appStart(IScope scope) {
        log.info("Application starting with cache: {}", cache.getClass().getName());
        return super.appStart(scope);
    }

    // Use cache in your methods
    public void cacheUserData(String userId, UserData data) {
        cache.put(userId, data);
        log.debug("Cached user data for: {}", userId);
    }

    public UserData getUserData(String userId) {
        ICacheable cached = cache.get(userId);
        if (cached != null) {
            log.debug("Cache hit for user: {}", userId);
            return (UserData) cached.getCachedObject();
        }
        log.debug("Cache miss for user: {}", userId);
        return null;
    }
}
```

### Caching File Metadata

```java
import org.red5.cache.ICacheStore;
import org.red5.io.IStreamableFile;

public class VideoStreamHandler {

    private ICacheStore cache;

    public VideoStreamHandler(ICacheStore cache) {
        this.cache = cache;
    }

    public IStreamableFile getStreamableFile(String filename) {
        // Try cache first
        ICacheable cached = cache.get("file:" + filename);
        if (cached != null) {
            return (IStreamableFile) cached.getCachedObject();
        }

        // Load file if not cached
        IStreamableFile file = loadFile(filename);
        if (file != null) {
            cache.put("file:" + filename, file);
        }

        return file;
    }

    private IStreamableFile loadFile(String filename) {
        // Load file from disk
        // ... implementation
        return null;
    }
}
```

## Custom Application Caching

### Session Data Caching

```java
import org.red5.server.adapter.MultiThreadedApplicationAdapter;
import org.red5.server.api.IConnection;
import org.red5.server.api.scope.IScope;
import org.red5.cache.ICacheStore;

public class SessionCachingApp extends MultiThreadedApplicationAdapter {

    private ICacheStore cache;

    public void setCache(ICacheStore cache) {
        this.cache = cache;
    }

    @Override
    public boolean connect(IConnection conn, IScope scope, Object[] params) {
        String sessionId = conn.getSessionId();

        // Store session data in cache
        SessionData session = new SessionData(conn.getClient().getId(),
                                               System.currentTimeMillis());
        cache.put(sessionId, session);

        log.info("Cached session: {}", sessionId);
        return super.connect(conn, scope, params);
    }

    @Override
    public void disconnect(IConnection conn, IScope scope) {
        String sessionId = conn.getSessionId();

        // Remove from cache on disconnect
        cache.remove(sessionId);
        log.info("Removed session from cache: {}", sessionId);

        super.disconnect(conn, scope);
    }

    public SessionData getSession(String sessionId) {
        ICacheable cached = cache.get(sessionId);
        return cached != null ? (SessionData) cached.getCachedObject() : null;
    }

    // Inner class for session data
    public static class SessionData implements Serializable {
        private String clientId;
        private long connectTime;

        public SessionData(String clientId, long connectTime) {
            this.clientId = clientId;
            this.connectTime = connectTime;
        }

        // Getters and setters...
    }
}
```

### Spring Configuration for Custom App

```xml
<bean id="sessionCachingApp" class="com.example.SessionCachingApp"
      init-method="init">
    <property name="cache" ref="object.cache"/>
</bean>
```

### Room/Scope Caching

```java
public class RoomManager {

    private ICacheStore cache;

    public RoomManager(ICacheStore cache) {
        this.cache = cache;
    }

    public void cacheRoomData(String roomName, RoomData data) {
        String key = "room:" + roomName;
        cache.put(key, data);
    }

    public RoomData getRoomData(String roomName) {
        String key = "room:" + roomName;
        ICacheable cached = cache.get(key);

        if (cached != null) {
            return (RoomData) cached.getCachedObject();
        }

        // Create new room data if not cached
        RoomData data = new RoomData(roomName);
        cache.put(key, data);
        return data;
    }

    public void clearRoom(String roomName) {
        cache.remove("room:" + roomName);
    }

    public static class RoomData implements Serializable {
        private String name;
        private int userCount;
        private long createdAt;

        public RoomData(String name) {
            this.name = name;
            this.userCount = 0;
            this.createdAt = System.currentTimeMillis();
        }

        // Getters and setters...
    }
}
```

## Cache Statistics

### Accessing Statistics (Caffeine Only)

```java
import org.red5.cache.impl.CaffeineCacheImpl;
import com.github.benmanes.caffeine.cache.stats.CacheStats;

public class CacheMonitor {

    public void printCacheStatistics() {
        // Get basic metrics
        long hits = CaffeineCacheImpl.getCacheHit();
        long misses = CaffeineCacheImpl.getCacheMiss();
        long size = CaffeineCacheImpl.getCacheSize();

        System.out.println("Cache Hits: " + hits);
        System.out.println("Cache Misses: " + misses);
        System.out.println("Cache Size: " + size);
        System.out.println("Hit Rate: " +
                          (double) hits / (hits + misses) * 100 + "%");

        // Get detailed stats
        CacheStats stats = CaffeineCacheImpl.getStats();
        if (stats != null) {
            System.out.println("\nDetailed Statistics:");
            System.out.println("  Hit Rate: " + stats.hitRate());
            System.out.println("  Miss Rate: " + stats.missRate());
            System.out.println("  Load Success: " + stats.loadSuccessCount());
            System.out.println("  Load Failure: " + stats.loadFailureCount());
            System.out.println("  Total Load Time: " + stats.totalLoadTime());
            System.out.println("  Eviction Count: " + stats.evictionCount());
            System.out.println("  Eviction Weight: " + stats.evictionWeight());
        }
    }
}
```

### Monitoring Cache Health

```java
import java.util.Timer;
import java.util.TimerTask;

public class CacheHealthMonitor {

    private Timer timer;

    public void startMonitoring(long intervalMs) {
        timer = new Timer("CacheMonitor", true);

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                checkCacheHealth();
            }
        }, 0, intervalMs);
    }

    private void checkCacheHealth() {
        CacheStats stats = CaffeineCacheImpl.getStats();
        if (stats != null) {
            double hitRate = stats.hitRate();
            long size = CaffeineCacheImpl.getCacheSize();

            // Alert if hit rate is too low
            if (hitRate < 0.5) {
                log.warn("Cache hit rate is low: {}%", hitRate * 100);
            }

            // Alert if cache is full
            // Note: This requires knowing the configured maxEntries
            if (size > 900) { // Assuming maxEntries=1000
                log.warn("Cache is nearly full: {} entries", size);
            }

            log.debug("Cache health - Size: {}, Hit Rate: {}%",
                     size, hitRate * 100);
        }
    }

    public void stopMonitoring() {
        if (timer != null) {
            timer.cancel();
        }
    }
}
```

## Advanced Configurations

### High-Performance Configuration

```xml
<!-- Optimized for high-traffic applications -->
<bean id="object.cache" class="org.red5.cache.impl.CaffeineCacheImpl"
      init-method="init" destroy-method="destroy">
    <!-- Large cache for many concurrent users -->
    <property name="maxEntries" value="10000"/>

    <!-- 30 minutes idle timeout -->
    <property name="expireAfterAccessSeconds" value="1800"/>

    <!-- 1 hour absolute timeout -->
    <property name="expireAfterWriteSeconds" value="3600"/>

    <!-- Enable statistics for monitoring -->
    <property name="recordStats" value="true"/>
</bean>
```

### Memory-Constrained Configuration

```xml
<!-- Optimized for low-memory environments -->
<bean id="object.cache" class="org.red5.cache.impl.CaffeineCacheImpl"
      init-method="init" destroy-method="destroy">
    <!-- Small cache size -->
    <property name="maxEntries" value="100"/>

    <!-- Short idle timeout to free memory -->
    <property name="expireAfterAccessSeconds" value="300"/>

    <!-- Aggressive absolute timeout -->
    <property name="expireAfterWriteSeconds" value="600"/>

    <!-- Disable stats to save memory -->
    <property name="recordStats" value="false"/>
</bean>
```

### Development/Testing Configuration

```xml
<!-- No caching for development -->
<bean id="object.cache" class="org.red5.cache.impl.NoCacheImpl"/>
```

## Best Practices

### 1. Use Appropriate Cache Keys

```java
// Good - Structured keys with prefixes
cache.put("user:" + userId, userData);
cache.put("file:" + filename, fileData);
cache.put("session:" + sessionId, sessionData);

// Bad - Generic keys that might collide
cache.put(userId, userData);  // Could collide with other IDs
```

### 2. Handle Null Values

```java
// Good - Check for null before using
ICacheable cached = cache.get(key);
if (cached != null) {
    Object value = cached.getCachedObject();
    if (value != null) {
        // Use value
    }
}

// Bad - Assuming non-null
Object value = cache.get(key).getCachedObject();  // NPE if cache miss
```

### 3. Use Offer for Optional Caching

```java
// Good - Only cache if space available
boolean cached = cache.offer(key, value);
if (!cached) {
    log.warn("Cache full, couldn't cache: {}", key);
}

// Alternative - Always cache (might evict entries)
cache.put(key, value);
```

### 4. Clean Up on Resource Disposal

```java
@Override
public void appStop(IScope scope) {
    // Remove scope-specific cached data
    Iterator<String> keys = cache.getObjectNames();
    while (keys.hasNext()) {
        String key = keys.next();
        if (key.startsWith("scope:" + scope.getName())) {
            cache.remove(key);
        }
    }

    super.appStop(scope);
}
```

### 5. Choose Appropriate Expiration

```java
// Short-lived data (user sessions)
<property name="expireAfterAccessSeconds" value="1800"/>  <!-- 30 min -->

// Long-lived data (file metadata)
<property name="expireAfterAccessSeconds" value="7200"/>  <!-- 2 hours -->

// Permanent until eviction (static data)
<!-- Don't set expireAfterAccessSeconds -->
<property name="expireAfterWriteSeconds" value="86400"/>  <!-- 24 hours -->
```

### 6. Monitor in Production

```java
// Periodic monitoring
@Scheduled(fixedDelay = 60000)  // Every minute
public void logCacheStats() {
    if (cache instanceof CaffeineCacheImpl) {
        CacheStats stats = CaffeineCacheImpl.getStats();
        log.info("Cache: size={}, hitRate={}%, evictions={}",
                CaffeineCacheImpl.getCacheSize(),
                stats.hitRate() * 100,
                stats.evictionCount());
    }
}
```

### 7. Use Serializable Objects

```java
// Good - Implements Serializable
public class UserData implements Serializable {
    private static final long serialVersionUID = 1L;
    private String userId;
    private String username;
    // ...
}

// Bad - Not serializable (may cause issues with persistence)
public class UserData {
    private String userId;
    // ...
}
```

## Common Patterns

### Cache-Aside Pattern

```java
public UserProfile getUserProfile(String userId) {
    // 1. Try cache first
    ICacheable cached = cache.get("profile:" + userId);
    if (cached != null) {
        return (UserProfile) cached.getCachedObject();
    }

    // 2. Load from database
    UserProfile profile = database.loadProfile(userId);

    // 3. Store in cache
    if (profile != null) {
        cache.put("profile:" + userId, profile);
    }

    return profile;
}
```

### Write-Through Pattern

```java
public void updateUserProfile(String userId, UserProfile profile) {
    // 1. Update cache
    cache.put("profile:" + userId, profile);

    // 2. Update database
    database.saveProfile(userId, profile);
}
```

### Cache Invalidation Pattern

```java
public void updateUser(String userId, UserData newData) {
    // Update in database
    database.updateUser(userId, newData);

    // Invalidate cache (will reload on next access)
    cache.remove("user:" + userId);
}
```

---

For more information, see `EHCACHE_TO_CAFFEINE_MIGRATION.md`.
