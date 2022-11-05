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

    public final static String WS_UPGRADE_HANDLER = "ws.upgrader";

    public final static String WS_MANAGER = "ws.manager";

    public final static String WS_SCOPE = "ws.scope";

    public final static String WS_CONNECTION = "ws.connection";

    public final static String SESSION = "session";

    public final static Object WS_HANDSHAKE = "ws.handshake";

    public final static String WS_HEADER_KEY = "Sec-WebSocket-Key";

    public final static String WS_HEADER_VERSION = "Sec-WebSocket-Version";

    public final static String WS_HEADER_EXTENSIONS = "Sec-WebSocket-Extensions";

    public final static String WS_HEADER_PROTOCOL = "Sec-WebSocket-Protocol";

    public final static String HTTP_HEADER_HOST = "Host";

    public final static String HTTP_HEADER_ORIGIN = "Origin";

    public final static String HTTP_HEADER_USERAGENT = "User-Agent";

    public final static String WS_HEADER_FORWARDED = "X-Forwarded-For";

    public final static String WS_HEADER_REAL_IP = "X-Real-IP";

    public final static String WS_HEADER_GENERIC_PREFIX = "X-";

    public final static String URI_QS_PARAMETERS = "querystring-parameters";

    public final static String IDLE_COUNTER = "idle.counter";

    public static final String WS_HEADER_REMOTE_IP = "remote.ip";

    public static final String WS_HEADER_REMOTE_PORT = "remote.port";

}
