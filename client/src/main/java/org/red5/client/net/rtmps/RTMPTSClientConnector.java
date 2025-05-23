/*
 * RED5 Open Source Flash Server - https://github.com/Red5/ Copyright 2006-2015 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.client.net.rtmps;

import java.io.IOException;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.util.EntityUtils;
import org.apache.mina.core.buffer.IoBuffer;
import org.red5.client.net.rtmp.OutboundHandshake;
import org.red5.client.net.rtmp.RTMPClientConnManager;
import org.red5.client.net.rtmpt.RTMPTClientConnection;
import org.red5.client.net.rtmpt.RTMPTClientConnector;
import org.red5.server.api.Red5;
import org.red5.server.net.rtmp.RTMPConnection;
import org.red5.server.net.rtmp.codec.RTMP;
import org.red5.server.util.HttpConnectionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client connector for RTMPT/S (RTMPS Tunneled)
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class RTMPTSClientConnector extends RTMPTClientConnector {

    private static final Logger log = LoggerFactory.getLogger(RTMPTSClientConnector.class);

    {
        httpClient = HttpConnectionUtil.getSecureClient();
    }

    /**
     * <p>Constructor for RTMPTSClientConnector.</p>
     *
     * @param server a {@link java.lang.String} object
     * @param port a int
     * @param client a {@link org.red5.client.net.rtmps.RTMPTSClient} object
     */
    public RTMPTSClientConnector(String server, int port, RTMPTSClient client) {
        targetHost = new HttpHost(server, port, "https");
        this.client = client;
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        HttpPost post = null;
        try {
            RTMPTClientConnection conn = openConnection();
            // set a reference to the connection on the client
            client.setConnection((RTMPConnection) conn);
            // set thread local
            Red5.setConnectionLocal(conn);
            while (!conn.isClosing() && !stopRequested) {
                IoBuffer toSend = conn.getPendingMessages(SEND_TARGET_SIZE);
                int limit = toSend != null ? toSend.limit() : 0;
                if (limit > 0 && toSend != null) {
                    post = makePost("send");
                    post.setEntity(new InputStreamEntity(toSend.asInputStream(), limit));
                    post.addHeader("Content-Type", CONTENT_TYPE);
                } else {
                    post = makePost("idle");
                    post.setEntity(ZERO_REQUEST_ENTITY);
                    post.addHeader("Content-Type", CONTENT_TYPE);
                }
                // execute
                HttpResponse response = httpClient.execute(targetHost, post);
                // check for error
                checkResponseCode(response);
                // handle data
                byte[] received = EntityUtils.toByteArray(response.getEntity());
                // wrap the bytes
                IoBuffer data = IoBuffer.wrap(received);
                log.debug("State: {}", RTMP.states[conn.getStateCode()]);
                // ensure handshake is done
                if (conn.hasAttribute(RTMPConnection.RTMP_HANDSHAKE)) {
                    client.messageReceived(data);
                    continue;
                }
                if (data.limit() > 0) {
                    data.skip(1); // XXX: polling interval lies in this byte
                }
                List<?> messages = conn.decode(data);
                if (messages == null || messages.isEmpty()) {
                    try {
                        // XXX handle polling delay
                        Thread.sleep(250);
                    } catch (InterruptedException e) {
                        if (stopRequested) {
                            post.abort();
                            break;
                        }
                    }
                    continue;
                }
                for (Object message : messages) {
                    try {
                        client.messageReceived(message);
                    } catch (Exception e) {
                        log.error("Could not process message", e);
                    }
                }
            }
            finalizeConnection();
            client.connectionClosed(conn);
        } catch (Throwable e) {
            log.debug("RTMPT handling exception", e);
            client.handleException(e);
            if (post != null) {
                post.abort();
            }
        } finally {
            Red5.setConnectionLocal(null);
        }
    }

    private RTMPTClientConnection openConnection() throws IOException {
        RTMPTClientConnection conn = null;
        HttpPost openPost = getPost("/open/1");
        setCommonHeaders(openPost);
        openPost.addHeader("Content-Type", CONTENT_TYPE);
        openPost.setEntity(ZERO_REQUEST_ENTITY);
        // execute
        HttpResponse response = httpClient.execute(targetHost, openPost);
        checkResponseCode(response);
        // get the response entity
        HttpEntity entity = response.getEntity();
        if (entity != null) {
            String responseStr = EntityUtils.toString(entity);
            sessionId = responseStr.substring(0, responseStr.length() - 1);
            log.debug("Got an id {}", sessionId);
            // create a new connection
            conn = (RTMPTClientConnection) RTMPClientConnManager.getInstance().createConnection(RTMPTClientConnection.class, sessionId);
            log.debug("Got session id {} from connection", conn.getSessionId());
            // client state
            conn.setHandler(client);
            conn.setDecoder(client.getDecoder());
            conn.setEncoder(client.getEncoder());
            // create an outbound handshake
            OutboundHandshake outgoingHandshake = new OutboundHandshake();
            // set the handshake type
            outgoingHandshake.setHandshakeType(RTMPConnection.RTMP_NON_ENCRYPTED);
            // add the handshake
            conn.setAttribute(RTMPConnection.RTMP_HANDSHAKE, outgoingHandshake);
            log.debug("Handshake 1st phase");
            IoBuffer handshake = outgoingHandshake.generateClientRequest1();
            conn.writeRaw(handshake);
        }
        return conn;
    }

}
