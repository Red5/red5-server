/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.Test;
import org.red5.io.amf3.ByteArray;
import org.red5.io.amf3.Input;
import org.red5.io.amf3.Output;
import org.red5.io.object.DataTypes;
import org.red5.io.object.Deserializer;
import org.red5.io.object.Serializer;
import org.red5.io.object.StreamAction;
import org.red5.io.utils.HexDump;
import org.red5.io.utils.IOUtils;

/*
 * @author The Red5 Project
 * @author Luke Hubbard, Codegent Ltd (luke@codegent.com)
 * @author Art Clarke
 */
public class AMF3IOTest extends AbstractIOTest {

    IoBuffer buf;

    {
        // setup to use AMF3
        encoding = 3;
    }

    /** {@inheritDoc} */
    @Override
    void dumpOutput() {
        buf.flip();
        System.err.println(HexDump.formatHexDump(buf.getHexDump()));
    }

    /** {@inheritDoc} */
    @Override
    void resetOutput() {
        setupIO();
    }

    /** {@inheritDoc} */
    @Override
    void setupIO() {
        buf = IoBuffer.allocate(0); // 1kb
        buf.setAutoExpand(true);
        buf.setAutoShrink(true);
        in = new Input(buf);
        out = new Output(buf);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testStreamData() {
        log.debug("\n Testing StreamData");

        //0002000a6f6e437565506f696e74110a03
        //0002000c6f6e53747265616d53656e640200095b696e6465783a30
        //0002000c6f6e53747265616d53656e640200095b696e6465783a31
        //0002000c6f6e53747265616d53656e640200095b696e6465783a32
        log.debug("\n onMetaInfo");
        buf.put(IOUtils.hexStringToByteArray(
                "0002000a6f6e4d657461496e666f110a0b011153796e63546578740a01116c616e67756167650607656e6709646174610621546869732069732053796e6354657874011750726976617465446174610a010f6f776e6572496406033006064f56476870637942706379426959584e6c4e6a51675a57356a6232526c5a43427a64484a70626d630131436f6d6d65726369616c496e666f726d6174696f6e55524c0a01060625687474703a2f2f7265643570726f2e636f6d011b47656e6572616c4f626a6563740a0106066f56476870637942706379426e5a57356c636d46736158706c5a434276596d706c59335167596d467a5a545930494756755932396b5a57511166696c656e616d6506156d7946696c652e747874011155736572546578740a0106061b55736572546578745269636861010f436f6d6d656e740a01020604060610011d55736572446566696e656455524c0a01060633687474703a2f2f6769746875622e636f6d2f6d6f6e6461696e01"));
        buf.flip();
        // skip the first byte
        assertEquals(0, buf.get());
        // validate data
        assertEquals(DataTypes.CORE_STRING, in.readDataType());
        // check the length of onMetaInfo
        buf.mark();
        int len = buf.getUnsignedShort();
        assertEquals(10, len);
        buf.reset();
        String str = in.readString();
        assertEquals("onMetaInfo", str);
        log.debug("MetaInfo: {}", str);
        assertEquals(DataTypes.CORE_OBJECT, in.readDataType());
        Object object = in.readObject();
        log.debug("Obj: {}", object);
        // reset for next test
        resetOutput();
        ///////////////////////////////////////////////
        log.debug("\n onMetaInfo #2");
        buf.put(IOUtils.hexStringToByteArray(
                "0002000a6f6e4d657461496e666f110a0b0131436f6d6d65726369616c496e666f726d6174696f6e55524c0a0109646174610625687474703a2f2f7265643570726f2e636f6d011155736572546578740a0102061b55736572546578745269636861011153796e63546578740a01116c616e67756167650607656e67020621546869732069732053796e6354657874011d55736572446566696e656455524c0a01020633687474703a2f2f6769746875622e636f6d2f6d6f6e6461696e011750726976617465446174610a0102064f56476870637942706379426959584e6c4e6a51675a57356a6232526c5a43427a64484a70626d630f6f776e65724964060330010f436f6d6d656e740a010c060e020618011b47656e6572616c4f626a6563740a0102066f56476870637942706379426e5a57356c636d46736158706c5a434276596d706c59335167596d467a5a545930494756755932396b5a57511166696c656e616d6506156d7946696c652e74787401"));
        buf.flip();
        // skip the first byte
        assertEquals(0, buf.get());
        // validate data
        assertEquals(DataTypes.CORE_STRING, in.readDataType());
        str = in.readString();
        assertEquals("onMetaInfo", str);
        //Object object = Deserializer.deserialize(in, String.class);
        log.debug("onStreamSend: {}", str);

        // reset for next test
        resetOutput();
        ///////////////////////////////////////////////
        log.debug("\n @setDataFrame");
        buf.put(IOUtils.hexStringToByteArray("0002000d40736574446174614672616d6502000c6f6e53747265616d53656e64110a0b0107726e640607363636"));
        buf.flip();
        // skip the first byte
        assertEquals(0, buf.get());
        // validate data
        assertEquals(DataTypes.CORE_STRING, in.readDataType());
        str = in.readString();
        assertEquals("@setDataFrame", str);
        //Object object = Deserializer.deserialize(in, String.class);
        log.debug("@setDataFrame: {}", str);
        // get the second datatype
        byte dataType2 = in.readDataType();
        log.debug("Dataframe method type: {}", dataType2);
        String onCueOrOnMeta = in.readString();
        // get the params datatype
        byte objectType = in.readDataType();
        log.debug("Dataframe params type: {}", objectType);
        Map<Object, Object> params;
        if (objectType == DataTypes.CORE_MAP) {
            // the params are sent as a Mixed-Array. Required to support the RTMP publish provided by ffmpeg
            params = (Map<Object, Object>) in.readMap();
        } else if (objectType == DataTypes.CORE_ARRAY) {
            params = (Map<Object, Object>) in.readArray(Object[].class);
        } else {
            // read the params as a standard object
            params = (Map<Object, Object>) in.readObject();
        }
        if (log.isDebugEnabled()) {
            log.debug("Dataframe: {} params: {}", onCueOrOnMeta, params.toString());
        }
        out.writeString(onCueOrOnMeta);
        out.writeMap(params);
        // reset for next test
        resetOutput();
        ///////////////////////////////////////////////
        //        log.debug("\n onCuePoint");
        //        // OSMF CuePoint object
        //        buf.put(IOUtils.hexStringToByteArray("0002000a6f6e437565506f696e74110a03"));
        //        // AS3 Object with cuepoint properties
        //        buf.put(IOUtils.hexStringToByteArray("0002000a6f6e437565506f696e74110a0b01096e616d6506176e6176437565506f696e740974797065060b6576656e740974696d65040115706172616d65746572730a01176f7269656e746174696f6e045a01"));
        //        buf.flip();
        //        // skip the first byte
        //        assertEquals(0, buf.get());
        //        // validate data
        //        assertEquals(DataTypes.CORE_STRING, in.readDataType());
        //        str = in.readString();
        //        assertEquals("onCuePoint", str);
        //        //Object object = Deserializer.deserialize(in, String.class);
        //        log.debug("onCuePoint: {}", str);
        //        //assertEquals(DataTypes.CORE_OBJECT, in.readDataType());
        //        object = Deserializer.deserialize(in, MetaCue.class);
        //        log.debug("Obj: {}", object);
        //        // reset for next test
        //        resetOutput();
    }

    @Test
    public void testEnum() {
        log.debug("\n Testing Enum");
        Serializer.serialize(out, StreamAction.CONNECT);
        dumpOutput();
        Object object = Deserializer.deserialize(in, StreamAction.class);
        log.debug("Enums - {} {}", object.getClass().getName(), StreamAction.CONNECT.getClass().getName());
        assertEquals(object.getClass().getName(), StreamAction.CONNECT.getClass().getName());
        resetOutput();
    }

    @Test
    public void testByteArray() {
        log.debug("\n Testing ByteArray");
        // just some ones and such
        ByteArray baIn = new ByteArray();
        baIn.writeBytes(new byte[] { (byte) 0, (byte) 0x1, (byte) 0x42, (byte) 0x1, (byte) 0x42, (byte) 0x1, (byte) 0x42, (byte) 0x1, (byte) 0x42, (byte) 0x1, (byte) 0x42, (byte) 0x1, (byte) 0x42, (byte) 0x1, (byte) 0x42, (byte) 0x1, (byte) 0x42, (byte) 0x99 });
        Serializer.serialize(out, baIn);
        dumpOutput();
        ByteArray baOut = Deserializer.deserialize(in, ByteArray.class);
        assertNotNull(baOut);
        assertEquals(baIn.length(), baOut.length());
        for (int i = 0; i < baOut.length(); i++) {
            System.err.println("Byte: " + baOut.readByte());
        }
        resetOutput();
    }

    @Test
    public void testVectorRoundTrip() {
        log.debug("\n Testing Vector on a round trip");
        Vector<String> vIn = new Vector<String>();
        vIn.add("This is my vector and her name is Sally");
        Serializer.serialize(out, vIn);
        dumpOutput();
        Vector<String> vOut = Deserializer.deserialize(in, Vector.class);
        assertNotNull(vOut);
        assertEquals(vIn.size(), vOut.size());
        for (int i = 0; i < vOut.size(); i++) {
            System.err.println("Element: " + vOut.elementAt(i));
        }
        resetOutput();
    }

    @Test
    public void testVectorIntInput() {
        log.debug("\n Testing Vector<int>");
        //0D090000000002000007D07FFFFFFF80000000
        byte[] v = new byte[] { (byte) 0x0D, (byte) 0x09, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x07, (byte) 0xD0, (byte) 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0x80, (byte) 0x00, (byte) 0x00, (byte) 0x00 };

        in = new Input(IoBuffer.wrap(v));
        List<Object> vectorOut = Deserializer.deserialize(in, null);
        //[2, 2000, 2147483647, -2147483648]
        assertNotNull(vectorOut);
        assertEquals(vectorOut.size(), 4);
        for (int i = 0; i < vectorOut.size(); i++) {
            System.err.println("Vector: " + vectorOut.get(i));
        }
        resetOutput();
    }

    @Test
    public void testVectorUIntInput() {
        log.debug("\n Testing Vector<uint>");
        //0E090000000002000007D0FFFFFFFF00000000
        byte[] v = new byte[] { (byte) 0x0E, (byte) 0x09, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x07, (byte) 0xD0, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00 };
        in = new Input(IoBuffer.wrap(v));
        List<Object> vectorOut = Deserializer.deserialize(in, null);
        //[2, 2000, 4294967295, 0]
        assertNotNull(vectorOut);
        assertEquals(vectorOut.size(), 4);
        for (int i = 0; i < vectorOut.size(); i++) {
            System.err.println("Vector: " + vectorOut.get(i));
        }
        resetOutput();
    }

    @Test
    public void testVectorNumberInput() {
        log.debug("\n Testing Vector<Number>");
        //0F0F003FF199999999999ABFF199999999999A7FEFFFFFFFFFFFFF0000000000000001FFF8000000000000FFF00000000000007FF0000000000000
        byte[] v = new byte[] { (byte) 0x0F, (byte) 0x0F, (byte) 0x00, (byte) 0x3F, (byte) 0xF1, (byte) 0x99, (byte) 0x99, (byte) 0x99, (byte) 0x99, (byte) 0x99, (byte) 0x9A, (byte) 0xBF, (byte) 0xF1, (byte) 0x99, (byte) 0x99, (byte) 0x99, (byte) 0x99, (byte) 0x99, (byte) 0x9A, (byte) 0x7F, (byte) 0xEF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0xFF, (byte) 0xF8, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xFF, (byte) 0xF0, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x7F, (byte) 0xF0, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00 };

        in = new Input(IoBuffer.wrap(v));
        List<Double> vectorOut = Deserializer.deserialize(in, null);
        //[1.1, -1.1, 1.7976931348623157E308, 4.9E-324, NaN, -Infinity, Infinity]
        assertNotNull(vectorOut);
        assertEquals(vectorOut.size(), 7);
        for (int i = 0; i < vectorOut.size(); i++) {
            System.err.println("Vector: " + vectorOut.get(i));
        }
        resetOutput();
    }

    @Test
    public void testVectorMixedInput() {
        log.debug("\n Testing Vector<Object>");
        //100700010607666f6f010a13256f72672e726564352e74673742e466f6f33000403
        //[foo, null, org.red5.test.Foo3[foo=0]] // Foo3 is a class instance
        byte[] v2 = new byte[] { (byte) 0x10, (byte) 0x07, (byte) 0x00, (byte) 0x01, (byte) 0x06, (byte) 0x07, (byte) 0x66, (byte) 0x6f, (byte) 0x6f, (byte) 0x01, (byte) 0x0a, (byte) 0x13, (byte) 0x25, (byte) 0x6f, (byte) 0x72, (byte) 0x67, (byte) 0x2e, (byte) 0x72, (byte) 0x65, (byte) 0x64, (byte) 0x35, (byte) 0x2e, (byte) 0x74, (byte) 0x65, (byte) 0x73, (byte) 0x74, (byte) 0x2e, (byte) 0x46, (byte) 0x6f,
                (byte) 0x6f, (byte) 0x33, (byte) 0x00, (byte) 0x04, (byte) 0x03 };

        in = new Input(IoBuffer.wrap(v2));
        List<Object> vectorOut = Deserializer.deserialize(in, null);
        assertNotNull(vectorOut);
        assertEquals(vectorOut.size(), 3);
        for (int i = 0; i < vectorOut.size(); i++) {
            System.err.println("Vector: " + vectorOut.get(i));
        }
        resetOutput();
    }

    @SuppressWarnings("unused")
    @Test
    public void testVectorStringInput() {
        log.debug("\n Testing Vector<String>");
        //[Paul, ]
        byte[] v = new byte[] { (byte) 0x10, (byte) 0x05, (byte) 0x00, (byte) 0x01, (byte) 0x06, (byte) 0x09, (byte) 0x50, (byte) 0x61, (byte) 0x75, (byte) 0x6c, (byte) 0x06, (byte) 0x01 };
        //[Paul, Paul]
        byte[] v1 = new byte[] { (byte) 0x10, (byte) 0x05, (byte) 0x00, (byte) 0x01, (byte) 0x06, (byte) 0x09, (byte) 0x50, (byte) 0x61, (byte) 0x75, (byte) 0x6c, (byte) 0x06, (byte) 0x00 };
        //[Paul, Paul, Paul]
        byte[] v2 = new byte[] { (byte) 0x10, (byte) 0x07, (byte) 0x00, (byte) 0x01, (byte) 0x06, (byte) 0x09, (byte) 0x50, (byte) 0x61, (byte) 0x75, (byte) 0x6c, (byte) 0x06, (byte) 0x00, (byte) 0x06, (byte) 0x00 };
        //[Paul, Tawnya]
        byte[] v3 = new byte[] { (byte) 0x10, (byte) 0x05, (byte) 0x00, (byte) 0x01, (byte) 0x06, (byte) 0x09, (byte) 0x50, (byte) 0x61, (byte) 0x75, (byte) 0x6c, (byte) 0x06, (byte) 0x0d, (byte) 0x54, (byte) 0x61, (byte) 0x77, (byte) 0x6e, (byte) 0x79, (byte) 0x61 };

        //[1.0, 3.0, aaa, 5.0, aaa, aaa, 5.0, bb, bb]
        byte[] v4 = new byte[] { (byte) 0x10, (byte) 0x13, (byte) 0x00, (byte) 0x01, (byte) 0x04, (byte) 0x01, (byte) 0x04, (byte) 0x03, (byte) 0x06, (byte) 0x07, (byte) 0x61, (byte) 0x61, (byte) 0x61, (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x00, (byte) 0x06, (byte) 0x00, (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x05, (byte) 0x62, (byte) 0x62, (byte) 0x06, (byte) 0x02 };
        //[1.0, 3.0, aaa, [1, 2]]
        byte[] v5 = new byte[] { (byte) 0x10, (byte) 0x09, (byte) 0x00, (byte) 0x01, (byte) 0x04, (byte) 0x01, (byte) 0x04, (byte) 0x03, (byte) 0x06, (byte) 0x07, (byte) 0x61, (byte) 0x61, (byte) 0x61, (byte) 0x0d, (byte) 0x05, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x02 };

        in = new Input(IoBuffer.wrap(v5));
        List<Object> vectorOut = Deserializer.deserialize(in, null);
        //[Paul, ]
        assertNotNull(vectorOut);
        //assertEquals(vectorOut.size(), 4);
        for (int i = 0; i < vectorOut.size(); i++) {
            System.err.println("Vector: " + vectorOut.get(i));
        }
        resetOutput();
    }

}
