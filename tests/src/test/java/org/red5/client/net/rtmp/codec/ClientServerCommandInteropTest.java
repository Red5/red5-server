package org.red5.client.net.rtmp.codec;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.apache.commons.codec.binary.Hex;
import org.apache.mina.core.buffer.IoBuffer;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.red5.server.api.IConnection.Encoding;
import org.red5.server.api.Red5;
import org.red5.server.net.rtmp.RTMPMinaConnection;
import org.red5.server.net.rtmp.codec.RTMP;
import org.red5.server.net.rtmp.codec.RTMPProtocolDecoder;
import org.red5.server.net.rtmp.event.IRTMPEvent;
import org.red5.server.net.rtmp.event.Invoke;
import org.red5.server.net.rtmp.event.Notify;
import org.red5.server.net.rtmp.message.Constants;
import org.red5.server.net.rtmp.message.Header;
import org.red5.server.service.Call;
import org.red5.server.service.PendingCall;

/**
 * Commands written by the client encoder and read by the server decoder, on AMF0 and AMF3 connections: calls with parameters that refer
 * to each other, notifies, and the results of server-to-client calls.
 */
public class ClientServerCommandInteropTest {

    private static final Date DATE = new Date(1700000000123L);

    @After
    public void tearDown() {
        Red5.setConnectionLocal(null);
    }

    private static RTMPMinaConnection connection(Encoding encoding) {
        // never opened, it only supplies the encoding
        RTMPMinaConnection conn = new RTMPMinaConnection() {
            @Override
            public Encoding getEncoding() {
                return encoding;
            }
        };
        conn.getState().setState(RTMP.STATE_CONNECTED);
        return conn;
    }

    /**
     * Encodes with the client encoder on a client connection and decodes with the server decoder on a server connection of the same
     * encoding.
     */
    private static IRTMPEvent roundTrip(Encoding encoding, byte dataType, Notify command, String expectedPrefix) {
        Red5.setConnectionLocal(connection(encoding));
        IoBuffer out = IoBuffer.allocate(256).setAutoExpand(true);
        new RTMPClientProtocolEncoder().encodeCommand(out, command);
        out.flip();
        byte[] bytes = new byte[out.remaining()];
        out.duplicate().get(bytes);
        String hex = Hex.encodeHexString(bytes);
        assertTrue(encoding + " " + hex, hex.startsWith(expectedPrefix));
        Header header = new Header();
        header.setDataType(dataType);
        header.setStreamId(0);
        IRTMPEvent event = new RTMPProtocolDecoder().decodeMessage(connection(encoding), header, out);
        assertFalse("the whole body must be read", out.hasRemaining());
        return event;
    }

    private static String amf0String(String s) {
        return String.format("02%04x%s", s.length(), Hex.encodeHexString(s.getBytes(StandardCharsets.UTF_8)));
    }

    private static String amf3String(String s) {
        return String.format("06%02x%s", s.length() << 1 | 1, Hex.encodeHexString(s.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCallWithParameters() {
        for (Encoding encoding : Encoding.values()) {
            Map<String, Object> shared = new LinkedHashMap<>();
            shared.put("name", "shared");
            shared.put("when", DATE);
            Map<String, Object> outer = new LinkedHashMap<>();
            outer.put("a", shared);
            outer.put("b", shared);
            Object[] args = encoding == Encoding.AMF3 ? new Object[] { "echo", DATE, outer, new Vector<>(List.of(1.5d, 2.5d)) } : new Object[] { "echo", DATE, outer };
            Invoke invoke = new Invoke(new PendingCall(null, "echo", args));
            invoke.setTransactionId(5);
            String prefix = encoding == Encoding.AMF3 ? "11" + amf3String("echo") + "110405" + "1101" + "110600" : amf0String("echo") + "004014000000000000" + "05" + amf0String("echo");
            Invoke decoded = (Invoke) roundTrip(encoding, Constants.TYPE_INVOKE, invoke, prefix);
            assertEquals(5, decoded.getTransactionId());
            assertEquals("echo", decoded.getCall().getServiceMethodName());
            Object[] params = decoded.getCall().getArguments();
            assertEquals(args.length, params.length);
            assertEquals("echo", params[0]);
            assertEquals(DATE, params[1]);
            Map<String, Object> outerDecoded = (Map<String, Object>) params[2];
            assertEquals(DATE, ((Map<String, Object>) outerDecoded.get("a")).get("when"));
            assertSame(outerDecoded.get("a"), outerDecoded.get("b"));
            if (encoding == Encoding.AMF3) {
                assertEquals(List.of(1.5d, 2.5d), params[3]);
            }
        }
    }

    /**
     * Reproducer: a notify (data message 0x12) on stream 0, as RTMPConnection.notify sends a call without a result in either direction.
     * The encoder writes the name and the arguments, as a data message has no transaction id, but unless the name starts with "on" or "@"
     * (stream data, issue #440) the decoder reads it as an invoke: it takes the first argument as the transaction id and fails on a
     * non-number (ClassCastException) or drops a numeric one. This is how the decoder has handled data messages on stream 0 since before
     * the AMF changes; changing it alters what third-party peers see, so it is left for a decision.
     */
    @Ignore("data messages on stream 0 are decoded as invokes, see the javadoc")
    @Test
    public void testNotify() {
        for (Encoding encoding : Encoding.values()) {
            Notify notify = new Notify();
            notify.setCall(new Call(null, "chat", new Object[] { "hi", 2.5d }));
            String prefix = encoding == Encoding.AMF3 ? "11" + amf3String("chat") + "11" + amf3String("hi") : amf0String("chat") + amf0String("hi");
            Notify decoded = (Notify) roundTrip(encoding, Constants.TYPE_NOTIFY, notify, prefix);
            assertEquals("chat", decoded.getCall().getServiceMethodName());
            assertArrayEquals(new Object[] { "hi", 2.5d }, decoded.getCall().getArguments());
        }
    }

    /**
     * The client answers a server-to-client call with _result: the name, transaction id and command object are AMF0, the result is AMF3 on
     * AMF3 connections.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testResultOfServerCall() {
        for (Encoding encoding : Encoding.values()) {
            PendingCall call = new PendingCall(null, "getClientInfo", null);
            call.setStatus(Call.STATUS_SUCCESS_RESULT);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("os", "linux");
            result.put("when", DATE);
            result.put("tags", Arrays.asList("a", "b"));
            call.setResult(result);
            Invoke invoke = new Invoke(call);
            invoke.setTransactionId(8);
            String prefix = amf0String("_result") + "004020000000000000" + "05" + (encoding == Encoding.AMF3 ? "11" : "");
            Invoke decoded = (Invoke) roundTrip(encoding, Constants.TYPE_INVOKE, invoke, prefix);
            assertEquals("_result", decoded.getCall().getServiceMethodName());
            assertEquals(8, decoded.getTransactionId());
            Map<String, Object> decodedResult = (Map<String, Object>) decoded.getCall().getArguments()[0];
            assertEquals("linux", decodedResult.get("os"));
            assertEquals(DATE, decodedResult.get("when"));
            assertEquals(Arrays.asList("a", "b"), decodedResult.get("tags") instanceof Object[] ? Arrays.asList((Object[]) decodedResult.get("tags")) : decodedResult.get("tags"));
        }
    }

}
