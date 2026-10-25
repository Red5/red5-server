package org.red5.compatibility.flex.messaging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.Test;
import org.red5.compatibility.flex.messaging.messages.AcknowledgeMessage;
import org.red5.compatibility.flex.messaging.messages.AcknowledgeMessageExt;
import org.red5.compatibility.flex.messaging.messages.AsyncMessage;
import org.red5.compatibility.flex.messaging.messages.AsyncMessageExt;
import org.red5.compatibility.flex.messaging.messages.CommandMessage;
import org.red5.io.amf3.Input;
import org.red5.io.amf3.Output;
import org.red5.io.object.Deserializer;
import org.red5.io.object.Serializer;
import org.red5.io.utils.RandomGUID;

/**
 * Flex messages and collections on the wire, as the Flex SDK (mx.messaging.messages, mx.collections) and BlazeDS write and read them.
 */
public class FlexMessagingWireTest {

    private static final String UID = "6E9E2F5A-3B1C-4D2E-8F70-112233445566";

    private static byte[] encode(Object value) {
        IoBuffer buf = IoBuffer.allocate(64).setAutoExpand(true);
        Output out = new Output(buf);
        out.enforceAMF3();
        Serializer.serialize(out, value);
        buf.flip();
        byte[] data = new byte[buf.remaining()];
        buf.get(data);
        return data;
    }

    private static Object decode(byte[] data) {
        IoBuffer buf = IoBuffer.wrap(data);
        Input in = new Input(buf);
        in.enforceAMF3();
        Object value = Deserializer.deserialize(in, Object.class);
        assertEquals("the whole value must be read", 0, buf.remaining());
        return value;
    }

    private static byte[] bytes(Object... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Object part : parts) {
            if (part instanceof Integer) {
                out.write((Integer) part);
            } else {
                out.writeBytes(((String) part).getBytes(StandardCharsets.UTF_8));
            }
        }
        return out.toByteArray();
    }

    private static String hex(byte[] data) {
        return HexFormat.of().formatHex(data);
    }

    /**
     * AsyncMessage.writeExternal: a message without a correlation id writes the correlation flags 0x00 and nothing else, as
     * mx.messaging.messages.AsyncMessage does.
     */
    @Test
    public void testAsyncMessageWithoutCorrelationId() {
        AsyncMessage message = new AsyncMessage();
        message.setMessageId(UID);
        message.setTimestamp(0);
        message.setHeaders(new HashMap<>());
        byte[] data = encode(new AsyncMessageExt(message));
        // DSA, flags: headers (0x08), next flags byte (0x80); flags 2: message id bytes (0x02); empty headers; the 16 id bytes; correlation
        // flags 0x00
        byte[] expected = bytes(0x0A, 0x07, 0x07, "DSA", 0x88, 0x02, 0x0A, 0x0B, 0x01, 0x01, 0x0C, 0x21);
        assertEquals(hex(expected), hex(Arrays.copyOf(data, expected.length)));
        assertEquals(expected.length + 16 + 1, data.length);
        assertEquals(0x00, data[data.length - 1]);
        AsyncMessage decoded = (AsyncMessage) decode(data);
        assertEquals(UID, decoded.getMessageId());
        assertNull(decoded.getCorrelationId());
    }

    /**
     * mx.utils.UIDUtil.toByteArray returns null for an id that is not a UID; the writer then sends the id as a string under its string flag
     * (message id 0x10, client id 0x02, correlation id 0x01) and sets no byte array flag.
     */
    @Test
    public void testIdsThatAreNotUidsAreWrittenAsStrings() {
        AcknowledgeMessage message = new AcknowledgeMessage();
        message.setMessageId("msg-1");
        message.setClientId("client1");
        message.setCorrelationId("corr-1");
        message.setTimestamp(0);
        message.setHeaders(new HashMap<>());
        byte[] data = encode(new AcknowledgeMessageExt(message));
        // DSK, flags: client id (0x02), headers (0x08), message id (0x10); correlation flags: correlation id (0x01); acknowledge flags 0x00
        byte[] expected = bytes(0x0A, 0x07, 0x07, "DSK", 0x1A, 0x06, 0x0F, "client1", 0x0A, 0x0B, 0x01, 0x01, 0x06, 0x0B, "msg-1", 0x01, 0x06, 0x0D, "corr-1", 0x00);
        assertEquals(hex(expected), hex(data));
        AcknowledgeMessage decoded = (AcknowledgeMessage) decode(data);
        assertEquals("msg-1", decoded.getMessageId());
        assertEquals("client1", decoded.getClientId());
        assertEquals("corr-1", decoded.getCorrelationId());
    }

    /**
     * Lower case UUIDs, as java.util.UUID writes them, are not UIDs: they are sent as strings so the receiver reads the id that was set.
     */
    @Test
    public void testLowerCaseUuidKeepsItsCase() {
        String id = UID.toLowerCase();
        AsyncMessage message = new AsyncMessage();
        message.setMessageId(id);
        message.setCorrelationId(id);
        AsyncMessage decoded = (AsyncMessage) decode(encode(new AsyncMessageExt(message)));
        assertEquals(id, decoded.getMessageId());
        assertEquals(id, decoded.getCorrelationId());
    }

    /**
     * Message ids Red5 creates are UIDs, upper case as mx.utils.UIDUtil.createUID makes them, so they keep their 16 byte form.
     */
    @Test
    public void testCreatedMessageIdsAreUids() {
        for (AsyncMessage message : new AsyncMessage[] { new AsyncMessage(), new AcknowledgeMessage(), new CommandMessage() }) {
            assertTrue(message.getMessageId(), RandomGUID.isUID(message.getMessageId()));
        }
    }

}
