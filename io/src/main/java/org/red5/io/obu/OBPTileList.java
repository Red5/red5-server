package org.red5.io.obu;

public class OBPTileList {
    public byte outputFrameWidthInTilesMinus1;

    public byte outputFrameHeightInTilesMinus1;

    public short tileCountMinus1;

    public TileListEntry[] tileListEntry = new TileListEntry[65536];

    public static class TileListEntry {
        public byte anchorFrameIdx;

        public byte anchorTileRow;

        public byte anchorTileCol;

        public short tileDataSizeMinus1;

        public byte[] codedTileData;

        public long codedTileDataSize;
    }
}
