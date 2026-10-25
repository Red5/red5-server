/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.io.amf;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.function.Consumer;

import org.apache.commons.codec.binary.Hex;
import org.apache.mina.core.buffer.IoBuffer;
import org.junit.Test;
import org.red5.io.model.CircularRefBean;
import org.red5.io.object.Deserializer;
import org.red5.io.object.Serializer;
import org.red5.io.utils.ObjectMap;
import org.red5.io.utils.XMLUtils;
import org.w3c.dom.Document;

/**
 * Wire-level AMF0 tests against the Adobe AMF0 specification (amf0-file-format-specification). Unlike the round-trip tests in AMFIOTest, which
 * pass whenever the Red5 writer and reader agree with each other, every test here either decodes a hand-built byte sequence taken from the
 * specification or compares encoder output with the exact bytes the specification requires.
 */
public class AMF0WireTest {

    /**
     * Builds AMF0 byte sequences.
     */
    private static final class Wire {

        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        Wire u8(int... values) {
            for (int value : values) {
                bytes.write(value);
            }
            return this;
        }

        Wire u16(int value) {
            return u8((value >> 8) & 0xff, value & 0xff);
        }

        Wire u32(long value) {
            return u8((int) (value >> 24) & 0xff, (int) (value >> 16) & 0xff, (int) (value >> 8) & 0xff, (int) value & 0xff);
        }

        Wire dbl(double value) {
            long bits = Double.doubleToLongBits(value);
            for (int shift = 56; shift >= 0; shift -= 8) {
                bytes.write((int) (bits >> shift) & 0xff);
            }
            return this;
        }

        Wire raw(byte[] data) {
            bytes.write(data, 0, data.length);
            return this;
        }

        /** UTF-8 with a U16 byte length, used for strings, property names and class names. */
        Wire utf8(String value) {
            byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
            return u16(encoded.length).raw(encoded);
        }

        /** UTF-8-long with a U32 byte length, used for long strings and XML documents. */
        Wire utf8Long(String value) {
            byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
            return u32(encoded.length).raw(encoded);
        }

        Wire number(double value) {
            return u8(AMF.TYPE_NUMBER).dbl(value);
        }

        Wire string(String value) {
            return u8(AMF.TYPE_STRING).utf8(value);
        }

        Wire reference(int index) {
            return u8(AMF.TYPE_REFERENCE).u16(index);
        }

        /** UTF-8-empty followed by the object-end-marker. */
        Wire objectEnd() {
            return u8(0x00, 0x00, AMF.TYPE_END_OF_OBJECT);
        }

        byte[] toArray() {
            return bytes.toByteArray();
        }
    }

    private static Wire wire() {
        return new Wire();
    }

    private static Input input(byte[] data) {
        return new Input(IoBuffer.wrap(data));
    }

    private static Object decode(Input in) {
        return Deserializer.deserialize(in, Object.class);
    }

    private static Object decode(byte[] data) {
        return decode(input(data));
    }

    private static byte[] encode(Consumer<Output> writer) {
        IoBuffer buf = IoBuffer.allocate(64).setAutoExpand(true);
        writer.accept(new Output(buf));
        buf.flip();
        byte[] encoded = new byte[buf.remaining()];
        buf.get(encoded);
        return encoded;
    }

    private static byte[] serialize(Object value) {
        return encode(out -> Serializer.serialize(out, value));
    }

    private static void assertBytes(byte[] expected, byte[] actual) {
        if (expected.length <= 256 && actual.length <= 256) {
            // hex strings give a readable failure message
            assertEquals(Hex.encodeHexString(expected), Hex.encodeHexString(actual));
        } else {
            assertArrayEquals(expected, actual);
        }
    }

    private static String repeat(char c, int count) {
        return String.valueOf(c).repeat(count);
    }

    // ---------------------------------------------------------------- decoding

    @Test
    public void decodeNumber() {
        assertEquals(1.5d, decode(wire().number(1.5).toArray()));
        assertEquals(-0.25d, decode(wire().u8(0x00, 0xBF, 0xD0, 0, 0, 0, 0, 0, 0).toArray()));
    }

    @Test
    public void decodeBoolean() {
        assertEquals(Boolean.TRUE, decode(wire().u8(0x01, 0x01).toArray()));
        assertEquals(Boolean.FALSE, decode(wire().u8(0x01, 0x00).toArray()));
    }

