# HTTP-FLV Implementation Plan for Red5 Server

## Overview

This document outlines the implementation plan for adding HTTP-FLV streaming support to Red5 Server, enabling compatibility with [bilibili/flv.js](https://github.com/Bilibili/flv.js/) and similar HTML5 FLV players.

**Implementation Status**: All Phases Complete (1-5)

## What is flv.js?

flv.js is a JavaScript library that enables FLV video playback in HTML5 browsers without Flash. It works by:

1. **Transmuxing**: Converting FLV data into ISO BMFF (Fragmented MP4) segments
2. **MSE Integration**: Feeding MP4 segments into HTML5 `<video>` via Media Source Extensions
3. **Low Latency**: Achieving 1-3 second latency (comparable to RTMP)

### Supported Features
- H.264 + AAC/MP3 codec playback
- HTTP-FLV live stream playback
- FLV over WebSocket live stream playback
- Chrome, Firefox, Safari 10+, IE11, Edge

### Server Requirements
- CORS headers configured correctly
- Chunked transfer encoding for HTTP-FLV
- H.264 video + AAC/MP3 audio encoding

## Current Red5 Capabilities

Red5 already has solid foundation for this implementation:

| Component | Location | Purpose |
|-----------|----------|---------|
| `FLVReader` | `io/src/main/java/org/red5/io/flv/impl/` | Reads FLV files and tags |
| `FLVWriter` | `io/src/main/java/org/red5/io/flv/impl/` | Writes FLV files and tags |
| `FLVHeader` | `io/src/main/java/org/red5/io/flv/` | FLV header structure |
| `Tag` | `io/src/main/java/org/red5/io/flv/impl/` | FLV tag structure |
| `IBroadcastStream` | `common/.../api/stream/` | Live stream interface |
| `IStreamListener` | `common/.../api/stream/` | Stream packet notification |
| `SSEServlet` | `server/.../net/sse/` | Reference for async HTTP streaming |
| `WebSocketConnection` | `server/.../websocket/` | WebSocket infrastructure |

## Architecture Design

```
                                    +-------------------+
                                    |   flv.js Player   |
                                    |   (Browser)       |
                                    +---------+---------+
                                              |
                              HTTP GET /live/stream.flv
                                              |
                                              v
+------------------+              +-----------------------+
|  RTMP Publisher  |  publish    |   HTTPFLVServlet      |
|  (OBS, FFmpeg)   +------------>|   (Async Servlet)     |
+------------------+              +-----------+-----------+
                                              |
                                   +----------+----------+
                                   |                     |
                                   v                     v
                        +------------------+   +------------------+
                        | HTTPFLVService   |   | HTTPFLVManager   |
                        | (Stream Config)  |   | (Lifecycle)      |
                        +--------+---------+   +--------+---------+
                                 |                      |
                                 v                      v
                        +------------------+   +------------------+
                        |StreamConfiguration|  |  Connection      |
                        | (GOP/Codec Cache)|   |  Timeout/Cleanup |
                        +------------------+   +------------------+
                                 |
                                 v
                        +-----------------------+
                        |  HTTPFLVConnection    |
                        |  (IStreamListener)    |
                        +-----------+-----------+
                                    |
                                    v
                        +-----------------------+
                        |  FLVStreamWriter      |
                        |  (FLV Tag Builder)    |
                        +-----------------------+
```

## Implemented Components

### 1. FLVStreamWriter (Implemented)
**Location**: `server/src/main/java/org/red5/server/net/httpflv/FLVStreamWriter.java`

Utility for building FLV stream data:
- `createFLVHeader(boolean hasAudio, boolean hasVideo)` - Creates 13-byte FLV header
- `packetToFLVTag(IStreamPacket)` - Converts stream packets to FLV tags
- `createFLVTag(byte dataType, int timestamp, IoBuffer/ByteBuffer/byte[] data)` - Multiple overloads
- Handles extended timestamps (> 24-bit) correctly
- Type detection helpers: `isAudio()`, `isVideo()`, `isMetadata()`

### 2. StreamConfiguration (Implemented)
**Location**: `server/src/main/java/org/red5/server/net/httpflv/StreamConfiguration.java`

Caches stream configuration for instant playback:
- Video decoder config (SPS/PPS for H.264, VPS/SPS/PPS for HEVC)
- Audio decoder config (AudioSpecificConfig for AAC)
- Stream metadata (onMetaData)
- GOP cache with configurable max frames (default 300)
- `CachedFrame` inner class for efficient frame storage

### 3. HTTPFLVConnection (Implemented)
**Location**: `server/src/main/java/org/red5/server/net/httpflv/HTTPFLVConnection.java`

Connection wrapper implementing IStreamListener:
- `initialize()` - Sends FLV header
- `sendInitialData(StreamConfiguration)` - Sends cached metadata, codec config, GOP
- `subscribe(IBroadcastStream)` - Subscribes to live stream
- `packetReceived()` - Handles incoming stream packets
- Codec detection for H.264 (AVC) and H.265 (HEVC)
- AAC codec configuration handling
- Keyframe detection and GOP cache population
- Connection statistics tracking (packets sent, bytes sent)

### 4. HTTPFLVService (Implemented)
**Location**: `server/src/main/java/org/red5/server/net/httpflv/HTTPFLVService.java`

Service for managing HTTP-FLV connections:
- Stream configuration caching per stream name
- Connection registration/unregistration
- Connection limits per stream (configurable)
- Stream publish start/stop event handlers
- Statistics: total connections, bytes sent, active streams

### 5. HTTPFLVManager (Implemented)
**Location**: `server/src/main/java/org/red5/server/net/httpflv/HTTPFLVManager.java`

Central manager for HTTP-FLV functionality:
- Scheduled timeout detection (configurable interval)
- Keep-alive mechanism for dead connection cleanup
- Connection lifecycle management
- Statistics and monitoring
- Spring lifecycle integration (InitializingBean, DisposableBean)

### 6. HTTPFLVServlet (Implemented)
**Location**: `server/src/main/java/org/red5/server/net/httpflv/HTTPFLVServlet.java`

Async HTTP servlet handling FLV stream requests:
- Handles GET requests for `*.flv` URLs
- CORS headers for cross-origin playback
- Integrates with HTTPFLVService and HTTPFLVManager
- Async servlet with proper lifecycle handling
- Stream lookup via IBroadcastScope

### 7. WebSocketFLVConnection (Implemented)
**Location**: `server/src/main/java/org/red5/net/websocket/flv/WebSocketFLVConnection.java`

WebSocket connection wrapper implementing IStreamListener:
- Wraps `WebSocketConnection` for binary frame delivery
- Same packet handling logic as HTTPFLVConnection
- `initialize()` - Sends FLV header via WebSocket binary frame
- `sendInitialData(StreamConfiguration)` - Sends cached metadata, codec config, GOP
- `subscribe(IBroadcastStream)` - Subscribes to live stream
- `packetReceived()` - Handles incoming stream packets
- Codec detection for H.264 (AVC) and H.265 (HEVC)
- AAC codec configuration handling
- Connection statistics tracking

### 8. WebSocketFLVDataListener (Implemented)
**Location**: `server/src/main/java/org/red5/net/websocket/flv/WebSocketFLVDataListener.java`

IWebSocketDataListener implementation for FLV streaming:
- Protocol: "flv" (configurable)
- `onWSConnect()` - Parses stream name, creates connection, subscribes to stream
- `onWSDisconnect()` - Cleanup and unsubscribe
- `onWSMessage()` - No-op (FLV is server-to-client only)
- Reuses HTTPFLVService for stream configuration
- Connection tracking and statistics

## FLV Protocol Details

### FLV Header (13 bytes total)
```
Signature: "FLV" (3 bytes)
Version: 0x01 (1 byte)
Flags: 0x05 (audio+video) or 0x01 (video only) or 0x04 (audio only)
DataOffset: 0x00000009 (4 bytes, big-endian)
PreviousTagSize0: 0x00000000 (4 bytes)
```

### FLV Tag Structure (11 bytes header + data + 4 bytes prev tag size)
```
TagType: 0x08 (audio), 0x09 (video), 0x12 (script/metadata)
DataSize: 3 bytes, big-endian
Timestamp: 3 bytes, big-endian (lower 24 bits)
TimestampExtended: 1 byte (upper 8 bits)
StreamID: 3 bytes (always 0)
Data: [DataSize bytes]
PreviousTagSize: 4 bytes, big-endian (11 + DataSize)
```

### Video Codec Detection
```
First byte:
  - Upper 4 bits: Frame type (1=keyframe, 2=inter frame)
  - Lower 4 bits: Codec ID (7=AVC/H.264, 12=HEVC/H.265)

Second byte (for AVC/HEVC):
  - 0 = Sequence header (codec config)
  - 1 = NALU (video data)
  - 2 = End of sequence
```

### Audio Codec Detection
```
First byte:
  - Upper 4 bits: Codec ID (10=AAC)
  - Lower 4 bits: Sample rate/size/channels

Second byte (for AAC):
  - 0 = Sequence header (AudioSpecificConfig)
  - 1 = Raw audio data
```

## Configuration

### Spring Bean Configuration (red5-common.xml)

```xml
<!-- HTTP-FLV Configuration for flv.js playback support -->

<!-- HTTP-FLV Service - handles stream configuration, GOP cache, and connection management -->
<bean id="httpflv.service" class="org.red5.server.net.httpflv.HTTPFLVService">
    <!-- Enable/disable HTTP-FLV streaming (default: true) -->
    <property name="enabled" value="${httpflv.enabled:true}"/>
    <!-- Enable GOP caching for instant playback start (default: true) -->
    <property name="gopCacheEnabled" value="${httpflv.gop.cache.enabled:true}"/>
    <!-- Maximum GOP frames to cache per stream (default: 300) -->
    <property name="maxGopFrames" value="${httpflv.gop.max.frames:300}"/>
    <!-- Maximum connections per stream, 0 = unlimited (default: 0) -->
    <property name="maxConnectionsPerStream" value="${httpflv.max.connections.per.stream:0}"/>
</bean>

<!-- HTTP-FLV Manager - handles connection lifecycle, keep-alive, and timeouts -->
<bean id="httpflv.manager" class="org.red5.server.net.httpflv.HTTPFLVManager"
      init-method="afterPropertiesSet" destroy-method="destroy">
    <!-- Reference to HTTP-FLV service for coordination -->
    <property name="httpFlvService" ref="httpflv.service"/>
    <!-- Connection timeout in milliseconds (default: 30 seconds) -->
    <property name="connectionTimeout" value="${httpflv.connection.timeout:30000}"/>
    <!-- Keep-alive check interval in milliseconds (default: 15 seconds) -->
    <property name="keepAliveInterval" value="${httpflv.keepalive.interval:15000}"/>
    <!-- Timeout check interval in milliseconds (default: 5 seconds) -->
    <property name="timeoutCheckInterval" value="${httpflv.timeout.check.interval:5000}"/>
    <!-- Enable/disable keep-alive checks (default: true) -->
    <property name="keepAliveEnabled" value="${httpflv.keepalive.enabled:true}"/>
</bean>
```

### Web.xml Servlet Mapping
Add to web application's `web.xml`:

```xml
<servlet>
    <servlet-name>HTTPFLVServlet</servlet-name>
    <servlet-class>org.red5.server.net.httpflv.HTTPFLVServlet</servlet-class>
    <async-supported>true</async-supported>
</servlet>

<servlet-mapping>
    <servlet-name>HTTPFLVServlet</servlet-name>
    <url-pattern>*.flv</url-pattern>
</servlet-mapping>
```

### Properties (red5.properties)
```properties
# HTTP-FLV Configuration
httpflv.enabled=true
httpflv.gop.cache.enabled=true
httpflv.gop.max.frames=300
httpflv.max.connections.per.stream=0
httpflv.connection.timeout=30000
httpflv.keepalive.interval=15000
httpflv.timeout.check.interval=5000
httpflv.keepalive.enabled=true
```

## URL Format

```
HTTP-FLV:      http://server:5080/app/stream.flv
               http://server:5080/live/mystream.flv

WebSocket-FLV: ws://server:5080/app/stream.flv
               wss://server:5080/live/mystream.flv (secure)

With query params:
               http://server:5080/live/stream.flv?token=abc123
               ws://server:5080/live/stream.flv?token=abc123
```

## Client Usage (flv.js)

### HTTP-FLV Mode
```javascript
if (flvjs.isSupported()) {
    var player = flvjs.createPlayer({
        type: 'flv',
        isLive: true,
        url: 'http://localhost:5080/live/stream.flv'
    }, {
        enableStashBuffer: false,  // Lower latency
        stashInitialSize: 128      // Smaller buffer
    });
    player.attachMediaElement(videoElement);
    player.load();
    player.play();
}
```

### WebSocket-FLV Mode
```javascript
if (flvjs.isSupported()) {
    var player = flvjs.createPlayer({
        type: 'flv',
        isLive: true,
        url: 'ws://localhost:5080/live/stream.flv'  // ws:// for WebSocket
    }, {
        enableStashBuffer: false,
        stashInitialSize: 128
    });
    player.attachMediaElement(videoElement);
    player.load();
    player.play();
}
```

## WebSocket-FLV Configuration

### Application red5-web.xml Configuration

Add the WebSocket-FLV listener to your application's `red5-web.xml`:

```xml
<!-- WebSocket-FLV Data Listener for flv.js WebSocket mode -->
<bean id="wsFlvListener" class="org.red5.net.websocket.flv.WebSocketFLVDataListener">
    <!-- Reference to shared HTTP-FLV service for stream configuration -->
    <property name="httpFlvService" ref="httpflv.service"/>
    <!-- Enable/disable WebSocket-FLV (default: true) -->
    <property name="enabled" value="true"/>
</bean>

<!-- WebSocket Scope with FLV listener -->
<bean id="webSocketScope" class="org.red5.net.websocket.WebSocketScope">
    <property name="scope" ref="web.scope"/>
    <property name="listeners">
        <list>
            <ref bean="wsFlvListener"/>
        </list>
    </property>
</bean>
```

### WebSocket-FLV vs HTTP-FLV

| Aspect | HTTP-FLV | WebSocket-FLV |
|--------|----------|---------------|
| Transport | HTTP chunked transfer | WebSocket binary frames |
| Keep-alive | Custom timeout checking | Built-in ping/pong |
| Proxy support | May buffer chunked responses | Generally better |
| Protocol overhead | HTTP headers per chunk | WebSocket frame headers |
| Connection mgmt | AsyncContext + servlet | WebSocket session |
| Code reuse | FLVStreamWriter, StreamConfiguration | Same components |

WebSocket-FLV is recommended when:
- HTTP proxies buffer chunked responses causing latency
- WebSocket infrastructure is already in place
- Built-in connection health monitoring is desired

## Implementation Phases

### Phase 1: Core Infrastructure (COMPLETE)
- [x] Create `httpflv` package structure
- [x] Implement `FLVStreamWriter` utility class
- [x] Implement `HTTPFLVConnection` with IStreamListener
- [x] Create basic `HTTPFLVServlet`

### Phase 2: Stream Integration (COMPLETE)
- [x] Implement broadcast stream subscription
- [x] Add codec configuration (SPS/PPS for H.264/HEVC, AudioSpecificConfig for AAC)
- [x] Implement GOP caching for clean stream start (`StreamConfiguration`)
- [x] Add metadata injection (onMetaData)
- [x] Create `HTTPFLVService` for stream configuration management

### Phase 3: Connection Management (COMPLETE)
- [x] Implement `HTTPFLVManager` for connection lifecycle
- [x] Add connection tracking and cleanup
- [x] Implement keep-alive mechanism (dead connection detection)
- [x] Add timeout handling (configurable connection timeout)
- [x] Add statistics/monitoring

### Phase 4: Configuration & Polish (COMPLETE - merged into Phase 3)
- [x] Add Spring bean configuration to `red5-common.xml`
- [x] Document servlet configuration for `web.xml`
- [x] Implement CORS handling (in HTTPFLVServlet and HTTPFLVConnection)
- [x] Add connection timeout handling (in HTTPFLVManager)

### Phase 5: WebSocket-FLV (COMPLETE)
- [x] Leverage existing WebSocket infrastructure
- [x] Create `WebSocketFLVConnection` (IStreamListener implementation)
- [x] Create `WebSocketFLVDataListener` (IWebSocketDataListener implementation)
- [x] Support `ws://server/live/stream.flv` URLs
- [x] Reuse FLVStreamWriter and StreamConfiguration from HTTP-FLV

## File Summary

```
server/src/main/java/org/red5/server/net/httpflv/
├── FLVStreamWriter.java       (11KB) - FLV header/tag building utilities
├── StreamConfiguration.java   (11KB) - Codec config, metadata, GOP cache
├── HTTPFLVConnection.java     (22KB) - IStreamListener, packet handling
├── HTTPFLVService.java        (11KB) - Stream config management
├── HTTPFLVManager.java        (13KB) - Lifecycle, keep-alive, timeouts
└── HTTPFLVServlet.java        (18KB) - Async servlet for HTTP-FLV

server/src/main/java/org/red5/net/websocket/flv/
├── WebSocketFLVConnection.java    (15KB) - WebSocket IStreamListener implementation
└── WebSocketFLVDataListener.java  (10KB) - IWebSocketDataListener for FLV streams

server/src/main/server/conf/
└── red5-common.xml            - Spring bean configuration (updated)
```

## Testing Strategy

### Unit Tests
- FLVStreamWriter tag building
- FLV header generation
- Timestamp handling (including extended)
- Codec detection logic

### Integration Tests
- Servlet request/response handling
- Stream subscription and data flow
- Connection cleanup
- GOP cache functionality

### Manual Testing
- OBS -> Red5 -> flv.js playback
- FFmpeg -> Red5 -> flv.js playback
- Multiple concurrent viewers
- Stream start/stop behavior
- Network interruption recovery
- Late-join playback (GOP cache verification)

## Dependencies

No new external dependencies required. Uses existing:
- Jakarta Servlet API (async servlet support)
- Apache MINA (IoBuffer)
- Spring Framework (bean configuration)

## Security Considerations

1. **Authentication**: Support token-based auth via query params or headers
2. **Rate Limiting**: Limit connections per IP/stream (via `maxConnectionsPerStream`)
3. **CORS**: Configurable allowed origins (currently allows all)
4. **Input Validation**: Validate stream names against injection

## Performance Considerations

1. **GOP Caching**: Cache last GOP for immediate playback start (up to 300 frames)
2. **Buffer Management**: Use direct buffers for reduced copying
3. **Async I/O**: Leverage servlet async for non-blocking writes
4. **Connection Limits**: Configurable max connections per stream
5. **Timeout Cleanup**: Automatic cleanup of idle connections

## Comparison with Alternatives

| Protocol | Latency | Browser Support | Complexity |
|----------|---------|-----------------|------------|
| HTTP-FLV | 1-3s | Chrome, Firefox, Safari 10+, Edge | Medium |
| HLS | 10-30s | Universal | Low |
| DASH | 3-10s | Most modern | Medium |
| WebRTC | <1s | Most modern | High |

HTTP-FLV provides a good balance of low latency and broad compatibility without requiring WebRTC complexity.

## References

- [flv.js GitHub](https://github.com/Bilibili/flv.js/)
- [flv.js API Documentation](http://bilibili.github.io/flv.js/docs/api.html)
- [FLV Specification](https://www.adobe.com/content/dam/acom/en/devnet/flv/video_file_format_spec_v10.pdf)
- [HTTP-FLV Introduction](https://www.yanxurui.cc/posts/server/2017-11-25-http-flv/)
- [SRS HTTP-FLV Documentation](https://ossrs.net/lts/en-us/docs/v4/doc/delivery-http-flv)
