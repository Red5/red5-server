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
 * RTMP Abort event.
 *
 * @author aclarke@xuggle.com
 */
public class Abort extends BaseEvent {

    private int channelId = 0;

    /**
     * <p>Constructor for Abort.</p>
     */
    public Abort() {
        super(Type.SYSTEM);
    }

    /**
     * <p>Constructor for Abort.</p>
     *
     * @param channelId a int
     */
    public Abort(int channelId) {
        this.channelId = channelId;
    }

    /**
     * <p>getDataType.</p>
     *
     * @return a byte
     */
    public byte getDataType() {
        return TYPE_ABORT;
    }

    /**
     * <p>releaseInternal.</p>
     */
    protected void releaseInternal() {

    }

    /**
     * <p>Setter for the field <code>channelId</code>.</p>
     *
     * @param channelId a int
     */
    public void setChannelId(int channelId) {
        this.channelId = channelId;
    }

    /**
     * <p>Getter for the field <code>channelId</code>.</p>
     *
     * @return a int
     */
    public int getChannelId() {
        return channelId;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "Abort Channel: " + channelId;
    }

    /** {@inheritDoc} */
    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        channelId = in.readInt();
    }

    /** {@inheritDoc} */
    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        out.writeInt(channelId);
    }

}
