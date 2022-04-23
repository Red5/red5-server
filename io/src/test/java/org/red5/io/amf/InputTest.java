package org.red5.io.amf;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.Test;
import org.red5.io.object.DataTypes;
import org.red5.io.utils.IOUtils;
import org.red5.io.utils.ObjectMap;

/**
 * @author varga.bence@ustream.tv
 */
public class InputTest {

    @Test
    public void testOnStreamSendMap() {
        // 02 = string
        // 08 = mixed array (map) max number = 0

        IoBuffer data = IoBuffer.wrap(IOUtils.hexStringToByteArray("02 00 0c 6f 6e 53 74 72 65 61 6d 53 65 6e 64 08 00000000 00 05 76 616c7565 02 00 01 31 00 00 09"));
        Input in0 = new Input(data);
        assertEquals(DataTypes.CORE_STRING, in0.readDataType());
        String method = in0.readString();
        assertEquals("onStreamSend", method);
        assertEquals(DataTypes.CORE_MAP, in0.readDataType());
        @SuppressWarnings("rawtypes")
        ObjectMap map = (ObjectMap) in0.readMap();
        assertEquals(map.get("value"), "1");
    }

    @Test
    public void testZeroBasedEcmaArray() {
        // { '0': 'hello', '1': 'world' }
        byte[] stream = new byte[] { 0x00, 0x00, 0x00, 0x02, 0x00, 0x01, 0x30, 0x02, 0x00, 0x05, 'h', 'e', 'l', 'l', 'o', 0x00, 0x01, 0x31, 0x02, 0x00, 0x05, 'w', 'o', 'r', 'l', 'd', 0x00, 0x00, 0x09 };
        Input input = new Input(IoBuffer.wrap(stream));
        Object actual = input.readMap();

        ObjectMap<Object, Object> expected = new ObjectMap<>();
        expected.put(0, "hello");
        expected.put(1, "world");

        assertEquals(expected, actual);
    }

    @Test
    public void testNonZeroBasedEcmaArray() {
        // { '1': 'hello' }
        byte[] stream = new byte[] { 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x31, 0x02, 0x00, 0x05, 'h', 'e', 'l', 'l', 'o', 0x00, 0x00, 0x09 };
        Input input = new Input(IoBuffer.wrap(stream));
        Object actual = input.readMap();

        Map<Object, Object> expected = new HashMap<>();
        expected.put(1, "hello");

        assertEquals(expected, actual);
    }

}