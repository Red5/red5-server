package org.red5.io.isobmff.atom;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import org.jcodec.codecs.mpeg4.es.DecoderConfig;
import org.jcodec.codecs.mpeg4.es.DecoderSpecific;
import org.jcodec.codecs.mpeg4.es.Descriptor;
import org.jcodec.codecs.mpeg4.es.DescriptorParser;
import org.jcodec.codecs.mpeg4.es.ES;
import org.jcodec.codecs.mpeg4.es.NodeDescriptor;
import org.jcodec.codecs.mpeg4.es.SL;
import org.jcodec.containers.mp4.boxes.FullBox;
import org.jcodec.containers.mp4.boxes.Header;

/**
 * <p>ShortEsdsBox class.</p>
 *
 * @author mondain
 */
public class ShortEsdsBox extends FullBox {

    private ByteBuffer streamInfo;

    private int objectType;

    private int bufSize;

    private int maxBitrate;

    private int avgBitrate;

    private int trackId;

    /**
     * <p>fourcc.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public static String fourcc() {
        return "esds";
    }

    /**
     * <p>Constructor for ShortEsdsBox.</p>
     *
     * @param atom a {@link org.jcodec.containers.mp4.boxes.Header} object
     */
    public ShortEsdsBox(Header atom) {
        super(atom);
    }

    /** {@inheritDoc} */
    @Override
    protected void doWrite(ByteBuffer out) {
        super.doWrite(out);
        if (streamInfo != null && streamInfo.remaining() > 0) {
            ArrayList<Descriptor> l = new ArrayList<Descriptor>();
            ArrayList<Descriptor> l1 = new ArrayList<Descriptor>();
            l1.add(new DecoderSpecific(streamInfo));
            l.add(new DecoderConfig(objectType, bufSize, maxBitrate, avgBitrate, l1));
            l.add(new SL());
            new ES(trackId, l).write(out);
        } else {
            ArrayList<Descriptor> l = new ArrayList<Descriptor>();
            l.add(new DecoderConfig(objectType, bufSize, maxBitrate, avgBitrate, new ArrayList<Descriptor>()));
            l.add(new SL());
            new ES(trackId, l).write(out);
        }
    }

    /** {@inheritDoc} */
    @Override
    public int estimateSize() {
        return 64;
    }

    /** {@inheritDoc} */
    @Override
    public void parse(ByteBuffer input) {
        super.parse(input);
        ES es = (ES) DescriptorParser.read(input);
        trackId = es.getTrackId();
        DecoderConfig decoderConfig = NodeDescriptor.findByTag(es, DecoderConfig.tag());
        objectType = decoderConfig.getObjectType();
        bufSize = decoderConfig.getBufSize();
        maxBitrate = decoderConfig.getMaxBitrate();
        avgBitrate = decoderConfig.getAvgBitrate();
        DecoderSpecific decoderSpecific = NodeDescriptor.findByTag(decoderConfig, DecoderSpecific.tag());
        if (decoderSpecific != null) {
            streamInfo = decoderSpecific.getData();
        }
    }

    /**
     * <p>hasStreamInfo.</p>
     *
     * @return a boolean
     */
    public boolean hasStreamInfo() {
        return streamInfo != null;
    }

    /**
     * <p>Getter for the field <code>streamInfo</code>.</p>
     *
     * @return a {@link java.nio.ByteBuffer} object
     */
    public ByteBuffer getStreamInfo() {
        return streamInfo;
    }

    /**
     * <p>Getter for the field <code>objectType</code>.</p>
     *
     * @return a int
     */
    public int getObjectType() {
        return objectType;
    }

    /**
     * <p>Getter for the field <code>bufSize</code>.</p>
     *
     * @return a int
     */
    public int getBufSize() {
        return bufSize;
    }

    /**
     * <p>Getter for the field <code>maxBitrate</code>.</p>
     *
     * @return a int
     */
    public int getMaxBitrate() {
        return maxBitrate;
    }

    /**
     * <p>Getter for the field <code>avgBitrate</code>.</p>
     *
     * @return a int
     */
    public int getAvgBitrate() {
        return avgBitrate;
    }

    /**
     * <p>Getter for the field <code>trackId</code>.</p>
     *
     * @return a int
     */
    public int getTrackId() {
        return trackId;
    }

}
