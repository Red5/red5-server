/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.io.interop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.red5.io.amf.Input;
import org.red5.io.object.Deserializer;
import org.red5.io.object.Serializer;

/**
 * Checks Red5's AMF0 and AMF3 codecs against byte sequences written by independent implementations.
 * <p>
 * The vectors in {@code src/test/resources/org/red5/io/interop} were produced by two oracles:
 * </p>
 * <ul>
 * <li>{@code miniamf-0.9.1.tsv}: Mini-AMF 0.9.1 from PyPI (the Python 3 fork of PyAMF, pure Python, run on Python 3.12 with setuptools 69 for
 * its pkg_resources import). It encoded the corpus of named cases whose Java counterparts are in {@link InteropCorpus}: null, undefined,
 * booleans, numbers including -0.0, NaN and the infinities, AMF3 integers at the U29 edges and beyond them, strings up to 2 MiB with
 * multi-byte UTF-8, dates, anonymous and typed objects with sealed and dynamic traits, object, traits and string references, self and parent
 * references, strict, dense, ECMA, mixed and sparse arrays, XML, ByteArray, an externalizable ArrayCollection, references past index 32767
 * and 65535, AMF0 bodies with 0x11 switched AMF3 values and plain AMF0 values after them.</li>
 * <li>{@code blazeds-4.7.3.tsv}: Apache Flex BlazeDS 4.7.3 (flex-messaging-core from Maven Central), for the AMF3 vector and dictionary
 * types Mini-AMF does not implement.</li>
 * </ul>
 * <p>
 * During the verification every vector was decoded by Red5, Red5's encoding of the Java value was decoded by the oracle, and both results
 * were compared with the value the oracle encoded. Where the oracle itself was wrong, the bytes were corrected and the note says how; the
 * notes also name every legitimate difference between the encodings. Each row of the files holds the codec, the case name, the oracle bytes
 * (or their SHA-256 when longer than 4 KiB), the value graph as written by {@link AmfDescriber} (or its SHA-256), how Red5's own encoding
 * compares and the note.
 * </p>
 * <p>
 * For each row this test decodes the oracle bytes with Red5 and compares the value graph, and encodes the Java value of the case with Red5:
 * a canonical encoding must equal the oracle bytes, any other must decode to the same value graph.
 * </p>
 */
@RunWith(Parameterized.class)
public class AmfInteropVectorsTest {

    private static final String[] FILES = { "miniamf-0.9.1.tsv", "blazeds-4.7.3.tsv" };

    private static final String SHA = "sha256:";

    private final String codec;

    private final String name;

    private final String check;

    private final String bytes;

    private final String value;

    private final String encoding;

    private final String note;

    public AmfInteropVectorsTest(String file, String codec, String name, String check, String bytes, String value, String encoding, String note) {
        this.codec = codec;
        this.name = name;
        this.check = check;
        this.bytes = bytes;
        this.value = value;
        this.encoding = encoding;
        this.note = note;
    }

    @Parameters(name = "{0} {1} {2} {3}")
    public static Collection<Object[]> vectors() throws IOException {
        List<Object[]> vectors = new ArrayList<>();
        for (String file : FILES) {
            try (InputStream in = AmfInteropVectorsTest.class.getResourceAsStream(file)) {
                assertNotNull(file, in);
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    String[] f = line.split("\t", -1);
                    // the oracle bytes are decoded when they are in the file, or when they equal Red5's encoding
                    if (!f[2].startsWith(SHA) || "canonical".equals(f[4])) {
                        vectors.add(new Object[] { file, f[0], f[1], "decode", f[2], f[3], f[4], f[5] });
                    }
                    if (!"none".equals(f[4])) {
                        vectors.add(new Object[] { file, f[0], f[1], "encode", f[2], f[3], f[4], f[5] });
                    }
                }
            }
        }
        return vectors;
    }

    @Test
    public void vector() {
        if ("decode".equals(check)) {
            byte[] data = bytes.startsWith(SHA) ? red5Bytes() : HexFormat.of().parseHex(bytes);
            assertValue(value, decode(data, false));
            if ("amf0".equals(codec) && !name.startsWith("nested_switch")) {
                // a body without switched values decodes the same with the plain AMF0 input
                assertValue(value, decode(data, true));
            }
        } else {
            byte[] data = red5Bytes();
            List<Object> values = javaValues();
            if ("canonical".equals(encoding)) {
                assertBytes(data);
            } else {
                // the Java value is the value the oracle encoded, and Red5's encoding of it decodes to the same graph
                assertValue(value, describe(values));
                assertEquals(message(), describe(values), decode(data, false));
            }
        }
    }

    private String message() {
        return name + (note.isEmpty() ? "" : " (" + note + ")");
    }

    private boolean amf0Numbers() {
        return "amf0".equals(codec) || "amf0mix".equals(codec);
    }

    private String describe(List<Object> values) {
        return AmfDescriber.describe(values, amf0Numbers());
    }

    private List<Object> javaValues() {
        // rows named <case>.red5 hold Red5's own encoding of the case
        List<Object> values = InteropCorpus.values(codec, name.replace(".red5", ""));
        assertNotNull("no Java value for " + name, values);
        return values;
    }

    private byte[] red5Bytes() {
        List<Object> values = javaValues();
        IoBuffer buf = IoBuffer.allocate(256);
        buf.setAutoExpand(true);
        org.red5.io.object.Output out;
        if ("amf0".equals(codec)) {
            out = new org.red5.io.amf.Output(buf);
        } else {
            org.red5.io.amf3.Output out3 = new org.red5.io.amf3.Output(buf);
            if ("amf3".equals(codec)) {
                out3.enforceAMF3();
            }
            out = out3;
        }
        for (Object v : values) {
            Serializer.serialize(out, v);
        }
        buf.flip();
        byte[] data = new byte[buf.remaining()];
        buf.get(data);
        return data;
    }

    private String decode(byte[] data, boolean plainAmf0) {
        IoBuffer buf = IoBuffer.wrap(data);
        org.red5.io.object.Input in;
        if (plainAmf0) {
            in = new Input(buf);
        } else {
            org.red5.io.amf3.Input in3 = new org.red5.io.amf3.Input(buf);
            if ("amf3".equals(codec)) {
                in3.enforceAMF3();
            }
            in = in3;
        }
        List<Object> values = new ArrayList<>();
        while (buf.hasRemaining()) {
            values.add(Deserializer.deserialize(in, Object.class));
        }
        return AmfDescriber.describe(values, amf0Numbers());
    }

    private void assertValue(String expected, String actual) {
        if (expected.startsWith(SHA)) {
            assertEquals(message(), expected, SHA + sha256(actual.getBytes(StandardCharsets.UTF_8)));
        } else {
            assertEquals(message(), expected, actual);
        }
    }

    private void assertBytes(byte[] data) {
        if (bytes.startsWith(SHA)) {
            String[] parts = bytes.split(":");
            assertEquals(message(), Integer.parseInt(parts[1]), data.length);
            assertEquals(message(), parts[2], sha256(data));
        } else {
            assertEquals(message(), bytes, HexFormat.of().formatHex(data));
        }
        assertTrue(message(), data.length > 0);
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

}
