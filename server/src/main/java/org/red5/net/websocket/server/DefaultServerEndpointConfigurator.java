package org.red5.net.websocket.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Stream;

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
 * Red5 implementation of the WebSocket JSR365 ServerEndpointConfig.Configurator.
 *
 * @author Paul Gregoire
 */
public class DefaultServerEndpointConfigurator extends ServerEndpointConfig.Configurator {

    private final Logger log = LoggerFactory.getLogger(DefaultServerEndpointConfigurator.class);

    // application scope associated with this endpoint configurator
    private IScope applicationScope;

    // Cross-origin policy enable/disabled (defaults to the plugin's setting)
    private boolean crossOriginPolicy = WebSocketPlugin.isCrossOriginPolicy();

    // Cross-origin names (defaults to the plugin's setting)
    private String[] allowedOrigins = WebSocketPlugin.getAllowedOrigins();

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

    /** {@inheritDoc} */
    @Override
    public boolean checkOrigin(String originHeaderValue) {
        log.debug("checkOrigin: {}", originHeaderValue);
        // if CORS is enabled
        if (crossOriginPolicy) {
            log.debug("allowedOrigins: {}", Arrays.toString(allowedOrigins));
            // allow "*" == any / all or origin suffix matches
            Optional<String> opt = Stream.of(allowedOrigins).filter(origin -> "*".equals(origin) || origin.endsWith(originHeaderValue)).findFirst();
            // non-match fail
            if (!opt.isPresent()) {
                log.info("Origin: {} did not match the allowed: {}", originHeaderValue, allowedOrigins);
                return false;
            }
        }
        return true;
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
        // trim off any non-path endings (like /?id=xxx)
        int idx = -1;
        if ((idx = path.lastIndexOf('/')) != -1) {
            path = path.substring(0, idx);
        }
        // get the manager
        WebSocketPlugin plugin = (WebSocketPlugin) PluginRegistry.getPlugin(WebSocketPlugin.NAME);
        WebSocketScopeManager manager = plugin.getManager(path);
        if (manager != null) {
            // add the websocket scope manager to the user props
            sec.getUserProperties().put(WSConstants.WS_MANAGER, manager);
            // get the associated scope
            WebSocketScope scope = manager.getScope(path);
            log.debug("WebSocketScope: {}", scope);
            if (scope == null) {
                // split up the path into usable scope names
                String[] paths = path.split("\\/");
                // parent scope
                IScope appScope = Optional.ofNullable(applicationScope).orElse(plugin.getApplicationScope(path));
                IScope parentScope = appScope;
                // room scope
                IScope roomScope = null;
                // create child scopes
                log.debug("Creating child websocket scope of {} for path: {} split: {}", applicationScope, path, Arrays.toString(paths));
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
                // create and add the websocket scope for the new room scope
                manager.makeScope(roomScope);
                // get the new ws scope
                scope = manager.getScope(path);
                // copy the listeners from the app websocket scope
                Set<IWebSocketDataListener> listeners = ((WebSocketScope) appScope.getAttribute(WSConstants.WS_SCOPE)).getListeners();
                for (IWebSocketDataListener listener : listeners) {
                    log.debug("Adding listener: {}", listener);
                    scope.addListener(listener);
                }
            }
            // add the websocket scope to the user props
            sec.getUserProperties().put(WSConstants.WS_SCOPE, scope);
            // run through any modifiers
            handshakeModifiers.forEach(modifier -> {
                modifier.modifyHandshake(request, response);
            });
        } else {
            log.warn("No websocket manager found for path: {} requested uri: {}", path, request.getRequestURI().toString());
        }
        super.modifyHandshake(sec, request, response);
    }

    /**
     * <p>Getter for the field <code>applicationScope</code>.</p>
     *
     * @return a {@link org.red5.server.api.scope.IScope} object
     */
    public IScope getApplicationScope() {
        return applicationScope;
    }

    /**
     * <p>Setter for the field <code>applicationScope</code>.</p>
     *
     * @param applicationScope a {@link org.red5.server.api.scope.IScope} object
     */
    public void setApplicationScope(IScope applicationScope) {
        this.applicationScope = applicationScope;
    }

    /**
     * <p>isCrossOriginPolicy.</p>
     *
     * @return a boolean
     */
    public boolean isCrossOriginPolicy() {
        return crossOriginPolicy;
    }

    /**
     * <p>Setter for the field <code>crossOriginPolicy</code>.</p>
     *
     * @param crossOriginPolicy a boolean
     */
    public void setCrossOriginPolicy(boolean crossOriginPolicy) {
        this.crossOriginPolicy = crossOriginPolicy;
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
     * Sets the allowed origins for this instance.
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
