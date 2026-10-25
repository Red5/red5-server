/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.io.amf3;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.Test;
import org.red5.compatibility.flex.messaging.io.ObjectProxy;
import org.red5.compatibility.flex.messaging.messages.CommandMessage;
import org.red5.io.object.Deserializer;
import org.red5.io.object.Serializer;
import org.red5.io.utils.ObjectMap;
import org.red5.io.utils.XMLUtils;
import org.w3c.dom.Document;

/**
 * Wire level tests of the AMF3 codec: hand written bytes following the AMF3 specification are decoded, and encoded output is compared byte
 * for byte.
 */
public class AMF3WireTest {

    // decoding helpers

    private static byte[] bytes(Object... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Object part : parts) {
            if (part instanceof Integer) {
                out.write((Integer) part);
            } else if (part instanceof String) {
                out.writeBytes(((String) part).getBytes(StandardCharsets.UTF_8));
            } else if (part instanceof Double) {
                IoBuffer d = IoBuffer.allocate(8);
                d.putDouble((Double) part);
                out.writeBytes(d.array());
            } else if (part instanceof byte[]) {
                out.writeBytes((byte[]) part);
            } else {
                throw new IllegalArgumentException(String.valueOf(part));
            }
        }
        return out.toByteArray();
    }

    /** Input in the AMF0 context, as used for RTMP message bodies. */
    private static Input amf0Context(IoBuffer buf) {
        return new Input(buf);
    }

    /** Input in the AMF3 context. */
    private static Input amf3Context(IoBuffer buf) {
        Input in = new Input(buf);
        in.enforceAMF3();
        return in;
    }

    private static Object decode(Input in) {
        return Deserializer.deserialize(in, Object.class);
    }

    // encoding helpers

    private static String hex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }

    private static byte[] written(IoBuffer buf) {
        IoBuffer d = buf.duplicate();
        d.flip();
        byte[] data = new byte[d.remaining()];
        d.get(data);
        return data;
    }

    private static IoBuffer newBuffer() {
        IoBuffer buf = IoBuffer.allocate(64);
        buf.setAutoExpand(true);
        return buf;
    }

    /** Serializes the values with one AMF3 output in the AMF0 context and returns the bytes. */
    private static byte[] encode(Object... values) {
        IoBuffer buf = newBuffer();
        Output out = new Output(buf);
        for (Object value : values) {
            Serializer.serialize(out, value);
        }
        return written(buf);
    }

    // traits references

    @Test
    public void testOnlyInlineTraitsEnterTheTraitsTable() {
        // [{a:1}, {b:2} (traits ref 0), Foo{x:3} sealed inline traits, traits ref 1 {x:4}]
        byte[] data = bytes(0x11, 0x09, 0x09, 0x01, //
                0x0A, 0x0B, 0x01, 0x03, "a", 0x04, 0x01, 0x01, //
                0x0A, 0x01, 0x03, "b", 0x04, 0x02, 0x01, //
                0x0A, 0x13, 0x07, "Foo", 0x03, "x", 0x04, 0x03, //
                0x0A, 0x05, 0x04, 0x04);
        IoBuffer buf = IoBuffer.wrap(data);
        List<?> result = (List<?>) decode(amf0Context(buf));
        assertEquals(4, result.size());
        assertEquals(Map.of("a", 1), result.get(0));
        assertEquals(Map.of("b", 2), result.get(1));
        assertEquals(Map.of("x", 3), result.get(2));
        // the second traits table entry is Foo; a traits reference must not add an entry
        assertEquals(Map.of("x", 4), result.get(3));
        assertFalse(buf.hasRemaining());
    }

    @Test
    public void testSealedTraitsReferenceReadsNoNames() {
        // two sealed anonymous objects {a}, the second one by traits reference 0, then a dynamic member-less object by reference
        byte[] data = bytes(0x11, 0x09, 0x05, 0x01, //
                0x0A, 0x13, 0x01, 0x03, "a", 0x04, 0x01, //
                0x0A, 0x01, 0x04, 0x02);
        IoBuffer buf = IoBuffer.wrap(data);
        List<?> result = (List<?>) decode(amf0Context(buf));
        assertEquals(Arrays.asList(Map.of("a", 1), Map.of("a", 2)), result);
        assertFalse(buf.hasRemaining());
    }

    @Test
    public void testCommandMessageWithInlineSealedTraits() {
        // flex.messaging.messages.CommandMessage as Flash writes it when not using the small message form: inline sealed traits with all
        // member names on the wire, the values follow in the order of the names
        String className = "flex.messaging.messages.CommandMessage";
        String[] names = { "body", "clientId", "correlationId", "destination", "headers", "messageId", "operation", "timeToLive", "timestamp" };
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x0A);
        // U29O-traits: 9 sealed members, not dynamic, not externalizable, inline traits, inline object = (9 << 4) | 0x03
        out.writeBytes(new byte[] { (byte) 0x81, 0x13 });
        out.write(className.length() << 1 | 1);
        out.writeBytes(className.getBytes(StandardCharsets.UTF_8));
        for (String name : names) {
            out.write(name.length() << 1 | 1);
            out.writeBytes(name.getBytes(StandardCharsets.UTF_8));
        }
        out.writeBytes(bytes(0x06, 0x11, "the-body")); // body
        out.writeBytes(bytes(0x06, 0x07, "cid")); // clientId
        out.writeBytes(bytes(0x06, 0x01)); // correlationId ""
        out.writeBytes(bytes(0x06, 0x09, "dest")); // destination
        out.writeBytes(bytes(0x0A, 0x0B, 0x01, 0x09, "DSId", 0x06, 0x03, "x", 0x01)); // headers {DSId: "x"}
        out.writeBytes(bytes(0x06, 0x07, "mid")); // messageId
        out.writeBytes(bytes(0x04, 0x05)); // operation 5
        out.writeBytes(bytes(0x04, 0x00)); // timeToLive 0
        out.writeBytes(bytes(0x05, 1.0E12d)); // timestamp
        out.writeBytes(bytes(0x06, 0x06)); // trailing value: string reference 3 ("cid")
        IoBuffer buf = IoBuffer.wrap(out.toByteArray());
        Input in = amf3Context(buf);
        Object result = decode(in);
        assertTrue(String.valueOf(result), result instanceof CommandMessage);
        CommandMessage msg = (CommandMessage) result;
        assertEquals("the-body", msg.getBody());
        assertEquals("cid", msg.getClientId());
        assertEquals("dest", msg.getDestination());
        assertEquals("x", msg.getHeader("DSId"));
        assertEquals("mid", msg.getMessageId());
        assertEquals(5, msg.getOperation());
        assertEquals(0L, msg.getTimeToLive());
        assertEquals(1000000000000L, msg.getTimestamp());
        // string table: class name, the 9 member names, then the values; reference 3 proves the names were read from the stream
        assertEquals("correlationId", decode(in));
        assertFalse(buf.hasRemaining());
    }

    @Test
    public void testCommandMessageRoundTrip() {
        CommandMessage original = new CommandMessage();
        original.setOperation(2);
        original.setDestination("dest");
        original.setMessageId("mid");
        original.setTimestamp(1234L);
        original.setBody("the-body");
        IoBuffer buf = IoBuffer.wrap(encode(original));
        Object result = decode(amf0Context(buf));
        assertTrue(String.valueOf(result), result instanceof CommandMessage);
        CommandMessage msg = (CommandMessage) result;
        assertEquals(2, msg.getOperation());
        assertEquals("dest", msg.getDestination());
        assertEquals("mid", msg.getMessageId());
        assertEquals(1234L, msg.getTimestamp());
        assertEquals("the-body", msg.getBody());
        assertFalse(buf.hasRemaining());
    }

    // avmplus-object marker scope

    @Test
    public void testSwitchAppliesToNextValueOnly() {
        // AMF0: 0x11 then AMF3 string "a", then AMF0 string "b"
        IoBuffer buf = IoBuffer.wrap(bytes(0x11, 0x06, 0x03, "a", 0x02, 0x00, 0x01, "b"));
        Input in = amf0Context(buf);
        assertEquals("a", decode(in));
        assertEquals("b", decode(in));
        assertFalse(buf.hasRemaining());
    }

    @Test
    public void testSwitchInsideAMF0Object() {
        // AMF0 object {x: <AMF3 "v">, y: "w"}: the property after the switched value is AMF0 again
        IoBuffer buf = IoBuffer.wrap(bytes(0x03, 0x00, 0x01, "x", 0x11, 0x06, 0x03, "v", 0x00, 0x01, "y", 0x02, 0x00, 0x01, "w", 0x00, 0x00, 0x09));
        Map<?, ?> result = (Map<?, ?>) decode(amf0Context(buf));
        assertEquals(Map.of("x", "v", "y", "w"), result);
        assertFalse(buf.hasRemaining());
    }

    @Test
    public void testDirectReadDataTypeSwitch() {
        // values read without the deserializer: the switch ends when the next value starts
        IoBuffer buf = IoBuffer.wrap(bytes(0x11, 0x06, 0x03, "a", 0x02, 0x00, 0x01, "b"));
        Input in = amf0Context(buf);
        in.readDataType();
        assertEquals("a", in.readString());
        in.readDataType();
        assertEquals("b", in.readString());
        assertFalse(buf.hasRemaining());
    }

    // AMF0 context follows the AMF0 type table

    @Test
    public void testAMF0MarkersAreNotAMF3Types() {
        // 0x0D unsupported has no data
        IoBuffer buf = IoBuffer.wrap(bytes(0x0D, 0x02, 0x00, 0x01, "z"));
        Input in = amf0Context(buf);
        assertNull(decode(in));
        assertEquals("z", decode(in));
        assertFalse(buf.hasRemaining());
        // 0x10 typed object, the class is unknown so a map is returned
        buf = IoBuffer.wrap(bytes(0x10, 0x00, 0x03, "Foo", 0x00, 0x01, "a", 0x00, 1.0d, 0x00, 0x00, 0x09, 0x02, 0x00, 0x01, "z"));
        in = amf0Context(buf);
        assertEquals(Map.of("a", 1.0d), decode(in));
        assertEquals("z", decode(in));
        assertFalse(buf.hasRemaining());
        // 0x00 number is a double, not an AMF3 integer
        buf = IoBuffer.wrap(bytes(0x00, 2.5d));
        assertEquals(2.5d, decode(amf0Context(buf)));
        assertFalse(buf.hasRemaining());
        // 0x01 boolean carries a value byte
        buf = IoBuffer.wrap(bytes(0x01, 0x01, 0x01, 0x00));
        in = amf0Context(buf);
        assertEquals(Boolean.TRUE, decode(in));
        assertEquals(Boolean.FALSE, decode(in));
        assertFalse(buf.hasRemaining());
    }

    @Test
    public void testAMF0ObjectsAndReferences() {
        // object {a: null}
        IoBuffer buf = IoBuffer.wrap(bytes(0x03, 0x00, 0x01, "a", 0x05, 0x00, 0x00, 0x09));
        assertEquals(Map.of(), stripNulls((Map<?, ?>) decode(amf0Context(buf))));
        assertFalse(buf.hasRemaining());
        // ECMA array {a: "b"}
        buf = IoBuffer.wrap(bytes(0x08, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, "a", 0x02, 0x00, 0x01, "b", 0x00, 0x00, 0x09));
        assertEquals(Map.of("a", "b"), decode(amf0Context(buf)));
        assertFalse(buf.hasRemaining());
        // strict array [{x: "y"}, reference 1]
        buf = IoBuffer.wrap(bytes(0x0A, 0x00, 0x00, 0x00, 0x02, 0x03, 0x00, 0x01, "x", 0x02, 0x00, 0x01, "y", 0x00, 0x00, 0x09, 0x07, 0x00, 0x01));
        List<?> list = (List<?>) decode(amf0Context(buf));
        assertEquals(2, list.size());
        assertSame(list.get(0), list.get(1));
        assertFalse(buf.hasRemaining());
    }

    private static Map<?, ?> stripNulls(Map<?, ?> map) {
        map.values().removeIf(v -> v == null);
        return map;
    }

    @Test
    public void testGetStringInAMF0Context() {
        IoBuffer buf = IoBuffer.wrap(bytes(0x00, 0x03, "foo", 0x01));
        assertEquals("foo", amf0Context(buf).getString());
        assertEquals(5, buf.position());
        // AMF3 context reads an UTF-8-vr
        buf = IoBuffer.wrap(bytes(0x07, "foo"));
        assertEquals("foo", amf3Context(buf).getString());
        assertFalse(buf.hasRemaining());
    }

    // dictionary

    @Test
    public void testDictionary() {
        IoBuffer buf = IoBuffer.wrap(bytes(0x11, 0x11, 0x03, 0x00, 0x06, 0x03, "k", 0x04, 0x01, 0x02, 0x00, 0x01, "z"));
        Input in = amf0Context(buf);
        assertEquals(Map.of("k", 1), decode(in));
        assertEquals("z", decode(in));
        assertFalse(buf.hasRemaining());
    }

    @Test
    public void testDictionaryKeysAndReferences() {
        // weak keys, {1: "a", "self": <reference 0>, <object {}>: true}
        IoBuffer buf = IoBuffer.wrap(bytes(0x11, 0x07, 0x01, //
                0x04, 0x01, 0x06, 0x03, "a", //
                0x06, 0x09, "self", 0x11, 0x00, //
                0x0A, 0x0B, 0x01, 0x01, 0x03));
        Map<?, ?> map = (Map<?, ?>) decode(amf3Context(buf));
        assertEquals(3, map.size());
        assertEquals("a", map.get(1));
        assertSame(map, map.get("self"));
        assertEquals(Boolean.TRUE, map.get(new ObjectMap<>()));
        assertFalse(buf.hasRemaining());
    }

    // vectors

    @Test
    public void testVectorNumberDecode() {
        IoBuffer buf = IoBuffer.wrap(bytes(0x11, 0x0F, 0x05, 0x00, 1.5d, -2.25d));
        assertEquals(new Vector<>(Arrays.asList(1.5d, -2.25d)), decode(amf0Context(buf)));
        assertFalse(buf.hasRemaining());
    }

    @Test
    public void testVectorObjectElementsAreFullValues() {
        // Vector.<*> [2.5, true, false, [1], Date(0), null]
        IoBuffer buf = IoBuffer.wrap(bytes(0x11, 0x10, 0x0D, 0x00, 0x01, 0x05, 2.5d, 0x03, 0x02, 0x09, 0x03, 0x01, 0x04, 0x01, 0x08, 0x01, 0.0d, 0x01));
        Vector<?> v = (Vector<?>) decode(amf0Context(buf));
        assertEquals(6, v.size());
        assertEquals(2.5d, v.get(0));
        assertEquals(Boolean.TRUE, v.get(1));
        assertEquals(Boolean.FALSE, v.get(2));
        assertEquals(Arrays.asList(1), v.get(3));
        assertEquals(new Date(0), v.get(4));
        assertNull(v.get(5));
        assertFalse(buf.hasRemaining());
    }

    @Test
    public void testVectorObjectTypeName() {
        // Vector.<String> ["s", "s"]: the object type name is an UTF-8-vr that enters the string table
        IoBuffer buf = IoBuffer.wrap(bytes(0x11, 0x10, 0x05, 0x00, 0x0D, "String", 0x06, 0x03, "s", 0x06, 0x02, 0x02, 0x00, 0x01, "z"));
        Input in = amf0Context(buf);
        assertEquals(new Vector<>(Arrays.asList("s", "s")), decode(in));
        assertEquals("z", decode(in));
        assertFalse(buf.hasRemaining());
    }

    @Test
    public void testVectorUIntIsUnsigned() {
        IoBuffer buf = IoBuffer.wrap(bytes(0x11, 0x0E, 0x05, 0x00, 0xFF, 0xFF, 0xFF, 0xFF, 0x80, 0x00, 0x00, 0x00));
        assertEquals(new Vector<>(Arrays.asList(4294967295L, 2147483648L)), decode(amf0Context(buf)));
        assertFalse(buf.hasRemaining());
    }

    @Test
    public void testVectorsEnterObjectTable() {
        // [Vector.<int>[7], reference 1, Vector.<*>[], reference 2]
        IoBuffer buf = IoBuffer.wrap(bytes(0x09, 0x09, 0x01, 0x0D, 0x03, 0x00, 0x00, 0x00, 0x00, 0x07, 0x0D, 0x02, 0x10, 0x01, 0x00, 0x01, 0x10, 0x04));
        List<?> list = (List<?>) decode(amf3Context(buf));
        assertEquals(4, list.size());
        assertEquals(new Vector<>(Arrays.asList(7)), list.get(0));
        assertSame(list.get(0), list.get(1));
        assertSame(list.get(2), list.get(3));
        assertFalse(buf.hasRemaining());
    }

    // U29

    @Test
    public void testIntegerValuesAreSigned29Bit() {
        Object[][] cases = { { 0x7F, 127 }, { new byte[] { (byte) 0xBF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF }, 268435455 }, { new byte[] { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF }, -1 }, { new byte[] { (byte) 0xC0, (byte) 0x80, (byte) 0x80, 0x00 }, -268435456 } };
        for (Object[] c : cases) {
            IoBuffer buf = IoBuffer.wrap(bytes(0x04, c[0]));
            assertEquals(c[1], decode(amf3Context(buf)));
            assertFalse(buf.hasRemaining());
        }
    }

    @Test
    public void testHeadersAreUnsigned() {
        // U29 0x1FFFFFFE: reference 268435455; read signed it was the negative reference -1
        byte[] max = { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFE };
        for (int marker : new int[] { 0x06, 0x09, 0x0A }) {
            try {
                decode(amf3Context(IoBuffer.wrap(bytes(marker, max))));
                fail("Expected an out of range reference");
            } catch (RuntimeException e) {
                assertTrue(e.getMessage(), e.getMessage() != null && e.getMessage().contains("268435455"));
            }
        }
    }

    @Test
    public void testIntegerEncoding() {
        Object[][] cases = { { 127, "04 7F" }, { 128, "04 81 00" }, { 16383, "04 FF 7F" }, { 16384, "04 81 80 00" }, { 2097151, "04 FF FF 7F" }, { 2097152, "04 80 C0 80 00" }, { 268435455, "04 BF FF FF FF" }, { -1, "04 FF FF FF FF" }, { -268435456, "04 C0 80 80 00" }, { 268435456, "05 41 B0 00 00 00 00 00 00" }, { -268435457, "05 C1 B0 00 00 01 00 00 00" } };
        for (Object[] c : cases) {
            IoBuffer buf = newBuffer();
            Output out = new Output(buf);
            out.enforceAMF3();
            out.writeNumber((Integer) c[0]);
            assertEquals(String.valueOf(c[0]), c[1], hex(written(buf)));
            Number decoded = (Number) decode(amf3Context(IoBuffer.wrap(written(buf))));
            assertEquals(((Integer) c[0]).intValue(), decoded.intValue());
        }
    }

    @Test
    public void testPutIntegerRange() {
        IoBuffer buf = newBuffer();
        Output out = new Output(buf);
        out.putInteger(0x1FFFFFFF);
        assertEquals("FF FF FF FF", hex(written(buf)));
        for (long value : new long[] { 0x20000000L, -268435457L }) {
            try {
                out.putInteger(value);
                fail("Expected IllegalArgumentException for " + value);
            } catch (IllegalArgumentException expected) {
            }
        }
        assertEquals("nothing written for rejected values", 4, written(buf).length);
    }

    // references across inputs sharing a RefStorage

    @Test
    public void testRefStorageSharedAcrossInputs() {
        IoBuffer buf = IoBuffer.wrap(bytes(0x11, 0x0A, 0x0B, 0x01, 0x03, "a", 0x04, 0x01, 0x01, //
                0x11, 0x0A, 0x01, 0x03, "b", 0x04, 0x02, 0x01, //
                0x11, 0x0A, 0x00, //
                0x11, 0x0A, 0x02, //
                0x11, 0x06, 0x00));
        Input.RefStorage refStorage = new Input.RefStorage();
        Object[] values = new Object[5];
        for (int i = 0; i < values.length; i++) {
            values[i] = decode(new Input(buf, refStorage));
        }
        assertEquals(Map.of("a", 1), values[0]);
        // traits reference 0 from the first input
        assertEquals(Map.of("b", 2), values[1]);
        // object references 0 and 1 resolve to the objects of the first and second input
        assertSame(values[0], values[2]);
        assertSame(values[1], values[3]);
        // string reference 0
        assertEquals("a", values[4]);
        assertFalse(buf.hasRemaining());
    }

    // encoding

    @Test
    public void testVectorNumberEncoding() {
        Vector<Double> v = new Vector<>(Arrays.asList(1.5d));
        byte[] data = encode(v);
        assertEquals("11 0F 03 00 3F F8 00 00 00 00 00 00", hex(data));
        assertEquals(v, decode(amf0Context(IoBuffer.wrap(data))));
    }

    @Test
    public void testVectorObjectEncodingKeepsLaterValuesSwitched() {
        Vector<Object> v = new Vector<>(Arrays.asList("s"));
        byte[] data = encode(v, "x");
        assertEquals("11 10 03 00 01 06 03 73 11 06 03 78", hex(data));
        IoBuffer buf = IoBuffer.wrap(data);
        Input in = amf0Context(buf);
        assertEquals(v, decode(in));
        assertEquals("x", decode(in));
        assertFalse(buf.hasRemaining());
    }

    @Test
    public void testVectorIntAndUIntEncoding() {
        assertEquals("11 0D 03 00 FF FF FF FE", hex(encode(new Vector<>(Arrays.asList(-2)))));
        byte[] data = encode(new Vector<>(Arrays.asList(4294967295L)));
        assertEquals("11 0E 03 00 FF FF FF FF", hex(data));
        assertEquals(new Vector<>(Arrays.asList(4294967295L)), decode(amf0Context(IoBuffer.wrap(data))));
    }

    @Test
    public void testResetClearsStringReferences() {
        IoBuffer buf = newBuffer();
        Output out = new Output(buf);
        out.writeString("a");
        out.writeString("a");
        out.reset();
        out.writeString("a");
        assertEquals("11 06 03 61 11 06 00 11 06 03 61", hex(written(buf)));
    }

    @Test
    public void testExternalizableTraitsHeader() {
        byte[] data = encode(new ObjectProxy<String, Object>(Map.of("k", "v")));
        String className = "flex.messaging.io.ObjectProxy";
        // inline object, inline traits, externalizable: 0x07
        assertEquals("11 0A 07", hex(Arrays.copyOf(data, 3)));
        assertEquals(className.length() << 1 | 1, data[3]);
        Object decoded = decode(amf0Context(IoBuffer.wrap(data)));
        assertTrue(String.valueOf(decoded), decoded instanceof ObjectProxy);
        assertEquals("v", ((ObjectProxy<?, ?>) decoded).get("k"));
    }

    @Test
    public void testXMLEncoding() throws Exception {
        IoBuffer buf = newBuffer();
        Output out = new Output(buf);
        out.writeXML("<a/>");
        Document doc = XMLUtils.stringToDoc("<b/>");
        out.writeXML(doc);
        out.writeXML(doc);
        ObjectMap<String, Object> map = new ObjectMap<>();
        Serializer.serialize(out, map);
        Serializer.serialize(out, map);
        // the xml values take object reference indexes 0 and 1, so the map is index 2
        assertEquals("11 0B 09 3C 61 2F 3E 11 0B 09 3C 62 2F 3E 11 0B 02 11 0A 0B 01 01 11 0A 04", hex(written(buf)));
        IoBuffer in = IoBuffer.wrap(written(buf));
        Input input = amf0Context(in);
        assertEquals("a", ((Document) decode(input)).getDocumentElement().getNodeName());
        Document b = (Document) decode(input);
        assertEquals("b", b.getDocumentElement().getNodeName());
        assertSame(b, decode(input));
        Object m = decode(input);
        assertSame(m, decode(input));
        assertFalse(in.hasRemaining());
        // AMF3 context: no avmplus-object marker
        buf = newBuffer();
        out = new Output(buf);
        out.enforceAMF3();
        out.writeXML("<a/>");
        assertEquals("0B 09 3C 61 2F 3E", hex(written(buf)));
    }

    @Test
    public void testArrayOfDatesRoundTrip() {
        Date date = new Date(1000L);
        byte[] data = encode(Arrays.asList(date, date, "s", "s"));
        List<?> list = (List<?>) decode(amf0Context(IoBuffer.wrap(data)));
        assertEquals(Arrays.asList(date, date, "s", "s"), list);
        assertSame(list.get(0), list.get(1));
    }

}
