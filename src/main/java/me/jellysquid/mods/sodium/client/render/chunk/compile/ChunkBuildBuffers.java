package me.jellysquid.mods.sodium.client.render.chunk.compile;

import me.jellysquid.mods.sodium.client.gl.buffer.IndexedVertexData;
import me.jellysquid.mods.sodium.client.gl.util.ElementRange;
import me.jellysquid.mods.sodium.client.model.IndexBufferBuilder;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferBuilder;
import me.jellysquid.mods.sodium.client.model.vertex.type.ChunkVertexType;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.BakedChunkModelBuilder;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkMeshData;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderData;
import me.jellysquid.mods.sodium.client.render.chunk.format.ModelVertexSink;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPassManager;
import me.jellysquid.mods.sodium.client.util.NativeBuffer;
import net.minecraft.client.render.RenderLayer;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * A collection of temporary buffers for each worker thread which will be used to build chunk meshes for given render
 * passes. This makes a best-effort attempt to pick a suitable size for each scratch buffer, but will never try to
 * shrink a buffer.
 */
public class ChunkBuildBuffers {
    private final ChunkModelBuilder[] delegates;

    private final VertexBufferBuilder[] vertexBuffers;
    private final IndexBufferBuilder[][] indexBuffers;

    private final ChunkVertexType vertexType;

    private final BlockRenderPassManager renderPassManager;

    public ChunkBuildBuffers(ChunkVertexType vertexType, BlockRenderPassManager renderPassManager) {
        this.vertexType = vertexType;
        this.renderPassManager = renderPassManager;

        this.delegates = new ChunkModelBuilder[BlockRenderPass.COUNT];

        this.vertexBuffers = new VertexBufferBuilder[BlockRenderPass.COUNT];
        this.indexBuffers = new IndexBufferBuilder[BlockRenderPass.COUNT][ModelQuadFacing.COUNT];

        for (BlockRenderPass pass : BlockRenderPass.VALUES) {
            IndexBufferBuilder[] indexBuffers = this.indexBuffers[pass.ordinal()];

            for (int facing = 0; facing < ModelQuadFacing.COUNT; facing++) {
                indexBuffers[facing] = new IndexBufferBuilder(1024);
            }

            this.vertexBuffers[pass.ordinal()] = new VertexBufferBuilder(this.vertexType.getBufferVertexFormat(),
                    pass.getLayer().getExpectedBufferSize());
        }
    }

    public void init(ChunkRenderData.Builder renderData, int chunkId) {
        for (VertexBufferBuilder vertexBuffer : this.vertexBuffers) {
            vertexBuffer.start();
        }

        for (IndexBufferBuilder[] indexBuffers : this.indexBuffers) {
            for (IndexBufferBuilder indexBuffer : indexBuffers) {
                indexBuffer.start();
            }
        }

        for (int i = 0; i < this.delegates.length; i++) {
            ModelVertexSink vertexSink = this.vertexType.createBufferWriter(this.vertexBuffers[i]);
            IndexBufferBuilder[] indexBuffers = this.indexBuffers[i];

            this.delegates[i] = new BakedChunkModelBuilder(indexBuffers, vertexSink, renderData, chunkId);
        }
    }

    /**
     * Return the {@link ChunkModelBuilder} for the given {@link RenderLayer} as mapped by the
     * {@link BlockRenderPassManager} for this render context.
     */
    public ChunkModelBuilder get(RenderLayer layer) {
        return this.delegates[this.renderPassManager.getRenderPassId(layer)];
    }

    /**
     * Creates immutable baked chunk meshes from all non-empty scratch buffers. This is used after all blocks
     * have been rendered to pass the finished meshes over to the graphics card. This function can be called multiple
     * times to return multiple copies.
     */
    public ChunkMeshData createMesh(BlockRenderPass pass) {
        NativeBuffer vertexBuffer = this.vertexBuffers[pass.ordinal()].pop();

        if (vertexBuffer == null) {
            return null;
        }

        IndexBufferBuilder.Result[] indexBuffers = Arrays.stream(this.indexBuffers[pass.ordinal()])
                .map(IndexBufferBuilder::pop)
                .toArray(IndexBufferBuilder.Result[]::new);

        NativeBuffer indexBuffer = new NativeBuffer(Arrays.stream(indexBuffers)
                .filter(Objects::nonNull)
                .mapToInt(IndexBufferBuilder.Result::getByteSize)
                .sum());

        int indexPointer = 0;

        Map<ModelQuadFacing, ElementRange> ranges = new EnumMap<>(ModelQuadFacing.class);

        for (ModelQuadFacing facing : ModelQuadFacing.VALUES) {
            IndexBufferBuilder.Result indices = indexBuffers[facing.ordinal()];

            if (indices == null) {
                continue;
            }

            ranges.put(facing,
                    new ElementRange(indexPointer, indices.getCount(), indices.getFormat(), indices.getBaseVertex()));

            indexPointer = indices.writeTo(indexPointer, indexBuffer.getDirectBuffer());
        }

        IndexedVertexData vertexData = new IndexedVertexData(this.vertexType.getCustomVertexFormat(),
                vertexBuffer, indexBuffer);

        return new ChunkMeshData(vertexData, ranges);
    }

    public void destroy() {
        for (VertexBufferBuilder builder : this.vertexBuffers) {
            builder.destroy();
        }
    }
}
