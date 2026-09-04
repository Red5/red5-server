package org.red5.server.net.rtmp;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.server.net.protocol.RTMPDecodeState;
import org.red5.server.net.rtmp.codec.RTMP;
import org.red5.server.net.rtmp.message.Packet;

/**
 * Minimal RTMP connection for unit tests that need a connected state without a transport.
 */
public class StubRTMPConnection extends RTMPConnection {

    private final RTMP state = new RTMP();

    private final RTMPDecodeState decoderState;

    private final String name;

    private final AtomicBoolean closed = new AtomicBoolean();

    public StubRTMPConnection(String name) {
        super("PERSISTENT");
        this.name = name;
        state.setState(RTMP.STATE_CONNECTED);
        decoderState = new RTMPDecodeState(name);
    }

    @Override
    public RTMP getState() {
        return state;
    }

    @Override
    public byte getStateCode() {
        return closed.get() ? RTMP.STATE_DISCONNECTED : RTMP.STATE_CONNECTED;
    }

    @Override
    public RTMPDecodeState getDecoderState() {
        return decoderState;
    }

    @Override
    public String getSessionId() {
        return name;
    }

    @Override
    public void write(Packet out) {
    }

    @Override
    public void writeRaw(IoBuffer out) {
    }

    @Override
    public void close() {
        closed.set(true);
    }

    @Override
    protected void onInactive() {
    }

    public boolean isClosedByHandler() {
        return closed.get();
    }
}
