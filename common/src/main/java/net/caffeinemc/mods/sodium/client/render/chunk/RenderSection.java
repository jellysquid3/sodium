package net.caffeinemc.mods.sodium.client.render.chunk;

import net.caffeinemc.mods.sodium.client.render.chunk.compile.estimation.MeshResultSize;
import net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionInfo;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.GraphDirection;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.GraphDirectionSet;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.VisibilityEncoding;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.TranslucentData;
import net.caffeinemc.mods.sodium.client.util.task.CancellationToken;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The render state object for a chunk section. This contains all the graphics state for each render pass along with
 * data about the render in the chunk visibility graph.
 */
public class RenderSection {
    // Render Region State
    private final RenderRegion region;
    private final int sectionIndex;

    // Chunk Section State
    private final int chunkX, chunkY, chunkZ;

    // Occlusion Culling State
    private long visibilityData = VisibilityEncoding.NULL;

    private int incomingDirections;
    private int lastVisibleSearchToken = -1;

    private int adjacentMask;
    public RenderSection
            adjacentDown,
            adjacentUp,
            adjacentNorth,
            adjacentSouth,
            adjacentWest,
            adjacentEast;

    // Rendering State
    @Nullable
    private TranslucentData translucentData;

    // Pending Update State
    @Nullable
    private CancellationToken taskCancellationToken = null;
    private long lastMeshResultSize = MeshResultSize.NO_DATA;

    @Nullable
    private ChunkUpdateType pendingUpdateType;
    private long pendingUpdateSince;

    private int lastUploadFrame = -1;
    private int lastSubmittedFrame = -1;

    // Lifetime state
    private boolean disposed;

    public RenderSection(RenderRegion region, int chunkX, int chunkY, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkY = chunkY;
        this.chunkZ = chunkZ;

        int rX = this.getChunkX() & RenderRegion.REGION_WIDTH_M;
        int rY = this.getChunkY() & RenderRegion.REGION_HEIGHT_M;
        int rZ = this.getChunkZ() & RenderRegion.REGION_LENGTH_M;
        this.sectionIndex = LocalSectionIndex.pack(rX, rY, rZ);

        this.region = region;
    }

    public RenderSection getAdjacent(int direction) {
        return switch (direction) {
            case GraphDirection.DOWN -> this.adjacentDown;
            case GraphDirection.UP -> this.adjacentUp;
            case GraphDirection.NORTH -> this.adjacentNorth;
            case GraphDirection.SOUTH -> this.adjacentSouth;
            case GraphDirection.WEST -> this.adjacentWest;
            case GraphDirection.EAST -> this.adjacentEast;
            default -> null;
        };
    }

    public void setAdjacentNode(int direction, RenderSection node) {
        if (node == null) {
            this.adjacentMask &= ~GraphDirectionSet.of(direction);
        } else {
            this.adjacentMask |= GraphDirectionSet.of(direction);
        }

        switch (direction) {
            case GraphDirection.DOWN -> this.adjacentDown = node;
            case GraphDirection.UP -> this.adjacentUp = node;
            case GraphDirection.NORTH -> this.adjacentNorth = node;
            case GraphDirection.SOUTH -> this.adjacentSouth = node;
            case GraphDirection.WEST -> this.adjacentWest = node;
            case GraphDirection.EAST -> this.adjacentEast = node;
            default -> { }
        }
    }

    public int getAdjacentMask() {
        return this.adjacentMask;
    }

    public TranslucentData getTranslucentData() {
        return this.translucentData;
    }

    public void setTranslucentData(TranslucentData translucentData) {
        if (translucentData == null) {
            throw new IllegalArgumentException("new translucentData cannot be null");
        }

        this.translucentData = translucentData;
    }

    /**
     * Deletes all data attached to this render and drops any pending tasks. This should be used when the render falls
     * out of view or otherwise needs to be destroyed. After the render has been destroyed, the object can no longer
     * be used.
     */
    public void delete() {
        if (this.taskCancellationToken != null) {
            this.taskCancellationToken.setCancelled();
            this.taskCancellationToken = null;
        }

        this.clearRenderState();
        this.disposed = true;
    }

    public boolean setInfo(@Nullable BuiltSectionInfo info) {
        if (info != null) {
            return this.setRenderState(info);
        } else {
            return this.clearRenderState();
        }
    }

    private boolean setRenderState(@NotNull BuiltSectionInfo info) {
        var prevFlags = this.region.getSectionFlags(this.sectionIndex);
        var prevVisibilityData = this.visibilityData;

        this.region.setSectionRenderState(this.sectionIndex, info);
        this.visibilityData = info.visibilityData;

        // the section is marked as having received graph-relevant changes if it's build state, flags, or connectedness has changed.
        // the entities and sprites don't need to be checked since whether they exist is encoded in the flags.
        return prevFlags != this.region.getSectionFlags(this.sectionIndex) || prevVisibilityData != this.visibilityData;
    }

