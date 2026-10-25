package org.red5.io.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * UID to byte array conversion as AS3 mx.utils.UIDUtil.toByteArray and BlazeDS UUIDUtils.uidToByteArray define it: only a UID of 36
 * characters, upper case hex digits with hyphens at 8, 13, 18 and 23, has a byte form; anything else has none.
 */
public class RandomGUIDTest {

    @Test
    public void testUidToBytesAndBack() {
        String uid = "6E9E2F5A-3B1C-4D2E-8F70-112233445566";
        byte[] bytes = RandomGUID.toByteArray(uid);
        assertArrayEquals(new byte[] { 0x6E, (byte) 0x9E, 0x2F, 0x5A, 0x3B, 0x1C, 0x4D, 0x2E, (byte) 0x8F, 0x70, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66 }, bytes);
        assertEquals(uid, RandomGUID.fromByteArray(bytes));
        String created = RandomGUID.create();
        assertEquals(created, RandomGUID.fromByteArray(RandomGUID.toByteArray(created)));
    }

    @Test
    public void testNoByteFormForOtherIds() {
        assertNull(RandomGUID.toByteArray(null));
        assertNull(RandomGUID.toByteArray(""));
        assertNull(RandomGUID.toByteArray("client1"));
        // one character short and one character too long
        assertNull(RandomGUID.toByteArray("6E9E2F5A-3B1C-4D2E-8F70-11223344556"));
        assertNull(RandomGUID.toByteArray("6E9E2F5A-3B1C-4D2E-8F70-1122334455667"));
        // lower case hex digits have no byte form, the string read back from the bytes would differ
        assertNull(RandomGUID.toByteArray("6e9e2f5a-3b1c-4d2e-8f70-112233445566"));
        // a hyphen out of place and a character that is not a hex digit
        assertNull(RandomGUID.toByteArray("6E9E2F5A3-B1C-4D2E-8F70-112233445566"));
        assertNull(RandomGUID.toByteArray("6E9E2F5A-3B1C-4D2E-8F70-11223344556G"));
        assertNull(RandomGUID.toByteArray("6E9E2F5A-3B1C-4D2E-8F70-1122334455-6"));
    }

}
