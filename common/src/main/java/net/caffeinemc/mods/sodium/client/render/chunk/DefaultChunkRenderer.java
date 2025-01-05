package net.caffeinemc.mods.sodium.client.render.chunk;

import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.gl.device.DrawCommandList;
import net.caffeinemc.mods.sodium.client.gl.device.MultiDrawBatch;
import net.caffeinemc.mods.sodium.client.gl.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.gl.tessellation.GlIndexType;
import net.caffeinemc.mods.sodium.client.gl.tessellation.GlPrimitiveType;
import net.caffeinemc.mods.sodium.client.gl.tessellation.GlTessellation;
import net.caffeinemc.mods.sodium.client.gl.tessellation.TessellationBinding;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import net.caffeinemc.mods.sodium.client.render.chunk.data.SectionRenderDataUnsafe;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.SortBehavior;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.caffeinemc.mods.sodium.client.util.BitwiseMath;
import net.caffeinemc.mods.sodium.client.util.UInt32;
import org.lwjgl.system.MemoryUtil;

import java.util.Iterator;

public class DefaultChunkRenderer extends ShaderChunkRenderer {
    private final MultiDrawBatch batch;

    private final SharedQuadIndexBuffer sharedIndexBuffer;

    public DefaultChunkRenderer(RenderDevice device, ChunkVertexType vertexType) {
        super(device, vertexType);

        this.batch = new MultiDrawBatch((ModelQuadFacing.COUNT * RenderRegion.REGION_SIZE) + 1);
        this.sharedIndexBuffer = new SharedQuadIndexBuffer(device.createCommandList(), SharedQuadIndexBuffer.IndexType.INTEGER);
    }

    /**
     * Renders the terrain for a particular render pass. Each region is rendered
     * with one draw call. The command buffer for each draw command is filled by
     * iterating the sections and adding the draw commands for each section.
     */
    @Override
    public void render(ChunkRenderMatrices matrices,
                       CommandList commandList,
                       ChunkRenderListIterable renderLists,
                       TerrainRenderPass renderPass,
                       CameraTransform camera) {
        super.begin(renderPass);

        final boolean useBlockFaceCulling = SodiumClientMod.options().performance.useBlockFaceCulling;
        final boolean useIndexedTessellation = isTranslucentRenderPass(renderPass);

        ChunkShaderInterface shader = this.activeProgram.getInterface();
        shader.setProjectionMatrix(matrices.projection());
        shader.setModelViewMatrix(matrices.modelView());

        Iterator<ChunkRenderList> iterator = renderLists.iterator(renderPass.isTranslucent());

        while (iterator.hasNext()) {
            ChunkRenderList renderList = iterator.next();

            var region = renderList.getRegion();
            var storage = region.getStorage(renderPass);

            if (storage == null) {
                continue;
            }

            fillCommandBuffer(this.batch, region, storage, renderList, camera, renderPass, useBlockFaceCulling, useIndexedTessellation);

            if (this.batch.isEmpty()) {
                continue;
            }

            // When the shared index buffer is being used, we must ensure the storage has been allocated *before*
            // the tessellation is prepared.
            if (!useIndexedTessellation) {
                this.sharedIndexBuffer.ensureCapacity(commandList, this.batch.getIndexBufferSize());
            }

            GlTessellation tessellation;

            if (useIndexedTessellation) {
                tessellation = this.prepareIndexedTessellation(commandList, region);
            } else {
                tessellation = this.prepareTessellation(commandList, region);
            }

            setModelMatrixUniforms(shader, region, camera);
            executeDrawBatch(commandList, tessellation, this.batch);
        }

        super.end(renderPass);
    }

    private static boolean isTranslucentRenderPass(TerrainRenderPass renderPass) {
        return renderPass.isTranslucent() && SodiumClientMod.options().performance.getSortBehavior() != SortBehavior.OFF;
    }