    private boolean clearRenderState() {
        var wasBuilt = this.isBuilt();

        this.region.clearSectionRenderState(this.sectionIndex);
        this.visibilityData = VisibilityEncoding.NULL;

        // changes to data if it moves from built to not built don't matter, so only build state changes matter
        return wasBuilt;
    }

    public void setLastMeshResultSize(long size) {
        this.lastMeshResultSize = size;
    }

    public long getLastMeshResultSize() {
        return this.lastMeshResultSize;
    }

    /**
     * Returns the chunk section position which this render refers to in the level.
     */
    public SectionPos getPosition() {
        return SectionPos.of(this.chunkX, this.chunkY, this.chunkZ);
    }

    /**
     * @return The x-coordinate of the origin position of this chunk render
     */
    public int getOriginX() {
        return this.chunkX << 4;
    }

    /**
     * @return The y-coordinate of the origin position of this chunk render
     */
    public int getOriginY() {
        return this.chunkY << 4;
    }

    /**
     * @return The z-coordinate of the origin position of this chunk render
     */
    public int getOriginZ() {
        return this.chunkZ << 4;
    }

    /**
     * @return The squared distance from the center of this chunk in the level to the center of the block position
     * given by {@param pos}
     */
    public float getSquaredDistance(BlockPos pos) {
        return this.getSquaredDistance(pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f);
    }

    /**
     * @return The squared distance from the center of this chunk to the given block position
     */
    public float getSquaredDistance(float x, float y, float z) {
        float xDist = x - this.getCenterX();
        float yDist = y - this.getCenterY();
        float zDist = z - this.getCenterZ();

        return (xDist * xDist) + (yDist * yDist) + (zDist * zDist);
    }

    /**
     * @return The x-coordinate of the center position of this chunk render
     */
    public int getCenterX() {
        return this.getOriginX() + 8;
    }

    /**
     * @return The y-coordinate of the center position of this chunk render
     */
    public int getCenterY() {
        return this.getOriginY() + 8;
    }

    /**
     * @return The z-coordinate of the center position of this chunk render
     */
    public int getCenterZ() {
        return this.getOriginZ() + 8;
    }

    public int getChunkX() {
        return this.chunkX;
    }

    public int getChunkY() {
        return this.chunkY;
    }

    public int getChunkZ() {
        return this.chunkZ;
    }

    public boolean isDisposed() {
        return this.disposed;
    }

    @Override
    public String toString() {
        return String.format("RenderSection at chunk (%d, %d, %d) from (%d, %d, %d) to (%d, %d, %d)",
                this.chunkX, this.chunkY, this.chunkZ,
                this.getOriginX(), this.getOriginY(), this.getOriginZ(),
                this.getOriginX() + 15, this.getOriginY() + 15, this.getOriginZ() + 15);
    }

    public boolean isBuilt() {
        return (this.region.getSectionFlags(this.sectionIndex) & RenderSectionFlags.MASK_IS_BUILT) != 0;
    }

    public int getSectionIndex() {
        return this.sectionIndex;
    }

    public RenderRegion getRegion() {
        return this.region;
    }

    public void setLastVisibleSearchToken(int frame) {
        this.lastVisibleSearchToken = frame;
    }

    public int getLastVisibleSearchToken() {
        return this.lastVisibleSearchToken;
    }

    public int getIncomingDirections() {
        return this.incomingDirections;
    }

    public void addIncomingDirections(int directions) {
        this.incomingDirections |= directions;
    }

    public void setIncomingDirections(int directions) {
        this.incomingDirections = directions;
    }

    /**
     * Returns the occlusion culling data which determines this chunk's connectedness on the visibility graph.
     */
    public long getVisibilityData() {
        return this.visibilityData;
    }

    public @Nullable CancellationToken getTaskCancellationToken() {
        return this.taskCancellationToken;
    }

    public void setTaskCancellationToken(@Nullable CancellationToken token) {
        this.taskCancellationToken = token;
    }

    public @Nullable ChunkUpdateType getPendingUpdate() {
        return this.pendingUpdateType;
    }

    public long getPendingUpdateSince() {
        return this.pendingUpdateSince;
    }

    public void setPendingUpdate(ChunkUpdateType type, long now) {
        this.pendingUpdateType = type;
        this.pendingUpdateSince = now;
    }

    public void clearPendingUpdate() {
        this.pendingUpdateType = null;
    }

    public void prepareTrigger(boolean isDirectTrigger) {
        if (this.translucentData != null) {
            this.translucentData.prepareTrigger(isDirectTrigger);
        }
    }

    public int getLastUploadFrame() {
        return this.lastUploadFrame;
    }

    public void setLastUploadFrame(int lastSortFrame) {
        this.lastUploadFrame = lastSortFrame;
    }

    public int getLastSubmittedFrame() {
        return this.lastSubmittedFrame;
    }

    public void setLastSubmittedFrame(int lastSubmittedFrame) {
        this.lastSubmittedFrame = lastSubmittedFrame;
    }
}
