package me.jellysquid.mods.sodium.client.render.chunk.compile.buffers;

import me.jellysquid.mods.sodium.client.model.IndexBufferBuilder;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderData;
import me.jellysquid.mods.sodium.client.render.chunk.format.ModelVertexSink;
import net.minecraft.client.texture.Sprite;

public class BakedChunkModelBuilder implements ChunkModelBuilder {
    private final ModelVertexSink vertexSink;
    private final IndexBufferBuilder[] indexBufferBuilders;

    private final ChunkRenderData.Builder renderData;
    private final int id;

    public BakedChunkModelBuilder(IndexBufferBuilder[] indexBufferBuilders,
                                  ModelVertexSink vertexSink,
                                  ChunkRenderData.Builder renderData,
                                  int chunkId) {
        this.indexBufferBuilders = indexBufferBuilders;
        this.vertexSink = vertexSink;

        this.renderData = renderData;
        this.id = chunkId;
    }

    @Override
    public ModelVertexSink getVertexSink() {
        return this.vertexSink;
    }

    @Override
    public IndexBufferBuilder getIndexBufferBuilder(ModelQuadFacing facing) {
        return this.indexBufferBuilders[facing.ordinal()];
    }

    @Override
    public void addSprite(Sprite sprite) {
        this.renderData.addSprite(sprite);
    }

    @Override
    public int getChunkId() {
        return this.id;
    }
}