    private static void fillCommandBuffer(MultiDrawBatch batch,
                                          RenderRegion renderRegion,
                                          SectionRenderDataStorage renderDataStorage,
                                          ChunkRenderList renderList,
                                          CameraTransform camera,
                                          TerrainRenderPass pass,
                                          boolean useBlockFaceCulling,
                                          boolean useIndexedTessellation) {
        batch.clear();

        var iterator = renderList.sectionsWithGeometryIterator(pass.isTranslucent());

        if (iterator == null) {
            return;
        }

        // The origin of the chunk in world space
        int originX = renderRegion.getChunkX();
        int originY = renderRegion.getChunkY();
        int originZ = renderRegion.getChunkZ();

        while (iterator.hasNext()) {
            int sectionIndex = iterator.nextByteAsInt();

            var pMeshData = renderDataStorage.getDataPointer(sectionIndex);

            int chunkX = originX + LocalSectionIndex.unpackX(sectionIndex);
            int chunkY = originY + LocalSectionIndex.unpackY(sectionIndex);
            int chunkZ = originZ + LocalSectionIndex.unpackZ(sectionIndex);

            // The bit field of "visible" geometry sets which should be rendered
            int slices;

            if (useBlockFaceCulling) {
                slices = getVisibleFaces(camera.intX, camera.intY, camera.intZ, chunkX, chunkY, chunkZ);
            } else {
                slices = ModelQuadFacing.ALL;
            }

            // Mask off any geometry sets which are empty (contain no geometry)
            slices &= SectionRenderDataUnsafe.getSliceMask(pMeshData);

            // If there are no geometry sets to render, don't try to build a draw command buffer for this section
            if (slices == 0) {
                continue;
            }

            // it's necessary to sometimes not the locally-indexed command generator even for indexed tessellations since
            // sometimes the index buffer is shared, but not globally shared. This means that translucent sections that
            // are sharing an index buffer amongst them need to use the shared index command generator since it sets the
            // same element offset for each draw command and doesn't increment it. Recall that in each draw command the indexing
            // of the elements needs to start at 0 and thus starting somewhere further into the shared index buffer is invalid.
            // there's also the optimization that draw commands can be combined when using a shared index buffer, be it
            // globally shared or just shared within the region, which isn't possible with the locally-indexed command generator.
            if (useIndexedTessellation && SectionRenderDataUnsafe.isLocalIndex(pMeshData)) {
                addLocalIndexedDrawCommands(batch, pMeshData, slices);
            } else {
                addSharedIndexedDrawCommands(batch, pMeshData, slices);
            }
        }
    }

    /**
     * Generates the draw commands for a chunk's meshes, where each mesh has a separate index buffer. This is used
     * when rendering translucent geometry, as each geometry set needs a sorted index buffer.
     */
    @SuppressWarnings("IntegerMultiplicationImplicitCastToLong")
    private static void addLocalIndexedDrawCommands(MultiDrawBatch batch, long pMeshData, int mask) {
        final var pElementPointer = batch.pElementPointer;
        final var pBaseVertex = batch.pBaseVertex;
        final var pElementCount = batch.pElementCount;

        int size = batch.size;

        long elementOffset = SectionRenderDataUnsafe.getBaseElement(pMeshData);
        long baseVertex = SectionRenderDataUnsafe.getBaseVertex(pMeshData);

        for (int facing = 0; facing < ModelQuadFacing.COUNT; facing++) {
            final long vertexCount = SectionRenderDataUnsafe.getVertexCount(pMeshData, facing);
            final long elementCount = (vertexCount >> 2) * 6;

            MemoryUtil.memPutInt(pElementCount + (size << 2), UInt32.uncheckedDowncast(elementCount));
            MemoryUtil.memPutInt(pBaseVertex + (size << 2), UInt32.uncheckedDowncast(baseVertex));

            // * 4 to convert to bytes (the index buffer contains integers)
            MemoryUtil.memPutAddress(pElementPointer + (size << 3), elementOffset << 2);

            baseVertex += vertexCount;
            elementOffset += elementCount;

            size += (mask >> facing) & 1;
        }

        batch.size = size;
    }