    @Test
    public void decodeString() {
        assertEquals("foo", decode(wire().u8(0x02, 0x00, 0x03, 'f', 'o', 'o').toArray()));
        // the U16 is a byte count, the pound sign is two UTF-8 bytes
        assertEquals("£", decode(wire().u8(0x02, 0x00, 0x02, 0xC2, 0xA3).toArray()));
    }

    @Test
    public void decodeLongString() {
        assertEquals("foo", decode(wire().u8(0x0C, 0x00, 0x00, 0x00, 0x03, 'f', 'o', 'o').toArray()));
        String big = repeat('x', 70000);
        assertEquals(big, decode(wire().u8(AMF.TYPE_LONG_STRING).utf8Long(big).toArray()));
    }

    @Test
    public void decodeObjectStopsAtObjectEnd() {
        Input in = input(wire().u8(AMF.TYPE_OBJECT).utf8("a").number(1).utf8("b").string("x").objectEnd().string("next").toArray());
        Map<?, ?> object = (Map<?, ?>) decode(in);
        assertEquals(2, object.size());
        assertEquals(1d, object.get("a"));
        assertEquals("x", object.get("b"));
        assertEquals("next", decode(in));
    }

    @Test
    public void decodeEcmaArray() {
        Input in = input(wire().u8(AMF.TYPE_MIXED_ARRAY).u32(0).utf8("k").string("v").objectEnd().string("next").toArray());
        Map<?, ?> map = (Map<?, ?>) decode(in);
        assertEquals(1, map.size());
        assertEquals("v", map.get("k"));
        assertEquals("next", decode(in));
    }

    @Test
    public void decodeStrictArray() {
        Input in = input(wire().u8(AMF.TYPE_ARRAY).u32(2).number(1).string("x").string("next").toArray());
        List<?> list = (List<?>) decode(in);
        assertEquals(2, list.size());
        assertEquals(1d, list.get(0));
        assertEquals("x", list.get(1));
        assertEquals("next", decode(in));
    }

    @Test
    public void decodeDateIgnoresTimeZone() {
        // the time-zone is reserved, a reader must not apply it: 0x01E0 is +480 minutes
        Input in = input(wire().u8(AMF.TYPE_DATE).dbl(1.0e12).u16(0x01E0).string("next").toArray());
        assertEquals(new Date(1000000000000L), decode(in));
        assertEquals("next", decode(in));
    }

    /**
     * Spec 2.9: a complex object gets its reference index when it is encountered, before its members, so <code>{a:{x:1}, b:{y:2}, c:ref 2}</code>
     * resolves c to b.
     */
    @Test
    public void decodeReferenceToSibling() {
        byte[] data = wire().u8(AMF.TYPE_OBJECT) //
                .utf8("a").u8(AMF.TYPE_OBJECT).utf8("x").number(1).objectEnd() //
                .utf8("b").u8(AMF.TYPE_OBJECT).utf8("y").number(2).objectEnd() //
                .utf8("c").reference(2) //
                .objectEnd().toArray();
        Map<?, ?> object = (Map<?, ?>) decode(data);
        assertNotNull(object.get("b"));
        assertSame(object.get("b"), object.get("c"));
        assertEquals(2d, ((Map<?, ?>) object.get("c")).get("y"));
    }

    @Test
    public void decodeReferenceToParentObject() {
        Map<?, ?> object = (Map<?, ?>) decode(wire().u8(AMF.TYPE_OBJECT).utf8("a").reference(0).objectEnd().toArray());
        assertSame(object, object.get("a"));
    }

    @Test
    public void decodeReferenceToParentEcmaArray() {
        Map<?, ?> map = (Map<?, ?>) decode(wire().u8(AMF.TYPE_MIXED_ARRAY).u32(0).utf8("a").reference(0).objectEnd().toArray());
        assertSame(map, map.get("a"));
    }

    @Test
    public void decodeReferenceToParentStrictArray() {
        List<?> list = (List<?>) decode(wire().u8(AMF.TYPE_ARRAY).u32(1).reference(0).toArray());
        assertEquals(1, list.size());
        assertSame(list, list.get(0));
    }

    @Test
    public void decodeReferenceToParentTypedObject() {
        byte[] data = wire().u8(AMF.TYPE_CLASS_OBJECT).utf8(CircularRefBean.class.getName()).utf8("refToSelf").reference(0).objectEnd().toArray();
        CircularRefBean bean = (CircularRefBean) decode(data);
        assertSame(bean, bean.getRefToSelf());
    }

