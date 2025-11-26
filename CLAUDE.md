# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Red5 Server is an open-source media server written in Java that supports RTMP, RTMPT, RTMPS, RTMPE, and Server-Sent Events (SSE) protocols for streaming video/audio and real-time messaging. The project uses Maven as its build system with a multi-module structure. Current version is **2.0.23**.

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
- **`common/`** - Core RTMP protocol implementation, codecs, SSE interfaces, and shared utilities
- **`io/`** - I/O operations, file formats (FLV, MP4), AMF encoding/decoding
- **`server/`** - Server application framework, scope management, SSE implementation, Spring integration
- **`servlet/`** - Servlet components including RTMPT support
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

#### Server-Sent Events (SSE)
SSE implementation for real-time server-to-client communication:
- **`common/.../net/sse/`** - Core interfaces (`ISSEService`, `SSEConnection`, `SSEEvent`)
- **`server/.../net/sse/`** - Implementation (`SSEServlet`, `SSEManager`, `SSEService`)
- **`server/.../adapter/SSEApplicationAdapter`** - Red5 application adapter with built-in SSE support
- Features: W3C SSE compliance, connection management, keep-alive support, scope integration, CORS support

### Technology Stack
- **Java 21** (minimum requirement)
- **Maven 3.6+** for build management
- **Spring Framework 6.2.x** for dependency injection and application context
- **Apache MINA 2.0.23** for network I/O
- **Tomcat 11.0.x** (embedded) for servlet container
- **Logback 1.5.x / SLF4J 2.0.x** for logging
- **Ehcache 2.10.x** for caching
- **JUnit 4** for testing

### Security Considerations
RTMP protocol security enhancements (see `SECURITY_FIXES_SUMMARY.md` for details):
- **Chunk size validation** - Bounds checking (RTMP spec: 1-16777215, soft limits: 32-65536 bytes)
- **Type 3 header validation** - Prevents stream confusion attacks with graceful handling
- **Extended timestamp handling** - Correct 32-bit timestamp processing and rollover protection
- **Client compatibility** - Maintains support for OBS Studio, FFmpeg, and other streaming clients

### Configuration
- **`server/src/main/server/conf/`** - Server configuration files
- **`red5.properties`** - Main server properties
- **`red5-common.xml`** - Common Spring beans including SSE configuration
- **`red5-core.xml`** - Core server beans
- **`logback.xml`** - Logging configuration
- **`ehcache.xml`** - Cache configuration
- **`jee-container.xml`** / **`no-jee-container.xml`** - Container-specific configuration

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
- **`ISSEService`** - Server-Sent Events service interface

### Related Documentation

- **`SSE-README.md`** - Server-Sent Events implementation guide
- **`SECURITY_FIXES_SUMMARY.md`** - RTMP security enhancements summary
- **`RTMPS.md`** - RTMPS (secure RTMP) configuration guide
- **`servlet/Readme.md`** - RTMPT servlet configuration

This codebase follows traditional Java enterprise patterns with Spring framework integration, focusing on media streaming protocol implementation and real-time communication features.
