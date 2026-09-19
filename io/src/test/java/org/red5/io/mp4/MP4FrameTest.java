package org.red5.io.mp4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class MP4FrameTest {

    private static MP4Frame frame(double time, long offset) {
        MP4Frame frame = new MP4Frame();
        frame.setTime(time);
        frame.setOffset(offset);
        return frame;
    }

    @Test
    public void testSort() {
        List<MP4Frame> frames = new ArrayList<>();
        frames.add(frame(1, 1));
        frames.add(frame(6, 6));
        frames.add(frame(660, 660));
        frames.add(frame(3, 3));
        frames.add(frame(400, 400));
        frames.add(frame(1000, 1010));
        frames.add(frame(1000, 1000));
        frames.add(frame(1000, 900));
        assertEquals(660, frames.get(2).getTime(), 0);
        Collections.sort(frames);
        double[] expectedTimes = { 1, 3, 6, 400, 660, 1000, 1000, 1000 };
        long[] expectedOffsets = { 1, 3, 6, 400, 660, 900, 1000, 1010 };
        for (int i = 0; i < frames.size(); i++) {
            assertEquals("time at " + i, expectedTimes[i], frames.get(i).getTime(), 0);
            assertEquals("offset at " + i, expectedOffsets[i], frames.get(i).getOffset());
        }
    }

    @Test
    public void testCompareTo() {
        MP4Frame earlier = frame(1, 100);
        MP4Frame later = frame(2, 50);
        assertTrue(earlier.compareTo(later) < 0);
        assertTrue(later.compareTo(earlier) > 0);
        // equal time orders by offset
        MP4Frame lowOffset = frame(5, 10);
        MP4Frame highOffset = frame(5, 20);
        assertTrue(lowOffset.compareTo(highOffset) < 0);
        assertTrue(highOffset.compareTo(lowOffset) > 0);
        assertEquals(0, lowOffset.compareTo(frame(5, 10)));
    }

}
