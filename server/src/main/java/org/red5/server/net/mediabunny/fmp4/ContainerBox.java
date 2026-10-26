package org.red5.server.net.mediabunny.fmp4;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Box whose payload is only child boxes (moov, trak, mdia, minf, dinf, stbl, mvex, moof, traf).
 */
final class ContainerBox extends Box {

    private final List<Box> children = new ArrayList<>();

    ContainerBox(String type, Box... children) {
        super(type);
        for (Box child : children) {
            add(child);
        }
    }

    ContainerBox add(Box child) {
        children.add(child);
        return this;
    }

    @Override
    int payloadSize() {
        int size = 0;
        for (Box child : children) {
            size += child.size();
        }
        return size;
    }

    @Override
    void writePayload(ByteBuffer buffer) {
        for (Box child : children) {
            child.write(buffer);
        }
    }

}
