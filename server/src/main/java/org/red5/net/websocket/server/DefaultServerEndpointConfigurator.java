package org.red5.net.websocket.server;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Stream;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.websocket.Extension;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;

import org.red5.net.websocket.WSConstants;
import org.red5.net.websocket.WebSocketPlugin;
import org.red5.net.websocket.WebSocketScope;
import org.red5.net.websocket.WebSocketScopeManager;
import org.red5.net.websocket.listener.IWebSocketDataListener;
import org.red5.server.api.scope.IScope;
import org.red5.server.plugin.PluginRegistry;
import org.red5.server.util.ScopeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Red5 implementation of the WebSocket JSR365 ServerEndpointConfig.Configurator. One instance serves one web application on one host and
 * is bound to that application's scope.
 * <p>
 * Origin checking starts from the {@link WebSocketPlugin} defaults and is set per web application with these web.xml context-params (or
 * servlet context attributes of the same names):
 *
 * <pre>
 * &lt;context-param&gt;
 *     &lt;param-name&gt;crossOriginPolicy&lt;/param-name&gt;
 *     &lt;param-value&gt;true&lt;/param-value&gt;
 * &lt;/context-param&gt;
 * &lt;context-param&gt;
 *     &lt;param-name&gt;allowedOrigins&lt;/param-name&gt;
 *     &lt;param-value&gt;https://example.com,https://example.com:8443&lt;/param-value&gt;
 * &lt;/context-param&gt;
 * </pre>
 *
 * <code>crossOriginPolicy</code> is true by default: {@link #checkOrigin(String, String)} accepts a handshake from the request's own
 * origin, see {@link #isSameOrigin(String, String)}, or from an entry of <code>allowedOrigins</code>, see
 * {@link #originMatches(String, String)}, and rejects any other with 403. <code>allowedOrigins</code> is empty by default, so only
 * same-origin handshakes are accepted; an entry without a scheme matches its host, and port if given, under any scheme, and
 * <code>*</code> accepts any origin. A handshake without an Origin header comes from a non-browser client and is accepted unless the
 * <code>allowMissingOrigin</code> context-param is false. Setting <code>crossOriginPolicy</code> to false accepts every origin. The HTTP
 * principal, session and cookies of the upgrade request are carried into the WebSocket connection, so with checking off any site the user
 * visits can open an authenticated connection.
 *
 * @author Paul Gregoire
 */
public class DefaultServerEndpointConfigurator extends ServerEndpointConfig.Configurator {

    /**
     * Maximum number of room levels below the application a websocket path may create, configurable with the
     * red5.websocket.max_room_depth system property.
     */
    public static final int MAX_ROOM_DEPTH = Integer.getInteger("red5.websocket.max_room_depth", 4);

    private static final Pattern ROOM_NAME = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final Logger log = LoggerFactory.getLogger(DefaultServerEndpointConfigurator.class);

    // application scope of the web application served by this endpoint configurator; handshakes only use this scope's manager
    private volatile IScope applicationScope;

    // Cross-origin policy enable/disabled (defaults to the plugin's setting)
    private boolean crossOriginPolicy = WebSocketPlugin.isCrossOriginPolicy();

    // Cross-origin names (defaults to the plugin's setting)
    private String[] allowedOrigins = WebSocketPlugin.getAllowedOrigins();

    // Whether a handshake without an Origin header is accepted while checking is on (defaults to the plugin's setting)
    private boolean allowMissingOrigin = WebSocketPlugin.isAllowMissingOrigin();

    // holds handshake modification implementations
    private CopyOnWriteArraySet<HandshakeModifier> handshakeModifiers = new CopyOnWriteArraySet<>();

    /** {@inheritDoc} */
    @Override
    public <T> T getEndpointInstance(Class<T> clazz) throws InstantiationException {
        log.debug("getEndpointInstance: {}", clazz.getName());
        try {
            return clazz.getConstructor().newInstance();
        } catch (InstantiationException e) {
            throw e;
        } catch (ReflectiveOperationException e) {
            InstantiationException ie = new InstantiationException();
            ie.initCause(e);
            throw ie;
        }
    }

    /** {@inheritDoc} */
    @Override
    public String getNegotiatedSubprotocol(List<String> supported, List<String> requested) {
        log.debug("getNegotiatedSubprotocol - supported: {} requested: {}", supported, requested);
        if (supported.contains("*")) {
            // return the first one in the list
            return requested.isEmpty() ? "" : requested.get(0);
        } else {
            for (String request : requested) {
                if (supported.contains(request)) {
                    return request;
                }
            }
        }
        return "";
    }

    /** {@inheritDoc} */
    @Override
    public List<Extension> getNegotiatedExtensions(List<Extension> installed, List<Extension> requested) {
        log.debug("getNegotiatedExtensions - installed: {} requested: {}", installed, requested);
        Set<String> installedNames = new HashSet<>();
        for (Extension e : installed) {
            installedNames.add(e.getName());
        }
        List<Extension> result = new ArrayList<>();
        for (Extension request : requested) {
            if (installedNames.contains(request.getName())) {
                result.add(request);
            }
        }
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * Without the request this cannot recognize a same-origin handshake, so only allowed origins are accepted; the upgrade uses
     * {@link #checkOrigin(String, String)} with the request's host.
     */
    @Override
    public boolean checkOrigin(String originHeaderValue) {
        return checkOrigin(originHeaderValue, null);
    }

    /**
     * Checks the Origin header of a handshake. With origin checking disabled every handshake is accepted. With it enabled, the default, a
     * handshake is accepted when its origin is the request's own, see {@link #isSameOrigin(String, String)}, or matches an allowed origin,
     * see {@link #originMatches(String, String)}; a handshake without an Origin header is accepted when allowMissingOrigin is true, the
     * default.
     *
     * @param originHeaderValue Origin header value, or null when the header is missing
     * @param requestHost host and optional port the request was made to, see {@link #requestHost(HttpServletRequest)}, or null if unknown
     * @return true if the handshake may proceed
     */
    public boolean checkOrigin(String originHeaderValue, String requestHost) {
        log.debug("checkOrigin: {} host: {}", originHeaderValue, requestHost);
        if (!crossOriginPolicy) {
            return true;
        }
        if (originHeaderValue == null) {
            // browsers always send an Origin, so this is a non-browser client
            if (!allowMissingOrigin) {
                log.info("Origin header is missing and missing origins are not allowed");
                return false;
            }
            return true;
        }
        if (requestHost != null && isSameOrigin(originHeaderValue, requestHost)) {
            return true;
        }
        log.debug("allowedOrigins: {}", Arrays.toString(allowedOrigins));
        // allow "*" == any / all, otherwise the origin must equal an allowed origin
        if (!Stream.of(allowedOrigins).anyMatch(allowed -> originMatches(allowed, originHeaderValue))) {
            log.info("Origin: {} is not the request's own ({}) and did not match the allowed: {}", originHeaderValue, requestHost, Arrays.toString(allowedOrigins));
            return false;
        }
        return true;
    }

    /**
     * Returns the host, and port if given, that a request was made to: the first X-Forwarded-Host value when a reverse proxy set one,
     * otherwise the Host header. A browser cannot set either header on a WebSocket handshake, so a cross-site page cannot use them to pass
     * as same-origin.
     *
     * @param request upgrade request
     * @return host[:port], or null when the request carries neither header
     */
    public static String requestHost(HttpServletRequest request) {
        String host = request.getHeader("X-Forwarded-Host");
        if (host != null && !host.isBlank()) {
            int comma = host.indexOf(',');
            return (comma == -1 ? host : host.substring(0, comma)).trim();
        }
        host = request.getHeader("Host");
        return (host == null || host.isBlank()) ? null : host.trim();
    }

    /**
     * Returns whether an Origin header names the site a request was made to: its host equals the request host, case ignored, and its
     * port equals the request host's port or, when the request host gives none, the default port of the Origin's scheme. The scheme is not
     * compared, since a TLS terminating proxy hides it, so a page served over http may connect to the same host and port over wss.
     *
     * @param origin Origin header value
     * @param requestHost host[:port] the request was made to
     * @return true if the origin is the request's own
     */
    public static boolean isSameOrigin(String origin, String requestHost) {
        if (origin == null || requestHost == null || requestHost.isBlank()) {
            return false;
        }
        String[] actual = parseOrigin(origin.trim());
        if (actual == null) {
            return false;
        }
        // under the origin's scheme, so a request host without a port gets that scheme's default port
        String[] expected = parseOrigin(actual[0] + "://" + requestHost.trim());
        return expected != null && expected[1].equals(actual[1]) && expected[2].equals(actual[2]);
    }

    /**
     * Compares an Origin header with an allowed origin. "*" allows any origin. An allowed origin with a scheme, such as
     * https://example.com:8443, must equal the header's scheme, host and port, with default ports filled in and case ignored. An
     * allowed origin without a scheme, such as example.com or example.com:8443, matches that exact host, and port if given, under any
     * scheme. Suffixes and sub-domains never match.
     *
     * @param allowed allowed origin
     * @param origin Origin header value
     * @return true if the origin is allowed
     */
    public static boolean originMatches(String allowed, String origin) {
        if (allowed == null) {
            return false;
        }
        allowed = allowed.trim();
        if ("*".equals(allowed)) {
            return true;
        }
        String[] actual = parseOrigin(origin.trim());
        if (actual == null) {
            return false;
        }
        if (allowed.contains("://")) {
            String[] expected = parseOrigin(allowed);
            return expected != null && Arrays.equals(expected, actual);
        }
        // host[:port] without a scheme
        String[] expected = parseOrigin("scheme://" + allowed);
        if (expected == null) {
            return false;
        }
        boolean portGiven = allowed.lastIndexOf(':') > allowed.lastIndexOf(']');
        return expected[1].equals(actual[1]) && (!portGiven || expected[2].equals(actual[2]));
    }

    /**
     * Parses a serialized origin into lower-case scheme, host and port, filling in the default port for http, https, ws and wss.
     *
     * @param origin origin such as https://example.com
     * @return scheme, host and port, or null when it is not a valid origin
     */
    private static String[] parseOrigin(String origin) {
        try {
            URI uri = new URI(origin);
            String scheme = uri.getScheme(), host = uri.getHost();
            if (scheme == null || host == null || uri.getRawUserInfo() != null || (uri.getRawPath() != null && !uri.getRawPath().isEmpty() && !"/".equals(uri.getRawPath())) || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                return null;
            }
            scheme = scheme.toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            if (port == -1) {
                switch (scheme) {
                    case "http":
                    case "ws":
                        port = 80;
                        break;
                    case "https":
                    case "wss":
                        port = 443;
                        break;
                    default:
                        break;
                }
            }
            return new String[] { scheme, host.toLowerCase(Locale.ROOT), String.valueOf(port) };
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
        //log.debug("modifyHandshake - config: {} req: {} resp: {}", sec, request, response);
        // get the path for this request
        String path = request.getRequestURI().toString();
        log.debug("Request URI: {}", path);
        // trim websocket protocol etc from the path
        // look for ws:// or wss:// prefixed paths
        if (path.startsWith("wss")) {
            path = path.substring(6);
            // now skip to first slash
            path = path.substring(path.indexOf('/'));
        } else if (path.startsWith("ws")) {
            path = path.substring(5);
            // now skip to first slash
            path = path.substring(path.indexOf('/'));
        }
        log.debug("Stripped path: {}", path);
        // trim off any non-path endings (like ?id=xxx or fragments)
        int idx = path.indexOf('?');
        if (idx == -1) {
            idx = path.indexOf('#');
        }
        if (idx != -1) {
            path = path.substring(0, idx);
        }
        // normalize path by removing trailing slashes (e.g., "/live/" -> "/live")
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        log.debug("Normalized path: {}", path);
        // get the manager of the application scope bound to this endpoint, never one found by application name
        IScope appScope = applicationScope;
        WebSocketPlugin plugin = (WebSocketPlugin) PluginRegistry.getPlugin(WebSocketPlugin.NAME);
        WebSocketScopeManager manager = (appScope != null && plugin != null) ? plugin.getManager(appScope) : null;
        if (manager != null) {
            // add the websocket scope manager to the user props
            sec.getUserProperties().put(WSConstants.WS_MANAGER, manager);
            // get the associated scope
            WebSocketScope scope = manager.getScope(path);
            log.debug("WebSocketScope: {}", scope);
            if (scope == null && !isValidRoomPath(path)) {
                log.warn("Refusing to create websocket room scopes for invalid path: {}", path);
            } else if (scope == null) {
                // split up the path into usable scope names
                String[] paths = path.split("\\/");
                // parent scope is the bound application scope
                IScope parentScope = appScope;
                // room scope
                IScope roomScope = null;
                // create child scopes
                log.debug("Creating child websocket scope of {} for path: {} split: {}", appScope, path, Arrays.toString(paths));
                for (int i = 2; i < paths.length; i++) {
                    // start with the first room and proceed from there
                    roomScope = ScopeUtils.resolveScope(parentScope, paths[i]);
                    // if the room scope doesnt already exist create it
                    if (roomScope == null) {
                        if (parentScope.createChildScope(paths[i])) {
                            roomScope = ScopeUtils.resolveScope(parentScope, paths[i]);
                        }
                    }
                    log.debug("Parent scope: {} room scope: {}", parentScope, roomScope);
                    if (roomScope != null) {
                        parentScope = roomScope;
                    }
                }
                // create and add the websocket scope for the room or app scope
                IScope scopeToUse = roomScope != null ? roomScope : appScope;
                if (scopeToUse != null) {
                    manager.makeScope(scopeToUse);
                    // get the new ws scope
                    scope = manager.getScope(path);
                    // copy the listeners from the app websocket scope if available
                    if (appScope.hasAttribute(WSConstants.WS_SCOPE)) {
                        WebSocketScope appWsScope = (WebSocketScope) appScope.getAttribute(WSConstants.WS_SCOPE);
                        if (appWsScope != null && scope != null) {
                            Set<IWebSocketDataListener> listeners = appWsScope.getListeners();
                            for (IWebSocketDataListener listener : listeners) {
                                log.debug("Adding listener: {}", listener);
                                scope.addListener(listener);
                            }
                        }
                    }
                } else {
                    log.warn("Cannot create websocket scope - no valid scope available for path: {}", path);
                }
            }
            // add the websocket scope to the user props if available
            if (scope != null) {
                sec.getUserProperties().put(WSConstants.WS_SCOPE, scope);
                // run through any modifiers
                handshakeModifiers.forEach(modifier -> {
                    modifier.modifyHandshake(request, response);
                });
            } else {
                log.warn("WebSocket scope is null for path: {} - handshake may fail", path);
            }
        } else if (appScope == null) {
            log.warn("No application scope is bound to the websocket endpoint for path: {} requested uri: {}", path, request.getRequestURI().toString());
        } else {
            log.warn("No websocket manager found for application scope: {} path: {} requested uri: {}", appScope, path, request.getRequestURI().toString());
        }
        super.modifyHandshake(sec, request, response);
    }

    /**
     * Binds this configurator to the application scope of the given web application, unless it is already bound to an application scope
     * that still has a websocket manager. Called before each handshake so the binding follows an application that has been reloaded.
     *
     * @param servletContext servlet context of the web application served by this configurator
     */
    void bindApplicationScope(ServletContext servletContext) {
        IScope current = applicationScope;
        if (current != null) {
            WebSocketPlugin plugin = (WebSocketPlugin) PluginRegistry.getPlugin(WebSocketPlugin.NAME);
            if (plugin == null || plugin.getManager(current) != null) {
                return;
            }
        }
        IScope resolved = WebSocketPlugin.resolveApplicationScope(servletContext);
        if (resolved != null && resolved != current) {
            log.debug("Binding websocket endpoint to application scope: {}", resolved);
            applicationScope = resolved;
        }
    }

    /**
     * Returns whether the room segments of a request path, those after the application name, may be created as scopes: at most
     * {@link #MAX_ROOM_DEPTH} segments of letters, digits, '.', '_' or '-', up to 64 characters each, and not "." or "..".
     *
     * @param path normalized request path, such as /app/room/subroom
     * @return true if the room segments are acceptable
     */
    static boolean isValidRoomPath(String path) {
        String[] paths = path.split("\\/");
        if (paths.length - 2 > MAX_ROOM_DEPTH) {
            return false;
        }
        for (int i = 2; i < paths.length; i++) {
            String name = paths[i];
            if (!ROOM_NAME.matcher(name).matches() || ".".equals(name) || "..".equals(name)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the application scope this configurator is bound to; handshakes are served by that scope's websocket manager only.
     *
     * @return a {@link org.red5.server.api.scope.IScope} object or null if not bound yet
     */
    public IScope getApplicationScope() {
        return applicationScope;
    }

    /**
     * Binds this configurator to an application scope. When not set, or when the bound scope no longer has a websocket manager, the scope
     * is resolved from the web application's <code>web.scope</code> or <code>web.handler</code> bean before the next handshake.
     *
     * @param applicationScope a {@link org.red5.server.api.scope.IScope} object
     */
    public void setApplicationScope(IScope applicationScope) {
        this.applicationScope = applicationScope;
    }

    /**
     * Returns whether Origin checking is enabled for this web application.
     *
     * @return a boolean
     */
    public boolean isCrossOriginPolicy() {
        return crossOriginPolicy;
    }

    /**
     * Enables or disables Origin checking for this web application; set from the <code>crossOriginPolicy</code> context-param when present.
     *
     * @param crossOriginPolicy a boolean
     */
    public void setCrossOriginPolicy(boolean crossOriginPolicy) {
        this.crossOriginPolicy = crossOriginPolicy;
    }

    /**
     * Returns whether a handshake without an Origin header is accepted while checking is on.
     *
     * @return a boolean
     */
    public boolean isAllowMissingOrigin() {
        return allowMissingOrigin;
    }

    /**
     * Sets whether a handshake without an Origin header is accepted while checking is on; set from the <code>allowMissingOrigin</code>
     * context-param when present.
     *
     * @param allowMissingOrigin a boolean
     */
    public void setAllowMissingOrigin(boolean allowMissingOrigin) {
        this.allowMissingOrigin = allowMissingOrigin;
    }

    /**
     * <p>Getter for the field <code>allowedOrigins</code>.</p>
     *
     * @return an array of {@link java.lang.String} objects
     */
    public String[] getAllowedOrigins() {
        return allowedOrigins;
    }

    /**
     * Sets the allowed origins for this instance; set from the comma-separated <code>allowedOrigins</code> context-param when present.
     *
     * @param allowedOrigins an array of {@link java.lang.String} objects
     */
    public void setAllowedOrigins(String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
        log.debug("allowedOrigins: {}", Arrays.toString(allowedOrigins));
    }

    /**
     * Adds a HandshakeModifier implementation to the instances modifiers.
     *
     * @param modifier a {@link org.red5.net.websocket.server.HandshakeModifier} object
     * @return true if added and false otherwise
     */
    public boolean addHandshakeModifier(HandshakeModifier modifier) {
        return handshakeModifiers.add(modifier);
    }

    /**
     * Removes a HandshakeModifier implementation from the instances modifiers.
     *
     * @param modifier a {@link org.red5.net.websocket.server.HandshakeModifier} object
     * @return true if removed and false otherwise
     */
    public boolean removeHandshakeModifier(HandshakeModifier modifier) {
        return handshakeModifiers.remove(modifier);
    }

}
