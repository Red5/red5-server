/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.net.rtmps;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.filterchain.IoFilterAdapter;
import org.apache.mina.core.session.IoSession;

/**
 * Placed ahead of the SslFilter, records the undecrypted buffer currently being processed. MINA 2.0.x forwards that same buffer instance
 * to the next filter when TLS has closed, whereas decrypted data always arrives in a different buffer. {@link RTMPSIoFilter} uses the
 * recorded instance to recognize undecrypted input.
 */
public class RTMPSInboundMarkFilter extends IoFilterAdapter {

    /** Session attribute holding the undecrypted inbound buffer while it is passed down the chain. */
    public static final String UNDECRYPTED_INBOUND = "rtmps.undecryptedInbound";

    /** {@inheritDoc} */
    @Override
    public void messageReceived(NextFilter nextFilter, IoSession session, Object message) throws Exception {
        if (message instanceof IoBuffer) {
            session.setAttribute(UNDECRYPTED_INBOUND, message);
            try {
                nextFilter.messageReceived(session, message);
            } finally {
                session.removeAttribute(UNDECRYPTED_INBOUND);
            }
        } else {
            nextFilter.messageReceived(session, message);
        }
    }

}
