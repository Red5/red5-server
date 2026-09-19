package org.red5.io.obu;

/**
 * <p>OBPTileList class.</p>
 *
 * @author mondain
 */
public class OBPTileList {
    /** {@code output_frame_width_in_tiles_minus_1} from the AV1 tile list OBU; output frame width in tile units, minus 1. */
    public byte outputFrameWidthInTilesMinus1;

    /** {@code output_frame_height_in_tiles_minus_1} from the AV1 tile list OBU; output frame height in tile units, minus 1. */
    public byte outputFrameHeightInTilesMinus1;

    /** {@code tile_count_minus_1} from the AV1 tile list OBU; number of tile list entries present, minus 1. */
    public short tileCountMinus1;

    /** Parsed tile list entries, one per coded tile referenced by the large-scale-tile output frame. */
    public TileListEntry[] tileListEntry = new TileListEntry[65536];

    /** A single entry of the AV1 tile list OBU, describing one anchor tile and its coded data. */
    public static class TileListEntry {
        /** {@code anchor_frame_idx} identifying the anchor frame this tile references. */
        public byte anchorFrameIdx;

        /** {@code anchor_tile_row}, the row of the referenced tile within the anchor frame. */
        public byte anchorTileRow;

        /** {@code anchor_tile_col}, the column of the referenced tile within the anchor frame. */
        public byte anchorTileCol;

        /** {@code tile_data_size_minus_1}, the size in bytes of {@link #codedTileData}, minus 1. */
        public short tileDataSizeMinus1;

        /** The coded tile data bytes for this tile list entry. */
        public byte[] codedTileData;

        /** Actual size in bytes of {@link #codedTileData}. */
        public long codedTileDataSize;
    }
}
