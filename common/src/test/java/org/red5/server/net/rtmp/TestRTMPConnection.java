package org.red5.server.net.rtmp;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.red5.server.net.rtmp.RTMPConnection.MAX_RESERVED_STREAMS;

public class TestRTMPConnection {

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    //	@Test
    //	public void testGetNextAvailableChannelId() {
    //		fail("Not yet implemented");
    //	}
    //
    //	@Test
    //	public void testIsChannelUsed() {
    //		fail("Not yet implemented");
    //	}
    //
    //	@Test
    //	public void testGetChannel() {
    //		fail("Not yet implemented");
    //	}
    //
    //	@Test
    //	public void testCloseChannel() {
    //		fail("Not yet implemented");
    //	}
    //
    //
    //	@Test
    //	public void testNewPlaylistSubscriberStream() {
    //		System.out.println("\n testNewPlaylistSubscriberStream");
    //		RTMPConnection conn = new RTMPMinaConnection();
    //		
    //		Number streamId = 0;
    //		
    //		IPlaylistSubscriberStream stream = conn.newPlaylistSubscriberStream(streamId);
    //		System.out.printf("PlaylistSubscriberStream for stream id 0: %s\n", stream);
    //		
    //	}
    //
    //	@Test
    //	public void testRemoveClientStream() {
    //		fail("Not yet implemented");
    //	}
    //
    //	@Test
    //	public void testGetUsedStreamCount() {
    //		fail("Not yet implemented");
    //	}
    //
    //	@Test
    //	public void testGetStreamById() {
    //		System.out.println("\n testGetStreamById");
    //		RTMPConnection conn = new RTMPMinaConnection();
    //		
    //		IClientStream stream = conn.getStreamById(0);
    //		System.out.printf("Stream for stream id 0: %s\n", stream);
    //		assertNull(stream);
    //		stream = conn.getStreamById(1);
    //		System.out.printf("Stream for stream id 1: %s\n", stream);
    //		
    //	}

    @Test
    public void testGetStreamIdForChannelId() {
        System.out.println("\n testGetStreamIdForChannelId");
        RTMPConnection conn = new RTMPMinaConnection();
        try {
            System.out.printf("Starting stream id: %f\n", conn.getStreamId().doubleValue());
            assertEquals(0.0d, conn.getStreamId().doubleValue(), 0d);
        } catch (Exception e) {
        }

        assertEquals(1.0d, conn.getStreamIdForChannelId(4).doubleValue(), 0d);
        assertEquals(1.0d, conn.getStreamIdForChannelId(5).doubleValue(), 0d);
        assertEquals(1.0d, conn.getStreamIdForChannelId(8).doubleValue(), 0d);
        assertEquals(2.0d, conn.getStreamIdForChannelId(9).doubleValue(), 0d);
        assertEquals(2.0d, conn.getStreamIdForChannelId(13).doubleValue(), 0d);
        assertEquals(3.0d, conn.getStreamIdForChannelId(14).doubleValue(), 0d);

        System.out.printf("Stream id - cid 0: %f cid 12: %f\n", conn.getStreamIdForChannelId(0).doubleValue(), conn.getStreamIdForChannelId(12).doubleValue());
    }

    //	@Test
    //	public void testGetStreamByChannelId() {
    //		System.out.println("\n testGetStreamByChannelId");
    //		RTMPConnection conn = new RTMPMinaConnection();
    //		// any channel less than 4 should be null
    //		assertNull(conn.getStreamByChannelId(3));		
    //		// stream id 0
    //		assertNotNull(conn.getStreamByChannelId(4));
    //		assertNotNull(conn.getStreamByChannelId(5));
    //		// stream id 1
    //		assertNotNull(conn.getStreamByChannelId(7));
    //	}

    @Test
    public void testGetChannelIdForStreamId() {
        System.out.println("\n testGetChannelIdForStreamId");
        RTMPConnection conn = new RTMPMinaConnection();
        try {
            assertEquals(conn.getStreamId().intValue(), 0);
        } catch (Exception e) {
        }

        // channel returned is 1 less than what we actually need
        //		assertEquals(3, conn.getChannelIdForStreamId(0));
        //		assertEquals(6, conn.getChannelIdForStreamId(1));
        //		assertEquals(9, conn.getChannelIdForStreamId(2));
        //		assertEquals(12, conn.getChannelIdForStreamId(3));

        assertEquals(-1, conn.getChannelIdForStreamId(0));
        assertEquals(4, conn.getChannelIdForStreamId(1));
        assertEquals(9, conn.getChannelIdForStreamId(2));
        assertEquals(14, conn.getChannelIdForStreamId(3));
        assertEquals(19, conn.getChannelIdForStreamId(4));

        System.out.printf("Channel id - sid 20: %d sid 33: %d\n", conn.getChannelIdForStreamId(20), conn.getChannelIdForStreamId(33));
    }

