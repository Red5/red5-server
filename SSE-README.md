# Red5 Server-Sent Events (SSE) Implementation

This document describes the Server-Sent Events (SSE) implementation for the Red5 server, which provides W3C-compliant SSE functionality integrated with the existing Red5 Tomcat servlet infrastructure.

## Overview

The SSE implementation consists of several components that work together to provide real-time server-to-client communication:

- **SSEConnection**: Manages individual SSE connections
- **SSEManager**: Handles connection lifecycle and broadcasting
- **SSEServlet**: HTTP servlet that establishes SSE connections
- **SSEService**: High-level API for application developers
- **SSEApplicationAdapter**: Red5 application adapter with built-in SSE support
- **SSEEvent**: Data structure for SSE events

## Features

- **W3C SSE Specification Compliance**: Full support for id, event, data, and retry fields
- **Connection Management**: Automatic cleanup of stale connections
- **Keep-alive Support**: Optional keep-alive messages to maintain connections
- **Scope Integration**: Events can be broadcast to specific Red5 scopes
- **CORS Support**: Built-in Cross-Origin Resource Sharing support
- **Async Servlet Processing**: Non-blocking connection handling
- **Thread-safe**: Concurrent connection management

## Architecture

### Core Components

```plaintext
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   SSEServlet    │────│   SSEManager     │────│  SSEConnection  │
│                 │    │                  │    │                 │
│ - HTTP endpoint │    │ - Lifecycle mgmt │    │ - Client conn   │
│ - Async support │    │ - Broadcasting   │    │ - Event sending │
│ - CORS handling │    │ - Cleanup tasks  │    │ - Keep-alive    │
└─────────────────┘    └──────────────────┘    └─────────────────┘
         │                       │
         │                       │
         ▼                       ▼
┌─────────────────┐    ┌──────────────────┐
│   SSEService    │    │  SSEApplication  │
│                 │    │     Adapter      │
│ - High-level    │    │                  │
│   API           │    │ - Red5 events    │
│ - Application   │    │ - SSE integration│
│   interface     │    │ - Broadcasting   │
└─────────────────┘    └──────────────────┘
```

## Installation and Configuration

### 1. Include SSE Configuration

Add the SSE configuration to your Red5 Tomcat / JEE setup by including the SSE Spring configuration:

```xml
    <!-- Server-Sent Events Configuration -->
    
    <!-- SSE Manager - handles connection lifecycle and cleanup -->
    <bean id="sseManager" class="org.red5.server.net.sse.SSEManager" init-method="afterPropertiesSet" destroy-method="destroy">
        <!-- Connection timeout in milliseconds (default: 5 minutes) -->
        <property name="connectionTimeoutMs" value="${sse.connection.timeout.ms:300000}"/>
        <!-- Keep-alive interval in milliseconds (default: 30 seconds) -->
        <property name="keepAliveIntervalMs" value="${sse.keepalive.interval.ms:30000}"/>
        <!-- Enable/disable keep-alive messages (default: true) -->
        <property name="keepAliveEnabled" value="${sse.keepalive.enabled:true}"/>
    </bean>
    
    <!-- SSE Service - high-level API for applications -->
    <bean id="sseService" class="org.red5.server.net.sse.SSEService">
        <property name="sseManager" ref="sseManager"/>
    </bean>
```

You may expect to find these sections in the `jee-container.xml` file.

### 2. Configure Web Application

Update your `web.xml` to include the SSE servlet:

```xml
<servlet>
    <servlet-name>sse</servlet-name>
    <servlet-class>org.red5.server.net.sse.SSEServlet</servlet-class>
    <load-on-startup>3</load-on-startup>
    <async-supported>true</async-supported>
</servlet>

<servlet-mapping>
    <servlet-name>sse</servlet-name>
    <url-pattern>/events</url-pattern>
    <url-pattern>/events/*</url-pattern>
</servlet-mapping>
```

### 3. Configuration Properties

Configure SSE behavior using properties in `red5.properties`:

```properties
# Connection timeout in milliseconds (default: 5 minutes)
sse.connection.timeout.ms=300000

# Keep-alive interval in milliseconds (default: 30 seconds)
sse.keepalive.interval.ms=30000

# Enable/disable keep-alive messages (default: true)
sse.keepalive.enabled=true
```

## Usage

### Using the SSE Service

```java
@Autowired
private ISSEService sseService;

// Broadcast to all connections
sseService.broadcastMessage("Hello, everyone!");

// Broadcast with event type
sseService.broadcastEvent("notification", "New message received");

// Broadcast to specific scope
sseService.broadcastToScope(scope, "Welcome to the application");

// Send to specific connection
sseService.sendToConnection(connectionId, "Personal message");
```

### Using the SSE Application Adapter

