package org.red5.server.net.rtmp.codec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.red5.server.net.rtmp.codec.RTMPProtocolEncoderAMFTest.amf3String;
import static org.red5.server.net.rtmp.codec.RTMPProtocolEncoderAMFTest.connection;
import static org.red5.server.net.rtmp.codec.RTMPProtocolEncoderAMFTest.hex;
import static org.red5.server.net.rtmp.codec.RTMPProtocolEncoderAMFTest.toList;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.apache.commons.codec.binary.Hex;
import org.apache.mina.core.buffer.IoBuffer;
import org.junit.After;
import org.junit.Test;
import org.red5.client.net.rtmp.codec.RTMPClientProtocolEncoder;
import org.red5.server.api.IConnection.Encoding;
import org.red5.server.api.Red5;
import org.red5.server.so.FlexSharedObjectMessage;
import org.red5.server.so.ISharedObjectEvent;
import org.red5.server.so.ISharedObjectEvent.Type;
import org.red5.server.so.ISharedObjectMessage;
import org.red5.server.so.SharedObjectMessage;

/**
 * Shared object messages encoded by the server and the client encoders and decoded by the decoder, on AMF0 and AMF3 connections: the
 * events the server sends to clients (as SharedObjectMessage on AMF0 and FlexSharedObjectMessage on AMF3 connections, as
 * RTMPConnection.sendSharedObjectMessage chooses) and the events a client sends to the server.
 */
public class SharedObjectCodecTest {

    private static final Date DATE = new Date(1700000000123L);

    @After
    public void tearDown() {
        Red5.setConnectionLocal(null);
    }

    private static Map<String, Object> shared() {
        Map<String, Object> shared = new LinkedHashMap<>();
        shared.put("name", "shared");
        shared.put("when", DATE);
        return shared;
    }

    /**
     * An object with a date, a nested object written twice (a reference the second time), a list and, on AMF3, vectors.
     */
    private static Map<String, Object> payload(Encoding encoding) {
        Map<String, Object> shared = shared();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("a", shared);
        payload.put("b", shared);
        payload.put("list", Arrays.asList("x", "y", 3.0d));
        payload.put("text", "héllo");
        if (encoding == Encoding.AMF3) {
            payload.put("numbers", new Vector<>(List.of(1.5d, 2.5d)));
            payload.put("ints", new Vector<>(List.of(1, -2)));
            payload.put("objects", new Vector<Object>(List.of("s", 1.5d)));
        }
        return payload;
    }

    @SuppressWarnings("unchecked")
    private static void assertPayload(Encoding encoding, Object value) {
        Map<String, Object> payload = (Map<String, Object>) value;
        Map<String, Object> a = (Map<String, Object>) payload.get("a");
        assertEquals("shared", a.get("name"));
        assertEquals(DATE, a.get("when"));
        assertSame(encoding.toString(), a, payload.get("b"));
        assertEquals(Arrays.asList("x", "y", 3.0d), toList(payload.get("list")));
        assertEquals("héllo", payload.get("text"));
        if (encoding == Encoding.AMF3) {
            assertEquals(List.of(1.5d, 2.5d), toList(payload.get("numbers")));
            assertEquals(List.of(1, -2), toList(payload.get("ints")));
            assertEquals(List.of("s", 1.5d), toList(payload.get("objects")));
        }
    }

    private static IoBuffer encode(RTMPProtocolEncoder encoder, Encoding encoding, ISharedObjectMessage msg) {
        encoder.setConnection(connection(encoding));
        IoBuffer out = msg instanceof FlexSharedObjectMessage ? encoder.encodeFlexSharedObject(msg) : encoder.encodeSharedObject(msg);
        return out.flip();
    }

    private static ISharedObjectMessage decode(IoBuffer in, boolean flex) {
        RTMPProtocolDecoder decoder = new RTMPProtocolDecoder();
        ISharedObjectMessage msg = flex ? decoder.decodeFlexSharedObject(in) : decoder.decodeSharedObject(in);
        assertFalse("the whole body must be read", in.hasRemaining());
        return msg;
    }

    private static List<ISharedObjectEvent> events(ISharedObjectMessage msg) {
        return new ArrayList<>(msg.getEvents());
    }

