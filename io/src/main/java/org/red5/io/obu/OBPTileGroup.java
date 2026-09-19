package org.red5.io.obu;

/**
 * <p>OBPTileGroup class.</p>
 *
 * @author mondain
 */
public class OBPTileGroup {
    /** AV1 {@code TileNum}/tile count context: total number of tiles ({@code TileCols * TileRows}) in the frame. */
    public short numTiles;

    /** AV1 {@code tile_start_and_end_present_flag}: whether this tile group specifies an explicit start/end tile range. */
    public boolean tileStartAndEndPresentFlag;

    /** AV1 {@code tg_start}: index of the first tile covered by this tile group. */
    public short tgStart;

    /** AV1 {@code tg_end}: index of the last tile covered by this tile group. */
    public short tgEnd;

    /** Size in bytes of each tile's coded data within this tile group, indexed by tile number. */
    public long[] tileSize = new long[4096];
}
