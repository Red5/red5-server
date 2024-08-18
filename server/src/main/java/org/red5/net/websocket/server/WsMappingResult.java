package org.red5.net.websocket.server;

import java.util.Map;

import jakarta.websocket.server.ServerEndpointConfig;

class WsMappingResult {

    private final ServerEndpointConfig config;

    private final Map<String, String> pathParams;

    WsMappingResult(ServerEndpointConfig config, Map<String, String> pathParams) {
        this.config = config;
        this.pathParams = pathParams;
    }

    ServerEndpointConfig getConfig() {
        return config;
    }

    Map<String, String> getPathParams() {
        return pathParams;
    }

}