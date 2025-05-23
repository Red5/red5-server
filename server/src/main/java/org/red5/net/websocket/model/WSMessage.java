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

    /**
     * <p>Constructor for WSMessage.</p>
     */
    public WSMessage() {
        payload = IoBuffer.allocate(0);
    }

    /**
     * <p>Constructor for WSMessage.</p>
     *
     * @param message a {@link java.lang.String} object
     * @throws java.io.UnsupportedEncodingException if any.
     */
    public WSMessage(String message) throws UnsupportedEncodingException {
        payload = IoBuffer.wrap(message.getBytes("UTF8"));
    }

    /**
     * <p>Constructor for WSMessage.</p>
     *
     * @param message a {@link java.lang.String} object
     * @param conn a {@link org.red5.net.websocket.WebSocketConnection} object
     * @throws java.io.UnsupportedEncodingException if any.
     */
    public WSMessage(String message, WebSocketConnection conn) throws UnsupportedEncodingException {
        setPayload(IoBuffer.wrap(message.getBytes("UTF8")));
        setConnection(conn);
    }

    /**
     * <p>Constructor for WSMessage.</p>
     *
     * @param payload a {@link org.apache.mina.core.buffer.IoBuffer} object
     * @param conn a {@link org.red5.net.websocket.WebSocketConnection} object
     */
    public WSMessage(IoBuffer payload, WebSocketConnection conn) {
        setPayload(payload);
        setConnection(conn);
    }

    /**
     * Returns the payload data as a UTF8 string.
     *
     * @return string
     * @throws java.io.UnsupportedEncodingException
     */
    public String getMessageAsString() throws UnsupportedEncodingException {
        return new String(payload.array(), "UTF8").trim();
    }

    /**
     * <p>Getter for the field <code>messageType</code>.</p>
     *
     * @return a {@link org.red5.net.websocket.model.WSMessage.MessageType} object
     */
    public MessageType getMessageType() {
        return messageType;
    }

    /**
     * <p>Setter for the field <code>messageType</code>.</p>
     *
     * @param messageType a {@link org.red5.net.websocket.model.WSMessage.MessageType} object
     */
    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }

    /**
     * <p>Getter for the field <code>connection</code>.</p>
     *
     * @return a {@link org.red5.net.websocket.WebSocketConnection} object
     */
    public WebSocketConnection getConnection() {
        return connection.get();
    }

    /**
     * <p>Setter for the field <code>connection</code>.</p>
     *
     * @param connection a {@link org.red5.net.websocket.WebSocketConnection} object
     */
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

    /**
     * <p>Setter for the field <code>payload</code>.</p>
     *
     * @param payload a {@link org.apache.mina.core.buffer.IoBuffer} object
     */
    public void setPayload(IoBuffer payload) {
        this.payload = payload;
    }

    /**
     * Adds additional payload data.
     *
     * @param additionalPayload a {@link org.apache.mina.core.buffer.IoBuffer} object
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
     * @param additionalPayload an array of {@link byte} objects
     */
    public void addPayload(byte[] additionalPayload) {
        if (payload == null) {
            payload = IoBuffer.allocate(additionalPayload.length);
            payload.setAutoExpand(true);
        }
        this.payload.put(additionalPayload);
    }

    /**
     * <p>isPayloadComplete.</p>
     *
     * @return a boolean
     */
    public boolean isPayloadComplete() {
        return !payload.hasRemaining();
    }

    /**
     * <p>Getter for the field <code>timeStamp</code>.</p>
     *
     * @return a long
     */
    public long getTimeStamp() {
        return timeStamp;
    }

    /**
     * <p>Getter for the field <code>path</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getPath() {
        return path;
    }

    /**
     * <p>Setter for the field <code>path</code>.</p>
     *
     * @param path a {@link java.lang.String} object
     */
    public void setPath(String path) {
        this.path = path;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "WSMessage [messageType=" + messageType + ", timeStamp=" + timeStamp + ", path=" + path + ", payload=" + payload + "]";
    }

}
