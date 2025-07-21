/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.net.rtmp.event;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Ping event, actually combination of different events. This is also known as a user control message.
 *
 * @author mondain
 */
public class Ping extends BaseEvent {

    private static final long serialVersionUID = -6478248060425544923L;

    public enum PingType {
        STREAM_BEGIN, // Stream begin / clear event
        STREAM_PLAYBUFFER_CLEAR, // Stream play buffer clear event
        STREAM_DRY, // Stream dry event
        CLIENT_BUFFER, // Client buffer event
        RECORDED_STREAM, // Recorded stream event
        UNKNOWN_5, // Unknown event
        PING_CLIENT, // Ping client event
        PONG_SERVER, // Pong server event
        UNKNOWN_8, // Unknown event
        PING_SWF_VERIFY, // Ping SWF verify event
        PONG_SWF_VERIFY, // Pong SWF verify event
        BUFFER_EMPTY, // Buffer empty event
        BUFFER_FULL, // Buffer full event
        UNDEFINED; // -1

        /**
         * <p>getType.</p>
         *
         * @param type a short
         * @return a {@link org.red5.server.net.rtmp.event.Ping.Type} object
         */
        public static PingType getType(int type) {
            switch (type) {
                case 0:
                    return STREAM_BEGIN;
                case 1:
                    return STREAM_PLAYBUFFER_CLEAR;
                case 2:
                    return STREAM_DRY;
                case 3:
                    return CLIENT_BUFFER;
                case 4:
                    return RECORDED_STREAM;
                case 5:
                    return UNKNOWN_5;
                case 6:
                    return PING_CLIENT;
                case 7:
                    return PONG_SERVER;
                case 8:
                    return UNKNOWN_8;
                case 26:
                    return PING_SWF_VERIFY;
                case 27:
                    return PONG_SWF_VERIFY;
                case 31:
                    return BUFFER_EMPTY;
                case 32:
                    return BUFFER_FULL;
                default:
                    return UNDEFINED;
            }
        }

        /**
         * <p>getType.</p>
         *
         * @param type a {@link org.red5.server.net.rtmp.event.Ping.Type} object
         * @return a short
         */
        public static short getType(PingType type) {
            switch (type) {
                case STREAM_BEGIN:
                    return 0;
                case STREAM_PLAYBUFFER_CLEAR:
                    return 1;
                case STREAM_DRY:
                    return 2;
                case CLIENT_BUFFER:
                    return 3;
                case RECORDED_STREAM:
                    return 4;
                case UNKNOWN_5:
                    return 5;
                case PING_CLIENT:
                    return 6;
                case PONG_SERVER:
                    return 7;
                case UNKNOWN_8:
                    return 8;
                case PING_SWF_VERIFY:
                    return 26;
                case PONG_SWF_VERIFY:
                    return 27;
                case BUFFER_EMPTY:
                    return 31;
                case BUFFER_FULL:
                    return 32;
                default:
                    return -1;
            }
        }
    }

    /**
     * The sub-type
     */
    protected short eventType;

    /**
     * Represents the stream id in all cases except PING_CLIENT and PONG_SERVER where it represents the local server timestamp.
     */
    private Number value2;

    private int value3 = -1; // -1 is undefined, used for PING_CLIENT and PONG_SERVER

    private int value4 = -1; // -1 is undefined

    /**
     * Debug string
     */
    private String debug = "";

    /**
     * Constructs a new Ping.
     */
    public Ping() {
        super(Type.SYSTEM);
    }

    /**
     * <p>Constructor for Ping.</p>
     *
     * @param eventType a short
     */
    public Ping(short eventType) {
        super(Type.SYSTEM);
        this.eventType = eventType;
    }

    /**
     * <p>Constructor for Ping.</p>
     *
     * @param eventType a short
     * @param value2 a int
     */
    public Ping(short eventType, int value2) {
        super(Type.SYSTEM);
        this.eventType = eventType;
        this.value2 = value2;
    }

    /**
     * <p>Constructor for Ping.</p>
     *
     * @param eventType a short
     * @param value2 a {@link java.lang.Number} object
     */
    public Ping(short eventType, Number value2) {
        super(Type.SYSTEM);
        this.eventType = eventType;
        this.value2 = value2;
    }

    /**
     * <p>Constructor for Ping.</p>
     *
     * @param eventType a short
     * @param value2 a int
     * @param value3 a int
     */
    public Ping(short eventType, int value2, int value3) {
        super(Type.SYSTEM);
        this.eventType = eventType;
        this.value2 = value2;
        this.value3 = value3;
    }

    /**
     * <p>Constructor for Ping.</p>
     *
     * @param eventType a short
     * @param value2 a {@link java.lang.Number} object
     * @param value3 a int
     */
    public Ping(short eventType, Number value2, int value3) {
        super(Type.SYSTEM);
        this.eventType = eventType;
        this.value2 = value2;
        this.value3 = value3;
    }

    /**
     * <p>Constructor for Ping.</p>
     *
     * @param eventType a short
     * @param value2 a int
     * @param value3 a int
     * @param value4 a int
     */
    public Ping(short eventType, int value2, int value3, int value4) {
        super(Type.SYSTEM);
        this.eventType = eventType;
        this.value2 = value2;
        this.value3 = value3;
        this.value4 = value4;
    }

