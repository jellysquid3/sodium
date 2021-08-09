package me.jellysquid.mods.sodium.client.gl.attribute;

import com.mojang.blaze3d.vertex.VertexFormat;

public interface BufferVertexFormat {
    static BufferVertexFormat from(VertexFormat format) {
        return (BufferVertexFormat) format;
    }

    int getStride();
}