    @Test
    public void testReserveStreamId() {
        System.out.println("\n testReserveStreamId");
        RTMPConnection conn = new RTMPMinaConnection();
        Number streamId = conn.reserveStreamId();
        boolean valid = conn.isValidStreamId(streamId);
        System.out.printf("Stream id: %f valid: %b\n", streamId, valid);
        assertTrue(1 == streamId.intValue());
        assertTrue(1.0d == streamId.doubleValue());
        assertTrue(valid);
        conn.unreserveStreamId(streamId);
        assertFalse(conn.isValidStreamId(streamId));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testReserveStreamIdImpossible() {
        System.out.println("\n testReserveStreamIdImpossible");
        RTMPConnection conn = new RTMPMinaConnection();
        for (int i = 0; i < MAX_RESERVED_STREAMS; i++) {
            conn.reserveStreamId();
        }
        conn.reserveStreamId();
    }

    @Test
    public void testReserveStreamIdNumber() {
        System.out.println("\n testReserveStreamIdNumber");
        RTMPConnection conn = new RTMPMinaConnection();
        // try integer first
        Number streamId = 5;
        streamId = conn.reserveStreamId(streamId);
        boolean valid = conn.isValidStreamId(streamId);
        System.out.printf("Stream id: %f valid: %b\n", streamId.doubleValue(), valid);
        assertTrue(5 == streamId.intValue());
        assertTrue(5.0d == streamId.doubleValue());
        assertTrue(valid);
        conn.unreserveStreamId(streamId);
        assertFalse(conn.isValidStreamId(streamId));
        // try double first
        streamId = 3.0d;
        streamId = conn.reserveStreamId(streamId);
        assertTrue(3 == streamId.intValue());
        assertTrue(3.0d == streamId.doubleValue());
        valid = conn.isValidStreamId(streamId);
        assertTrue(valid);
        System.out.printf("Stream id: %f valid: %b\n", streamId.doubleValue(), valid);
        conn.unreserveStreamId(streamId);
        assertFalse(conn.isValidStreamId(streamId));
    }

    @Test
    public void testReserveStreamIdMixed() {
        System.out.println("\n testReserveStreamIdMixed");
        RTMPConnection conn = new RTMPMinaConnection();
        // try integer
        Number streamId = 1;
        streamId = conn.reserveStreamId(streamId);
        boolean valid = conn.isValidStreamId(streamId);
        System.out.printf("Stream id: %f valid: %b\n", streamId.doubleValue(), valid);
        assertTrue(1 == streamId.intValue());
        assertTrue(1.0d == streamId.doubleValue());
        assertTrue(valid);
        // try double
        streamId = 3.0d;
        streamId = conn.reserveStreamId(streamId);
        assertTrue(3 == streamId.intValue());
        assertTrue(3.0d == streamId.doubleValue());
        valid = conn.isValidStreamId(streamId);
        assertTrue(valid);
        System.out.printf("Stream id: %f valid: %b\n", streamId.doubleValue(), valid);
        //try one without specified streamIds
        streamId = conn.reserveStreamId();
        assertTrue(2 == streamId.intValue());
        assertTrue(2.0d == streamId.doubleValue());
        valid = conn.isValidStreamId(streamId);
        assertTrue(valid);
        System.out.printf("Stream id: %f valid: %b\n", streamId.doubleValue(), valid);
        //try one with already reserved stream id
        streamId = 3.0d;
        streamId = conn.reserveStreamId(streamId);
        assertTrue(4 == streamId.intValue());
        assertTrue(4.0d == streamId.doubleValue());
        valid = conn.isValidStreamId(streamId);
        assertTrue(valid);
        System.out.printf("Stream id: %f valid: %b\n", streamId.doubleValue(), valid);
    }

    //	@Test
    //	public void testDeleteStreamById() {
    //		fail("Not yet implemented");
    //	}

}
