package org.red5.net.websocket.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.websocket.Endpoint;
import javax.websocket.Extension;
import javax.websocket.HandshakeResponse;
import javax.websocket.server.ServerEndpointConfig;

import org.apache.tomcat.util.codec.binary.Base64;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.security.ConcurrentMessageDigest;
import org.apache.tomcat.websocket.Constants;
import org.apache.tomcat.websocket.Transformation;
import org.apache.tomcat.websocket.TransformationFactory;
import org.apache.tomcat.websocket.Util;
import org.apache.tomcat.websocket.WsHandshakeResponse;
import org.apache.tomcat.websocket.pojo.PojoEndpointServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpgradeUtil {

    private static final Logger log = LoggerFactory.getLogger(UpgradeUtil.class);

    private static final StringManager sm = StringManager.getManager(UpgradeUtil.class.getPackage().getName());

    private static final byte[] WS_ACCEPT = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11".getBytes(StandardCharsets.ISO_8859_1);

    private UpgradeUtil() {
        // Utility class. Hide default constructor.
    }

    /**
     * Checks to see if this is an HTTP request that includes a valid upgrade request to web socket.
     * <p>
     * Note: RFC 2616 does not limit HTTP upgrade to GET requests but the Java WebSocket spec 1.0, section 8.2 implies such a limitation and RFC 6455 section 4.1 requires that a WebSocket Upgrade uses GET.
     *
     * @param request
     *            The request to check if it is an HTTP upgrade request for a WebSocket connection
     * @param response
     *            The response associated with the request
     * @return <code>true</code> if the request includes a HTTP Upgrade request for the WebSocket protocol, otherwise <code>false</code>
     */
    public static boolean isWebSocketUpgradeRequest(ServletRequest request, ServletResponse response) {
        if (log.isTraceEnabled()) {
            List<String> headers = new ArrayList<>();
            Enumeration<String> en = ((HttpServletRequest) request).getHeaderNames();
            while (en.hasMoreElements()) {
                headers.add(en.nextElement());
            }
            log.trace("Headers: {}", headers);
        }
        log.debug("isWebSocketUpgradeRequest: {}", Constants.UPGRADE_HEADER_VALUE.equalsIgnoreCase(((HttpServletRequest) request).getHeader(Constants.UPGRADE_HEADER_NAME)));
        return ((request instanceof HttpServletRequest) && (response instanceof HttpServletResponse) && Constants.UPGRADE_HEADER_VALUE.equalsIgnoreCase(((HttpServletRequest) request).getHeader(Constants.UPGRADE_HEADER_NAME)));
    }

    public static void doUpgrade(DefaultWsServerContainer sc, HttpServletRequest req, HttpServletResponse resp, ServerEndpointConfig sec, Map<String, String> pathParams) throws ServletException, IOException {
        log.debug("doUpgrade - sc: {} sec: {} params: {}", sc, sec, pathParams);
        // Validate the rest of the headers and reject the request if that validation fails
        String key;
        String subProtocol = null;
        if (!headerContainsToken(req, Constants.CONNECTION_HEADER_NAME, Constants.CONNECTION_HEADER_VALUE)) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        if (!headerContainsToken(req, Constants.WS_VERSION_HEADER_NAME, Constants.WS_VERSION_HEADER_VALUE)) {
            resp.setStatus(426);
            resp.setHeader(Constants.WS_VERSION_HEADER_NAME, Constants.WS_VERSION_HEADER_VALUE);
            return;
        }
        key = req.getHeader(Constants.WS_KEY_HEADER_NAME);
        if (key == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        // Origin check
        String origin = req.getHeader(Constants.ORIGIN_HEADER_NAME);
        if (!sec.getConfigurator().checkOrigin(origin)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        // Sub-protocols
        List<String> subProtocols = getTokensFromHeader(req, Constants.WS_PROTOCOL_HEADER_NAME);
        subProtocol = sec.getConfigurator().getNegotiatedSubprotocol(sec.getSubprotocols(), subProtocols);
        // Extensions
        // Should normally only be one header but handle the case of multiple headers
        List<Extension> extensionsRequested = new ArrayList<>();
        Enumeration<String> extHeaders = req.getHeaders(Constants.WS_EXTENSIONS_HEADER_NAME);
        while (extHeaders.hasMoreElements()) {
            Util.parseExtensionHeader(extensionsRequested, extHeaders.nextElement());
        }
        // Negotiation phase 1. By default this simply filters out the extensions that the server does not support but applications could
        // use a custom configurator to do more than this.
        List<Extension> installedExtensions = null;
        if (sec.getExtensions().size() == 0) {
            installedExtensions = Constants.INSTALLED_EXTENSIONS;
        } else {
            installedExtensions = new ArrayList<>();
            installedExtensions.addAll(sec.getExtensions());
            installedExtensions.addAll(Constants.INSTALLED_EXTENSIONS);
        }
        List<Extension> negotiatedExtensionsPhase1 = sec.getConfigurator().getNegotiatedExtensions(installedExtensions, extensionsRequested);
        // Negotiation phase 2. Create the Transformations that will be applied to this connection. Note than an extension may be dropped at this
        // point if the client has requested a configuration that the server is unable to support.
        List<Transformation> transformations = createTransformations(negotiatedExtensionsPhase1);
        List<Extension> negotiatedExtensionsPhase2;
        if (transformations.isEmpty()) {
            negotiatedExtensionsPhase2 = Collections.emptyList();
        } else {
            negotiatedExtensionsPhase2 = new ArrayList<>(transformations.size());
            for (Transformation t : transformations) {
                negotiatedExtensionsPhase2.add(t.getExtensionResponse());
            }
        }
        // Build the transformation pipeline
        Transformation transformation = null;
        StringBuilder responseHeaderExtensions = new StringBuilder();
        boolean first = true;
        for (Transformation t : transformations) {
            if (first) {
                first = false;
            } else {
                responseHeaderExtensions.append(',');
            }
            append(responseHeaderExtensions, t.getExtensionResponse());
            if (transformation == null) {
                transformation = t;
            } else {
                transformation.setNext(t);
            }
        }
        // Now we have the full pipeline, validate the use of the RSV bits.
        if (transformation != null && !transformation.validateRsvBits(0)) {
            throw new ServletException(sm.getString("upgradeUtil.incompatibleRsv"));
        }
        // If we got this far, all is good. Accept the connection.
        resp.setHeader(Constants.UPGRADE_HEADER_NAME, Constants.UPGRADE_HEADER_VALUE);
        resp.setHeader(Constants.CONNECTION_HEADER_NAME, Constants.CONNECTION_HEADER_VALUE);
        resp.setHeader(HandshakeResponse.SEC_WEBSOCKET_ACCEPT, getWebSocketAccept(key));
        if (subProtocol != null && subProtocol.length() > 0) {
            // RFC6455 4.2.2 explicitly states "" is not valid here
            resp.setHeader(Constants.WS_PROTOCOL_HEADER_NAME, subProtocol);
        }
        if (!transformations.isEmpty()) {
            resp.setHeader(Constants.WS_EXTENSIONS_HEADER_NAME, responseHeaderExtensions.toString());
        }
        WsHandshakeRequest wsRequest = new WsHandshakeRequest(req, pathParams);
        WsHandshakeResponse wsResponse = new WsHandshakeResponse();
        WsPerSessionServerEndpointConfig perSessionServerEndpointConfig = new WsPerSessionServerEndpointConfig(sec);
        sec.getConfigurator().modifyHandshake(perSessionServerEndpointConfig, wsRequest, wsResponse);
        wsRequest.finished();
        // Add any additional headers
        for (Entry<String, List<String>> entry : wsResponse.getHeaders().entrySet()) {
            for (String headerValue : entry.getValue()) {
                resp.addHeader(entry.getKey(), headerValue);
            }
        }
        Endpoint ep;
        try {
            Class<?> clazz = sec.getEndpointClass();
            if (Endpoint.class.isAssignableFrom(clazz)) {
                ep = (Endpoint) sec.getConfigurator().getEndpointInstance(clazz);
            } else {
                ep = new PojoEndpointServer(pathParams, clazz);
                // Need to make path params available to POJO
                perSessionServerEndpointConfig.getUserProperties().put("org.apache.tomcat.websocket.pojo.PojoEndpoint.pathParams", pathParams);
                // removed in 8.5.x post .61
                //perSessionServerEndpointConfig.getUserProperties().put(org.apache.tomcat.websocket.pojo.Constants.POJO_PATH_PARAM_KEY, pathParams);
            }
        } catch (InstantiationException e) {
            throw new ServletException(e);
        }
        log.debug("About to upgrade http session: {} qs: {}", wsRequest.getHttpSession(), wsRequest.getQueryString());
        WsHttpUpgradeHandler wsHandler = req.upgrade(WsHttpUpgradeHandler.class);
        wsHandler.preInit(ep, perSessionServerEndpointConfig, sc, wsRequest, negotiatedExtensionsPhase2, subProtocol, transformation, pathParams, req.isSecure());
        log.debug("preinit completed");
    }

    private static List<Transformation> createTransformations(List<Extension> negotiatedExtensions) {
        TransformationFactory factory = TransformationFactory.getInstance();
        LinkedHashMap<String, List<List<Extension.Parameter>>> extensionPreferences = new LinkedHashMap<>();
        // Result will likely be smaller than this
        List<Transformation> result = new ArrayList<>(negotiatedExtensions.size());
        for (Extension extension : negotiatedExtensions) {
            List<List<Extension.Parameter>> preferences = extensionPreferences.get(extension.getName());
            if (preferences == null) {
                preferences = new ArrayList<>();
                extensionPreferences.put(extension.getName(), preferences);
            }
            preferences.add(extension.getParameters());
        }
        for (Map.Entry<String, List<List<Extension.Parameter>>> entry : extensionPreferences.entrySet()) {
            Transformation transformation = factory.create(entry.getKey(), entry.getValue(), true);
            if (transformation != null) {
                result.add(transformation);
            }
        }
        return result;
    }

    private static void append(StringBuilder sb, Extension extension) {
        if (extension == null || extension.getName() == null || extension.getName().length() == 0) {
            return;
        }
        sb.append(extension.getName());
        for (Extension.Parameter p : extension.getParameters()) {
            sb.append(';');
            sb.append(p.getName());
            if (p.getValue() != null) {
                sb.append('=');
                sb.append(p.getValue());
            }
        }
    }

    /*
     * This only works for tokens. Quoted strings need more sophisticated parsing.
     */
    private static boolean headerContainsToken(HttpServletRequest req, String headerName, String target) {
        Enumeration<String> headers = req.getHeaders(headerName);
        //log.debug("headerContainsToken - header name: {} target: {} headers: {}", headerName, target, headers);
        while (headers.hasMoreElements()) {
            String header = headers.nextElement();
            String[] tokens = header.split(",");
            for (String token : tokens) {
                if (target.equalsIgnoreCase(token.trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    /*
     * This only works for tokens. Quoted strings need more sophisticated parsing.
     */
    private static List<String> getTokensFromHeader(HttpServletRequest req, String headerName) {
        log.debug("getTokensFromHeader - header name: {}", headerName);
        List<String> result = new ArrayList<>();
        Enumeration<String> headers = req.getHeaders(headerName);
        while (headers.hasMoreElements()) {
            String header = headers.nextElement();
            String[] tokens = header.split(",");
            for (String token : tokens) {
                result.add(token.trim());
            }
        }
        return result;
    }

    private static String getWebSocketAccept(String key) {
        log.debug("getWebSocketAccept: {}", key);
        byte[] digest = ConcurrentMessageDigest.digestSHA1(key.getBytes(StandardCharsets.ISO_8859_1), WS_ACCEPT);
        return Base64.encodeBase64String(digest);
    }

}
