package org.red5.codec;

import org.apache.mina.core.buffer.IoBuffer;

public class AbstractAudio implements IAudioStreamCodec {

    protected AudioCodec codec;

    @Override
    public AudioCodec getCodec() {
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
    public boolean canHandleData(IoBuffer data) {
        return false;
    }

    @Override
    public boolean addData(IoBuffer data) {
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

}
