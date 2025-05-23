/*
 * RED5 Open Source Flash Server - https://github.com/Red5/ Copyright 2006-2015 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.client.net.rtmps;

import org.red5.client.net.rtmpt.RTMPTClient;
import org.red5.server.net.rtmpt.codec.RTMPTCodecFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RTMPT/S client object
 *
 * @author Paul Gregoire
 */
public class RTMPTSClient extends RTMPTClient {

    private static final Logger log = LoggerFactory.getLogger(RTMPTSClient.class);

    /**
     * <p>Constructor for RTMPTSClient.</p>
     */
    public RTMPTSClient() {
        protocol = "rtmps";
        codecFactory = new RTMPTCodecFactory();
        codecFactory.init();
    }

    /** {@inheritDoc} */
    @Override
    protected synchronized void startConnector(String server, int port) {
        connector = new RTMPTSClientConnector(server, port, this);
        log.debug("Created connector {}", connector);
        connector.start();
    }

}
