/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2016 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.api.scope;

import org.red5.server.api.IClient;
import org.red5.server.api.IConnection;
import org.red5.server.api.event.IEventHandler;
import org.red5.server.api.service.IServiceCall;

/**
 * The scope handler controls actions performed against a scope object, and also is notified of all events.
 * 
 * Gives fine grained control over what actions can be performed with the can* methods. Allows for detailed reporting on what is happening within the scope with the on* methods. This is the core interface users implement to create applications.
 * 
 * The thread local connection is always available via the Red5 object within these methods
 * 
 * @author The Red5 Project
 * @author Luke Hubbard (luke@codegent.com)
 */
public interface IScopeHandler extends IEventHandler {

    /**
     * Called when a scope is created for the first time.
     * 
     * @param scope
     *            the new scope object
     * @return true to allow, false to deny
     */
    boolean start(IScope scope);

    /**
     * Called just before a scope is disposed.
     * 
     * @param scope
     *            Scope that id disposed
     */
    void stop(IScope scope);

    /**
     * Called just before every connection to a scope. You can pass additional params from client using NetConnection.connect method (see below).
     * 
     * @param conn
     *            Connection object
     * @param params
     *            List of params passed from client via NetConnection.connect method. All parameters but the first one passed to NetConnection.connect
     *            method are available as params array.
     * @param scope
     *            Scope object
     * @return true to allow, false to deny
     */
    boolean connect(IConnection conn, IScope scope, Object[] params);

    /**
     * Called just after the a connection is disconnected.
     * 
     * @param conn
     *            Connection object
     * @param scope
     *            Scope object
     */
    void disconnect(IConnection conn, IScope scope);

    /**
     * Called just before a child scope is added.
     * 
     * @param scope
     *            Scope that will be added
     * @return true to allow, false to deny
     */
    boolean addChildScope(IBasicScope scope);

    /**
     * Called just after a child scope has been removed.
     * 
     * @param scope
     *            Scope that has been removed
     */
    void removeChildScope(IBasicScope scope);

    /**
     * Called just before a client enters the scope.
     * 
     * @param client
     *            Client object
     * @param scope
     *            Scope that is joined by client
     * @return true to allow, false to deny
     */
    boolean join(IClient client, IScope scope);

    /**
     * Called just after the client leaves the scope.
     * 
     * @param client
     *            Client object
     * @param scope
     *            Scope object
     */
    void leave(IClient client, IScope scope);

    /**
     * Called when a service is called.
     * 
     * @param conn
     *            The connection object
     * @param call
     *            The call object.
     * 
     * @return true to allow, false to deny
     */
    boolean serviceCall(IConnection conn, IServiceCall call);

}
