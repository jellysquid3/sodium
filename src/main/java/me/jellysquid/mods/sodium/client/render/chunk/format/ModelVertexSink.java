package me.jellysquid.mods.sodium.client.render.chunk.format;

import me.jellysquid.mods.sodium.client.model.vertex.VertexSink;
import net.minecraft.core.Vec3i;

public interface ModelVertexSink extends VertexSink {
    /**
     * Writes a quad vertex to this sink.
     * @param posX The x-position of the vertex
     * @param posY The y-position of the vertex
     * @param posZ The z-position of the vertex
     * @param color The ABGR-packed color of the vertex
     * @param u The u-texture of the vertex
     * @param v The y-texture of the vertex
     * @param light The packed light-map coordinates of the vertex
     * @param chunkId
     */
    void writeVertex(float posX, float posY, float posZ, int color, float u, float v, int light, int chunkId);

    default void writeVertex(Vec3i offset, float posX, float posY, float posZ, int color, float u, float v, int light, int chunkId) {
        this.writeVertex(offset.getX() + posX, offset.getY() + posY, offset.getZ() + posZ, color, u, v, light, chunkId);
    }

}
