package org.red5.server.net.rtmp.codec;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.apache.commons.codec.binary.Hex;
import org.apache.mina.core.buffer.IoBuffer;
import org.junit.Ignore;
import org.junit.Test;
import org.red5.server.api.IConnection.Encoding;
import org.red5.server.net.rtmp.RTMPMinaConnection;
import org.red5.server.net.rtmp.event.FlexMessage;
import org.red5.server.net.rtmp.event.IRTMPEvent;
import org.red5.server.net.rtmp.event.Invoke;
import org.red5.server.net.rtmp.event.Notify;
import org.red5.server.net.rtmp.message.Constants;
import org.red5.server.net.rtmp.message.Header;
import org.red5.server.net.rtmp.status.Status;
import org.red5.server.net.rtmp.status.StatusCodes;
import org.red5.server.net.rtmp.status.StatusObject;
import org.red5.server.service.Call;
import org.red5.server.service.PendingCall;

/**
 * Commands, results and status objects written by the server encoder, checked against the bytes the AMF0 and AMF3 specifications define and
 * decoded back by the decoder, on AMF0 and AMF3 connections.
 */
public class RTMPProtocolEncoderAMFTest {

    private static final Date DATE = new Date(1700000000123L);

    static RTMPMinaConnection connection(Encoding encoding) {
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

    static RTMPProtocolEncoder encoder(Encoding encoding) {
        RTMPProtocolEncoder encoder = new RTMPProtocolEncoder();
        encoder.setConnection(connection(encoding));
        return encoder;
    }

    static String hex(IoBuffer buf) {
        byte[] bytes = new byte[buf.remaining()];
        buf.duplicate().get(bytes);
        return Hex.encodeHexString(bytes);
    }

    static String amf0String(String s) {
        return String.format("02%04x%s", s.length(), Hex.encodeHexString(s.getBytes(StandardCharsets.UTF_8)));
    }

    static String amf3String(String s) {
        // inline UTF-8-vr for strings shorter than 64 bytes
        return String.format("06%02x%s", s.length() << 1 | 1, Hex.encodeHexString(s.getBytes(StandardCharsets.UTF_8)));
    }

    static String amf0Number(double d) {
        return String.format("00%016x", Double.doubleToLongBits(d));
    }

    static String double64(double d) {
        return String.format("%016x", Double.doubleToLongBits(d));
    }

    static IoBuffer flip(IoBuffer buf) {
        if (buf.position() > 0) {
            buf.flip();
        }
        return buf;
    }

    static IRTMPEvent decode(Encoding encoding, byte dataType, IoBuffer in) {
        Header header = new Header();
        header.setDataType(dataType);
        header.setStreamId(0);
        IRTMPEvent event = new RTMPProtocolDecoder().decodeMessage(connection(encoding), header, in);
        assertFalse("the whole body must be read", in.hasRemaining());
        return event;
    }

    static Invoke invoke(String method, int transactionId, Object... args) {
        Invoke invoke = new Invoke(new PendingCall(null, method, args));
        invoke.setTransactionId(transactionId);
        return invoke;
    }

    static Invoke result(String method, int transactionId, Object result) {
        PendingCall call = new PendingCall(null, method, null);
        call.setStatus(Call.STATUS_SUCCESS_RESULT);
        call.setResult(result);
        Invoke invoke = new Invoke(call);
        invoke.setTransactionId(transactionId);
        return invoke;
    }

    /**
     * AMF0 command: the name is a string (0x02), the transaction id a number (0x00), the command object null (0x05), dates carry a zero
     * time-zone.
     */
    @Test
    public void testInvokeOnAMF0Connection() {
        IoBuffer out = flip(encoder(Encoding.AMF0).encodeInvoke(invoke("echo", 2, "echo", 1.5d, DATE)));
        String expected = amf0String("echo") + amf0Number(2) + "05" + amf0String("echo") + amf0Number(1.5d) + "0b" + double64(DATE.getTime()) + "0000";
        assertEquals(expected, hex(out));
        Invoke decoded = (Invoke) decode(Encoding.AMF0, Constants.TYPE_INVOKE, out);
        assertEquals(2, decoded.getTransactionId());
        assertEquals("echo", decoded.getCall().getServiceMethodName());
        assertArrayEquals(new Object[] { "echo", 1.5d, DATE }, decoded.getCall().getArguments());
    }

    /**
     * AMF3 connection: every value is switched with the avmplus-object marker (0x11) and the string table spans the whole body, so the
     * repeated "echo" is a reference to index 0.
     */
    @Test
    public void testInvokeOnAMF3Connection() {
        IoBuffer out = flip(encoder(Encoding.AMF3).encodeInvoke(invoke("echo", 2, "echo", 1.5d, DATE, 7)));
        String expected = "11" + amf3String("echo") + "110402" + "1101" + "110600" + "1105" + double64(1.5d) + "110801" + double64(DATE.getTime()) + "110407";
        assertEquals(expected, hex(out));
        Invoke decoded = (Invoke) decode(Encoding.AMF3, Constants.TYPE_INVOKE, out);
        assertEquals(2, decoded.getTransactionId());
        assertEquals("echo", decoded.getCall().getServiceMethodName());
        assertArrayEquals(new Object[] { "echo", 1.5d, DATE, 7 }, decoded.getCall().getArguments());
    }

    /**
     * Objects, arrays and vectors as parameters on both encodings, including an object written twice (a reference the second time).
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testComplexParamsRoundTrip() {
        for (Encoding encoding : Encoding.values()) {
            Map<String, Object> shared = new LinkedHashMap<>();
            shared.put("name", "shared");
            shared.put("when", DATE);
            Map<String, Object> outer = new LinkedHashMap<>();
            outer.put("a", shared);
            outer.put("b", shared);
            outer.put("list", Arrays.asList("x", "y", 3.0d));
            Object[] args = new Object[] { outer, shared };
            if (encoding == Encoding.AMF3) {
                args = new Object[] { outer, shared, new Vector<>(List.of(1.5d, 2.5d)), new Vector<>(List.of(1, -2)), new Vector<Object>(List.of("s", 1.5d)) };
            }
            IoBuffer out = flip(encoder(encoding).encodeInvoke(invoke("complex", 4, args)));
            Invoke decoded = (Invoke) decode(encoding, Constants.TYPE_INVOKE, out);
            Object[] params = decoded.getCall().getArguments();
            assertEquals(encoding.toString(), args.length, params.length);
            Map<String, Object> outerDecoded = (Map<String, Object>) params[0];
            Map<String, Object> sharedDecoded = (Map<String, Object>) outerDecoded.get("a");
            assertEquals("shared", sharedDecoded.get("name"));
            assertEquals(DATE, sharedDecoded.get("when"));
            assertSame(encoding.toString(), sharedDecoded, outerDecoded.get("b"));
            assertEquals(Arrays.asList("x", "y", 3.0d), toList(outerDecoded.get("list")));
            if (encoding == Encoding.AMF3) {
                // object references span the parameters of one body on AMF3
                assertSame(sharedDecoded, params[1]);
                assertEquals(List.of(1.5d, 2.5d), toList(params[2]));
                assertEquals(List.of(1, -2), toList(params[3]));
                assertEquals(List.of("s", 1.5d), toList(params[4]));
            } else {
                assertEquals(sharedDecoded, params[1]);
            }
        }
    }

    static List<?> toList(Object value) {
        if (value instanceof Object[]) {
            return Arrays.asList((Object[]) value);
        }
        return (List<?>) value;
    }

    /**
     * The result of connect is always AMF0, also when the client asked for AMF3 (the server switches the connection to AMF3 before the
     * result is written).
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testConnectResultIsAMF0() {
        Map<String, Object> status = new HashMap<>();
        status.put("code", StatusCodes.NC_CONNECT_SUCCESS);
        status.put("level", "status");
        status.put("objectEncoding", 3);
        for (Encoding encoding : Encoding.values()) {
            IoBuffer out = flip(encoder(encoding).encodeInvoke(result("connect", 1, status)));
            String body = hex(out);
            assertTrue(encoding + " " + body, body.startsWith(amf0String("_result") + amf0Number(1) + "05" + "08"));
            Invoke decoded = (Invoke) decode(encoding, Constants.TYPE_INVOKE, out);
            assertEquals("_result", decoded.getCall().getServiceMethodName());
            assertEquals(1, decoded.getTransactionId());
            Map<String, Object> result = (Map<String, Object>) decoded.getCall().getArguments()[0];
            assertEquals(StatusCodes.NC_CONNECT_SUCCESS, result.get("code"));
            assertEquals("status", result.get("level"));
            assertEquals(3, ((Number) result.get("objectEncoding")).intValue());
        }
    }

    private static StatusObject connectSuccess() {
        // as RTMPHandler builds it
        StatusObject status = new StatusObject(StatusCodes.NC_CONNECT_SUCCESS, StatusObject.STATUS, "Connection succeeded.");
        status.setAdditional("fmsVer", "RED5/1,0,10,0");
        status.setAdditional("capabilities", 33);
        status.setAdditional("mode", 1);
        return status;
    }

    /**
     * NetConnection.Connect.Success as the server builds it, a StatusObject written as an anonymous AMF0 object.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testConnectSuccessStatusObject() {
        IoBuffer out = flip(encoder(Encoding.AMF0).encodeInvoke(result("connect", 1, connectSuccess())));
        String body = hex(out);
        // an anonymous object (0x03), not a typed object naming a server class
        assertTrue(body, body.startsWith(amf0String("_result") + amf0Number(1) + "05" + "03"));
        assertFalse(body, body.contains(Hex.encodeHexString("org.red5".getBytes(StandardCharsets.US_ASCII))));
        Invoke decoded = (Invoke) decode(Encoding.AMF0, Constants.TYPE_INVOKE, out);
        Map<String, Object> result = (Map<String, Object>) decoded.getCall().getArguments()[0];
        assertEquals(StatusCodes.NC_CONNECT_SUCCESS, result.get("code"));
        assertEquals(StatusObject.STATUS, result.get("level"));
        assertEquals("Connection succeeded.", result.get("description"));
    }

    /**
     * Reproducer: the properties RTMPHandler adds to the connect result with StatusObject.setAdditional (fmsVer, capabilities, mode, data
     * and the Enhanced RTMP fourCcList, audioFourCcInfoMap, videoFourCcInfoMap and CapsEx) never reach the wire. StatusObject has no getter
     * for them, so the AMF0 writer, which writes bean properties, leaves them out; on AMF3 connections RTMPHandler copies the status into a
     * map without them. Where they belong (the command object, where FMS sends fmsVer, capabilities and mode, or the information object)
     * needs a decision, so this is not fixed here.
     */
    @Ignore("connect result additional properties are not written, see the javadoc")
    @Test
    public void testConnectSuccessCarriesAdditionalProperties() {
        IoBuffer out = flip(encoder(Encoding.AMF0).encodeInvoke(result("connect", 1, connectSuccess())));
        assertTrue(hex(out), hex(out).contains(Hex.encodeHexString("fmsVer".getBytes(StandardCharsets.US_ASCII))));
    }

    /**
     * onStatus for a stream as Channel.sendStatus writes it, an invoke with transaction id 0 carrying a Status.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testOnStatusOnAMF0Connection() {
        Status status = new Status(StatusCodes.NS_PUBLISH_START, Status.STATUS, "test is now published");
        status.setClientid(1);
        status.setDetails("test");
        IoBuffer out = flip(encoder(Encoding.AMF0).encodeInvoke(invoke("onStatus", 0, status)));
        String body = hex(out);
        assertTrue(body, body.startsWith(amf0String("onStatus") + amf0Number(0) + "05" + "03"));
        Invoke decoded = (Invoke) decode(Encoding.AMF0, Constants.TYPE_INVOKE, out);
        assertEquals("onStatus", decoded.getCall().getServiceMethodName());
        Map<String, Object> info = (Map<String, Object>) decoded.getCall().getArguments()[0];
        assertEquals(StatusCodes.NS_PUBLISH_START, info.get("code"));
        assertEquals(Status.STATUS, info.get("level"));
        assertEquals("test is now published", info.get("description"));
        assertEquals("test", info.get("details"));
        assertEquals(1, ((Number) info.get("clientid")).intValue());
    }

    /**
     * Reproducer: onStatus on an AMF3 connection. Status is IExternalizable, so the AMF3 encoder writes it as an externalizable object
     * (traits 0x07) of the class org.red5.server.net.rtmp.status.Status, ignoring its {@code @Anonymous} annotation. Red5's decoder refuses
     * to instantiate it (org.red5.server. is on the deserialization deny-list since 919dff6a) and fails the message, so a Red5 client on
     * an AMF3 connection is disconnected on the first onStatus; a Flash client cannot read it either without registering the class alias.
     * The AMF0 encoder writes the same Status as an anonymous object.
     */
    @Ignore("AMF3 status objects are externalizable server classes, see the javadoc")
    @Test
    public void testOnStatusOnAMF3Connection() {
        Status status = new Status(StatusCodes.NS_PLAY_START, Status.STATUS, "Started playing test.");
        status.setClientid(1);
        status.setDetails("test");
        IoBuffer out = flip(encoder(Encoding.AMF3).encodeInvoke(invoke("onStatus", 0, status)));
        String body = hex(out);
        assertTrue(body, body.startsWith("11" + amf3String("onStatus") + "110400" + "1101" + "110a07"));
        Invoke decoded = (Invoke) decode(Encoding.AMF3, Constants.TYPE_INVOKE, out);
        Object info = decoded.getCall().getArguments()[0];
        assertEquals(String.valueOf(info), StatusCodes.NS_PLAY_START, value(info, "code"));
        assertEquals(Status.STATUS, value(info, "level"));
        assertEquals("Started playing test.", value(info, "description"));
        assertEquals("test", value(info, "details"));
    }

    static Object value(Object info, String key) {
        if (info instanceof Map) {
            return ((Map<?, ?>) info).get(key);
        }
        if (info instanceof Status) {
            Status status = (Status) info;
            switch (key) {
                case "code":
                    return status.getCode();
                case "level":
                    return status.getLevel();
                case "description":
                    return status.getDescription();
                case "details":
                    return status.getDetails();
            }
        }
        return null;
    }

    /**
     * A call that failed is answered with _error and a NetConnection.Call.Failed status object.
     */
    @Test
    public void testErrorResult() {
        assertErrorResult(Encoding.AMF0);
    }

    /**
     * Reproducer: the NetConnection.Call.Failed StatusObject is written as an externalizable server class on AMF3 connections, as in
     * {@link #testOnStatusOnAMF3Connection()}.
     */
    @Ignore("AMF3 status objects are externalizable server classes, see testOnStatusOnAMF3Connection")
    @Test
    public void testErrorResultOnAMF3Connection() {
        assertErrorResult(Encoding.AMF3);
    }

    @SuppressWarnings("unchecked")
    private static void assertErrorResult(Encoding encoding) {
        PendingCall call = new PendingCall(null, "missing", null);
        call.setStatus(Call.STATUS_METHOD_NOT_FOUND);
        Invoke invoke = new Invoke(call);
        invoke.setTransactionId(9);
        IoBuffer out = flip(encoder(encoding).encodeInvoke(invoke));
        String body = hex(out);
        String head = encoding == Encoding.AMF3 ? "11" + amf3String("_error") + "110409" + "1101" : amf0String("_error") + amf0Number(9) + "05";
        assertTrue(encoding + " " + body, body.startsWith(head));
        Invoke decoded = (Invoke) decode(encoding, Constants.TYPE_INVOKE, out);
        assertEquals("_error", decoded.getCall().getServiceMethodName());
        assertEquals(9, decoded.getTransactionId());
        Object info = decoded.getCall().getArguments()[0];
        Object code = info instanceof Map ? ((Map<String, Object>) info).get("code") : ((StatusObject) info).getCode();
        assertEquals(String.valueOf(info), StatusCodes.NC_CALL_FAILED, code);
    }

    /**
     * A notify (no transaction id) such as onBWDone or a client method call from the server.
     */
    @Test
    public void testNotify() {
        Notify notify = new Notify();
        notify.setCall(new Call(null, "onBWDone", new Object[] { 1.5d }));
        IoBuffer out = flip(encoder(Encoding.AMF0).encodeNotify(notify));
        assertEquals(amf0String("onBWDone") + amf0Number(1.5d), hex(out));
        notify = new Notify();
        notify.setCall(new Call(null, "onBWDone", new Object[] { 1.5d }));
        out = flip(encoder(Encoding.AMF3).encodeNotify(notify));
        assertEquals("11" + amf3String("onBWDone") + "1105" + double64(1.5d), hex(out));
    }

    /**
     * Flex message (0x11) written by the server on an AMF3 connection and read back by the decoder.
     */
    @Test
    public void testFlexMessageRoundTrip() {
        FlexMessage msg = new FlexMessage();
        msg.setTransactionId(6);
        msg.setCall(new PendingCall(null, "receive", new Object[] { "a", "a", DATE }));
        IoBuffer out = flip(encoder(Encoding.AMF3).encodeFlexMessage(msg));
        assertEquals("00" + "11" + amf3String("receive") + "110406" + "1101" + "11" + amf3String("a") + "110602" + "110801" + double64(DATE.getTime()), hex(out));
        FlexMessage decoded = (FlexMessage) decode(Encoding.AMF3, Constants.TYPE_FLEX_MESSAGE, out);
        assertEquals(6, decoded.getTransactionId());
        assertEquals("receive", decoded.getCall().getServiceMethodName());
        assertArrayEquals(new Object[] { "a", "a", DATE }, decoded.getCall().getArguments());
    }

    /**
     * A data notify with pre-encoded data (as stream metadata is sent) is passed through unchanged.
     */
    @Test
    public void testStreamMetadataPassThrough() {
        IoBuffer data = IoBuffer.allocate(64).setAutoExpand(true);
        org.red5.io.amf.Output output = new org.red5.io.amf.Output(data);
        output.writeString("onMetaData");
        Map<Object, Object> meta = new LinkedHashMap<>();
        meta.put("duration", 2.0d);
        output.writeMap(meta);
        data.flip();
        String expected = hex(data);
        Notify notify = new Notify(data, "onMetaData");
        assertNull(notify.getCall());
        Header header = new Header();
        header.setDataType(Constants.TYPE_NOTIFY);
        header.setStreamId(1);
        IoBuffer out = encoder(Encoding.AMF3).encodeMessage(header, notify);
        assertEquals(expected, hex(out));
        Notify decoded = new RTMPProtocolDecoder().decodeStreamData(out.duplicate());
        assertEquals("onMetaData", decoded.getAction());
    }

}