    /**
     * A typed object whose class cannot be loaded is decoded as an anonymous map and must keep its own reference index.
     */
    @Test
    public void decodeReferenceInsideUnknownTypedObject() {
        byte[] data = wire().u8(AMF.TYPE_CLASS_OBJECT).utf8("org.red5.io.amf.NoSuchClass") //
                .utf8("a").u8(AMF.TYPE_OBJECT).utf8("x").number(1).objectEnd() //
                .utf8("b").reference(1) //
                .utf8("c").reference(0) //
                .objectEnd().toArray();
        Map<?, ?> object = (Map<?, ?>) decode(data);
        assertNotNull(object.get("a"));
        assertSame(object.get("a"), object.get("b"));
        assertSame(object, object.get("c"));
    }

    /**
     * Spec 2.9: dates are not complex objects and take no reference index; in <code>[date, {}, ref 1]</code> ref 1 is the object.
     */
    @Test
    public void decodeDateTakesNoReferenceIndex() {
        byte[] data = wire().u8(AMF.TYPE_ARRAY).u32(3).u8(AMF.TYPE_DATE).dbl(0).u16(0).u8(AMF.TYPE_OBJECT).objectEnd().reference(1).toArray();
        List<?> list = (List<?>) decode(data);
        assertTrue(list.get(1) instanceof Map);
        assertSame(list.get(1), list.get(2));
    }

    @Test
    public void decodeXmlDocumentTakesNoReferenceIndex() {
        byte[] data = wire().u8(AMF.TYPE_ARRAY).u32(3).u8(AMF.TYPE_XML).utf8Long("<a/>").u8(AMF.TYPE_OBJECT).objectEnd().reference(1).toArray();
        List<?> list = (List<?>) decode(data);
        assertTrue(list.get(0) instanceof Document);
        assertTrue(list.get(1) instanceof Map);
        assertSame(list.get(1), list.get(2));
    }

    @Test
    public void decodeStringTakesNoReferenceIndex() {
        byte[] data = wire().u8(AMF.TYPE_ARRAY).u32(3).string("s").u8(AMF.TYPE_OBJECT).objectEnd().reference(1).toArray();
        List<?> list = (List<?>) decode(data);
        assertTrue(list.get(1) instanceof Map);
        assertSame(list.get(1), list.get(2));
    }

    /**
     * Spec 2.17: an XML document is UTF-8-long, a U32 byte length followed by the text.
     */
    @Test
    public void decodeXmlDocument() {
        Input in = input(wire().u8(0x0F, 0x00, 0x00, 0x00, 0x04, '<', 'a', '/', '>').string("z").toArray());
        Document doc = (Document) decode(in);
        assertNotNull(doc);
        assertEquals("a", doc.getDocumentElement().getNodeName());
        assertEquals("z", decode(in));
    }

    @Test
    public void decodeXmlDocumentLongerThanU16() {
        String xml = "<a>" + repeat('x', 70000) + "</a>";
        Input in = input(wire().u8(AMF.TYPE_XML).utf8Long(xml).string("z").toArray());
        Document doc = (Document) decode(in);
        assertNotNull(doc);
        assertEquals(70000, doc.getDocumentElement().getTextContent().length());
        assertEquals("z", decode(in));
    }

    // ---------------------------------------------------------------- encoding

    @Test
    public void encodeNumber() {
        assertBytes(wire().u8(0x00, 0x3F, 0xF8, 0, 0, 0, 0, 0, 0).toArray(), serialize(1.5d));
        assertBytes(wire().number(42).toArray(), serialize(42));
    }

    @Test
    public void encodeBoolean() {
        assertBytes(wire().u8(0x01, 0x01).toArray(), serialize(Boolean.TRUE));
        assertBytes(wire().u8(0x01, 0x00).toArray(), serialize(Boolean.FALSE));
    }

    @Test
    public void encodeString() {
        assertBytes(wire().u8(0x02, 0x00, 0x03, 'f', 'o', 'o').toArray(), serialize("foo"));
        assertBytes(wire().u8(0x02, 0x00, 0x02, 0xC2, 0xA3).toArray(), serialize("£"));
    }

