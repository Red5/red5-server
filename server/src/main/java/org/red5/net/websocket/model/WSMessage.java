/*
 * RED5 Open Source Flash Server - https://github.com/red5 Copyright 2006-2018 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.net.websocket.model;

import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.net.websocket.WebSocketConnection;

/**
 * Represents WebSocket message data.
 * 
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class WSMessage {

    public enum MessageType {
        BINARY, TEXT;
    }

    // message type
    private MessageType messageType;

    // the originating connection for this message
    private WeakReference<WebSocketConnection> connection;

    // payload
    private IoBuffer payload;

    // the path on which this message originated
    private String path;

    // creation time
    private long timeStamp = System.currentTimeMillis();

    public WSMessage() {
        payload = IoBuffer.allocate(0);
    }

    public WSMessage(String message) throws UnsupportedEncodingException {
        payload = IoBuffer.wrap(message.getBytes("UTF8"));
    }

    /**
     * Returns the payload data as a UTF8 string.
     * 
     * @return string
     * @throws UnsupportedEncodingException
     */
    public String getMessageAsString() throws UnsupportedEncodingException {
        return new String(payload.array(), "UTF8").trim();
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }

    public WebSocketConnection getConnection() {
        return connection.get();
    }

    public void setConnection(WebSocketConnection connection) {
        this.connection = new WeakReference<WebSocketConnection>(connection);
        // set the connections path on the message
        setPath(connection.getPath());
    }

    /**
     * Returns the payload.
     * 
     * @return payload
     */
    public IoBuffer getPayload() {
        return payload.flip();
    }

    public void setPayload(IoBuffer payload) {
        this.payload = payload;
    }

    /**
     * Adds additional payload data.
     * 
     * @param additionalPayload
     */
    public void addPayload(IoBuffer additionalPayload) {
        if (payload == null) {
            payload = IoBuffer.allocate(additionalPayload.remaining());
            payload.setAutoExpand(true);
        }
        this.payload.put(additionalPayload);
    }

    /**
     * Adds additional payload data.
     * 
     * @param additionalPayload
     */
    public void addPayload(byte[] additionalPayload) {
        if (payload == null) {
            payload = IoBuffer.allocate(additionalPayload.length);
            payload.setAutoExpand(true);
        }
        this.payload.put(additionalPayload);
    }

    public boolean isPayloadComplete() {
        return !payload.hasRemaining();
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @Override
    public String toString() {
        return "WSMessage [messageType=" + messageType + ", timeStamp=" + timeStamp + ", path=" + path + ", payload=" + payload + "]";
    }

}
