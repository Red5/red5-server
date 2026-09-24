package org.red5.server.net.sse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Verifies SSE endpoint hardening: CORS allowlist, disabled posts, body limits and event field sanitization (issue #468).
 */
public class SSEServletHardeningTest {

    @Test
    public void testNoCorsHeadersWithoutAllowlist() throws Exception {
        Map<String, String> headers = new HashMap<>();
        servlet(Collections.emptyMap()).doOptions(request("OPTIONS", "https://evil.example"), response(headers, new int[1]));
        assertNull(headers.get("Access-Control-Allow-Origin"));
        assertNull(headers.get("Access-Control-Allow-Credentials"));
    }

    @Test
    public void testAllowlistedOriginIsReflected() throws Exception {
        SSEServlet servlet = servlet(Map.of("allowedOrigins", "https://app.example.com"));
        Map<String, String> headers = new HashMap<>();
        servlet.doOptions(request("OPTIONS", "https://app.example.com"), response(headers, new int[1]));
        assertEquals("https://app.example.com", headers.get("Access-Control-Allow-Origin"));
        assertEquals("true", headers.get("Access-Control-Allow-Credentials"));
        headers.clear();
        servlet.doOptions(request("OPTIONS", "https://app.example.com.evil.net"), response(headers, new int[1]));
        assertNull(headers.get("Access-Control-Allow-Origin"));
    }

    @Test
    public void testWildcardNeverAllowsCredentials() throws Exception {
        Map<String, String> headers = new HashMap<>();
        servlet(Map.of("allowedOrigins", "*")).doOptions(request("OPTIONS", "https://any.example"), response(headers, new int[1]));
        assertEquals("*", headers.get("Access-Control-Allow-Origin"));
        assertNull(headers.get("Access-Control-Allow-Credentials"));
    }

    @Test
    public void testPostIsDisabledByDefault() throws Exception {
        int[] status = new int[1];
        servlet(Collections.emptyMap()).doPost(request("POST", null), response(new HashMap<>(), status));
        assertEquals(HttpServletResponse.SC_METHOD_NOT_ALLOWED, status[0]);
    }

    @Test
    public void testBodyLimit() throws Exception {
        assertEquals("abc", SSEServlet.readBody(new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)), 3));
        assertNull(SSEServlet.readBody(new ByteArrayInputStream(new byte[100_000]), 65_536));
    }

    @Test
    public void testEventFieldsCannotInjectLines() {
        String formatted = new SSEEvent("1\nid: 2", "evt\r\nevent: forged", "line1\rdata: x\r\nline3", null).toSSEFormat();
        assertFalse(formatted.contains("\nevent: forged"));
        assertFalse(formatted.contains("\nid: 2"));
        assertFalse(formatted.contains("\r"));
        assertTrue(formatted.contains("event: evtevent: forged\n"));
        assertTrue(formatted.contains("data: line1\ndata: data: x\ndata: line3\n"));
    }

    private static SSEServlet servlet(Map<String, String> params) throws Exception {
        ServletContext context = proxy(ServletContext.class, (name, args) -> null);
        ServletConfig config = proxy(ServletConfig.class, (name, args) -> {
            switch (name) {
                case "getInitParameter":
                    return params.get(args[0]);
                case "getInitParameterNames":
                    return Collections.enumeration(params.keySet());
                case "getServletContext":
                    return context;
                case "getServletName":
                    return "sse";
                default:
                    return null;
            }
        });
        SSEServlet servlet = new SSEServlet();
        servlet.init(config);
        return servlet;
    }

    private static HttpServletRequest request(String method, String origin) {
        return proxy(HttpServletRequest.class, (name, args) -> {
            switch (name) {
                case "getHeader":
                    return "Origin".equals(args[0]) ? origin : null;
                case "getMethod":
                    return method;
                case "getContentType":
                    return "application/json";
                case "getRemoteAddr":
                    return "127.0.0.1";
                case "getRequestURI":
                    return "/live/events";
                default:
                    return null;
            }
        });
    }

    private static HttpServletResponse response(Map<String, String> headers, int[] status) {
        return proxy(HttpServletResponse.class, (name, args) -> {
            switch (name) {
                case "setHeader":
                case "addHeader":
                    headers.put((String) args[0], (String) args[1]);
                    return null;
                case "setStatus":
                case "sendError":
                    status[0] = (Integer) args[0];
                    return null;
                default:
                    return null;
            }
        });
    }

    interface Handler {
        Object handle(String name, Object[] args);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (p, method, args) -> {
            Object result = handler.handle(method.getName(), args);
            if (result == null && method.getReturnType() == boolean.class) {
                return false;
            }
            if (result == null && method.getReturnType() == int.class) {
                return 0;
            }
            if (result == null && method.getReturnType() == long.class) {
                return 0L;
            }
            return result;
        });
    }

}
