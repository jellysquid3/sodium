package net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.impl;

import net.caffeinemc.mods.sodium.api.util.ColorARGB;
import net.caffeinemc.mods.sodium.client.gl.attribute.GlVertexFormat;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderBindingPoints;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.caffeinemc.mods.sodium.client.render.texture.SpriteFinderCache;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import org.lwjgl.system.MemoryUtil;

public class CompactChunkVertex implements ChunkVertexType {
    public static final int STRIDE = 24;

    public static final GlVertexFormat VERTEX_FORMAT = GlVertexFormat.builder(STRIDE)
            .addElement(DefaultChunkMeshAttributes.POSITION, ChunkShaderBindingPoints.ATTRIBUTE_POSITION, 0)
            .addElement(DefaultChunkMeshAttributes.COLOR, ChunkShaderBindingPoints.ATTRIBUTE_COLOR, 8)
            .addElement(DefaultChunkMeshAttributes.TEXTURE_COORD, ChunkShaderBindingPoints.ATTRIBUTE_TEXTURE_COORD, 12)
            .addElement(DefaultChunkMeshAttributes.TEXTURE_ORIGIN, ChunkShaderBindingPoints.ATTRIBUTE_TEXTURE_ORIGIN, 16)
            .addElement(DefaultChunkMeshAttributes.LIGHT_INDEX, ChunkShaderBindingPoints.ATTRIBUTE_LIGHT_INDEX, 20)
            .build();

    private static final int POSITION_MAX_VALUE = 1 << 20;

    private static final float MODEL_ORIGIN = 8.0f;
    private static final float MODEL_RANGE = 32.0f;

    @Override
    public GlVertexFormat getVertexFormat() {
        return VERTEX_FORMAT;
    }

    @Override
    public ChunkVertexEncoder getEncoder() {
        return (ptr, vertices, section) -> {
            // Calculate the center point of the texture region which is mapped to the quad
            float texCentroidU = 0.0f;
            float texCentroidV = 0.0f;

            for (var vertex : vertices) {
                texCentroidU += vertex.u;
                texCentroidV += vertex.v;
            }

            texCentroidU *= (1.0f / 4.0f);
            texCentroidV *= (1.0f / 4.0f);

            TextureAtlasSprite sprite = SpriteFinderCache.forBlockAtlas()
                    .find(texCentroidU, texCentroidV);

            if (sprite == null) {
                throw new RuntimeException("Couldn't find sprite");
            }

            for (int i = 0; i < 4; i++) {
                var vertex = vertices[i];

                int x = quantizePosition(vertex.x);
                int y = quantizePosition(vertex.y);
                int z = quantizePosition(vertex.z);

                int light = encodeLight(vertex.light);

                MemoryUtil.memPutInt(ptr +  0L, packPositionHi(x, y, z));
                MemoryUtil.memPutInt(ptr +  4L, packPositionLo(x, y, z));
                MemoryUtil.memPutInt(ptr +  8L, ColorARGB.mulRGB(vertex.color, vertex.ao));
                MemoryUtil.memPutInt(ptr + 12L, encodeTexture(vertex.u, vertex.v, sprite));
                MemoryUtil.memPutInt(ptr + 16L, encodeSprite(sprite));
                MemoryUtil.memPutInt(ptr + 20L, packLightAndSectionIndex(light, section));

                ptr += STRIDE;
            }

            return ptr;
        };
    }

    private static int encodeSprite(TextureAtlasSprite sprite) {
        return ((sprite.getX() & 0xFFFF) << 0) | ((sprite.getY() & 0xFFFF) << 16);
    }

    private static int packPositionHi(int x, int y, int z) {
        return  (((x >>> 10) & 0x3FF) <<  0) |
                (((y >>> 10) & 0x3FF) << 10) |
                (((z >>> 10) & 0x3FF) << 20);
    }

    private static int packPositionLo(int x, int y, int z) {
        return  ((x & 0x3FF) <<  0) |
                ((y & 0x3FF) << 10) |
                ((z & 0x3FF) << 20);
    }

    private static int quantizePosition(float position) {
        return ((int) (normalizePosition(position) * POSITION_MAX_VALUE)) & 0xFFFFF;
    }

    private static float normalizePosition(float v) {
        return (MODEL_ORIGIN + v) / MODEL_RANGE;
    }

    private static int encodeTexture(float u, float v, TextureAtlasSprite sprite) {
        u = (u - sprite.getU0()) / (sprite.getU1() - sprite.getU0());
        v = (v - sprite.getV0()) / (sprite.getV1() - sprite.getV0());

        int iu = Math.round(u * (1 << 15));
        int iv = Math.round(v * (1 << 15));

        return ((iu & 0xFFFF) << 0) | ((iv & 0xFFFF) << 16);
    }

    private static int encodeLight(int light) {
        int sky = Mth.clamp((light >>> 16) & 0xFF, 8, 248);
        int block = Mth.clamp((light >>>  0) & 0xFF, 8, 248);

        return (block << 0) | (sky << 8);
    }

    private static int packLightAndSectionIndex(int light, int section) {
        return ((light & 0xFFFF) << 0) |
                ((section & 0xFF) << 24);
    }

    private static int floorInt(float x) {
        return (int) Math.floor(x);
    }
}
