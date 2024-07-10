package org.red5.io.obu;

public class OBPTileGroup {
    public short numTiles;

    public boolean tileStartAndEndPresentFlag;

    public short tgStart;

    public short tgEnd;

    public long[] tileSize = new long[4096];
}