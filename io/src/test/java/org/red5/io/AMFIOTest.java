/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2016 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.Assert;
import org.junit.Test;
import org.red5.io.amf.Input;
import org.red5.io.amf.Output;
import org.red5.io.object.DataTypes;
import org.red5.io.object.Deserializer;
import org.red5.io.utils.HexDump;
import org.red5.io.utils.IOUtils;
import org.red5.io.utils.ObjectMap;

/**
 * AMF I/O test
 * 
 * @author The Red5 Project
 * @author Luke Hubbard, Codegent Ltd (luke@codegent.com)
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class AMFIOTest extends AbstractIOTest {

    IoBuffer buf;

    /** {@inheritDoc} */
    @Override
    void setupIO() {
        buf = IoBuffer.allocate(0); // 1kb
        buf.setAutoExpand(true);
        in = new Input(buf);
        out = new Output(buf);
    }

    /** {@inheritDoc} */
    @Override
    void resetOutput() {
        buf.clear();
        buf.free();
        setupIO();
    }

    /** {@inheritDoc} */
    @Override
    void dumpOutput() {
        buf.flip();
        System.out.println(HexDump.formatHexDump(buf.getHexDump()));
    }

    /**
     * Sample data from https://en.wikipedia.org/wiki/Action_Message_Format
     */
    @Test
    public void testAMF0Wiki() {
        log.debug("\ntestAMF0Wiki");
        IoBuffer data = IoBuffer.wrap(IOUtils.hexStringToByteArray("03 00 04 6e 61 6d 65 02 00 04 4d 69 6b 65 00 03 61 67 65 00 40 3e 00 00 00 00 00 00 00 05 61 6c 69 61 73 02 00 04 4d 69 6b 65 00 00 09"));
        Input in0 = new Input(data);
        // object
        assertEquals(DataTypes.CORE_OBJECT, in0.readDataType());
        @SuppressWarnings("rawtypes")
        ObjectMap person = (ObjectMap) in0.readObject();
        assertEquals(person.get("name"), "Mike");
        assertEquals(person.get("alias"), "Mike");
        assertEquals(person.get("age"), 30d);
    }

    /**
     * Sample data from https://en.wikipedia.org/wiki/Action_Message_Format
     * @throws Exception 
     */
    @Test
    public void testAMF0Wiki2() throws Exception {
        log.debug("\ntestAMF0Wiki2");
        /*
         * The AMF message starts with a 0x03 which denotes an RTMP packet with Header Type of 0, so 12 bytes are expected to follow. It is of Message Type 0x14, which denotes a command in
         * the form of a string of value "_result" and two serialized objects as arguments. The message can be decoded as follows: (command) "_result" (transaction id) 1 (value) [1] {
         * fmsVer: "FMS/3,5,5,2004" capabilities: 31.0 mode: 1.0 }, [2] { level: "status", code: "NetConnection.Connect.Success", description: "Connection succeeded", data: (array) {
         * version: "3,5,5,2004" }, clientId: 1584259571.0, objectEncoding: 3.0 } Here one can see an array (in turquoise) as a value of the 'data' key which has one member. We can see the
         * objectEncoding value to be 3. This means that subsequent messages are going to be sent with the 0x11 message type, which will imply an AMF3 encoding.
         */
        // AMF packet
        IoBuffer data = IoBuffer.wrap(IOUtils.hexStringToByteArray(
                "03 00 00 00 00 01 05 14 00 00 00 00 02 00 07 5F 72 65 73 75 6C 74 00 3F F0 00 00 00 00 00 00 03 00 06 66 6D 73 56 65 72 02 00 0E 46 4D 53 2F 33 2C 35 2C 35 2C 32 30 30 34 00 0C 63 61 70 61 62 69 6C 69 74 69 65 73 00 40 3F 00 00 00 00 00 00 00 04 6D 6F 64 65 00 3F F0 00 00 00 00 00 00 00 00 09 03 00 05 6C 65 76 65 6C 02 00 06 73 74 61 74 75 73 00 04 63 6F 64 65 02 00 1D 4E 65 74 43 6F 6E 6E 65 63 74 69 6F 6E 2E 43 6F 6E 6E 65 63 74 2E 53 75 63 63 65 73 73 00 0B 64 65 73 63 72 69 70 74 69 6F 6E 02 00 15 43 6F 6E 6E 65 63 74 69 6F 6E 20 73 75 63 63 65 65 64 65 64 2E 00 04 64 61 74 61 08 00 00 00 01 00 07 76 65 72 73 69 6F 6E 02 00 0A 33 2C 35 2C 35 2C 32 30 30 34 00 00 09 00 08 63 6C 69 65 6E 74 69 64 00 41 D7 9B 78 7C C0 00 00 00 0E 6F 62 6A 65 63 74 45 6E 63 6F 64 69 6E 67 00 40 08 00 00 00 00 00 00 00 00 09"));
        byte headerByte = data.get();
        int headerValue, byteCount;
        if ((headerByte & 0x3f) == 0) {
            // two byte header
            headerValue = (headerByte & 0xff) << 8 | (data.get() & 0xff);
            byteCount = 2;
        } else if ((headerByte & 0x3f) == 1) {
            // three byte header
            headerValue = (headerByte & 0xff) << 16 | (data.get() & 0xff) << 8 | (data.get() & 0xff);
            byteCount = 3;
        } else {
            // single byte header
            headerValue = headerByte & 0xff;
            byteCount = 1;
        }
        log.debug("Header byte: {} value: {} byte count: {}", headerByte, headerValue, byteCount);
        int channelId = 0;
        if (byteCount == 1) {
            channelId = (headerValue & 0x3f);
        } else if (byteCount == 2) {
            channelId = 64 + (headerValue & 0xff);
        } else {
            channelId = 64 + ((headerValue >> 8) & 0xff) + ((headerValue & 0xff) << 8);
        }
        log.debug("Channel id: {}", channelId);
        byte headerSize = 0;
        if (byteCount == 1) {
            headerSize = (byte) (headerValue >> 6);
        } else if (byteCount == 2) {
            headerSize = (byte) (headerValue >> 14);
        } else {
            headerSize = (byte) (headerValue >> 22);
        }
        log.debug("Header size: {}", headerSize);
        int headerLength = 0;
        switch (headerSize) {
            case 0x00: // HEADER_NEW
                headerLength = 12;
            case 0x01: //HEADER_SAME_SOURCE
                headerLength = 8;
            case 0x02: //HEADER_TIMER_CHANGE
                headerLength = 4;
            case 0x03: //HEADER_CONTINUE
                headerLength = 1;
            default:
                headerLength = -1;
        }
        log.debug("Header length: {}", headerLength);
        headerLength += byteCount - 1;
        log.debug("Header length 2: {}", headerLength);
        byte headerDataType = 0;
        int headerStreamId = 0;
        switch (headerSize) {
            case 0x00: // HEADER_NEW
            case 0x01: //HEADER_SAME_SOURCE
            case 0x02: //HEADER_TIMER_CHANGE
                //                if (remaining >= headerLength) {
                int timeValue = readUnsignedMediumInt(data);
                if (timeValue == 0xffffff) {
                    headerLength += 4;
                }
                log.debug("Time value: {}", timeValue);
                headerSize = (byte) readUnsignedMediumInt(data);
                headerDataType = data.get();
                headerStreamId = readReverseInt(data);
                if (timeValue == 0xffffff) {
                    timeValue = (int) (data.getUnsignedInt() & Integer.MAX_VALUE);
                    //                        headerExtendedTimestamp = timeValue;
                }
                //                    headerTimerBase = timeValue;
                //                    headerTimerDelta = 0;
                //                }
                break;
            case 0x03: //HEADER_CONTINUE
                //                if (lastHeader != null && lastHeader.getExtendedTimestamp() != 0) {
                //                    headerLength += 4;
                //                }
                break;
            default:
                throw new Exception("Unexpected header size " + headerSize + " check for error");
        }
        log.debug("Header - size: {} data type: {} stream id: {}", headerSize, headerDataType, headerStreamId);
        // invoke
        assertTrue(headerDataType == 20);
        Input in0 = new Input(data);
        // object
        assertEquals(DataTypes.CORE_STRING, in0.readDataType());
        String command = in0.readString();
        assertEquals(command, "_result");
        assertEquals(DataTypes.CORE_NUMBER, in0.readDataType());
        Number transactionId = in0.readNumber();
        assertTrue(Double.valueOf(1.0d).equals(transactionId.doubleValue()));
        assertEquals(DataTypes.CORE_OBJECT, in0.readDataType());

        @SuppressWarnings("rawtypes")
        ObjectMap param1 = (ObjectMap) in0.readObject();
        log.debug("Invoke: {}", param1);
        assertTrue(((double) param1.get("capabilities")) == 31.0d);
        assertEquals(DataTypes.CORE_OBJECT, in0.readDataType());
        @SuppressWarnings("rawtypes")
        ObjectMap param2 = (ObjectMap) in0.readObject();
        log.debug("Invoke: {}", param2);
        assertEquals(param2.get("code"), "NetConnection.Connect.Success");
    }

    public static int readUnsignedMediumInt(IoBuffer in) {
        final byte a = in.get();
        final byte b = in.get();
        final byte c = in.get();
        int val = 0;
        val += (a & 0xff) << 16;
        val += (b & 0xff) << 8;
        val += (c & 0xff);
        return val;
    }

    public static int readReverseInt(IoBuffer in) {
        final byte a = in.get();
        final byte b = in.get();
        final byte c = in.get();
        final byte d = in.get();
        int val = 0;
        val += (d & 0xff) << 24;
        val += (c & 0xff) << 16;
        val += (b & 0xff) << 8;
        val += (a & 0xff);
        return val;
    }

    @Test
    public void testAMF0Connect() {
        log.debug("\ntestAMF0Connect");
        IoBuffer data = IoBuffer.wrap(IOUtils.hexStringToByteArray(
                "020007636f6e6e656374003ff00000000000000300036170700200086f666c6144656d6f0008666c61736856657202000e4c4e582032302c302c302c323836000673776655726c020029687474703a2f2f6c6f63616c686f73743a353038302f64656d6f732f6f666c615f64656d6f2e7377660005746355726c02001972746d703a2f2f6c6f63616c686f73742f6f666c6144656d6f0004667061640100000c6361706162696c697469657300406de00000000000000b617564696f436f646563730040abee0000000000000b766964656f436f6465637300406f800000000000000d766964656f46756e6374696f6e003ff000000000000000077061676555726c02002a687474703a2f2f6c6f63616c686f73743a353038302f64656d6f732f6f666c615f64656d6f2e68746d6c000009"));
        Input in0 = new Input(data);
        // action string
        Assert.assertEquals(DataTypes.CORE_STRING, in0.readDataType());
        String action = in0.readString();
        Assert.assertEquals("connect", action);
        // invoke trasaction id
        log.trace("Before reading number type: {}", data.position());
        byte type = in0.readDataType();
        log.trace("After reading number type({}): {}", type, data.position());
        Assert.assertEquals(DataTypes.CORE_NUMBER, type);
        Number transactionId = in0.readNumber();
        System.out.printf("Number - i: %d d: %f%n", transactionId.intValue(), transactionId.doubleValue());
        Map<String, Object> obj1 = Deserializer.deserialize(in0, Map.class);
        assertNotNull("Connection parameters should be valid", obj1);
        log.debug("Parameters: {}", obj1.toString());
        assertEquals("Application does not match", "oflaDemo", obj1.get("app"));
    }
}
