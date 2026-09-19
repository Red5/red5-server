package org.red5.server.net.rtmp.codec;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.DummySession;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.AbstractProtocolDecoderOutput;
import org.apache.mina.filter.codec.ProtocolCodecFactory;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolEncoder;
import org.apache.mina.filter.codec.ProtocolEncoderAdapter;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;
import org.junit.Test;
import org.red5.server.net.IConnectionManager;
import org.red5.server.net.rtmp.RTMPConnection;

/**
 * RED5DEV-2425: input for a session whose RTMP connection is no longer registered must be consumed, otherwise MINA's ProtocolCodecFilter
 * calls the decoder again forever on the I/O thread.
 */
public class RTMPMinaProtocolDecoderUnregisteredTest {

    /** A connection manager that no longer knows the session. */
    static class EmptyConnectionManager implements IConnectionManager<RTMPConnection> {

        final AtomicInteger lookups = new AtomicInteger();

        @Override
        public RTMPConnection getConnectionBySessionId(String sessionId) {
            lookups.incrementAndGet();
            return null;
        }

        @Override
        public Collection<RTMPConnection> getAllConnections() {
            return Collections.emptyList();
        }

        @Override
        public RTMPConnection createConnection(Class<?> connCls) {
            return null;
        }

        @Override
        public RTMPConnection createConnection(Class<?> connCls, String sessionId) {
            return null;
        }

        @Override
        public RTMPConnection removeConnection(RTMPConnection conn) {
            return null;
        }

        @Override
        public RTMPConnection removeConnection(String sessionId) {
            return null;
        }

        @Override
        public Collection<RTMPConnection> removeConnections() {
            return Collections.emptyList();
        }
    }

    private static DummySession unregisteredSession(EmptyConnectionManager manager) {
        DummySession session = new DummySession();
        session.setAttribute(RTMPConnection.RTMP_SESSION_ID, "UNREGISTERED");
        session.setAttribute(RTMPConnection.RTMP_CONN_MANAGER, new WeakReference<IConnectionManager<RTMPConnection>>(manager));
        return session;
    }

    private static IoBuffer input() {
        IoBuffer in = IoBuffer.allocate(1420);
        for (int i = 0; i < 1420; i++) {
            in.put((byte) i);
        }
        return in.flip();
    }

    @Test
    public void decodeConsumesInputForUnregisteredConnection() throws Exception {
        EmptyConnectionManager manager = new EmptyConnectionManager();
        DummySession session = unregisteredSession(manager);
        IoBuffer in = input();
        new RTMPMinaProtocolDecoder().decode(session, in, new AbstractProtocolDecoderOutput() {
            @Override
            public void flush(org.apache.mina.core.filterchain.IoFilter.NextFilter nextFilter, IoSession s) {
            }
        });
        assertFalse("input left unconsumed: " + in, in.hasRemaining());
        assertTrue("close not requested", session.isClosing());
    }

    @Test
    public void protocolCodecFilterTerminatesForUnregisteredConnection() throws Exception {
        EmptyConnectionManager manager = new EmptyConnectionManager();
        DummySession session = unregisteredSession(manager);
        RTMPMinaProtocolDecoder decoder = new RTMPMinaProtocolDecoder();
        ProtocolEncoder encoder = new ProtocolEncoderAdapter() {
            @Override
            public void encode(IoSession s, Object message, ProtocolEncoderOutput out) {
            }
        };
        session.getFilterChain().addLast("protocolFilter", new ProtocolCodecFilter(new ProtocolCodecFactory() {
            @Override
            public ProtocolEncoder getEncoder(IoSession s) {
                return encoder;
            }

            @Override
            public ProtocolDecoder getDecoder(IoSession s) {
                return decoder;
            }
        }));
        Thread reader = new Thread(() -> session.getFilterChain().fireMessageReceived(input()), "unregistered-reader");
        // an unfixed decoder spins forever; a daemon thread lets the test JVM exit
        reader.setDaemon(true);
        reader.start();
        reader.join(2000);
        System.out.println("RTMPMinaProtocolDecoderUnregisteredTest v1 lookups=" + manager.lookups.get() + " readerAlive=" + reader.isAlive());
        assertFalse("ProtocolCodecFilter still looping after 2 s, connection lookups=" + manager.lookups.get(), reader.isAlive());
    }

}
