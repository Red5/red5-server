# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Red5 Server is an open-source Flash media server written in Java that supports RTMP, RTMPT, RTMPS, and RTMPE protocols for streaming video/audio and real-time messaging. The project uses Maven as its build system with a multi-module structure.

## Build Commands

### Basic Build
```bash
# Build all modules, skip tests
mvn -Dmaven.test.skip=true install

# Build with tests
mvn clean install

# Quick compile check
mvn compile

# Format code
mvn formatter:format
```

### Testing
```bash
# Run all tests
mvn test

# Run tests for specific module
mvn -pl common test
mvn -pl server test
```

### Assembly & Packaging
```bash
# Create distribution package
mvn -Dmaven.test.skip=true clean package -P assemble

# Create milestone build
mvn -Dmilestone.version=1.0.7-M1 clean package -Pmilestone
```

## Project Architecture

### Module Structure
- **`common/`** - Core RTMP protocol implementation, codecs, and shared utilities
- **`io/`** - I/O operations, file formats (FLV, MP4), AMF encoding/decoding
- **`server/`** - Server application framework, scope management, Spring integration
- **`client/`** - RTMP client implementation for outbound connections
- **`service/`** - System service and daemon integration
- **`tests/`** - Integration and unit tests

### Key Components

#### RTMP Protocol Stack
Core RTMP implementation in `common/src/main/java/org/red5/server/net/rtmp/`:
- **`codec/`** - Protocol encoding/decoding (RTMPProtocolDecoder/Encoder, RTMP state)
- **`message/`** - RTMP message and packet structures
- **`event/`** - RTMP events (Audio/VideoData, Invoke, Notify, etc.)
- **`status/`** - Status codes and error handling

#### Server Framework
Application server in `server/src/main/java/org/red5/server/`:
- **`scope/`** - Hierarchical scope management (Global/Web/Room scopes)
- **`adapter/`** - Application adapters and lifecycle management
- **`stream/`** - Stream services (broadcast, playback, recording)
- **`so/`** - Shared Object implementation
- **`messaging/`** - Internal messaging and pipe system

#### I/O and Media
Media handling in `io/src/main/java/org/red5/io/`:
- **`flv/`**, **`mp4/`** - Media container support
- **`amf/`**, **`amf3/`** - Action Message Format serialization
- **`object/`** - Object serialization framework

### Technology Stack
- **Java 21** (minimum requirement)
- **Maven 3.6+** for build management
- **Spring Framework 6.x** for dependency injection and application context
- **Apache MINA 2.x** for network I/O
- **Caffeine 3.1.8** for high-performance caching
- **Logback/SLF4J** for logging
- **JUnit 4** for testing

### Caching Implementation
Red5 uses **Caffeine** for high-performance caching:

#### AMF Serialization Caching (Automatic)
The AMF/AMF3 serialization layer uses Caffeine internally for:
- **String encoding cache**: Caches UTF-8 encoded strings (1000 entries, 20-min idle)
- **Serialization cache**: Caches field serialization decisions (200 entries, 20-min idle)
- **Field cache**: Caches reflection field lookups (200 entries, 20-min idle)
- **Getter cache**: Caches getter method references (200 entries, 20-min idle)

These caches are automatically initialized and require no configuration.

#### Object Caching (Optional)
File metadata and object caching via `ICacheStore` interface:
- **`CaffeineCacheImpl`** - Recommended modern implementation using Caffeine
  - Configurable in `conf/red5-common.xml`
  - Supports expiration policies, max entries, statistics
  - Example: See `EHCACHE_TO_CAFFEINE_MIGRATION.md`
- **`NoCacheImpl`** - Default (no caching, useful for development)
- **`CacheImpl`** - Simple HashMap-based cache with SoftReferences
- **`EhCacheImpl`** - Deprecated (excluded from build, removed dependency)

**Cache Configuration Example**:
```xml
<bean id="object.cache" class="org.red5.cache.impl.CaffeineCacheImpl"
      init-method="init" destroy-method="destroy">
    <property name="maxEntries" value="1000"/>
    <property name="expireAfterAccessSeconds" value="1200"/>
</bean>
```

See `EHCACHE_TO_CAFFEINE_MIGRATION.md` for migration details.

### Security Considerations
Recent security enhancements to RTMP protocol handling:
- Chunk size validation with bounds checking (128-65536 bytes)
- Type 3 header validation to prevent stream confusion attacks
- Extended timestamp rollover handling for 32-bit timestamp wraparound

### Configuration
- **`server/conf/`** - Server configuration files
- **`red5.properties`** - Main server properties
- **`logback.xml`** - Logging configuration
- **Spring XML files** - Application context configuration

### Development Workflow
1. Code formatting uses Eclipse formatter (`red5-eclipse-format.xml`)
2. Line endings enforced as LF (Linux/Mac style)
3. Tests should be written for new functionality
4. Use `mvn formatter:format` before committing

### Key Interfaces
- **`IApplication`** - Application lifecycle and event handling
- **`IScope`** - Scope hierarchy management
- **`IConnection`** - Client connection abstraction
- **`IStream`** - Stream operations (publish/play)
- **`ISharedObject`** - Shared object management

This codebase follows traditional Java enterprise patterns with Spring framework integration, focusing on media streaming protocol implementation and real-time communication features.