    /**
     * The message the server sends to its clients: initial data, attribute updates with complex values, a send message with arguments that
     * refer to each other, deletes, clear and a status.
     */
    private static SharedObjectMessage serverMessage(Encoding encoding) {
        SharedObjectMessage msg = encoding == Encoding.AMF3 ? new FlexSharedObjectMessage(null, "so", 3, true) : new SharedObjectMessage(null, "so", 3, true);
        msg.addEvent(Type.CLIENT_INITIAL_DATA, null, null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("payload", payload(encoding));
        msg.addEvent(Type.CLIENT_UPDATE_DATA, null, data);
        msg.addEvent(Type.CLIENT_UPDATE_ATTRIBUTE, "mine", null);
        Map<String, Object> shared = shared();
        msg.addEvent(Type.CLIENT_SEND_MESSAGE, "hello", Arrays.asList("arg", DATE, shared, shared));
        msg.addEvent(Type.CLIENT_DELETE_DATA, "gone", null);
        msg.addEvent(Type.CLIENT_STATUS, "SharedObject.BadPersistence", "error");
        return msg;
    }

    @SuppressWarnings("unchecked")
    private static void assertServerMessage(Encoding encoding, ISharedObjectMessage decoded) {
        assertEquals("so", decoded.getName());
        assertEquals(3, decoded.getVersion());
        assertTrue(decoded.isPersistent());
        List<ISharedObjectEvent> events = events(decoded);
        assertEquals(String.valueOf(events), 6, events.size());
        assertEquals(Type.CLIENT_INITIAL_DATA, events.get(0).getType());
        // the decoder reads an update data event into a map of the attributes it carries
        assertEquals(Type.CLIENT_UPDATE_DATA, events.get(1).getType());
        Map<String, Object> data = (Map<String, Object>) events.get(1).getValue();
        assertEquals(1, data.size());
        assertPayload(encoding, data.get("payload"));
        assertEquals(Type.CLIENT_UPDATE_ATTRIBUTE, events.get(2).getType());
        assertEquals("mine", events.get(2).getKey());
        // the send message marker is shared by both directions, the decoder names it with the server type
        assertEquals(Type.SERVER_SEND_MESSAGE, events.get(3).getType());
        assertEquals("hello", events.get(3).getKey());
        List<Object> args = (List<Object>) events.get(3).getValue();
        assertEquals(4, args.size());
        assertEquals("arg", args.get(0));
        assertEquals(DATE, args.get(1));
        assertEquals("shared", ((Map<String, Object>) args.get(2)).get("name"));
        assertSame(args.get(2), args.get(3));
        assertEquals(Type.CLIENT_DELETE_DATA, events.get(4).getType());
        assertEquals("gone", events.get(4).getKey());
        assertEquals(Type.CLIENT_STATUS, events.get(5).getType());
    }

    @Test
    public void testServerMessageOnAMF0Connection() {
        IoBuffer out = encode(new RTMPProtocolEncoder(), Encoding.AMF0, serverMessage(Encoding.AMF0));
        String body = hex(out);
        // name, version, persistent flag 2, reserved; the initial data event has no body
        assertTrue(body, body.startsWith("0002736f" + "00000003" + "00000002" + "00000000" + "0b00000000"));
        // the update data event: its key and the map as an AMF0 ECMA array; "b" is a reference to "a" (index 1, the outer array is 0)
        assertTrue(body, body.contains("0007" + Hex.encodeHexString("payload".getBytes(StandardCharsets.US_ASCII)) + "08"));
        assertTrue(body, body.contains("000162" + "070001"));
        assertFalse("no AMF3 value on an AMF0 connection: " + body, body.contains("0007" + Hex.encodeHexString("payload".getBytes(StandardCharsets.US_ASCII)) + "11"));
        assertServerMessage(Encoding.AMF0, decode(out, false));
    }

    /**
     * On an AMF3 connection the server sends a FlexSharedObjectMessage: an encoding byte, AMF0 names and keys, and values switched to AMF3.
     */
    @Test
    public void testServerMessageOnAMF3Connection() {
        IoBuffer out = encode(new RTMPProtocolEncoder(), Encoding.AMF3, serverMessage(Encoding.AMF3));
        String body = hex(out);
        assertTrue(body, body.startsWith("00" + "0002736f" + "00000003" + "00000002" + "00000000" + "0b00000000"));
        // the map is an AMF3 array with an associative part
        assertTrue(body, body.contains("0007" + Hex.encodeHexString("payload".getBytes(StandardCharsets.US_ASCII)) + "1109"));
        // the send message: the handler name is an AMF0 string, the arguments are AMF3; the AMF3 reference tables span the events of the
        // message, so the date, written in the payload before, is the object reference 2
        assertTrue(body, body.contains(RTMPProtocolEncoderAMFTest.amf0String("hello") + "11" + amf3String("arg") + "110804"));
        assertServerMessage(Encoding.AMF3, decode(out, true));
    }

    /**
     * A single attribute update with an AMF3 value, as the server sends one changed attribute.
     */
    @Test
    public void testServerUpdateDataSingleValueOnAMF3Connection() {
        SharedObjectMessage msg = new FlexSharedObjectMessage(null, "so", 1, false);
        msg.addEvent(Type.CLIENT_UPDATE_DATA, "color", "red");
        IoBuffer out = encode(new RTMPProtocolEncoder(), Encoding.AMF3, msg);
        String key = Hex.encodeHexString("color".getBytes(StandardCharsets.US_ASCII));
        assertEquals("00" + "0002736f" + "00000001" + "00000000" + "00000000" + "04" + "0000000d" + "0005" + key + "11" + amf3String("red"), hex(out));
        List<ISharedObjectEvent> events = events(decode(out, true));
        assertEquals(1, events.size());
        assertEquals(Map.of("color", "red"), events.get(0).getValue());
    }

    /**
     * The message a client sends to the server: connect, set attribute, send message, delete attribute.
     */
    private static SharedObjectMessage clientMessage(Encoding encoding) {
        SharedObjectMessage msg = new SharedObjectMessage(null, "so", 0, false);
        msg.addEvent(Type.SERVER_CONNECT, null, null);
        msg.addEvent(Type.SERVER_SET_ATTRIBUTE, "payload", payload(encoding));
        Map<String, Object> shared = shared();
        msg.addEvent(Type.SERVER_SEND_MESSAGE, "hello", Arrays.asList("arg", DATE, shared, shared));
        msg.addEvent(Type.SERVER_DELETE_ATTRIBUTE, "gone", null);
        return msg;
    }

    @SuppressWarnings("unchecked")
    private static void assertClientMessage(Encoding encoding, ISharedObjectMessage decoded) {
        assertEquals("so", decoded.getName());
        assertFalse(decoded.isPersistent());
        List<ISharedObjectEvent> events = events(decoded);
        assertEquals(String.valueOf(events), 4, events.size());
        assertEquals(Type.SERVER_CONNECT, events.get(0).getType());
        assertNull(events.get(0).getKey());
        assertEquals(Type.SERVER_SET_ATTRIBUTE, events.get(1).getType());
        assertEquals("payload", events.get(1).getKey());
        assertPayload(encoding, events.get(1).getValue());
        assertEquals(Type.SERVER_SEND_MESSAGE, events.get(2).getType());
        List<Object> args = (List<Object>) events.get(2).getValue();
        assertEquals("arg", args.get(0));
        assertEquals(DATE, args.get(1));
        assertSame(args.get(2), args.get(3));
        assertEquals(Type.SERVER_DELETE_ATTRIBUTE, events.get(3).getType());
        assertEquals("gone", events.get(3).getKey());
    }

    @Test
    public void testClientMessageToServer() {
        for (Encoding encoding : Encoding.values()) {
            IoBuffer out = encode(new RTMPClientProtocolEncoder(), encoding, clientMessage(encoding));
            String body = hex(out);
            String setAttribute = "0007" + Hex.encodeHexString("payload".getBytes(StandardCharsets.US_ASCII)) + (encoding == Encoding.AMF3 ? "1109" : "08");
            assertTrue(encoding + " " + body, body.contains(setAttribute));
            assertClientMessage(encoding, decode(out, false));
        }
    }

}
