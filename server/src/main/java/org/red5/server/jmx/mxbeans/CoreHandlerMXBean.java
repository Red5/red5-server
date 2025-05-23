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
import org.red5.server.api.event.IEvent;
import org.red5.server.api.scope.IBasicScope;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.service.IServiceCall;

/**
 * Base IScopeHandler implementation
 *
 * @author The Red5 Project
 */
@MXBean
public interface CoreHandlerMXBean {

    /**
     * <p>connect.</p>
     *
     * @param conn a {@link org.red5.server.api.IConnection} object
     * @param scope a {@link org.red5.server.api.scope.IScope} object
     * @return a boolean
     */
    public boolean connect(IConnection conn, IScope scope);

    /**
     * <p>connect.</p>
     *
     * @param conn a {@link org.red5.server.api.IConnection} object
     * @param scope a {@link org.red5.server.api.scope.IScope} object
     * @param params an array of {@link java.lang.Object} objects
     * @return a boolean
     */
    public boolean connect(IConnection conn, IScope scope, Object[] params);

    /**
     * <p>disconnect.</p>
     *
     * @param conn a {@link org.red5.server.api.IConnection} object
     * @param scope a {@link org.red5.server.api.scope.IScope} object
     */
    public void disconnect(IConnection conn, IScope scope);

    /**
     * <p>join.</p>
     *
     * @param client a {@link org.red5.server.api.IClient} object
     * @param scope a {@link org.red5.server.api.scope.IScope} object
     * @return a boolean
     */
    public boolean join(IClient client, IScope scope);

    /**
     * <p>leave.</p>
     *
     * @param client a {@link org.red5.server.api.IClient} object
     * @param scope a {@link org.red5.server.api.scope.IScope} object
     */
    public void leave(IClient client, IScope scope);

    /**
     * <p>removeChildScope.</p>
     *
     * @param scope a {@link org.red5.server.api.scope.IBasicScope} object
     */
    public void removeChildScope(IBasicScope scope);

    /**
     * <p>serviceCall.</p>
     *
     * @param conn a {@link org.red5.server.api.IConnection} object
     * @param call a {@link org.red5.server.api.service.IServiceCall} object
     * @return a boolean
     */
    public boolean serviceCall(IConnection conn, IServiceCall call);

    /**
     * <p>start.</p>
     *
     * @param scope a {@link org.red5.server.api.scope.IScope} object
     * @return a boolean
     */
    public boolean start(IScope scope);

    /**
     * <p>stop.</p>
     *
     * @param scope a {@link org.red5.server.api.scope.IScope} object
     */
    public void stop(IScope scope);

    /**
     * <p>handleEvent.</p>
     *
     * @param event a {@link org.red5.server.api.event.IEvent} object
     * @return a boolean
     */
    public boolean handleEvent(IEvent event);

}
