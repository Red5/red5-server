/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.api;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

import javax.management.openmbean.CompositeData;

import org.red5.server.api.scope.IScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for accessing Red5 API objects.
 *
 * This class uses a thread local, and will be setup by the service invoker.
 *
 * The code below shows various uses. <br>
 *
 * <pre>
 * IConnection conn = Red5.getConnectionLocal();
 * Red5 r5 = new Red5();
 * IScope scope = r5.getScope();
 * conn = r5.getConnection();
 * r5 = new Red5(conn);
 * IClient client = r5.getClient();
 * </pre>
 *
 * @author The Red5 Project
 * @author Luke Hubbard (luke@codegent.com)
 * @author Paul Gregoire (mondain@gmail.com)
 * @author Tiago Daniel Jacobs (os@tdj.cc)
 */
public final class Red5 {

    private static Logger log = LoggerFactory.getLogger(Red5.class);

    private static boolean isDebug = log.isDebugEnabled();

    /**
     * Connection associated with the current thread. Each connection runs in a separate thread.
     */
    private static final ThreadLocal<WeakReference<IConnection>> connThreadLocal = new ThreadLocal<WeakReference<IConnection>>();

    /**
     * Connection local to the current thread
     */
    public IConnection conn;

    /**
     * Server version with revision
     */
    public static final String VERSION = "Red5 Server 2.0.20";

    /**
     * Server version for fmsVer requests
     */
    public static final String FMS_VERSION = "RED5/2,0,20,0";

    /**
     * Server capabilities
     */
    public static final Integer CAPABILITIES = Integer.valueOf(33);

    /**
     * Target for ChunkSize messaging in RTMP. Defaulting to 128 which is the original FMS setting, until modifying this is more
     * throughly tested.
     */
    public static int targetChunkSize = 128;

    /**
     * Data version for NetStatusEvents
     */
    @SuppressWarnings("serial")
    public static final Map<String, Object> DATA_VERSION = new HashMap<String, Object>(2) {
        {
            put("version", "4,0,0,1122");
            put("type", "red5");
        }
    };

    /**
     * Server start time
     */
    private static final long START_TIME = System.currentTimeMillis();

    /**
     * Detection of debug mode
     */
    private static boolean debug = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().toString().indexOf("jdwp") >= 0;

    /**
     * Plugins ready flag
     */
    private static boolean pluginsReady;

    /**
     * Create a new Red5 object using given connection.
     *
     * @param conn
     *            Connection object.
     */
    public Red5(IConnection conn) {
        this.conn = conn;
    }

    /**
     * Create a new Red5 object using the connection local to the current thread A bit of magic that lets you access the red5 scope from anywhere
     */
    public Red5() {
        conn = Red5.getConnectionLocal();
    }

    /**
     * Setter for connection
     *
     * @param connection
     *            Thread local connection
     */
    public static void setConnectionLocal(IConnection connection) {
        if (isDebug) {
            log.debug("Set connection: {} with thread: {}", (connection != null ? connection.getSessionId() : null), Thread.currentThread().getName());
            try {
                StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
                StackTraceElement stackTraceElement = stackTraceElements[2];
                log.debug("Caller: {}.{} #{}", stackTraceElement.getClassName(), stackTraceElement.getMethodName(), stackTraceElement.getLineNumber());
            } catch (Exception e) {
            }
        }
        if (connection != null) {
            connThreadLocal.set(new WeakReference<IConnection>(connection));
            IScope scope = connection.getScope();
            if (scope != null) {
                Thread.currentThread().setContextClassLoader(scope.getClassLoader());
            }
        } else {
            // use null to clear the value
            connThreadLocal.remove();
        }
    }

    /**
     * Get the connection associated with the current thread. This method allows you to get connection object local to current thread. When you need to get a connection associated with event handler and so forth, this method provides you with it.
     *
     * @return Connection object
     */
    public static IConnection getConnectionLocal() {
        WeakReference<IConnection> ref = connThreadLocal.get();
        if (ref != null) {
            IConnection connection = ref.get();
            if (isDebug) {
                log.debug("Get connection: {} on thread: {}", (connection != null ? connection.getSessionId() : null), Thread.currentThread().getName());
            }
            return connection;
        } else {
            return null;
        }
    }

    /**
     * Get the connection object.
     *
     * @return Connection object
     */
    public IConnection getConnection() {
        return conn;
    }

    /**
     * Get the scope
     *
     * @return Scope object
     */
    public IScope getScope() {
        return conn.getScope();
    }

    /**
     * Get the client
     *
     * @return Client object
     */
    public IClient getClient() {
        return conn.getClient();
    }

    /**
     * Get the spring application context
     *
     * @return Application context
     */
    public IContext getContext() {
        return conn.getScope().getContext();
    }

    /**
     * Returns the current version with revision number
     *
     * @return String version
     */
    public static String getVersion() {
        return VERSION;
    }

    /**
     * Returns the current version for fmsVer requests
     *
     * @return String fms version
     */
    public static String getFMSVersion() {
        return FMS_VERSION;
    }

    /**
     * <p>getCapabilities.</p>
     *
     * @return a {@link java.lang.Integer} object
     */
    public static Integer getCapabilities() {
        return CAPABILITIES;
    }

    /**
     * <p>getDataVersion.</p>
     *
     * @return a {@link java.lang.Object} object
     */
    public static Object getDataVersion() {
        return DATA_VERSION;
    }

    /**
     * Returns true if java debugging was detected.
     *
     * @return true if debugging, false otherwise
     */
    public static boolean isDebug() {
        return debug;
    }

    /**
     * Returns server uptime in milliseconds.
     *
     * @return String version
     */
    public static long getUpTime() {
        return System.currentTimeMillis() - START_TIME;
    }

    /**
     * Allows for reconstruction via CompositeData.
     *
     * @param cd
     *            composite data
     * @return Red5 class instance
     */
    public static Red5 from(CompositeData cd) {
        Red5 instance = null;
        if (cd.containsKey("connection")) {
            Object cn = cd.get("connection");
            if (cn != null && cn instanceof IConnection) {
                instance = new Red5((IConnection) cn);
            } else {
                instance = new Red5();
            }
        } else {
            instance = new Red5();
        }
        return instance;
    }

    /**
     * Sets the target chunk size to use for publish and play invocations. Default is 128.
     *
     * @param targetChunkSize the chunk size to use
     */
    public static void setTargetChunkSize(int targetChunkSize) {
        Red5.targetChunkSize = targetChunkSize;
    }

    /**
     * Returns the target chunk size.
     *
     * @return targetChunkSize
     */
    public static int getTargetChunkSize() {
        return targetChunkSize;
    }

    /**
     * Plugins ready flag. This is set to true when the PluginLauncher has completed loading all plugins.
     *
     * @param pluginsReady a boolean
     */
    public static void setPluginsReady(boolean pluginsReady) {
        Red5.pluginsReady = pluginsReady;
    }

    /**
     * <p>isPluginsReady.</p>
     *
     * @return a boolean
     */
    public static boolean isPluginsReady() {
        return pluginsReady;
    }

}
