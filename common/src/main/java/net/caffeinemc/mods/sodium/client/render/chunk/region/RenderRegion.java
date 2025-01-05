package net.caffeinemc.mods.sodium.client.render.chunk.region;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.caffeinemc.mods.sodium.client.gl.arena.GlBufferArena;
import net.caffeinemc.mods.sodium.client.gl.arena.staging.StagingBuffer;
import net.caffeinemc.mods.sodium.client.gl.buffer.GlBuffer;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.gl.tessellation.GlTessellation;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkMeshFormats;
import net.caffeinemc.mods.sodium.client.util.MathUtil;
import net.minecraft.core.SectionPos;
import org.apache.commons.lang3.Validate;

import java.util.Arrays;
import java.util.Map;

public class RenderRegion {
    public static final int REGION_WIDTH = 8;
    public static final int REGION_HEIGHT = 4;
    public static final int REGION_LENGTH = 8;

    public static final int REGION_WIDTH_M = RenderRegion.REGION_WIDTH - 1;
    public static final int REGION_HEIGHT_M = RenderRegion.REGION_HEIGHT - 1;
    public static final int REGION_LENGTH_M = RenderRegion.REGION_LENGTH - 1;

    public static final int REGION_WIDTH_SH = Integer.bitCount(REGION_WIDTH_M);
    public static final int REGION_HEIGHT_SH = Integer.bitCount(REGION_HEIGHT_M);
    public static final int REGION_LENGTH_SH = Integer.bitCount(REGION_LENGTH_M);

    public static final int REGION_SIZE = REGION_WIDTH * REGION_HEIGHT * REGION_LENGTH;

    static {
        Validate.isTrue(MathUtil.isPowerOfTwo(REGION_WIDTH));
        Validate.isTrue(MathUtil.isPowerOfTwo(REGION_HEIGHT));
        Validate.isTrue(MathUtil.isPowerOfTwo(REGION_LENGTH));
    }

    private final StagingBuffer stagingBuffer;
    private final int x, y, z;

    private final ChunkRenderList renderList;

    private final RenderSection[] sections = new RenderSection[RenderRegion.REGION_SIZE];
    private int sectionCount;

    private final Map<TerrainRenderPass, SectionRenderDataStorage> sectionRenderData = new Reference2ReferenceOpenHashMap<>();
    private DeviceResources resources;

    public RenderRegion(int x, int y, int z, StagingBuffer stagingBuffer) {
        this.x = x;
        this.y = y;
        this.z = z;

        this.stagingBuffer = stagingBuffer;
        this.renderList = new ChunkRenderList(this);
    }

