/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package org.red5.server.api.scope;

import org.red5.server.api.stream.IClientBroadcastStream;
import org.red5.server.messaging.IPipe;

/**
 * Broadcast scope is marker interface that represents object that works as basic scope and has pipe connection event dispatching capabilities.
 *
 * @author mondain
 */
public interface IBroadcastScope extends IBasicScope, IPipe {

    /**
     * <p>getClientBroadcastStream.</p>
     *
     * @return a {@link org.red5.server.api.stream.IClientBroadcastStream} object
     */
    public IClientBroadcastStream getClientBroadcastStream();

    /**
     * <p>setClientBroadcastStream.</p>
     *
     * @param clientBroadcastStream a {@link org.red5.server.api.stream.IClientBroadcastStream} object
     */
    public void setClientBroadcastStream(IClientBroadcastStream clientBroadcastStream);

}
