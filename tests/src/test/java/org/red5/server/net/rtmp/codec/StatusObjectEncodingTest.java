package org.red5.server.net.rtmp.codec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.commons.codec.binary.Hex;
import org.apache.mina.core.buffer.IoBuffer;
import org.junit.Test;
import org.red5.server.api.IConnection.Encoding;
import org.red5.server.net.rtmp.RTMPMinaConnection;
import org.red5.server.net.rtmp.event.Invoke;
import org.red5.server.net.rtmp.message.Constants;
import org.red5.server.net.rtmp.message.Header;
import org.red5.server.net.rtmp.status.Status;
import org.red5.server.net.rtmp.status.StatusCodes;
import org.red5.server.net.rtmp.status.StatusObject;
import org.red5.server.service.Call;
import org.red5.server.service.PendingCall;

/**
 * Status info objects (Status and StatusObject, both {@code @Anonymous}) are anonymous objects on the wire on AMF0 and AMF3 connections, as
 * Flash Player and FMS expect them, with the same members on both encodings.
 */
public class StatusObjectEncodingTest {

    private static final String SERVER_PACKAGE = Hex.encodeHexString("org.red5".getBytes(StandardCharsets.US_ASCII));

    private static RTMPMinaConnection connection(Encoding encoding) {
        // the connection is never opened, it only supplies the encoding
        RTMPMinaConnection conn = new RTMPMinaConnection() {
            @Override
            public Encoding getEncoding() {
                return encoding;
            }
        };
        conn.getState().setState(RTMP.STATE_CONNECTED);
        return conn;
    }

    private static IoBuffer encode(Encoding encoding, Invoke invoke) {
        RTMPProtocolEncoder encoder = new RTMPProtocolEncoder();
        encoder.setConnection(connection(encoding));
        return encoder.encodeInvoke(invoke).flip();
    }

    private static Invoke decode(Encoding encoding, IoBuffer in) {
        Header header = new Header();
        header.setDataType(Constants.TYPE_INVOKE);
        header.setStreamId(0);
        Invoke invoke = (Invoke) new RTMPProtocolDecoder().decodeMessage(connection(encoding), header, in);
        assertFalse("the whole body must be read", in.hasRemaining());
        return invoke;
    }

    private static String hex(IoBuffer buf) {
        byte[] bytes = new byte[buf.remaining()];
        buf.duplicate().get(bytes);
        return Hex.encodeHexString(bytes);
    }

    /**
     * Encodes the invoke on the connection, checks the info object starts at the expected position, names no server class and decodes as
     * a map; returns the map.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> info(Encoding encoding, Invoke invoke, String head) {
        IoBuffer out = encode(encoding, invoke);
        String body = hex(out);
        // AMF0 anonymous object 0x03; AMF3 object 0x0a, U29O-traits 0x0b (inline, dynamic, no sealed members), empty class name 0x01
        String marker = encoding == Encoding.AMF3 ? "110a0b01" : "03";
        assertTrue(encoding + " " + body, body.startsWith(head + marker));
        assertFalse(encoding + " " + body, body.contains(SERVER_PACKAGE));
        Object info = decode(encoding, out).getCall().getArguments()[0];
        assertTrue(encoding + " " + info, info instanceof Map);
        return (Map<String, Object>) info;
    }

    private static String amf0String(String s) {
        return String.format("02%04x%s", s.length(), Hex.encodeHexString(s.getBytes(StandardCharsets.UTF_8)));
    }

    private static String amf3String(String s) {
        return String.format("06%02x%s", s.length() << 1 | 1, Hex.encodeHexString(s.getBytes(StandardCharsets.UTF_8)));
    }

    private static String head(Encoding encoding, String method, int transactionId) {
        if (encoding == Encoding.AMF3) {
            return "11" + amf3String(method) + String.format("1104%02x", transactionId) + "1101";
        }
        return amf0String(method) + String.format("00%016x", Double.doubleToLongBits(transactionId)) + "05";
    }

    /**
     * onStatus for a stream, as Channel.sendStatus writes it.
     */
    @Test
    public void testOnStatus() {
        Map<String, Object> amf0 = null;
        for (Encoding encoding : Encoding.values()) {
            Status status = new Status(StatusCodes.NS_PLAY_START, Status.STATUS, "Started playing test.");
            status.setClientid(1);
            status.setDetails("test");
            Invoke invoke = new Invoke(new PendingCall(null, "onStatus", new Object[] { status }));
            invoke.setTransactionId(0);
            Map<String, Object> info = info(encoding, invoke, head(encoding, "onStatus", 0));
            assertEquals(StatusCodes.NS_PLAY_START, info.get("code"));
            assertEquals(Status.STATUS, info.get("level"));
            assertEquals("Started playing test.", info.get("description"));
            assertEquals("test", info.get("details"));
            assertEquals(1, ((Number) info.get("clientid")).intValue());
            if (amf0 == null) {
                amf0 = info;
            } else {
                assertEquals(amf0.keySet(), info.keySet());
            }
        }
    }

    /**
     * The _error of a failed call, a NetConnection.Call.Failed StatusObject built by RTMPProtocolEncoder.generateErrorResult.
     */
    @Test
    public void testErrorResult() {
        Map<String, Object> amf0 = null;
        for (Encoding encoding : Encoding.values()) {
            PendingCall call = new PendingCall(null, "fails", null);
            call.setStatus(Call.STATUS_INVOCATION_EXCEPTION);
            call.setException(new IllegalStateException("boom"));
            Invoke invoke = new Invoke(call);
            invoke.setTransactionId(9);
            Map<String, Object> info = info(encoding, invoke, head(encoding, "_error", 9));
            assertEquals(StatusCodes.NC_CALL_FAILED, info.get("code"));
            assertEquals(StatusObject.ERROR, info.get("level"));
            assertEquals("boom", info.get("description"));
            assertEquals(IllegalStateException.class.getCanonicalName(), info.get("application"));
            if (amf0 == null) {
                amf0 = info;
            } else {
                assertEquals(amf0, info);
            }
        }
    }

}
