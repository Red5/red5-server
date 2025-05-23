/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.jmx.mxbeans;

import javax.management.MXBean;

import org.red5.server.api.IClient;
import org.red5.server.api.IConnection;
import org.red5.server.api.scope.IScope;

/**
 * JMX mbean for Application.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
@MXBean
public interface ApplicationMXBean {

    /**
     * <p>appStart.</p>
     *
     * @param app a {@link org.red5.server.api.scope.IScope} object
     * @return a boolean
     */
    public boolean appStart(IScope app);

    /**
     * <p>appConnect.</p>
     *
     * @param conn a {@link org.red5.server.api.IConnection} object
     * @param params an array of {@link java.lang.Object} objects
     * @return a boolean
     */
    public boolean appConnect(IConnection conn, Object[] params);

    /**
     * <p>appJoin.</p>
     *
     * @param client a {@link org.red5.server.api.IClient} object
     * @param app a {@link org.red5.server.api.scope.IScope} object
     * @return a boolean
     */
    public boolean appJoin(IClient client, IScope app);

    /**
     * <p>appDisconnect.</p>
     *
     * @param conn a {@link org.red5.server.api.IConnection} object
     */
    public void appDisconnect(IConnection conn);

    /**
     * <p>appLeave.</p>
     *
     * @param client a {@link org.red5.server.api.IClient} object
     * @param app a {@link org.red5.server.api.scope.IScope} object
     */
    public void appLeave(IClient client, IScope app);

    /**
     * <p>appStop.</p>
     *
     * @param app a {@link org.red5.server.api.scope.IScope} object
     */
    public void appStop(IScope app);

    /**
     * <p>roomStart.</p>
     *
     * @param room a {@link org.red5.server.api.scope.IScope} object
     * @return a boolean
     */
    public boolean roomStart(IScope room);

    /**
     * <p>roomConnect.</p>
     *
     * @param conn a {@link org.red5.server.api.IConnection} object
     * @param params an array of {@link java.lang.Object} objects
     * @return a boolean
     */
    public boolean roomConnect(IConnection conn, Object[] params);

    /**
     * <p>roomJoin.</p>
     *
     * @param client a {@link org.red5.server.api.IClient} object
     * @param room a {@link org.red5.server.api.scope.IScope} object
     * @return a boolean
     */
    public boolean roomJoin(IClient client, IScope room);

    /**
     * <p>roomDisconnect.</p>
     *
     * @param conn a {@link org.red5.server.api.IConnection} object
     */
    public void roomDisconnect(IConnection conn);

    /**
     * <p>roomLeave.</p>
     *
     * @param client a {@link org.red5.server.api.IClient} object
     * @param room a {@link org.red5.server.api.scope.IScope} object
     */
    public void roomLeave(IClient client, IScope room);

    /**
     * <p>roomStop.</p>
     *
     * @param room a {@link org.red5.server.api.scope.IScope} object
     */
    public void roomStop(IScope room);

}
