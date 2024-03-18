package org.red5.codec;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.io.IoConstants;

public class AbstractVideo implements IVideoStreamCodec, IoConstants {

    protected VideoCodec codec;

    /** Current timestamp for the stored keyframe */
    protected int keyframeTimestamp;

    /**
     * Storage for key frames
     */
    protected final CopyOnWriteArrayList<FrameData> keyframes = new CopyOnWriteArrayList<>();

    /**
     * Storage for frames buffered since last key frame
     */
    protected final CopyOnWriteArrayList<FrameData> interframes = new CopyOnWriteArrayList<>();

    /**
     * Number of frames buffered since last key frame
     */
    protected final AtomicInteger numInterframes = new AtomicInteger(0);

    /**
     * Whether or not to buffer interframes
     */
    protected boolean bufferInterframes = false;

    @Override
    public VideoCodec getCodec() {
        return codec;
    }

    @Override
    public String getName() {
        return codec.name();
    }

    @Override
    public void reset() {
    }

    @Override
    public boolean canDropFrames() {
        return false;
    }

    @Override
    public boolean canHandleData(IoBuffer data) {
        return false;
    }

    @Override
    public boolean addData(IoBuffer data) {
        return false;
    }

    @Override
    public boolean addData(IoBuffer data, int timestamp) {
        return false;
    }

    @Override
    public boolean addData(IoBuffer data, int timestamp, boolean amf) {
        return false;
    }

    @Override
    public IoBuffer getDecoderConfiguration() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public IoBuffer getKeyframe() {
        if (keyframes.isEmpty()) {
            return null;
        }
        return keyframes.get(0).getFrame();
    }

    /** {@inheritDoc} */
    @Override
    public FrameData[] getKeyframes() {
        return keyframes.toArray(new FrameData[0]);
    }

    /** {@inheritDoc} */
    @Override
    public int getNumInterframes() {
        return numInterframes.get();
    }

    /** {@inheritDoc} */
    @Override
    public FrameData getInterframe(int index) {
        if (index < numInterframes.get()) {
            return interframes.get(index);
        }
        return null;
    }

    public boolean isBufferInterframes() {
        return bufferInterframes;
    }

    public void setBufferInterframes(boolean bufferInterframes) {
        this.bufferInterframes = bufferInterframes;
    }

}
