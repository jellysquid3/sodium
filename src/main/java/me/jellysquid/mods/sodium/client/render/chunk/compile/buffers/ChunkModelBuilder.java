package me.jellysquid.mods.sodium.client.render.chunk.compile.buffers;

import me.jellysquid.mods.sodium.client.model.IndexBufferBuilder;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.render.chunk.format.ModelVertexSink;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

public interface ChunkModelBuilder {
    ModelVertexSink getVertexSink();

    IndexBufferBuilder getIndexBufferBuilder(ModelQuadFacing facing);

    void addSprite(TextureAtlasSprite sprite);

    int getChunkId();
}
