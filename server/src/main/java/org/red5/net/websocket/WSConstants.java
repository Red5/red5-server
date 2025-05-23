/*
 * RED5 Open Source Flash Server - https://github.com/red5 Copyright 2006-2018 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.net.websocket;

/**
 * Convenience class for holding constants.
 *
 * @author Paul Gregoire
 */
public class WSConstants {

    /** Constant <code>WS_UPGRADE_HANDLER="ws.upgrader"</code> */
    public final static String WS_UPGRADE_HANDLER = "ws.upgrader";

    /** Constant <code>WS_MANAGER="ws.manager"</code> */
    public final static String WS_MANAGER = "ws.manager";

    /** Constant <code>WS_SCOPE="ws.scope"</code> */
    public final static String WS_SCOPE = "ws.scope";

    /** Constant <code>WS_CONNECTION="ws.connection"</code> */
    public final static String WS_CONNECTION = "ws.connection";

    /** Constant <code>SESSION="session"</code> */
    public final static String SESSION = "session";

    /** Constant <code>WS_HANDSHAKE</code> */
    public final static Object WS_HANDSHAKE = "ws.handshake";

    /** Constant <code>WS_HEADER_KEY="Sec-WebSocket-Key"</code> */
    public final static String WS_HEADER_KEY = "Sec-WebSocket-Key";

    /** Constant <code>WS_HEADER_VERSION="Sec-WebSocket-Version"</code> */
    public final static String WS_HEADER_VERSION = "Sec-WebSocket-Version";

    /** Constant <code>WS_HEADER_EXTENSIONS="Sec-WebSocket-Extensions"</code> */
    public final static String WS_HEADER_EXTENSIONS = "Sec-WebSocket-Extensions";

    /** Constant <code>WS_HEADER_PROTOCOL="Sec-WebSocket-Protocol"</code> */
    public final static String WS_HEADER_PROTOCOL = "Sec-WebSocket-Protocol";

    /** Constant <code>HTTP_HEADER_HOST="Host"</code> */
    public final static String HTTP_HEADER_HOST = "Host";

    /** Constant <code>HTTP_HEADER_ORIGIN="Origin"</code> */
    public final static String HTTP_HEADER_ORIGIN = "Origin";

    /** Constant <code>HTTP_HEADER_USERAGENT="User-Agent"</code> */
    public final static String HTTP_HEADER_USERAGENT = "User-Agent";

    /** Constant <code>WS_HEADER_FORWARDED="X-Forwarded-For"</code> */
    public final static String WS_HEADER_FORWARDED = "X-Forwarded-For";

    /** Constant <code>WS_HEADER_REAL_IP="X-Real-IP"</code> */
    public final static String WS_HEADER_REAL_IP = "X-Real-IP";

    /** Constant <code>WS_HEADER_GENERIC_PREFIX="X-"</code> */
    public final static String WS_HEADER_GENERIC_PREFIX = "X-";

    /** Constant <code>URI_QS_PARAMETERS="querystring-parameters"</code> */
    public final static String URI_QS_PARAMETERS = "querystring-parameters";

    /** Constant <code>IDLE_COUNTER="idle.counter"</code> */
    public final static String IDLE_COUNTER = "idle.counter";

    /** Constant <code>WS_HEADER_REMOTE_IP="remote.ip"</code> */
    public static final String WS_HEADER_REMOTE_IP = "remote.ip";

    /** Constant <code>WS_HEADER_REMOTE_PORT="remote.port"</code> */
    public static final String WS_HEADER_REMOTE_PORT = "remote.port";

}
