/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.io.mp4;

/**
 * Represents an MP4 frame / chunk sample
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class MP4Frame implements Comparable<MP4Frame> {

    private byte type;

    private long offset;

    private int size;

    private double time;

    //this value originates from the ctts atom
    private int timeOffset;

    private boolean keyFrame;

    /**
     * Returns the data type, being audio or video.
     *
     * @return the data type
     */
    public byte getType() {
        return type;
    }

    public void setType(byte type) {
        this.type = type;
    }

    /**
     * Returns the offset of the data chunk in the media source.
     *
     * @return the offset in bytes
     */
    public long getOffset() {
        return offset;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    /**
     * Returns the size of the data chunk.
     *
     * @return the size in bytes
     */
    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    /**
     * Returns the timestamp.
     *
     * @return the timestamp
     */
    public double getTime() {
        return time;
    }

    public void setTime(double time) {
        this.time = time;
    }

    /**
     * @return the timeOffset
     */
    public int getTimeOffset() {
        return timeOffset;
    }

    /**
     * @param timeOffset
     *            the timeOffset to set
     */
    public void setTimeOffset(int timeOffset) {
        this.timeOffset = timeOffset;
    }

    /**
     * Returns whether or not this chunk represents a key frame.
     *
     * @return true if a key frame
     */
    public boolean isKeyFrame() {
        return keyFrame;
    }

    public void setKeyFrame(boolean keyFrame) {
        this.keyFrame = keyFrame;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (offset ^ (offset >>> 32));
        result = prime * result + type;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        MP4Frame other = (MP4Frame) obj;
        if (offset != other.offset)
            return false;
        if (type != other.type)
            return false;
        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("MP4Frame type=");
        sb.append(type);
        sb.append(", time=");
        sb.append(time);
        sb.append(", timeOffset=");
        sb.append(timeOffset);
        sb.append(", size=");
        sb.append(size);
        sb.append(", offset=");
        sb.append(offset);
        sb.append(", keyframe=");
        sb.append(keyFrame);
        return sb.toString();
    }

    /**
     * The frames are expected to be sorted by their timestamp
     */
    @Override
    public int compareTo(MP4Frame that) {
        int ret = 0;
        if (this.time > that.getTime()) {
            ret = 1;
        } else if (this.time < that.getTime()) {
            ret = -1;
        } else if (Double.doubleToLongBits(time) == Double.doubleToLongBits(that.getTime()) && this.offset > that.getOffset()) {
            ret = 1;
        } else if (Double.doubleToLongBits(time) == Double.doubleToLongBits(that.getTime()) && this.offset < that.getOffset()) {
            ret = -1;
        }
        return ret;
    }

}