    /**
     * Generates the draw commands for a chunk's meshes using the shared index buffer.
     */
    @SuppressWarnings("IntegerMultiplicationImplicitCastToLong")
    private static void addSharedIndexedDrawCommands(MultiDrawBatch batch, long pMeshData, int mask) {
        final var pElementPointer = batch.pElementPointer;
        final var pBaseVertex = batch.pBaseVertex;
        final var pElementCount = batch.pElementCount;

        // this is either zero (global shared index buffer) or the offset to the location of the shared element buffer (region shared index buffer)
        final var elementOffsetBytes = SectionRenderDataUnsafe.getBaseElement(pMeshData) << 2;
        final var facingList = SectionRenderDataUnsafe.getFacingList(pMeshData);

        int size = batch.size;
        long groupVertexCount = 0;
        long baseVertex = SectionRenderDataUnsafe.getBaseVertex(pMeshData);
        int lastMaskBit = 0;

        for (int i = 0; i <= ModelQuadFacing.COUNT; i++) {
            var maskBit = 0;
            long vertexCount = 0;
            if (i < ModelQuadFacing.COUNT) {
                vertexCount = SectionRenderDataUnsafe.getVertexCount(pMeshData, i);

                // if there's no vertexes, the mask bit is just 0
                if (vertexCount != 0) {
                    var facing = (facingList >>> (i * 8)) & 0xFF;
                    maskBit = (mask >>> facing) & 1;
                }
            }

            if (maskBit == 0) {
                if (lastMaskBit == 1) {
                    // delay writing out draw command if there's a zero-size group
                    if (i < ModelQuadFacing.COUNT && vertexCount == 0) {
                        continue;
                    }

                    MemoryUtil.memPutInt(pElementCount + (size << 2), UInt32.uncheckedDowncast((groupVertexCount >> 2) * 6));
                    MemoryUtil.memPutInt(pBaseVertex + (size << 2), UInt32.uncheckedDowncast(baseVertex));
                    MemoryUtil.memPutAddress(pElementPointer + (size << 3), elementOffsetBytes);
                    size++;
                    baseVertex += groupVertexCount;
                    groupVertexCount = 0;
                }

                baseVertex += vertexCount;
            } else {
                groupVertexCount += vertexCount;
            }

            lastMaskBit = maskBit;
        }

        batch.size = size;
    }

    private static final int MODEL_UNASSIGNED = ModelQuadFacing.UNASSIGNED.ordinal();
    private static final int MODEL_POS_X      = ModelQuadFacing.POS_X.ordinal();
    private static final int MODEL_POS_Y      = ModelQuadFacing.POS_Y.ordinal();
    private static final int MODEL_POS_Z      = ModelQuadFacing.POS_Z.ordinal();

    private static final int MODEL_NEG_X      = ModelQuadFacing.NEG_X.ordinal();
    private static final int MODEL_NEG_Y      = ModelQuadFacing.NEG_Y.ordinal();
    private static final int MODEL_NEG_Z      = ModelQuadFacing.NEG_Z.ordinal();