    /**
     * <p>Constructor for Ping.</p>
     *
     * @param eventType a short
     * @param value2 a {@link java.lang.Number} object
     * @param value3 a int
     * @param value4 a int
     */
    public Ping(short eventType, Number value2, int value3, int value4) {
        super(Type.SYSTEM);
        this.eventType = eventType;
        this.value2 = value2;
        this.value3 = value3;
        this.value4 = value4;
    }

    /**
     * <p>Constructor for Ping.</p>
     *
     * @param in a {@link org.red5.server.net.rtmp.event.Ping} object
     */
    public Ping(Ping in) {
        super(Type.SYSTEM);
        this.eventType = in.getEventType();
        this.value2 = in.getValue2();
        this.value3 = in.getValue3();
        this.value4 = in.getValue4();
    }

    /**
     * <p>Constructor for Ping.</p>
     *
     * @param pingType a {@link org.red5.server.net.rtmp.event.Ping.PingType} object
     */
    public Ping(PingType pingType) {
        super(Type.SYSTEM);
        this.eventType = PingType.getType(pingType);
        this.value2 = 0;
        this.value3 = -1; // -1 is undefined
        this.value4 = -1; // -1 is undefined
    }

    /**
     * <p>Constructor for Ping.</p>
     *
     * @param pingType a {@link org.red5.server.net.rtmp.event.Ping.PingType} object
     * @param streamId a {@link java.lang.Number} object
     */
    public Ping(PingType pingType, Number streamId) {
        super(Type.SYSTEM);
        this.eventType = PingType.getType(pingType);
        this.value2 = streamId;
        this.value3 = -1; // -1 is undefined
        this.value4 = -1; // -1 is undefined
    }

    /**
     * <p>Constructor for Ping.</p>
     *
     * @param pingType a {@link org.red5.server.net.rtmp.event.Ping.PingType} object
     * @param streamId a {@link java.lang.Number} object
     * @param param3 an int
     */
    public Ping(PingType pingType, Number streamId, int param3) {
        super(Type.SYSTEM);
        this.eventType = PingType.getType(pingType);
        this.value2 = streamId;
        this.value3 = param3;
        this.value4 = -1; // -1 is undefined
    }

    /**
     * <p>Constructor for Ping.</p>
     *
     * @param pingType a {@link org.red5.server.net.rtmp.event.Ping.PingType} object
     * @param streamId a {@link java.lang.Number} object
     * @param param3 an int
     * @param param4 an int
     */
    public Ping(PingType pingType, Number streamId, int param3, int param4) {
        super(Type.SYSTEM);
        this.eventType = PingType.getType(pingType);
        this.value2 = streamId;
        this.value3 = param3;
        this.value4 = param4;
    }

    /** {@inheritDoc} */
    @Override
    public byte getDataType() {
        return TYPE_PING;
    }

    /**
     * Returns the events sub-type
     *
     * @return the event type
     */
    public short getEventType() {
        return eventType;
    }

    /**
     * Sets the events sub-type
     *
     * @param eventType
     *            event type
     */
    public void setEventType(PingType eventType) {
        this.eventType = PingType.getType(eventType);
    }

    /**
     * Getter for property 'value2'.
     *
     * @return Value for property 'value2'.
     */
    public Number getValue2() {
        return value2;
    }

    /**
     * Setter for property 'value2'.
     *
     * @param value2
     *            Value to set for property 'value2'.
     */
    public void setValue2(Number value2) {
        this.value2 = value2;
    }

    /**
     * Getter for property 'value3'.
     *
     * @return Value for property 'value3'.
     */
    public int getValue3() {
        return value3;
    }

    /**
     * Setter for property 'value3'.
     *
     * @param value3
     *            Value to set for property 'value3'.
     */
    public void setValue3(int value3) {
        this.value3 = value3;
    }

    /**
     * Getter for property 'value4'.
     *
     * @return Value for property 'value4'.
     */
    public int getValue4() {
        return value4;
    }

    /**
     * Setter for property 'value4'.
     *
     * @param value4
     *            Value to set for property 'value4'.
     */
    public void setValue4(int value4) {
        this.value4 = value4;
    }

    /**
     * Getter for property 'debug'.
     *
     * @return Value for property 'debug'.
     */
    public String getDebug() {
        return debug;
    }

    /**
     * Setter for property 'debug'.
     *
     * @param debug
     *            Value to set for property 'debug'.
     */
    public void setDebug(String debug) {
        this.debug = debug;
    }

    /**
     * <p>doRelease.</p>
     */
    protected void doRelease() {
        eventType = 0;
        value2 = 0;
        value3 = -1;
        value4 = -1;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return String.format("Ping: %s, %f, %d, %d", PingType.getType(eventType), value2.doubleValue(), value3, value4);
    }

    /** {@inheritDoc} */
    @Override
    protected void releaseInternal() {

    }

    /** {@inheritDoc} */
    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        eventType = in.readShort();
        PingType pingType = PingType.getType(eventType);
        switch (pingType) {
            case PING_CLIENT:
            case PONG_SERVER:
                value2 = (Number) in.readInt();
                break;
            default:
                value2 = (Number) in.readDouble();
        }
        value3 = in.readInt();
        value4 = in.readInt();
    }

    /** {@inheritDoc} */
    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        out.writeShort(eventType);
        PingType pingType = PingType.getType(eventType);
        switch (pingType) {
            case PING_CLIENT:
            case PONG_SERVER:
                out.writeInt(value2.intValue());
                break;
            default:
                out.writeDouble(value2.doubleValue());
        }
        out.writeInt(value3);
        out.writeInt(value4);
    }
}