    public static long key(int x, int y, int z) {
        return SectionPos.asLong(x, y, z);
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getZ() {
        return this.z;
    }

    public int getChunkX() {
        return this.x << REGION_WIDTH_SH;
    }

    public int getChunkY() {
        return this.y << REGION_HEIGHT_SH;
    }

    public int getChunkZ() {
        return this.z << REGION_LENGTH_SH;
    }

    public int getOriginX() {
        return this.getChunkX() << 4;
    }

    public int getOriginY() {
        return this.getChunkY() << 4;
    }

    public int getOriginZ() {
        return this.getChunkZ() << 4;
    }

    public void delete(CommandList commandList) {
        for (var storage : this.sectionRenderData.values()) {
            storage.delete();
        }

        this.sectionRenderData.clear();

        if (this.resources != null) {
            this.resources.delete(commandList);
            this.resources = null;
        }

        Arrays.fill(this.sections, null);
    }

    public boolean isEmpty() {
        return this.sectionCount == 0;
    }

    public SectionRenderDataStorage getStorage(TerrainRenderPass pass) {
        return this.sectionRenderData.get(pass);
    }

    public SectionRenderDataStorage createStorage(TerrainRenderPass pass) {
        var storage = this.sectionRenderData.get(pass);

        if (storage == null) {
            storage = new SectionRenderDataStorage(pass.isTranslucent());
            this.sectionRenderData.put(pass, storage);
        }

        return storage;
    }

    public void refreshTesselation(CommandList commandList) {
        if (this.resources != null) {
            this.resources.deleteTessellation(commandList);
            this.resources.deleteIndexedTessellation(commandList);
        }

        for (var storage : this.sectionRenderData.values()) {
            storage.onBufferResized();
        }
    }

    public void refreshIndexedTesselation(CommandList commandList) {
        if (this.resources != null) {
            this.resources.deleteIndexedTessellation(commandList);
        }

        this.sectionRenderData.get(DefaultTerrainRenderPasses.TRANSLUCENT).onIndexBufferResized();
    }

    public void addSection(RenderSection section) {
        var sectionIndex = section.getSectionIndex();
        var prev = this.sections[sectionIndex];

        if (prev != null) {
            throw new IllegalStateException("Section has already been added to the region");
        }

        this.sections[sectionIndex] = section;
        this.sectionCount++;
    }

    public void removeSection(RenderSection section) {
        var sectionIndex = section.getSectionIndex();
        var prev = this.sections[sectionIndex];

        if (prev == null) {
            throw new IllegalStateException("Section was not loaded within the region");
        } else if (prev != section) {
            throw new IllegalStateException("Tried to remove the wrong section");
        }

        for (var storage : this.sectionRenderData.values()) {
            storage.removeData(sectionIndex);
        }

        this.sections[sectionIndex] = null;
        this.sectionCount--;
    }

    public RenderSection getSection(int id) {
        return this.sections[id];
    }

    public DeviceResources getResources() {
        return this.resources;
    }

    public DeviceResources createResources(CommandList commandList) {
        if (this.resources == null) {
            this.resources = new DeviceResources(commandList, this.stagingBuffer);
        }

        return this.resources;
    }

    public void update(CommandList commandList) {
        if (this.resources != null && this.resources.shouldDelete()) {
            this.resources.delete(commandList);
            this.resources = null;
        }
    }

    public ChunkRenderList getRenderList() {
        return this.renderList;
    }

    public static class DeviceResources {
        private final GlBufferArena geometryArena;
        private final GlBufferArena indexArena;
        private GlTessellation tessellation;
        private GlTessellation indexedTessellation;

        /**
         * The buffer arenas return offsets in terms of how many stride units big things
         * are. This means that if the stride is the length of a vertex, the buffer
         * arena works with vertices and returns vertex offsets. The arena working with
         * indices has as stride of four corresponding to the length of an integer. The
         * two can't easily be combined because integers and vertices require different
         * amounts of data which makes the returned offsets incompatible.
         */
        public DeviceResources(CommandList commandList, StagingBuffer stagingBuffer) {
            int stride = ChunkMeshFormats.COMPACT.getVertexFormat().getStride();

            // the magic number 756 for the initial size is arbitrary, it was made up.
            var initialVertices = 756;
            this.geometryArena = new GlBufferArena(commandList, REGION_SIZE * initialVertices, stride, stagingBuffer, false);
            var initialIndices = (initialVertices / 4) * 6;
            this.indexArena = new GlBufferArena(commandList, REGION_SIZE * initialIndices, Integer.BYTES, stagingBuffer, true);
        }

        public void updateTessellation(CommandList commandList, GlTessellation tessellation) {
            if (this.tessellation != null) {
                this.tessellation.delete(commandList);
            }

            this.tessellation = tessellation;
        }

        public void updateIndexedTessellation(CommandList commandList, GlTessellation tessellation) {
            if (this.indexedTessellation != null) {
                this.indexedTessellation.delete(commandList);
            }

            this.indexedTessellation = tessellation;
        }

        public GlTessellation getTessellation() {
            return this.tessellation;
        }

        public GlTessellation getIndexedTessellation() {
            return this.indexedTessellation;
        }

        public void deleteTessellation(CommandList commandList) {
            if (this.tessellation != null) {
                this.tessellation.delete(commandList);
                this.tessellation = null;
            }
        }

        public void deleteIndexedTessellation(CommandList commandList) {
            if (this.indexedTessellation != null) {
                this.indexedTessellation.delete(commandList);
                this.indexedTessellation = null;
            }
        }

        public GlBuffer getGeometryBuffer() {
            return this.geometryArena.getBufferObject();
        }

        public GlBuffer getIndexBuffer() {
            return this.indexArena.getBufferObject();
        }

        public void delete(CommandList commandList) {
            this.deleteTessellation(commandList);
            this.deleteIndexedTessellation(commandList);
            this.geometryArena.delete(commandList);
            this.indexArena.delete(commandList);
        }

        public GlBufferArena getGeometryArena() {
            return this.geometryArena;
        }

        public GlBufferArena getIndexArena() {
            return this.indexArena;
        }

        public boolean shouldDelete() {
            return this.geometryArena.isEmpty() && this.indexArena.isEmpty();
        }
    }
}