    /**
     * Spec 2.4 and 2.14: the string marker is used when the UTF-8 form fits in a U16, i.e. up to and including 65535 bytes.
     */
    @Test
    public void encodeStringOf65535BytesUsesStringMarker() {
        String value = repeat('x', 65535);
        assertBytes(wire().u8(AMF.TYPE_STRING).u16(0xFFFF).raw(value.getBytes(StandardCharsets.UTF_8)).toArray(), serialize(value));
        // the limit counts bytes, not characters: 32767 two-byte characters plus one ASCII character
        String multiByte = repeat('£', 32767) + "x";
        assertBytes(wire().u8(AMF.TYPE_STRING).utf8(multiByte).toArray(), serialize(multiByte));
    }

    @Test
    public void encodeStringOf65536BytesUsesLongStringMarker() {
        String value = repeat('x', 65536);
        assertBytes(wire().u8(AMF.TYPE_LONG_STRING).u32(65536).raw(value.getBytes(StandardCharsets.UTF_8)).toArray(), serialize(value));
    }

    @Test
    public void encodeAnonymousObject() {
        ObjectMap<String, Object> object = new ObjectMap<>();
        object.put("a", 1d);
        object.put("b", "x");
        assertBytes(wire().u8(AMF.TYPE_OBJECT).utf8("a").number(1).utf8("b").string("x").objectEnd().toArray(), serialize(object));
    }

    @Test
    public void encodeEcmaArray() {
        Map<String, Object> map = new HashMap<>();
        map.put("k", "v");
        assertBytes(wire().u8(AMF.TYPE_MIXED_ARRAY).u32(0).utf8("k").string("v").objectEnd().toArray(), serialize(map));
    }

    @Test
    public void encodeStrictArray() {
        assertBytes(wire().u8(AMF.TYPE_ARRAY).u32(2).number(1).string("x").toArray(), serialize(new Object[] { 1d, "x" }));
    }