```java
public class MyApplication extends SSEApplicationAdapter {
    
    @Override
    public boolean appConnect(IConnection conn, Object[] params) {
        // Parent method automatically broadcasts user.connect event
        boolean result = super.appConnect(conn, params);
        
        // Send custom welcome message via SSE
        broadcastSSEEvent("welcome", "New user joined: " + conn.getClient().getId());
        
        return result;
    }
    
    public void sendNotification(String message) {
        // Custom method to send notifications
        broadcastSSEEvent("notification", message);
    }
}
```

### Creating SSE Events

```java
// Simple message
SSEEvent event1 = SSEEvent.message("Hello World");

// Event with type and data
SSEEvent event2 = SSEEvent.of("chat", "User says hello");

// Full event with all fields
SSEEvent event3 = SSEEvent.builder()
    .setId("msg-123")
    .setEvent("notification")
    .setData("System maintenance in 5 minutes")
    .setRetry(5000);

sseService.broadcastEvent(event3);
```

## Client-Side Usage

### JavaScript EventSource

```javascript
// Connect to SSE endpoint
const eventSource = new EventSource('/events');

// Handle connection events
eventSource.onopen = function(event) {
    console.log('Connected to SSE');
};

// Handle messages (default event type)
eventSource.onmessage = function(event) {
    console.log('Message:', event.data);
};

// Handle custom event types
eventSource.addEventListener('notification', function(event) {
    console.log('Notification:', event.data);
});

eventSource.addEventListener('user.connect', function(event) {
    console.log('User connected:', event.data);
});

// Handle errors
eventSource.onerror = function(event) {
    console.error('SSE error:', event);
};

// Close connection when done
eventSource.close();
```

### Testing

A test HTML page is provided at `/sse-test.html` which demonstrates:

- Connecting to the SSE endpoint
- Receiving various event types
- Connection status monitoring
- Event logging and statistics

## SSE Event Format

The implementation follows the W3C Server-Sent Events specification:

```plaintext
id: unique-event-id
event: event-type
data: event data line 1
data: event data line 2
retry: 5000

```

### Event Fields

- **id**: Unique identifier for the event (optional)
- **event**: Event type name (optional, defaults to "message")
- **data**: Event payload (can be multi-line)
- **retry**: Reconnection timeout in milliseconds (optional)

## Integration with Red5 Features

### Scope-based Broadcasting

Events can be broadcast to specific Red5 scopes, allowing for:

- Room-based messaging
- Application-specific notifications
- User group communications

### Connection Management

SSE connections are managed alongside Red5's existing connection handling:

- Automatic cleanup when connections are lost
- Integration with Red5's scope lifecycle
- Thread-safe concurrent access

### Application Events

The SSEApplicationAdapter automatically broadcasts SSE events for:

- Application start/stop
- User connect/disconnect
- Custom application events

## Performance Considerations

### Connection Limits

- Each SSE connection consumes one thread in async mode
- Configure thread pools appropriately for expected load
- Monitor connection count and cleanup effectiveness

### Memory Usage

- Each connection maintains minimal state
- Connection timeout prevents memory leaks
- Regular cleanup cycles remove stale connections

### Network Efficiency

- Keep-alive messages maintain connection state
- Event compression is handled by HTTP layer
- Batch broadcasts are more efficient than individual sends

## Troubleshooting

### Common Issues

1. **Connection Timeouts**
   - Increase `sse.connection.timeout.ms`
   - Check network stability
   - Verify keep-alive configuration

2. **CORS Errors**
   - Ensure proper CORS headers are set
   - Check origin restrictions
   - Verify browser CORS policy compliance

3. **Memory Leaks**
   - Monitor connection cleanup logs
   - Verify timeout configuration
   - Check for proper connection closing

### Logging

Enable debug logging for SSE components:

```xml
<logger name="org.red5.server.net.sse" level="DEBUG"/>
```

## Security Considerations

- **Authentication**: Implement authentication in the servlet if needed
- **Authorization**: Control access to different event types/scopes
- **Rate Limiting**: Consider implementing rate limiting for broadcast operations
- **Input Validation**: Validate all event data before broadcasting
- **CORS**: Configure CORS policies appropriately for your deployment

## Compliance and Standards

This implementation follows:

- W3C Server-Sent Events specification
- HTTP/1.1 and HTTP/2 compatibility
- Jakarta EE servlet specification
- Red5 coding standards and patterns

## Files Created

### Core Implementation

- `org.red5.server.net.sse.SSEConnection`
- `org.red5.server.net.sse.SSEManager`
- `org.red5.server.net.sse.SSEServlet`
- `org.red5.server.net.sse.SSEEvent`
- `org.red5.server.net.sse.ISSEService`
- `org.red5.server.net.sse.SSEService`
- `org.red5.server.adapter.SSEApplicationAdapter`

### Test

- `server/src/main/server/webapps/root/sse-test.html`

This SSE implementation provides a robust, standards-compliant solution for real-time server-to-client communication in Red5 applications.