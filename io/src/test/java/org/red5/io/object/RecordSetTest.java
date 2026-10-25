package org.red5.io.object;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.Test;
import org.red5.io.amf.AMF;
import org.red5.io.amf.Input;
import org.red5.io.amf.Output;

/**
 * Verifies that a Flash Remoting RecordSet decodes from AMF0 and that Red5 can read back the RecordSet it writes.
 */
public class RecordSetTest {

    @Test
    public void testDecodeFlashRemotingRecordSet() {
        RecordSet recordSet = decode(recordSetBytes("serverInfo"));
        assertRecordSet(recordSet);
    }

    @Test
    public void testDecodeLegacyLowerCaseKey() {
        assertRecordSet(decode(recordSetBytes("serverinfo")));
    }

    @Test
    public void testRoundTrip() {
        RecordSet recordSet = decode(recordSetBytes("serverInfo"));
        IoBuffer buf = IoBuffer.allocate(256).setAutoExpand(true);
        Serializer.serialize(new Output(buf), recordSet);
        buf.flip();
        assertRecordSet(decode(buf));
    }

    private static void assertRecordSet(RecordSet recordSet) {
        assertEquals(List.of("id", "name"), recordSet.getColumnNames());
        assertEquals(2, recordSet.getLength());
        assertEquals(List.of(1.0, "one"), recordSet.getItemAt(0));
        assertEquals(List.of(2.0, "two"), recordSet.getItemAt(1));
    }

    private static RecordSet decode(IoBuffer buf) {
        Object value = Deserializer.deserialize(new Input(buf), Object.class);
        assertTrue("expected a RecordSet but got " + value, value instanceof RecordSet);
        assertFalse(buf.hasRemaining());
        return (RecordSet) value;
    }

    /**
     * typed-object "RecordSet" with one member holding the server info object, as Flash Remoting sends it.
     */
    private static IoBuffer recordSetBytes(String serverInfoKey) {
        IoBuffer buf = IoBuffer.allocate(256).setAutoExpand(true);
        buf.put(AMF.TYPE_CLASS_OBJECT);
        name(buf, "RecordSet");
        name(buf, serverInfoKey);
        buf.put(AMF.TYPE_OBJECT);
        name(buf, "totalCount");
        number(buf, 2);
        name(buf, "cursor");
        number(buf, 1);
        name(buf, "serviceName");
        string(buf, "svc");
        name(buf, "version");
        number(buf, 1);
        name(buf, "id");
        string(buf, "rs1");
        name(buf, "columnNames");
        buf.put(AMF.TYPE_ARRAY).putInt(2);
        string(buf, "id");
        string(buf, "name");
        name(buf, "initialData");
        buf.put(AMF.TYPE_ARRAY).putInt(2);
        buf.put(AMF.TYPE_ARRAY).putInt(2);
        number(buf, 1);
        string(buf, "one");
        buf.put(AMF.TYPE_ARRAY).putInt(2);
        number(buf, 2);
        string(buf, "two");
        buf.put(AMF.END_OF_OBJECT_SEQUENCE);
        buf.put(AMF.END_OF_OBJECT_SEQUENCE);
        return buf.flip();
    }

    private static void name(IoBuffer buf, String name) {
        byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
        buf.putShort((short) bytes.length).put(bytes);
    }

    private static void string(IoBuffer buf, String value) {
        buf.put(AMF.TYPE_STRING);
        name(buf, value);
    }

    private static void number(IoBuffer buf, double value) {
        buf.put(AMF.TYPE_NUMBER).putDouble(value);
    }

}
