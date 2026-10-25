package org.red5.server.net.rtmp.codec;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.apache.commons.codec.binary.Hex;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.junit.Test;
import org.red5.client.net.rtmp.codec.RTMPClientProtocolDecoder;
import org.red5.server.api.IConnection.Encoding;
import org.red5.server.api.IContext;
import org.red5.server.api.IServer;
import org.red5.server.api.Red5;
import org.red5.server.api.scope.IGlobalScope;
import org.red5.server.api.scope.IScope;
import org.red5.server.net.rtmp.RTMPHandler;
import org.red5.server.net.rtmp.RTMPMinaConnection;
import org.red5.server.net.rtmp.event.FlexMessage;
import org.red5.server.net.rtmp.event.IRTMPEvent;
import org.red5.server.net.rtmp.event.Invoke;
import org.red5.server.net.rtmp.event.Notify;
import org.red5.server.net.rtmp.event.Ping;
import org.red5.server.net.rtmp.message.Constants;
import org.red5.server.net.rtmp.message.Header;
import org.red5.server.net.rtmp.message.Packet;
import org.red5.server.net.rtmp.status.RuntimeStatusObject;
import org.red5.server.net.rtmp.status.Status;
import org.red5.server.net.rtmp.status.StatusCodes;
import org.red5.server.net.rtmp.status.StatusObject;
import org.red5.server.net.rtmp.status.StatusObjectService;
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

    /**
     * A StatusObject as the result of a call is written as an anonymous AMF0 object.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testConnectSuccessStatusObject() {
        StatusObject connectSuccess = new StatusObject(StatusCodes.NC_CONNECT_SUCCESS, StatusObject.STATUS, "Connection succeeded.");
        IoBuffer out = flip(encoder(Encoding.AMF0).encodeInvoke(result("connect", 1, connectSuccess)));
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
     * The result of a successful connect as RTMPHandler answers it and the encoder writes it, always AMF0: ["_result", 1, properties,
     * information]. The properties of the server go in the command object, where FMS sends fmsVer, capabilities and mode and FFmpeg's
     * RTMP server fmsVer and capabilities, with the Enhanced RTMP support (fourCcList, videoFourCcInfoMap, audioFourCcInfoMap, capsEx),
     * which the server states in an object of the connect result as the client does in the command object of connect. The information
     * object carries level, code, description, data (the data version) and objectEncoding, 3 when the client asked for AMF3, otherwise
     * 0. All are anonymous objects (0x03).
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testConnectSuccessCarriesAdditionalProperties() {
        for (Encoding encoding : new Encoding[] { Encoding.AMF0, Encoding.AMF3 }) {
            ConnectConnection conn = new ConnectConnection();
            Invoke reply = new ConnectHandler().connect(conn, encoding, true);
            // the connection asked for AMF3 is switched to it before the result is written
            assertEquals(encoding, conn.getEncoding());
            RTMPProtocolEncoder encoder = new RTMPProtocolEncoder();
            encoder.setConnection(conn);
            IoBuffer out = flip(encoder.encodeInvoke(reply));
            String fourCcInfoMap = "03" + key("*") + amf0Number(4) + "000009";
            String properties = "03" + key("fmsVer") + amf0String(Red5.getFMSVersion()) + key("capabilities") + amf0Number(Red5.getCapabilities()) + key("mode") + amf0Number(1) //
                    + key("fourCcList") + "0a00000001" + amf0String("*") + key("videoFourCcInfoMap") + fourCcInfoMap + key("audioFourCcInfoMap") + fourCcInfoMap + key("capsEx") + amf0Number(15) + "000009";
            String data = "03";
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) Red5.getDataVersion()).entrySet()) {
                data += key(entry.getKey()) + amf0String((String) entry.getValue());
            }
            data += "000009";
            String info = "03" + key("level") + amf0String("status") + key("code") + amf0String(StatusCodes.NC_CONNECT_SUCCESS) + key("description") + amf0String("Connection succeeded.") //
                    + key("data") + data + key("objectEncoding") + amf0Number(encoding == Encoding.AMF3 ? 3 : 0) + "000009";
            assertEquals(encoding.toString(), amf0String("_result") + amf0Number(1) + properties + info, hex(out));
            // a client reads the properties as the command object and the information object as the result
            Invoke decoded = (Invoke) decode(Encoding.AMF0, Constants.TYPE_INVOKE, out);
            assertEquals(1, decoded.getTransactionId());
            Map<String, Object> decodedProperties = decoded.getConnectionParams();
            assertEquals(Red5.getFMSVersion(), decodedProperties.get("fmsVer"));
            assertEquals(15, ((Number) decodedProperties.get("capsEx")).intValue());
            assertEquals(1, decoded.getCall().getArguments().length);
            Map<String, Object> decodedInfo = (Map<String, Object>) decoded.getCall().getArguments()[0];
            assertEquals(StatusCodes.NC_CONNECT_SUCCESS, decodedInfo.get("code"));
            assertEquals(encoding == Encoding.AMF3 ? 3 : 0, ((Number) decodedInfo.get("objectEncoding")).intValue());
        }
    }

    /**
     * A refused connect is answered with _error, no properties and the information object, whose description is that of this connect:
     * the shared status object of the status object service is not changed.
     */
    @Test
    public void testConnectRejected() {
        for (Encoding encoding : new Encoding[] { Encoding.AMF0, Encoding.AMF3 }) {
            ConnectConnection conn = new ConnectConnection();
            ConnectHandler handler = new ConnectHandler();
            Invoke reply = handler.connect(conn, encoding, false);
            RTMPProtocolEncoder encoder = new RTMPProtocolEncoder();
            encoder.setConnection(conn);
            IoBuffer out = flip(encoder.encodeInvoke(reply));
            String info = "03" + key("level") + amf0String("error") + key("code") + amf0String(StatusCodes.NC_CONNECT_REJECTED) + key("description") + amf0String("Connection to 'live' denied.") //
                    + key("objectEncoding") + amf0Number(encoding == Encoding.AMF3 ? 3 : 0) + "000009";
            assertEquals(encoding.toString(), amf0String("_error") + amf0Number(1) + "05" + info, hex(out));
            assertEquals("", handler.getStatus(StatusCodes.NC_CONNECT_REJECTED).getDescription());
        }
    }

    /** An AMF0 object key, a UTF-8 string without a type marker. */
    static String key(String k) {
        return amf0String(k).substring(2);
    }

    /**
     * Drives the connect handling of RTMPHandler: the server finds the application scope "live" and the scope allows the connection or
     * not.
     */
    static class ConnectHandler extends RTMPHandler {

        ConnectHandler() {
            StatusObjectService statusObjectService = new StatusObjectService();
            statusObjectService.loadAndCacheStatusObjects();
            setStatusObjectService(statusObjectService);
        }

        Invoke connect(ConnectConnection conn, Encoding encoding, boolean allowed) {
            IScope scope = stub(IScope.class, Map.of("isConnectionAllowed", allowed, "getName", "live"));
            IContext context = stub(IContext.class, Map.of("resolveScope", scope));
            IGlobalScope global = stub(IGlobalScope.class, Map.of("getContext", context));
            setServer(stub(IServer.class, Map.of("lookupGlobal", global)));
            Map<String, Object> params = new HashMap<>();
            params.put("app", "live");
            params.put("tcUrl", "rtmp://localhost/live");
            params.put("objectEncoding", encoding == Encoding.AMF3 ? 3d : 0d);
            Invoke connect = new Invoke(new PendingCall(null, "connect", null));
            connect.setTransactionId(1);
            connect.setConnectionParams(params);
            Header header = new Header();
            header.setStreamId(0);
            onCommand(conn, conn.getChannel(3), header, connect);
            assertEquals(1, conn.written.size());
            return (Invoke) conn.written.get(0).getMessage();
        }

    }

    /** A connection that is never opened: its connect succeeds and it keeps what is written to it. */
    static class ConnectConnection extends RTMPMinaConnection {

        final List<Packet> written = new ArrayList<>();

        ConnectConnection() {
            getState().setState(RTMP.STATE_CONNECTED);
        }

        @Override
        public boolean connect(IScope newScope, Object[] params) {
            return true;
        }

        @Override
        public boolean connect(IScope newScope) {
            return true;
        }

        @Override
        public void ping(Ping ping) {
        }

        @Override
        public void write(Packet out) {
            written.add(out);
        }

        @Override
        public IoSession getIoSession() {
            return stub(IoSession.class, Map.of());
        }

    }

    /** A stub of an interface that answers the named methods and otherwise returns the default value of the return type. */
    @SuppressWarnings("unchecked")
    static <T> T stub(Class<T> type, Map<String, Object> answers) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
            if (answers.containsKey(method.getName())) {
                return answers.get(method.getName());
            }
            switch (method.getName()) {
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                case "toString":
                    return type.getSimpleName();
            }
            Class<?> returnType = method.getReturnType();
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == int.class) {
                return 0;
            }
            if (returnType == long.class) {
                return 0L;
            }
            if (returnType == double.class) {
                return 0d;
            }
            return null;
        });
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
     * onStatus on an AMF3 connection. Status is IExternalizable but {@code @Anonymous}, so it is written as an anonymous dynamic object
     * (0x0A, traits 0x0B, empty class name 0x01) with the members the AMF0 encoder writes. It used to be written as an externalizable object
     * of the class org.red5.server.net.rtmp.status.Status, which Red5's decoder refuses (deny-list) and Flash cannot read without a class
     * alias, so a client on an AMF3 connection was disconnected on the first onStatus.
     */
    @Test
    public void testOnStatusOnAMF3Connection() {
        Status status = new Status(StatusCodes.NS_PLAY_START, Status.STATUS, "Started playing test.");
        status.setClientid(1);
        status.setDetails("test");
        IoBuffer out = flip(encoder(Encoding.AMF3).encodeInvoke(invoke("onStatus", 0, status)));
        String body = hex(out);
        assertTrue(body, body.startsWith("11" + amf3String("onStatus") + "110400" + "1101" + "110a0b01"));
        Invoke decoded = (Invoke) decode(Encoding.AMF3, Constants.TYPE_INVOKE, out);
        Object info = decoded.getCall().getArguments()[0];
        assertTrue(String.valueOf(info), info instanceof Map);
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
     * The NetConnection.Call.Failed StatusObject on an AMF3 connection is an anonymous object as well, see
     * {@link #testOnStatusOnAMF3Connection()}.
     */
    @Test
    public void testErrorResultOnAMF3Connection() {
        assertErrorResult(Encoding.AMF3);
    }

    /**
     * RuntimeStatusObject, which the status object service returns for NetStream.Play codes, is anonymous on both encodings as well.
     */
    @Test
    public void testRuntimeStatusObjectIsAnonymous() {
        for (Encoding encoding : new Encoding[] { Encoding.AMF0, Encoding.AMF3 }) {
            RuntimeStatusObject status = new RuntimeStatusObject(StatusCodes.NS_PLAY_START, StatusObject.STATUS, "Started playing test.");
            IoBuffer out = flip(encoder(encoding).encodeInvoke(invoke("onStatus", 0, status)));
            String body = hex(out);
            String object = encoding == Encoding.AMF3 ? "110a0b01" : "03";
            String head = encoding == Encoding.AMF3 ? "11" + amf3String("onStatus") + "110400" + "1101" : amf0String("onStatus") + amf0Number(0) + "05";
            assertTrue(encoding + " " + body, body.startsWith(head + object));
            Invoke decoded = (Invoke) decode(encoding, Constants.TYPE_INVOKE, out);
            Object info = decoded.getCall().getArguments()[0];
            assertTrue(encoding + " " + info, info instanceof Map);
            assertEquals(StatusCodes.NS_PLAY_START, ((Map<?, ?>) info).get("code"));
        }
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
     * A notify (data message, no transaction id) such as onBWDone or a client method call from the server, read back by the client
     * decoder as a call with all its arguments.
     */
    @Test
    public void testNotify() {
        Notify notify = new Notify();
        notify.setCall(new Call(null, "onBWDone", new Object[] { 1.5d }));
        IoBuffer out = flip(encoder(Encoding.AMF0).encodeNotify(notify));
        assertEquals(amf0String("onBWDone") + amf0Number(1.5d), hex(out));
        assertClientNotify(Encoding.AMF0, out, "onBWDone", 1.5d);
        notify = new Notify();
        notify.setCall(new Call(null, "onBWDone", new Object[] { 1.5d }));
        out = flip(encoder(Encoding.AMF3).encodeNotify(notify));
        assertEquals("11" + amf3String("onBWDone") + "1105" + double64(1.5d), hex(out));
        assertClientNotify(Encoding.AMF3, out, "onBWDone", 1.5d);
        // a server-side call of a client method through ServiceUtils.notifyOnConnection
        for (Encoding encoding : Encoding.values()) {
            notify = new Notify();
            notify.setCall(new Call(null, "receiveMessage", new Object[] { "from", "text", DATE }));
            out = flip(encoder(encoding).encodeNotify(notify));
            assertClientNotify(encoding, out, "receiveMessage", "from", "text", DATE);
        }
    }

    private static void assertClientNotify(Encoding encoding, IoBuffer in, String method, Object... args) {
        Header header = new Header();
        header.setDataType(Constants.TYPE_NOTIFY);
        header.setStreamId(0);
        IRTMPEvent event = new RTMPClientProtocolDecoder().decodeMessage(connection(encoding), header, in);
        assertFalse("the whole body must be read", in.hasRemaining());
        assertFalse(encoding.toString(), event instanceof Invoke);
        Notify decoded = (Notify) event;
        assertEquals(method, decoded.getCall().getServiceMethodName());
        assertArrayEquals(encoding.toString(), args, decoded.getCall().getArguments());
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