    public static int getVisibleFaces(int originX, int originY, int originZ, int chunkX, int chunkY, int chunkZ) {
        // This is carefully written so that we can keep everything branch-less.
        //
        // Normally, this would be a ridiculous way to handle the problem. But the Hotspot VM's
        // heuristic for generating SETcc/CMOV instructions is broken, and it will always create a
        // branch even when a trivial ternary is encountered.
        //
        // For example, the following will never be transformed into a SETcc:
        //   (a > b) ? 1 : 0
        //
        // So we have to instead rely on sign-bit extension and masking (which generates a ton
        // of unnecessary instructions) to get this to be branch-less.
        //
        // To do this, we can transform the previous expression into the following.
        //   (b - a) >> 31
        //
        // This works because if (a > b) then (b - a) will always create a negative number. We then shift the sign bit
        // into the least significant bit's position (which also discards any bits following the sign bit) to get the
        // output we are looking for.
        //
        // If you look at the output which LLVM produces for a series of ternaries, you will instantly become distraught,
        // because it manages to a) correctly evaluate the cost of instructions, and b) go so far
        // as to actually produce vector code.  (https://godbolt.org/z/GaaEx39T9)

        int boundsMinX = (chunkX << 4), boundsMaxX = boundsMinX + 16;
        int boundsMinY = (chunkY << 4), boundsMaxY = boundsMinY + 16;
        int boundsMinZ = (chunkZ << 4), boundsMaxZ = boundsMinZ + 16;

        // the "unassigned" plane is always front-facing, since we can't check it
        int planes = (1 << MODEL_UNASSIGNED);

        planes |= BitwiseMath.greaterThan(originX, (boundsMinX - 3)) << MODEL_POS_X;
        planes |= BitwiseMath.greaterThan(originY, (boundsMinY - 3)) << MODEL_POS_Y;
        planes |= BitwiseMath.greaterThan(originZ, (boundsMinZ - 3)) << MODEL_POS_Z;

        planes |=    BitwiseMath.lessThan(originX, (boundsMaxX + 3)) << MODEL_NEG_X;
        planes |=    BitwiseMath.lessThan(originY, (boundsMaxY + 3)) << MODEL_NEG_Y;
        planes |=    BitwiseMath.lessThan(originZ, (boundsMaxZ + 3)) << MODEL_NEG_Z;

        return planes;
    }

    private static void setModelMatrixUniforms(ChunkShaderInterface shader, RenderRegion region, CameraTransform camera) {
        float x = getCameraTranslation(region.getOriginX(), camera.intX, camera.fracX);
        float y = getCameraTranslation(region.getOriginY(), camera.intY, camera.fracY);
        float z = getCameraTranslation(region.getOriginZ(), camera.intZ, camera.fracZ);

        shader.setRegionOffset(x, y, z);
    }

    private static float getCameraTranslation(int chunkBlockPos, int cameraBlockPos, float cameraPos) {
        return (chunkBlockPos - cameraBlockPos) - cameraPos;
    }

    private GlTessellation prepareTessellation(CommandList commandList, RenderRegion region) {
        var resources = region.getResources();

        GlTessellation tessellation = resources.getTessellation();
        if (tessellation == null) {
            tessellation = this.createRegionTessellation(commandList, resources, true);
            resources.updateTessellation(commandList, tessellation);
        }

        return tessellation;
    }

    private GlTessellation prepareIndexedTessellation(CommandList commandList, RenderRegion region) {
        var resources = region.getResources();

        GlTessellation tessellation = resources.getIndexedTessellation();
        if (tessellation == null) {
            tessellation = this.createRegionTessellation(commandList, resources, false);
            resources.updateIndexedTessellation(commandList, tessellation);
        }

        return tessellation;
    }

    private GlTessellation createRegionTessellation(CommandList commandList, RenderRegion.DeviceResources resources, boolean useSharedIndexBuffer) {
        return commandList.createTessellation(GlPrimitiveType.TRIANGLES, new TessellationBinding[] {
                TessellationBinding.forVertexBuffer(resources.getGeometryBuffer(), this.vertexFormat.getShaderBindings()),
                TessellationBinding.forElementBuffer(useSharedIndexBuffer
                        ? this.sharedIndexBuffer.getBufferObject()
                        : resources.getIndexBuffer())
        });
    }

    private static void executeDrawBatch(CommandList commandList, GlTessellation tessellation, MultiDrawBatch batch) {
        try (DrawCommandList drawCommandList = commandList.beginTessellating(tessellation)) {
            drawCommandList.multiDrawElementsBaseVertex(batch, GlIndexType.UNSIGNED_INT);
        }
    }

    @Override
    public void delete(CommandList commandList) {
        super.delete(commandList);

        this.sharedIndexBuffer.delete(commandList);
        this.batch.delete();
    }
}
