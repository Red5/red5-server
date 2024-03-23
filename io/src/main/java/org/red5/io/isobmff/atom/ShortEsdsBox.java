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

public class ShortEsdsBox extends FullBox {

    private ByteBuffer streamInfo;

    private int objectType;

    private int bufSize;

    private int maxBitrate;

    private int avgBitrate;

    private int trackId;

    public static String fourcc() {
        return "esds";
    }

    public ShortEsdsBox(Header atom) {
        super(atom);
    }

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

    @Override
    public int estimateSize() {
        return 64;
    }

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

    public boolean hasStreamInfo() {
        return streamInfo != null;
    }

    public ByteBuffer getStreamInfo() {
        return streamInfo;
    }

    public int getObjectType() {
        return objectType;
    }

    public int getBufSize() {
        return bufSize;
    }

    public int getMaxBitrate() {
        return maxBitrate;
    }

    public int getAvgBitrate() {
        return avgBitrate;
    }

    public int getTrackId() {
        return trackId;
    }

}