    /**
     * Spec 2.13: the date time-zone is reserved and should be 0x0000, whatever the local time zone is.
     */
    @Test
    public void encodeDateWithZeroTimeZone() {
        TimeZone saved = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            assertBytes(wire().u8(AMF.TYPE_DATE).dbl(1000).u16(0x0000).toArray(), serialize(new Date(1000)));
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            assertBytes(wire().u8(AMF.TYPE_DATE).dbl(1000).u16(0x0000).toArray(), serialize(new Date(1000)));
        } finally {
            TimeZone.setDefault(saved);
        }
    }

    @Test
    public void encodeReferenceToSibling() {
        ObjectMap<String, Object> shared = new ObjectMap<>();
        ObjectMap<String, Object> object = new ObjectMap<>();
        object.put("a", shared);
        object.put("b", shared);
        assertBytes(wire().u8(AMF.TYPE_OBJECT).utf8("a").u8(AMF.TYPE_OBJECT).objectEnd().utf8("b").reference(1).objectEnd().toArray(), serialize(object));
    }

    @Test
    public void encodeReferenceToParent() {
        ObjectMap<String, Object> object = new ObjectMap<>();
        object.put("self", object);
        assertBytes(wire().u8(AMF.TYPE_OBJECT).utf8("self").reference(0).objectEnd().toArray(), serialize(object));
    }

    @Test
    public void encodeDateTakesNoReferenceIndex() {
        Date date = new Date(0);
        ObjectMap<String, Object> shared = new ObjectMap<>();
        byte[] dateBytes = wire().u8(AMF.TYPE_DATE).dbl(0).u16(0).toArray();
        byte[] expected = wire().u8(AMF.TYPE_ARRAY).u32(4).raw(dateBytes).raw(dateBytes).u8(AMF.TYPE_OBJECT).objectEnd().reference(1).toArray();
        assertBytes(expected, serialize(new Object[] { date, date, shared, shared }));
    }

    /**
     * Spec 2.9: a reference is a U16 index. Indices up to 65535 are referenced; an object whose index does not fit is written inline
     * again and takes a new index, as a decoder gives every inline object one.
     */
    @Test
    public void encodeReferencesBeyondU16() {
        // the array takes index 0, objects.get(k) index k + 1, so objects.get(65535) is the first object with index 65536
        List<Object> objects = new ArrayList<>();
        for (int i = 0; i < 65536; i++) {
            ObjectMap<String, Object> object = new ObjectMap<>();
            object.put("i", (double) i);
            objects.add(object);
        }
        objects.add(objects.get(0));
        objects.add(objects.get(40000));
        objects.add(objects.get(65535));
        objects.add(objects.get(65535));
        byte[] encoded = serialize(objects);
        byte[] last = wire().u8(AMF.TYPE_OBJECT).utf8("i").number(65535).objectEnd().toArray();
        byte[] tail = wire().raw(last).reference(1).reference(40001).raw(last).raw(last).toArray();
        assertBytes(tail, Arrays.copyOfRange(encoded, encoded.length - tail.length, encoded.length));
        List<?> decoded = (List<?>) decode(encoded);
        assertEquals(65540, decoded.size());
        assertSame(decoded.get(0), decoded.get(65536));
        assertSame(decoded.get(40000), decoded.get(65537));
        assertEquals(decoded.get(65535), decoded.get(65538));
        assertEquals(decoded.get(65535), decoded.get(65539));
    }

    /**
     * Spec 2.17: the XML document marker is followed by UTF-8-long, a U32 byte length.
     */
    @Test
    public void encodeXmlDocument() {
        assertBytes(wire().u8(0x0F, 0x00, 0x00, 0x00, 0x04, '<', 'a', '/', '>').toArray(), encode(out -> out.writeXML("<a/>")));
    }

    @Test
    public void encodeXmlDocumentFromDom() throws Exception {
        Document doc = XMLUtils.stringToDoc("<a><b>text</b></a>");
        String xml = XMLUtils.docToString(doc);
        assertBytes(wire().u8(AMF.TYPE_XML).utf8Long(xml).toArray(), serialize(doc));
    }

    @Test
    public void encodePropertyNameOf65535Bytes() {
        String name = repeat('k', 65535);
        ObjectMap<String, Object> object = new ObjectMap<>();
        object.put(name, 1d);
        assertBytes(wire().u8(AMF.TYPE_OBJECT).u16(0xFFFF).raw(name.getBytes(StandardCharsets.UTF_8)).number(1).objectEnd().toArray(), serialize(object));
    }

    /**
     * Spec 2.5: property names are UTF-8 with a U16 length, a name longer than 65535 bytes cannot be encoded and must not be written with a
     * U32 length.
     */
    @Test
    public void encodePropertyNameOf65536BytesFails() {
        ObjectMap<String, Object> object = new ObjectMap<>();
        object.put(repeat('k', 65536), 1d);
        assertRejectedLongName(() -> serialize(object));
    }

    @Test
    public void encodeEcmaArrayKeyOf65536BytesFails() {
        Map<String, Object> map = new HashMap<>();
        map.put(repeat('k', 65536), 1d);
        assertRejectedLongName(() -> serialize(map));
    }

    @Test
    public void encodeShortStringOf65536BytesFails() {
        IoBuffer buf = IoBuffer.allocate(16).setAutoExpand(true);
        assertRejectedLongName(() -> Output.putString(buf, repeat('c', 65536)));
        assertEquals("nothing may be written for a rejected name", 0, buf.position());
    }

    private static void assertRejectedLongName(Runnable encode) {
        try {
            encode.run();
            fail("a UTF-8 name longer than 65535 bytes must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("65536"));
        }
    }

    // ---------------------------------------------------------------- Red5 writer to Red5 reader

    @Test
    public void roundTripReferenceToSibling() {
        ObjectMap<String, Object> x = new ObjectMap<>();
        x.put("x", 1d);
        ObjectMap<String, Object> y = new ObjectMap<>();
        y.put("y", 2d);
        ObjectMap<String, Object> object = new ObjectMap<>();
        object.put("a", x);
        object.put("b", y);
        object.put("c", y);
        Map<?, ?> decoded = (Map<?, ?>) decode(serialize(object));
        assertEquals(x, decoded.get("a"));
        assertEquals(y, decoded.get("b"));
        assertSame(decoded.get("b"), decoded.get("c"));
    }

    @Test
    public void roundTripXmlDocument() throws Exception {
        String text = repeat('x', 70000);
        Document doc = XMLUtils.stringToDoc("<a>" + text + "</a>");
        Input in = input(encode(out -> {
            out.writeXML(doc);
            out.writeString("next");
        }));
        Document decoded = (Document) decode(in);
        assertNotNull(decoded);
        assertEquals(text, decoded.getDocumentElement().getTextContent());
        assertEquals("next", decode(in));
    }

}
