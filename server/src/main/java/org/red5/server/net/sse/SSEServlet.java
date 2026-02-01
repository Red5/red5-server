/*
 * RED5 Open Source Flash Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.net.sse;

import java.io.BufferedReader;
import java.io.IOException;

import org.apache.commons.lang3.RandomStringUtils;
import org.red5.server.api.IServer;
import org.red5.server.api.scope.IGlobalScope;
import org.red5.server.api.scope.IScope;
import org.red5.server.scope.WebScope;
import org.red5.server.util.ScopeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servlet that handles Server-Sent Events (SSE) connections.
 * This servlet establishes and manages SSE connections following the W3C
 * Server-Sent Events specification.
 *
 * The servlet supports:
 * - Standard SSE event format with id, event, data, and retry fields
 * - Connection management and cleanup
 * - Integration with Red5 scopes
 * - Async servlet processing for long-lived connections
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class SSEServlet extends HttpServlet implements AsyncListener {

    private static final long serialVersionUID = 1L;

    private static Logger log = LoggerFactory.getLogger(SSEServlet.class);

    private transient WebApplicationContext webAppCtx;

    private transient IServer server;

    private transient WebScope webScope;

    private transient SSEService sseService;

    private transient SSEManager sseManager;

    @Override
    public void init() throws ServletException {
        super.init();
        log.debug("Initializing SSE servlet");
        ServletContext ctx = getServletContext();
        log.debug("Context path: {}", ctx.getContextPath());
        // Get the web application context
        try {
            webAppCtx = WebApplicationContextUtils.getRequiredWebApplicationContext(ctx);
        } catch (IllegalStateException e) {
            log.debug("Required web application context not found, trying fallback");
            webAppCtx = (WebApplicationContext) ctx.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
        }
        if (webAppCtx != null) {
            // Get the Red5 server instance
            server = (IServer) webAppCtx.getBean("red5.server");
            // get the application scope
            webScope = (WebScope) webAppCtx.getBean("web.scope");
            sseService = (SSEService) webScope.getServiceHandler(SSEService.BEAN_NAME);
            if (sseService != null) {
                sseManager = sseService.getSseManager();
                if (sseManager == null) {
                    log.warn("SSEManager not available from SSEService");
                }
            } else {
                log.info("SSEService not available from WebScope");
            }
            log.info("SSE servlet initialized successfully");
        } else {
            log.warn("No web application context available");
        }
    }

    @Override
    public void destroy() {
        log.debug("Destroying SSE servlet");
        super.destroy();
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // Handle CORS preflight
        handleCORS(req, resp);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        log.debug("SSE connection request from: {} {}", req.getRemoteAddr(), req.getRequestURI());
        // Validate that this is an SSE request
        String accept = req.getHeader("Accept");
        if (accept == null || !accept.contains("text/event-stream")) {
            log.debug("Request does not accept text/event-stream, rejecting");
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "This endpoint only supports Server-Sent Events");
            return;
        }
        // Handle CORS preflight if needed
        handleCORS(req, resp);
        // Get the scope for this connection
        IScope scope = getScope(req);
        if (scope == null) {
            log.warn("No scope available for SSE connection");
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Scope not available");
            return;
        }
        // Generate unique connection ID
        String connectionId = RandomStringUtils.insecure().nextAlphabetic(11); // random 11 char string
        // Start async processing
        AsyncContext asyncContext = req.startAsync();
        asyncContext.setTimeout(0); // No timeout, managed by SSEManager
        asyncContext.addListener(this);
        // Create SSE connection
        SSEConnection sseConnection = new SSEConnection(connectionId, asyncContext, resp, scope);
        // Add to manager
        sseManager.addConnection(sseConnection);
        // Send initial connection confirmation
        sseConnection.sendEvent("connection", "connected: " + connectionId);
        log.info("Established SSE connection: {} for scope: {}", connectionId, scope.getName());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        log.debug("SSE event post request from: {} {}", req.getRemoteAddr(), req.getRequestURI());
        // Handle CORS
        handleCORS(req, resp);
        // Validate content type
        String contentType = req.getContentType();
        if (contentType == null || !contentType.toLowerCase().contains("application/json")) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Content-Type must be application/json");
            return;
        }
        // Check if SSE manager is available
        if (sseManager == null) {
            log.warn("SSE manager not available for event posting");
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "SSE service not available");
            return;
        }
        try {
            // Read request body
            StringBuilder requestBody = new StringBuilder();
            try (BufferedReader reader = req.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    requestBody.append(line);
                }
            }
            String jsonBody = requestBody.toString();
            if (jsonBody.isEmpty()) {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Request body is required");
                return;
            }
            // Parse JSON manually (simple parsing for basic event structure)
            SSEEventRequest eventRequest = parseEventRequest(jsonBody);
            if (eventRequest == null) {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid JSON format or missing required fields");
                return;
            }
            int successCount = 0;
            // Handle different event targets
            if (eventRequest.connectionId != null && !eventRequest.connectionId.isEmpty()) {
                // Send to specific connection
                boolean success = sseManager.sendEventToConnection(eventRequest.connectionId, eventRequest.event, eventRequest.data);
                successCount = success ? 1 : 0;
                log.debug("Sent event '{}' to connection '{}': {}", eventRequest.event, eventRequest.connectionId, success);
            } else if (eventRequest.scope != null && !eventRequest.scope.isEmpty()) {
                // Send to specific scope
                IScope targetScope = resolveScope(eventRequest.scope);
                if (targetScope != null) {
                    successCount = sseManager.broadcastEventToScope(targetScope, eventRequest.event, eventRequest.data);
                    log.debug("Broadcast event '{}' to scope '{}': {} connections", eventRequest.event, eventRequest.scope, successCount);
                } else {
                    resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Scope not found: " + eventRequest.scope);
                    return;
                }
            } else {
                // Broadcast to all connections
                successCount = sseManager.broadcastEvent(eventRequest.event, eventRequest.data);
                log.debug("Broadcast event '{}' to all connections: {} recipients", eventRequest.event, successCount);
            }
            // Return success response
            resp.setStatus(HttpServletResponse.SC_OK);
        } catch (Exception e) {
            log.warn("Error processing SSE event post: {}", e.getMessage(), e);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error processing event");
        }
    }

    /**
     * Handles CORS headers for cross-origin requests.
     */
    @SuppressWarnings("null")
    private void handleCORS(HttpServletRequest req, HttpServletResponse resp) {
        String origin = req.getHeader("Origin");
        if (origin != null) {
            resp.setHeader("Access-Control-Allow-Origin", origin);
        } else {
            resp.setHeader("Access-Control-Allow-Origin", "*");
        }
        resp.setHeader("Access-Control-Allow-Credentials", "true");
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Accept, Cache-Control, Last-Event-ID, Content-Type");
        resp.setHeader("Access-Control-Max-Age", "3600");
        // Ensure web application context is available
        if (webAppCtx == null) {
            ServletContext ctx = getServletContext();
            try {
                webAppCtx = WebApplicationContextUtils.getRequiredWebApplicationContext(ctx);
            } catch (IllegalStateException e) {
                webAppCtx = (WebApplicationContext) ctx.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
            }
            if (webAppCtx != null) {
                // Get the Red5 server instance
                server = (IServer) webAppCtx.getBean("red5.server");
                // get the application scope
                webScope = (WebScope) webAppCtx.getBean("web.scope");
                sseService = (SSEService) webScope.getServiceHandler(SSEService.BEAN_NAME);
                if (sseService != null) {
                    sseManager = sseService.getSseManager();
                    if (sseManager == null) {
                        log.warn("SSEManager not available from SSEService");
                    }
                } else {
                    log.info("SSEService not available from WebScope");
                }
                log.info("SSE servlet initialized successfully");
            } else {
                log.warn("No web application context available");
            }
        }
    }

    /**
     * Gets the scope for the current request.
     */
    private IScope getScope(HttpServletRequest req) {
        if (webScope == null) {
            log.warn("Web scope is not available");
            return null;
        }
        IGlobalScope globalScope = server.getGlobal("default");
        if (globalScope == null) {
            log.warn("Global scope is not available");
            return null;
        }
        String path = req.getContextPath();
        log.debug("Request context path: {} path info: {}", path, req.getPathInfo());
        if (path == null || path.equals("/")) {
            // Default to root application
            return ScopeUtils.resolveScope(globalScope, "/live");
        } else {
            // Extract application name from path
            String[] parts = path.split("/");
            if (parts.length > 1) {
                String appName = parts[1];
                IScope appScope = ScopeUtils.resolveScope(globalScope, appName);
                if (appScope != null && ScopeUtils.isApp(appScope)) {
                    return appScope;
                } else {
                    log.warn("Application scope '{}' not found, defaulting to 'live'", appName);
                    return ScopeUtils.resolveScope(globalScope, "/live");
                }
            } else {
                log.warn("Invalid path info '{}', defaulting to 'live'", path);
                return ScopeUtils.resolveScope(globalScope, "/live");
            }
        }
    }

    // AsyncListener implementation

    @Override
    public void onComplete(AsyncEvent event) throws IOException {
        log.debug("Async context completed");
        cleanupConnection(event);
    }

    @Override
    public void onTimeout(AsyncEvent event) throws IOException {
        log.debug("Async context timed out");
        cleanupConnection(event);
    }

    @Override
    public void onError(AsyncEvent event) throws IOException {
        log.debug("Async context error: {}", event.getThrowable() != null ? event.getThrowable().getMessage() : "unknown");
        cleanupConnection(event);
    }

    @Override
    public void onStartAsync(AsyncEvent event) throws IOException {
        log.trace("Async context started");
    }

    /**
     * Cleans up connection resources when async context ends.
     */
    private void cleanupConnection(AsyncEvent event) {
        try {
            AsyncContext asyncContext = event.getAsyncContext();
            if (asyncContext != null) {
                HttpServletRequest req = (HttpServletRequest) asyncContext.getRequest();
                String connectionId = (String) req.getAttribute("sse.connectionId");
                if (connectionId != null && sseManager != null) {
                    SSEConnection connection = sseManager.removeConnection(connectionId);
                    if (connection != null) {
                        connection.close();
                        log.debug("Cleaned up SSE connection: {}", connectionId);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Error during connection cleanup: {}", e.getMessage());
        }
    }

    /**
     * Gets the SSE manager for this servlet.
     *
     * @return the SSE manager
     */
    public SSEManager getSseManager() {
        return sseManager;
    }

    /**
     * Parses a JSON event request into an SSEEventRequest object.
     * Simple JSON parser for basic event structure.
     */
    private SSEEventRequest parseEventRequest(String json) {
        try {
            // trim whitespace and validate basic JSON structure
            json = json.trim();
            if (json.startsWith("{") && json.endsWith("}")) {
                JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
                if (jsonObject != null) {
                    log.debug("Parsed JSON successfully: {}", jsonObject.toString());
                    SSEEventRequest request = new SSEEventRequest();
                    jsonObject.entrySet().forEach(entry -> {
                        String key = entry.getKey();
                        String value = entry.getValue().isJsonObject() ? entry.getValue().toString() : entry.getValue().getAsString();
                        log.debug("JSON field: {} = {}", key, value);
                        switch (key) {
                            case "event":
                                request.event = value;
                                break;
                            case "data":
                                request.data = value;
                                break;
                            case "connectionId":
                                request.connectionId = value;
                                break;
                            case "scope":
                                request.scope = value;
                                break;
                        }
                    });
                    // Validate required fields
                    if (request.event != null && request.data != null) {
                        log.debug("Parsed event request: event='{}', data='{}'", request.event, request.data);
                        return request;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error parsing event request JSON: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Resolves a scope by name.
     */
    private IScope resolveScope(String scopeName) {
        if (server == null) {
            return null;
        }
        IGlobalScope globalScope = server.getGlobal("default");
        if (globalScope == null) {
            return null;
        }
        return ScopeUtils.resolveScope(globalScope, scopeName);
    }

    /**
     * Simple data class for SSE event requests.
     */
    private static class SSEEventRequest {
        String event;

        String data;

        String connectionId; // Optional: target specific connection

        String scope; // Optional: target specific scope
    }

}