package me.jellysquid.mods.sodium.client.model.vertex.formats.line;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import me.jellysquid.mods.sodium.client.model.vertex.VertexSink;

public interface LineVertexSink extends VertexSink {
    VertexFormat VERTEX_FORMAT = DefaultVertexFormat.POSITION_COLOR_NORMAL;

    /**
     * Writes a line vertex to the sink.
     * @param x The x-position of the vertex
     * @param y The y-position of the vertex
     * @param z The z-position of the vertex
     * @param color The ABGR-packed color of the vertex
     * @param normal The 3 byte packed normal vector of the vertex
     */
    void vertexLine(float x, float y, float z, int color, int normal);
}
