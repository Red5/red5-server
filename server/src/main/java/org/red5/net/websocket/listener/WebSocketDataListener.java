/*
 * RED5 Open Source Flash Server - https://github.com/red5 Copyright 2006-2018 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.net.websocket.listener;

import java.util.Objects;

/**
 * Adapter class for WebSocket data listener interface.
 *
 * @author Paul Gregoire
 */
public abstract class WebSocketDataListener implements IWebSocketDataListener {

    // used as a seed for hashCode/equals to prevent dupe instances
    protected final int localId = Objects.hash(System.nanoTime());

    /**
     * The protocol which this listener is interested in handling.
     */
    protected String protocol = "undefined";

    /** {@inheritDoc} */
    @Override
    public String getProtocol() {
        return protocol;
    }

    /** {@inheritDoc} */
    @Override
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    @Override
    public int hashCode() {
        return localId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        WebSocketDataListener other = (WebSocketDataListener) obj;
        return localId == other.localId;
    }

}
