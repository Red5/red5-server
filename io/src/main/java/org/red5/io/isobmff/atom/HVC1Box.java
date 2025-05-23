package org.red5.io.isobmff.atom;

import org.jcodec.containers.mp4.boxes.Header;
import org.jcodec.containers.mp4.boxes.VideoSampleEntry;

/**
 * <p>HVC1Box class.</p>
 *
 * @author mondain
 */
public class HVC1Box extends VideoSampleEntry {

    /**
     * <p>Constructor for HVC1Box.</p>
     */
    public HVC1Box() {
        super(new Header("hvc1"));
    }

}
