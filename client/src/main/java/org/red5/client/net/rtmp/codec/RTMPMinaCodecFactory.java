package org.red5.client.net.rtmp.codec;

import java.util.List;
import java.util.concurrent.Semaphore;

import org.apache.commons.codec.binary.Hex;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecException;
import org.apache.mina.filter.codec.ProtocolCodecFactory;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.apache.mina.filter.codec.ProtocolEncoder;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;
import org.red5.client.net.rtmp.BaseRTMPClientHandler;
import org.red5.client.net.rtmp.RTMPClientConnManager;
import org.red5.server.api.Red5;
import org.red5.server.net.rtmp.RTMPConnection;
import org.red5.server.net.rtmp.codec.RTMPMinaProtocolDecoder;
import org.red5.server.net.rtmp.codec.RTMPMinaProtocolEncoder;

/**
 * <p>RTMPMinaCodecFactory class.</p>
 *
 * @author mondain
 */
public class RTMPMinaCodecFactory implements ProtocolCodecFactory {

    private RTMPMinaProtocolDecoder clientDecoder;

    private RTMPMinaProtocolEncoder clientEncoder;

    {
        // RTMP Decoding
        clientDecoder = new RTMPMinaProtocolDecoder() {
            @Override
            public void decode(IoSession session, IoBuffer in, ProtocolDecoderOutput out) throws ProtocolCodecException {
                if (log.isDebugEnabled()) {
                    log.debug("decode: {} out: {}", in, out);
                }
                // get the connection from the session
                String sessionId = (String) session.getAttribute(RTMPConnection.RTMP_SESSION_ID);
                log.trace("Session id: {}", sessionId);
                BaseRTMPClientHandler client = (BaseRTMPClientHandler) session.getAttribute(RTMPConnection.RTMP_HANDLER);
                RTMPConnection conn = client != null ? (RTMPConnection) client.getConnection() : (RTMPConnection) RTMPClientConnManager.getInstance().getConnectionBySessionId(sessionId);
                if (conn != null) {
                    Red5.setConnectionLocal(conn);
                    byte[] arr = new byte[in.remaining()];
                    in.get(arr);
                    // create a buffer and store it on the session
                    IoBuffer buf = (IoBuffer) session.getAttribute("buffer");
                    if (buf == null) {
                        buf = IoBuffer.allocate(arr.length);
                        buf.setAutoExpand(true);
                        session.setAttribute("buffer", buf);
                    }
                    // copy incoming into buffer
                    buf.put(arr);
                    // flip so we can read
                    buf.flip();
                    final Semaphore lock = conn.getDecoderLock();
                    try {
                        // acquire the decoder lock
                        lock.acquire();
                        // construct any objects from the decoded buffer
                        List<?> objects = getDecoder().decodeBuffer(conn, buf);
                        log.trace("Decoded: {}", objects);
                        if (objects != null) {
                            for (Object object : objects) {
                                log.trace("Writing {} to decoder output: {}", object, out);
                                out.write(object);
                            }
                        }
                        log.trace("Input buffer position: {}", in.position());
                    } catch (Exception e) {
                        log.error("Error during decode", e);
                    } finally {
                        lock.release();
                        Red5.setConnectionLocal(null);
                    }
                } else {
                    log.debug("Connection is no longer available for decoding, may have been closed already");
                }
            }
        };
        clientDecoder.setDecoder(new RTMPClientProtocolDecoder());
        // RTMP Encoding
        clientEncoder = new RTMPMinaProtocolEncoder() {
            @Override
            public void encode(IoSession session, Object message, ProtocolEncoderOutput out) throws ProtocolCodecException {
                if (log.isDebugEnabled()) {
                    log.debug("encode: {} out: {}", message, out);
                }
                // get the connection from the session
                String sessionId = (String) session.getAttribute(RTMPConnection.RTMP_SESSION_ID);
                log.trace("Session id: {}", sessionId);
                BaseRTMPClientHandler client = (BaseRTMPClientHandler) session.getAttribute(RTMPConnection.RTMP_HANDLER);
                RTMPConnection conn = client != null ? (RTMPConnection) client.getConnection() : (RTMPConnection) RTMPClientConnManager.getInstance().getConnectionBySessionId(sessionId);
                if (conn != null) {
                    Red5.setConnectionLocal(conn);
                    final Semaphore lock = conn.getEncoderLock();
                    try {
                        // acquire the encoder lock
                        lock.acquire();
                        // get the buffer
                        IoBuffer buf = message instanceof IoBuffer ? (IoBuffer) message : getEncoder().encode(message);
                        if (buf != null) {
                            if (log.isTraceEnabled()) {
                                log.trace("Writing output data: {}", Hex.encodeHexString(buf.array()));
                            }
                            out.write(buf);
                        } else {
                            log.trace("Response buffer was null after encoding");
                        }
                    } catch (Exception ex) {
                        log.error("Exception during encode", ex);
                    } finally {
                        lock.release();
                        Red5.setConnectionLocal(null);
                    }
                } else {
                    log.debug("Connection is no longer available for encoding, may have been closed already");
                }
            }
        };
        clientEncoder.setEncoder(new RTMPClientProtocolEncoder());
    }

    /** {@inheritDoc} */
    @Override
    public ProtocolDecoder getDecoder(IoSession session) {
        return clientDecoder;
    }

    /** {@inheritDoc} */
    @Override
    public ProtocolEncoder getEncoder(IoSession session) {
        return clientEncoder;
    }

}
