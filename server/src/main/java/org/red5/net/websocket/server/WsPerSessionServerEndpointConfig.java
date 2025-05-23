package org.red5.net.websocket.server;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.websocket.Decoder;
import jakarta.websocket.Encoder;
import jakarta.websocket.Extension;
import jakarta.websocket.server.ServerEndpointConfig;

/**
 * Wraps the provided {@link jakarta.websocket.server.ServerEndpointConfig} and provides a per session view - the difference being that the map returned by
 * {@link #getUserProperties()} is unique to this instance rather than shared with the wrapped {@link jakarta.websocket.server.ServerEndpointConfig}.
 *
 * @author mondain
 */
public class WsPerSessionServerEndpointConfig implements ServerEndpointConfig {

    private final ServerEndpointConfig perEndpointConfig;

    private final Map<String, Object> perSessionUserProperties = new ConcurrentHashMap<>();

    WsPerSessionServerEndpointConfig(ServerEndpointConfig perEndpointConfig) {
        this.perEndpointConfig = perEndpointConfig;
        perSessionUserProperties.putAll(perEndpointConfig.getUserProperties());
    }

    /** {@inheritDoc} */
    @Override
    public List<Class<? extends Encoder>> getEncoders() {
        return perEndpointConfig.getEncoders();
    }

    /** {@inheritDoc} */
    @Override
    public List<Class<? extends Decoder>> getDecoders() {
        return perEndpointConfig.getDecoders();
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> getUserProperties() {
        return perSessionUserProperties;
    }

    /** {@inheritDoc} */
    @Override
    public Class<?> getEndpointClass() {
        return perEndpointConfig.getEndpointClass();
    }

    /** {@inheritDoc} */
    @Override
    public String getPath() {
        return perEndpointConfig.getPath();
    }

    /** {@inheritDoc} */
    @Override
    public List<String> getSubprotocols() {
        return perEndpointConfig.getSubprotocols();
    }

    /** {@inheritDoc} */
    @Override
    public List<Extension> getExtensions() {
        return perEndpointConfig.getExtensions();
    }

    /** {@inheritDoc} */
    @Override
    public Configurator getConfigurator() {
        return perEndpointConfig.getConfigurator();
    }
}
