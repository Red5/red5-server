package org.red5.server.net.mediabunny.fmp4;

import java.util.ArrayList;
import java.util.List;

import org.red5.server.net.mediabunny.fmp4.InitBoxes.AudioSampleEntry;
import org.red5.server.net.mediabunny.fmp4.InitBoxes.DrefBox;
import org.red5.server.net.mediabunny.fmp4.InitBoxes.EmptyTableBox;
import org.red5.server.net.mediabunny.fmp4.InitBoxes.FtypBox;
import org.red5.server.net.mediabunny.fmp4.InitBoxes.HdlrBox;
import org.red5.server.net.mediabunny.fmp4.InitBoxes.MdhdBox;
import org.red5.server.net.mediabunny.fmp4.InitBoxes.MvhdBox;
import org.red5.server.net.mediabunny.fmp4.InitBoxes.SmhdBox;
import org.red5.server.net.mediabunny.fmp4.InitBoxes.StsdBox;
import org.red5.server.net.mediabunny.fmp4.InitBoxes.TkhdBox;
import org.red5.server.net.mediabunny.fmp4.InitBoxes.TrexBox;
import org.red5.server.net.mediabunny.fmp4.InitBoxes.VisualSampleEntry;
import org.red5.server.net.mediabunny.fmp4.InitBoxes.VmhdBox;

/**
 * Builds fMP4 initialization segments (ftyp + moov) for CMAF-style streaming, where the init segment is sent once and
 * followed by fragments. The moov box includes an mvex container with one trex per track, as required by ISO/IEC
 * 14496-12 for fragmented files.
 *
 * Derived from the write path of the CMAF packager in red5-moq-pkgr 1.3.11.
 */
public class Fmp4InitSegmentBuilder {

    // iso6 rather than CMAF's cmf2 as the major brand, which is what MediaBunny live playback has always been served
    private static final String MAJOR_BRAND = "iso6";

    private static final List<String> COMPATIBLE_BRANDS = List.of("cmfc", "iso6");

    private static final long MOVIE_TIMESCALE = 1000;

    private final List<TrackConfig> tracks = new ArrayList<>();

    public Fmp4InitSegmentBuilder addVideoTrack(VideoTrackConfig config) {
        tracks.add(config);
        return this;
    }

    public Fmp4InitSegmentBuilder addAudioTrack(AudioTrackConfig config) {
        tracks.add(config);
        return this;
    }

    /**
     * @return serialized ftyp + moov
     */
    public byte[] build() {
        if (tracks.isEmpty()) {
            throw new IllegalStateException("At least one track is required");
        }
        long nextTrackId = 0;
        for (TrackConfig track : tracks) {
            nextTrackId = Math.max(nextTrackId, track.trackId);
        }
        ContainerBox moov = new ContainerBox("moov", new MvhdBox(MOVIE_TIMESCALE, nextTrackId + 1));
        ContainerBox mvex = new ContainerBox("mvex");
        for (TrackConfig track : tracks) {
            moov.add(track.toTrak());
            mvex.add(new TrexBox(track.trackId));
        }
        moov.add(mvex);
        return Box.serialize(new FtypBox(MAJOR_BRAND, 0, COMPATIBLE_BRANDS), moov);
    }

    public abstract static class TrackConfig {

        final long trackId;

        final long timescale;

        final String codecFourcc;

        final byte[] codecConfig;

        private final String handlerType;

        private final String handlerName;

        TrackConfig(long trackId, long timescale, String codecFourcc, byte[] codecConfig, String handlerType, String handlerName) {
            this.trackId = trackId;
            this.timescale = timescale;
            this.codecFourcc = codecFourcc;
            this.codecConfig = codecConfig;
            this.handlerType = handlerType;
            this.handlerName = handlerName;
        }

        Box toTrak() {
            ContainerBox stbl = new ContainerBox("stbl", new StsdBox(buildSampleEntry()));
            for (Box table : EmptyTableBox.all()) {
                stbl.add(table);
            }
            ContainerBox minf = new ContainerBox("minf", buildMediaHeader(), new ContainerBox("dinf", new DrefBox()), stbl);
            ContainerBox mdia = new ContainerBox("mdia", new MdhdBox(timescale), new HdlrBox(handlerType, handlerName), minf);
            return new ContainerBox("trak", buildTkhd(), mdia);
        }

        abstract Box buildTkhd();

        abstract Box buildSampleEntry();

        abstract Box buildMediaHeader();

    }

    public static class VideoTrackConfig extends TrackConfig {

        private final int width;

        private final int height;

        public VideoTrackConfig(long trackId, long timescale, String codecFourcc, byte[] codecConfig, int width, int height) {
            super(trackId, timescale, codecFourcc, codecConfig, "vide", "VideoHandler");
            this.width = width;
            this.height = height;
        }

        @Override
        Box buildTkhd() {
            return new TkhdBox(trackId, 0, width, height);
        }

        @Override
        Box buildSampleEntry() {
            return new VisualSampleEntry(codecFourcc, width, height, codecConfig);
        }

        @Override
        Box buildMediaHeader() {
            return new VmhdBox();
        }

    }

    public static class AudioTrackConfig extends TrackConfig {

        private final int channelCount;

        private final int sampleRate;

        private final int sampleSize;

        public AudioTrackConfig(long trackId, long timescale, String codecFourcc, byte[] codecConfig, int channelCount, int sampleRate, int sampleSize) {
            super(trackId, timescale, codecFourcc, codecConfig, "soun", "SoundHandler");
            this.channelCount = channelCount;
            this.sampleRate = sampleRate;
            this.sampleSize = sampleSize;
        }

        @Override
        Box buildTkhd() {
            return new TkhdBox(trackId, 0x0100, 0, 0);
        }

        @Override
        Box buildSampleEntry() {
            return new AudioSampleEntry(codecFourcc, channelCount, sampleRate, sampleSize, codecConfig);
        }

        @Override
        Box buildMediaHeader() {
            return new SmhdBox();
        }

    }

}
