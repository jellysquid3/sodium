package net.caffeinemc.mods.sodium.client.render.chunk.compile;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.buffers.BakedChunkModelBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionInfo;
import net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionMeshParts;
import net.caffeinemc.mods.sodium.client.render.chunk.data.SectionRenderDataUnsafe;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.builder.ChunkMeshBufferBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.caffeinemc.mods.sodium.client.util.NativeBuffer;

/**
 * A collection of temporary buffers for each worker thread which will be used to build chunk meshes for given render
 * passes. This makes a best-effort attempt to pick a suitable size for each scratch buffer, but will never try to
 * shrink a buffer.
 */
public class ChunkBuildBuffers {
    private final Reference2ReferenceOpenHashMap<TerrainRenderPass, BakedChunkModelBuilder> builders = new Reference2ReferenceOpenHashMap<>();

    private final ChunkVertexType vertexType;

    public ChunkBuildBuffers(ChunkVertexType vertexType) {
        this.vertexType = vertexType;

        for (TerrainRenderPass pass : DefaultTerrainRenderPasses.ALL) {
            var vertexBuffers = new ChunkMeshBufferBuilder[ModelQuadFacing.COUNT];

            for (int facing = 0; facing < ModelQuadFacing.COUNT; facing++) {
                vertexBuffers[facing] = new ChunkMeshBufferBuilder(this.vertexType, 128 * 1024);
            }

            this.builders.put(pass, new BakedChunkModelBuilder(vertexBuffers));
        }
    }

    public void init(BuiltSectionInfo.Builder renderData, int sectionIndex) {
        for (var builder : this.builders.values()) {
            builder.begin(renderData, sectionIndex);
        }
    }

    public ChunkModelBuilder get(Material material) {
        return this.builders.get(material.pass);
    }

    public ChunkModelBuilder get(TerrainRenderPass pass) {
        return this.builders.get(pass);
    }

    /**
     * Creates immutable baked chunk meshes from all non-empty scratch buffers. This is used after all blocks
     * have been rendered to pass the finished meshes over to the graphics card. This function can be called multiple
     * times to return multiple copies.
     */
    public BuiltSectionMeshParts createMesh(TerrainRenderPass pass, int visibleSlices, boolean forceUnassigned, boolean sliceReordering) {
        var builder = this.builders.get(pass);
        int[] vertexSegments = new int[ModelQuadFacing.COUNT << 1];
        int vertexTotal = 0;

        // get the total vertex count to initialize the buffer
        for (ModelQuadFacing facing : ModelQuadFacing.VALUES) {
            vertexTotal += builder.getVertexBuffer(facing).count();
        }

        if (vertexTotal == 0) {
            return null;
        }

        var mergedBuffer = new NativeBuffer(vertexTotal * this.vertexType.getVertexFormat().getStride());
        var mergedBufferBuilder = mergedBuffer.getDirectBuffer();

        if (sliceReordering) {
            // sliceReordering implies !forceUnassigned

            // write all currently visible slices first, and then the rest.
            // start with unassigned as it will never become invisible
            var unassignedBuffer = builder.getVertexBuffer(ModelQuadFacing.UNASSIGNED);
            int vertexSegmentCount = 0;
            vertexSegments[vertexSegmentCount++] = unassignedBuffer.count();
            vertexSegments[vertexSegmentCount++] = ModelQuadFacing.UNASSIGNED.ordinal();
            if (!unassignedBuffer.isEmpty()) {
                mergedBufferBuilder.put(unassignedBuffer.slice());
            }

            // write all visible and then invisible slices
            for (var step = 0; step < 2; step++) {
                for (ModelQuadFacing facing : ModelQuadFacing.VALUES) {
                    var facingIndex = facing.ordinal();
                    if (facing == ModelQuadFacing.UNASSIGNED || ((visibleSlices >> facingIndex) & 1) == step) {
                        continue;
                    }

                    var buffer = builder.getVertexBuffer(facing);

                    // generate empty ranges to prevent SectionRenderData storage from making up indexes for null ranges
                    vertexSegments[vertexSegmentCount++] = buffer.count();
                    vertexSegments[vertexSegmentCount++] = facingIndex;

                    if (!buffer.isEmpty()) {
                        mergedBufferBuilder.put(buffer.slice());
                    }
                }
            }
        } else {
            // forceUnassigned implies !sliceReordering

            if (forceUnassigned) {
                var segmentIndex = ModelQuadFacing.UNASSIGNED.ordinal() << 1;
                vertexSegments[segmentIndex] = vertexTotal;
                vertexSegments[segmentIndex + 1] = ModelQuadFacing.UNASSIGNED.ordinal();
            }

            for (ModelQuadFacing facing : ModelQuadFacing.VALUES) {
                var buffer = builder.getVertexBuffer(facing);
                if (!buffer.isEmpty()) {
                    if (!forceUnassigned) {
                        var facingIndex = facing.ordinal();
                        var segmentIndex = facingIndex << 1;
                        vertexSegments[segmentIndex] = buffer.count();
                        vertexSegments[segmentIndex + 1] = facingIndex;
                    }
                    mergedBufferBuilder.put(buffer.slice());
                }
            }
        }

        return new BuiltSectionMeshParts(mergedBuffer, vertexSegments);
    }

    public void destroy() {
        for (var builder : this.builders.values()) {
            builder.destroy();
        }
    }
}
