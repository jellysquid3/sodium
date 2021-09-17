package me.jellysquid.mods.sodium.client.render.chunk.region;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.gl.arena.AsyncBufferArena;
import me.jellysquid.mods.sodium.client.gl.arena.GlBufferArena;
import me.jellysquid.mods.sodium.client.gl.arena.SwapBufferArena;
import me.jellysquid.mods.sodium.client.gl.arena.staging.StagingBuffer;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlTessellation;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.format.ChunkModelVertexFormats;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import me.jellysquid.mods.sodium.client.util.MathUtil;
import me.jellysquid.mods.sodium.client.util.math.FrustumExtended;
import net.minecraft.util.math.ChunkSectionPos;
import org.apache.commons.lang3.Validate;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public class RenderRegion {
    public static final int REGION_WIDTH = 8;
    public static final int REGION_HEIGHT = 4;
    public static final int REGION_LENGTH = 8;

    private static final int REGION_WIDTH_M = RenderRegion.REGION_WIDTH - 1;
    private static final int REGION_HEIGHT_M = RenderRegion.REGION_HEIGHT - 1;
    private static final int REGION_LENGTH_M = RenderRegion.REGION_LENGTH - 1;

    private static final int REGION_WIDTH_SH = Integer.bitCount(REGION_WIDTH_M);
    private static final int REGION_HEIGHT_SH = Integer.bitCount(REGION_HEIGHT_M);
    private static final int REGION_LENGTH_SH = Integer.bitCount(REGION_LENGTH_M);

    public static final int REGION_SIZE = REGION_WIDTH * REGION_HEIGHT * REGION_LENGTH;

    private static final int REGION_EXCESS = 8;

    static {
        Validate.isTrue(MathUtil.isPowerOfTwo(REGION_WIDTH));
        Validate.isTrue(MathUtil.isPowerOfTwo(REGION_HEIGHT));
        Validate.isTrue(MathUtil.isPowerOfTwo(REGION_LENGTH));
    }

    private final RenderRegionManager manager;

    private final Set<RenderSection> chunks = new ObjectOpenHashSet<>();
    private RenderRegionArenas arenas;

    private final int x, y, z;

    private RenderRegionVisibility visibility;

    public RenderRegion(RenderRegionManager manager, int x, int y, int z) {
        this.manager = manager;

        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static RenderRegion createRegionForChunk(RenderRegionManager manager, int x, int y, int z) {
        return new RenderRegion(manager, x >> REGION_WIDTH_SH, y >> REGION_HEIGHT_SH, z >> REGION_LENGTH_SH);
    }

    public RenderRegionArenas getArenas() {
        return this.arenas;
    }

    public void deleteResources(CommandList commandList) {
        if (this.arenas != null) {
            this.arenas.delete(commandList);
            this.arenas = null;
        }
    }

    public static long getRegionKeyForChunk(int x, int y, int z) {
        return ChunkSectionPos.asLong(x >> REGION_WIDTH_SH, y >> REGION_HEIGHT_SH, z >> REGION_LENGTH_SH);
    }

    public int getOriginX() {
        return this.x << REGION_WIDTH_SH << 4;
    }

    public int getOriginY() {
        return this.y << REGION_HEIGHT_SH << 4;
    }

    public int getOriginZ() {
        return this.z << REGION_LENGTH_SH << 4;
    }

    public RenderRegionArenas getOrCreateArenas(CommandList commandList) {
        RenderRegionArenas arenas = this.arenas;

        if (arenas == null) {
            this.arenas = (arenas = this.manager.createRegionArenas(commandList));
        }

        return arenas;
    }

    public void addChunk(RenderSection chunk) {
        if (!this.chunks.add(chunk)) {
            throw new IllegalStateException("Chunk " + chunk + " is already a member of region " + this);
        }
    }

    public void removeChunk(RenderSection chunk) {
        if (!this.chunks.remove(chunk)) {
            throw new IllegalStateException("Chunk " + chunk + " is not a member of region " + this);
        }
    }

    public boolean isEmpty() {
        return this.chunks.isEmpty();
    }

    public int getChunkCount() {
        return this.chunks.size();
    }

    public void updateVisibility(FrustumExtended frustum) {
        int x = this.getOriginX();
        int y = this.getOriginY();
        int z = this.getOriginZ();

        // HACK: Regions need to be slightly larger than their real volume
        // Otherwise, the first node in the iteration graph might be incorrectly culled when the camera
        // is at the extreme end of a region
        this.visibility = frustum.aabbTest(x - REGION_EXCESS, y - REGION_EXCESS, z - REGION_EXCESS,
                x + (REGION_WIDTH << 4) + REGION_EXCESS, y + (REGION_HEIGHT << 4) + REGION_EXCESS, z + (REGION_LENGTH << 4) + REGION_EXCESS);
    }

    public RenderRegionVisibility getVisibility() {
        return this.visibility;
    }

    public static int getChunkIndex(int x, int y, int z) {
        return (x * RenderRegion.REGION_LENGTH * RenderRegion.REGION_HEIGHT) + (y * RenderRegion.REGION_LENGTH) + z;
    }

    public static class RenderRegionArenas {
        public final GlBufferArena vertexBuffers;
        public final GlBufferArena indexBuffers;

        public final Map<BlockRenderPass, GlTessellation> tessellations = new EnumMap<>(BlockRenderPass.class);

        public RenderRegionArenas(CommandList commandList, StagingBuffer stagingBuffer) {
            int expectedVertexCount = REGION_SIZE * 756;
            int expectedIndexCount = (expectedVertexCount / 4) * 6;

            this.vertexBuffers = createArena(commandList, expectedVertexCount * ChunkModelVertexFormats.DEFAULT.getBufferVertexFormat().getStride(), stagingBuffer);
            this.indexBuffers = createArena(commandList, expectedIndexCount * 4, stagingBuffer);
        }

        public void delete(CommandList commandList) {
            this.deleteTessellations(commandList);

            this.vertexBuffers.delete(commandList);
            this.indexBuffers.delete(commandList);
        }

        public void deleteTessellations(CommandList commandList) {
            for (GlTessellation tessellation : this.tessellations.values()) {
                commandList.deleteTessellation(tessellation);
            }

            this.tessellations.clear();
        }

        public void setTessellation(BlockRenderPass pass, GlTessellation tessellation) {
            this.tessellations.put(pass, tessellation);
        }

        public GlTessellation getTessellation(BlockRenderPass pass) {
            return this.tessellations.get(pass);
        }

        public boolean isEmpty() {
            return this.vertexBuffers.isEmpty() && this.indexBuffers.isEmpty();
        }

        public long getDeviceUsedMemory() {
            return this.vertexBuffers.getDeviceUsedMemory() + this.indexBuffers.getDeviceUsedMemory();
        }

        public long getDeviceAllocatedMemory() {
            return this.vertexBuffers.getDeviceAllocatedMemory() + this.indexBuffers.getDeviceAllocatedMemory();
        }

        private static GlBufferArena createArena(CommandList commandList, int initialCapacity, StagingBuffer stagingBuffer) {
            return switch (SodiumClientMod.options().advanced.arenaMemoryAllocator) {
                case ASYNC -> new AsyncBufferArena(commandList, initialCapacity, stagingBuffer);
                case SWAP -> new SwapBufferArena(commandList);
            };
        }
    }
}
