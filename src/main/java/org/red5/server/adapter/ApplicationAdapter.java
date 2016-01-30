/*
 * RED5 Open Source Flash Server - https://github.com/Red5/
 * 
 * Copyright 2006-2016 by respective authors (see below). All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.red5.server.adapter;

import org.red5.server.api.IClient;
import org.red5.server.api.IConnection;
import org.red5.server.api.scope.IScope;

/**
 * Base class for applications, takes care that callbacks are executed single-threaded. If you want to have maximum performance, use {@link MultiThreadedApplicationAdapter} instead.
 * 
 * Using this class may lead to problems if accepting a client in the
 * 
 * <pre>
 * *Connect
 * </pre>
 * 
 * or
 * 
 * <pre>
 * *Join
 * </pre>
 * 
 * methods takes too long, so using the multi-threaded version is preferred.
 * 
 * @author The Red5 Project
 * @author Joachim Bauch (jojo@struktur.de)
 */
public class ApplicationAdapter extends MultiThreadedApplicationAdapter {

    /** {@inheritDoc} */
    @Override
    public boolean connect(IConnection conn, IScope scope, Object[] params) {
        synchronized (this) {
            return super.connect(conn, scope, params);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void disconnect(IConnection conn, IScope scope) {
        synchronized (this) {
            super.disconnect(conn, scope);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean start(IScope scope) {
        synchronized (this) {
            return super.start(scope);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void stop(IScope scope) {
        synchronized (this) {
            super.stop(scope);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean join(IClient client, IScope scope) {
        synchronized (this) {
            return super.join(client, scope);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void leave(IClient client, IScope scope) {
        synchronized (this) {
            super.leave(client, scope);
        }
    }

}
