package org.red5.codec;

import org.junit.Test;
import org.red5.io.utils.IOUtils;

public class FourCCTest {

    @Test
    public void test() {
        VideoCodec[] values = VideoCodec.values();
        for (VideoCodec codec : values) {
            System.out.println(codec.name() + " fourcc: " + codec.getFourcc());
            System.out.println("mimeType: " + codec.getMimeType() + " fourcc from mime: " + IOUtils.makeFourcc(codec.getMimeType()));
        }
        AudioCodec[] audioValues = AudioCodec.values();
        for (AudioCodec codec : audioValues) {
            System.out.println(codec.name() + " fourcc: " + codec.getFourcc());
            System.out.println("mimeType: " + codec.getMimeType() + " fourcc from mime: " + IOUtils.makeFourcc(codec.getMimeType()));
        }
    }

}
