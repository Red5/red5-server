/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.net;

import java.util.Collection;

/**
 * <p>IConnectionManager interface.</p>
 *
 * @param <T> type of connection
 * @author Paul Gregoire (mondain@gmail.com)
 */
public interface IConnectionManager<T> {

    /**
     * Returns a connection matching the given session id.
     *
     * @param sessionId
     *            session id
     * @return connection
     */
    T getConnectionBySessionId(String sessionId);

    /**
     * Returns all the current connections. It doesn't remove anything.
     *
     * @return list of connections
     */
    Collection<T> getAllConnections();

    /**
     * Creates a connection based on the given type class.
     *
     * @param connCls
     *            class
     * @return connection
     */
    T createConnection(Class<?> connCls);

    /**
     * Creates a connection of the type specified with associated session id.
     *
     * @param connCls
     *            class
     * @param sessionId
     *            session id
     * @return connection
     */
    T createConnection(Class<?> connCls, String sessionId);

    /**
     * Removes a connection matching the client id specified. If found, the connection will be returned.
     *
     * @param conn the connection to be removed
     * @return connection
     */
    T removeConnection(T conn);

    /**
     * Removes a connection by the given sessionId.
     *
     * @param sessionId
     *            session id
     * @return connection that was removed
     */
    T removeConnection(String sessionId);

    /**
     * Removes all the connections from the set.
     *
     * @return connections
     */
    Collection<T> removeConnections();

}
