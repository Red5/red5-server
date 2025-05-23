/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.net.rtmp.event;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.server.api.stream.IStreamPacket;

/**
 * Provides a means for storage of RTMP events.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class CachedEvent implements IStreamPacket {

    /**
     * Event timestamp
     */
    private int timestamp;

    /**
     * Time at which the event entered the server
     */
    private long receivedTime;

    private byte dataType;

    private IoBuffer data;

    /**
     * <p>Getter for the field <code>timestamp</code>.</p>
     *
     * @return the timestamp
     */
    public int getTimestamp() {
        return timestamp;
    }

    /**
     * <p>Setter for the field <code>timestamp</code>.</p>
     *
     * @param timestamp
     *            the timestamp to set
     */
    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * <p>Getter for the field <code>receivedTime</code>.</p>
     *
     * @return the receivedTime
     */
    public long getReceivedTime() {
        return receivedTime;
    }

    /**
     * <p>Setter for the field <code>receivedTime</code>.</p>
     *
     * @param receivedTime
     *            the receivedTime to set
     */
    public void setReceivedTime(long receivedTime) {
        this.receivedTime = receivedTime;
    }

    /**
     * <p>Getter for the field <code>dataType</code>.</p>
     *
     * @return the dataType
     */
    public byte getDataType() {
        return dataType;
    }

    /**
     * <p>Setter for the field <code>dataType</code>.</p>
     *
     * @param dataType
     *            the dataType to set
     */
    public void setDataType(byte dataType) {
        this.dataType = dataType;
    }

    /**
     * <p>Getter for the field <code>data</code>.</p>
     *
     * @return the data
     */
    public IoBuffer getData() {
        return data;
    }

    /**
     * <p>Setter for the field <code>data</code>.</p>
     *
     * @param data
     *            the data to set
     */
    public void setData(IoBuffer data) {
        this.data = data;
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + dataType;
        result = prime * result + timestamp;
        return result;
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    /** {@inheritDoc} */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        CachedEvent other = (CachedEvent) obj;
        if (dataType != other.dataType)
            return false;
        if (timestamp != other.timestamp)
            return false;
        return true;
    }

}
