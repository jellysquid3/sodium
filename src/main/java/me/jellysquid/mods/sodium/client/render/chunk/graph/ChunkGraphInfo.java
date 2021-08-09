package me.jellysquid.mods.sodium.client.render.chunk.graph;

import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderData;
import me.jellysquid.mods.sodium.client.util.math.FrustumExtended;
import me.jellysquid.mods.sodium.common.util.DirectionUtil;
import net.minecraft.client.renderer.chunk.VisibilitySet;
import net.minecraft.core.Direction;

public class ChunkGraphInfo {
    private static final long DEFAULT_VISIBILITY_DATA = calculateVisibilityData(ChunkRenderData.EMPTY.getOcclusionData());

    private final RenderSection parent;

    private int lastVisibleFrame = -1;

    private long visibilityData;
    private byte cullingState;

    public ChunkGraphInfo(RenderSection parent) {
        this.parent = parent;
        this.visibilityData = DEFAULT_VISIBILITY_DATA;
    }

    public void setLastVisibleFrame(int frame) {
        this.lastVisibleFrame = frame;
    }

    public int getLastVisibleFrame() {
        return this.lastVisibleFrame;
    }

    public void setOcclusionData(VisibilitySet occlusionData) {
        this.visibilityData = calculateVisibilityData(occlusionData);
    }

    private static long calculateVisibilityData(VisibilitySet occlusionData) {
        long visibilityData = 0;

        for (Direction from : DirectionUtil.ALL_DIRECTIONS) {
            for (Direction to : DirectionUtil.ALL_DIRECTIONS) {
                if (occlusionData == null || occlusionData.visibilityBetween(from, to)) {
                    visibilityData |= (1L << ((from.ordinal() << 3) + to.ordinal()));
                }
            }
        }

        return visibilityData;
    }

    public boolean isVisibleThrough(Direction from, Direction to) {
        return ((this.visibilityData & (1L << ((from.ordinal() << 3) + to.ordinal()))) != 0L);
    }

    public void setCullingState(byte parent, Direction dir) {
        this.cullingState = (byte) (parent | (1 << dir.ordinal()));
    }

    public boolean canCull(Direction dir) {
        return (this.cullingState & 1 << dir.ordinal()) != 0;
    }

    public byte getCullingState() {
        return this.cullingState;
    }

    public void resetCullingState() {
        this.cullingState = 0;
    }

    public boolean isCulledByFrustum(FrustumExtended frustum) {
        float x = this.getOriginX();
        float y = this.getOriginY();
        float z = this.getOriginZ();

        return !frustum.fastAabbTest(x, y, z, x + 16.0f, y + 16.0f, z + 16.0f);
    }

    /**
     * @return The x-coordinate of the origin position of this chunk render
     */
    public int getOriginX() {
        return this.parent.getChunkX() << 4;
    }

    /**
     * @return The y-coordinate of the origin position of this chunk render
     */
    public int getOriginY() {
        return this.parent.getChunkY() << 4;
    }

    /**
     * @return The z-coordinate of the origin position of this chunk render
     */
    public int getOriginZ() {
        return this.parent.getChunkZ() << 4;
    }
}
