package net.caffeinemc.mods.sodium.client.render.immediate.model;

import java.util.Set;

import net.caffeinemc.mods.sodium.client.util.Int2;
import net.minecraft.core.Direction;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;

public class ModelCuboid {
    public static final int NUM_CUBE_VERTICES = 8;
    public static final int NUM_CUBE_FACES = 6;
    public static final int NUM_FACE_VERTICES = 4;

    public static final int
            VERTEX_X0_Y0_Z0 = 0,
            VERTEX_X1_Y0_Z0 = 1,
            VERTEX_X1_Y1_Z0 = 2,
            VERTEX_X0_Y1_Z0 = 3,
            VERTEX_X0_Y0_Z1 = 4,
            VERTEX_X1_Y0_Z1 = 5,
            VERTEX_X1_Y1_Z1 = 6,
            VERTEX_X0_Y1_Z1 = 7;

    // The ordering needs to be the same as Minecraft, otherwise some core shader replacements
    // will be unable to identify the facing.
    public static final int
            FACE_NEG_Y = 0, // DOWN
            FACE_POS_Y = 1, // UP
            FACE_NEG_X = 2, // WEST
            FACE_NEG_Z = 3, // NORTH
            FACE_POS_X = 4, // EAST
            FACE_POS_Z = 5; // SOUTH

    public final float originX, originY, originZ;
    public final float sizeX, sizeY, sizeZ;

    // Bit-mask of visible faces
    private final int cullMask;

    // Per-face attributes
    public final int[] normals;

    // Per-vertex attributes
    public final int[] positions;
    public final long[] textures; // UVs are packed into 64-bit integers to reduce the number of memory loads

    public ModelCuboid(int u, int v,
                       float x1, float y1, float z1,
                       float sizeX, float sizeY, float sizeZ,
                       float extraX, float extraY, float extraZ,
                       boolean mirror,
                       float textureWidth, float textureHeight,
                       Set<Direction> renderDirections) {
        float x2 = x1 + sizeX;
        float y2 = y1 + sizeY;
        float z2 = z1 + sizeZ;

        x1 -= extraX;
        y1 -= extraY;
        z1 -= extraZ;

        x2 += extraX;
        y2 += extraY;
        z2 += extraZ;

        if (mirror) {
            float tmp = x2;
            x2 = x1;
            x1 = tmp;
        }

        x1 /= 16.0f;
        y1 /= 16.0f;
        z1 /= 16.0f;

        x2 /= 16.0f;
        y2 /= 16.0f;
        z2 /= 16.0f;

        this.originX = x1;
        this.originY = y1;
        this.originZ = z1;

        this.sizeX = x2 - x1;
        this.sizeY = y2 - y1;
        this.sizeZ = z2 - z1;

        var scaleU = 1.0f / textureWidth;
        var scaleV = 1.0f / textureHeight;

        float u0 = scaleU * (u);
        float u1 = scaleU * (u + sizeZ);
        float u2 = scaleU * (u + sizeZ + sizeX);
        float u3 = scaleU * (u + sizeZ + sizeX + sizeX);
        float u4 = scaleU * (u + sizeZ + sizeX + sizeZ);
        float u5 = scaleU * (u + sizeZ + sizeX + sizeZ + sizeX);

        float v0 = scaleV * (v);
        float v1 = scaleV * (v + sizeZ);
        float v2 = scaleV * (v + sizeZ + sizeY);

        this.cullMask = createCullMask(renderDirections);

        final int[] positions = new int[NUM_CUBE_FACES * NUM_FACE_VERTICES];
        final long[] textures = new long[NUM_CUBE_FACES * NUM_FACE_VERTICES];
        final int[] normals = new int[] { FACE_NEG_Y, FACE_POS_Y, FACE_NEG_X, FACE_NEG_Z, FACE_POS_X, FACE_POS_Z };

        writeVertexList(positions, FACE_NEG_Y, VERTEX_X1_Y0_Z1, VERTEX_X0_Y0_Z1, VERTEX_X0_Y0_Z0, VERTEX_X1_Y0_Z0);
        writeTexCoords(textures, FACE_NEG_Y, u1, v0, u2, v1);

        writeVertexList(positions, FACE_POS_Y, VERTEX_X1_Y1_Z0, VERTEX_X0_Y1_Z0, VERTEX_X0_Y1_Z1, VERTEX_X1_Y1_Z1);
        writeTexCoords(textures, FACE_POS_Y, u2, v1, u3, v0);

        writeVertexList(positions, FACE_NEG_Z, VERTEX_X1_Y0_Z0, VERTEX_X0_Y0_Z0, VERTEX_X0_Y1_Z0, VERTEX_X1_Y1_Z0);
        writeTexCoords(textures, FACE_NEG_Z, u1, v1, u2, v2);

        writeVertexList(positions, FACE_POS_Z, VERTEX_X0_Y0_Z1, VERTEX_X1_Y0_Z1, VERTEX_X1_Y1_Z1, VERTEX_X0_Y1_Z1);
        writeTexCoords(textures, FACE_POS_Z, u4, v1, u5, v2);

        writeVertexList(positions, FACE_NEG_X, VERTEX_X1_Y0_Z1, VERTEX_X1_Y0_Z0, VERTEX_X1_Y1_Z0, VERTEX_X1_Y1_Z1);
        writeTexCoords(textures, FACE_NEG_X, u2, v1, u4, v2);

        writeVertexList(positions, FACE_POS_X, VERTEX_X0_Y0_Z0, VERTEX_X0_Y0_Z1, VERTEX_X0_Y1_Z1, VERTEX_X0_Y1_Z0);
        writeTexCoords(textures, FACE_POS_X, u0, v1, u1, v2);

        if (mirror) {
            reverseVertices(positions, textures);

            // When mirroring is used, the normals for EAST and WEST are swapped.
            normals[FACE_POS_X] = FACE_NEG_X;
            normals[FACE_NEG_X] = FACE_POS_X;
        }

        this.normals = normals;
        this.positions = positions;
        this.textures = textures;
    }

    private static int createCullMask(Set<Direction> directions) {
        int mask = 0;

        for (var direction : directions) {
            mask |= 1 << getFaceIndex(direction);
        }

        return mask;
    }

    private static void reverseVertices(int[] vertices, long[] texCoords) {
        for (int faceIndex = 0; faceIndex < NUM_CUBE_FACES; faceIndex++) {
            final int vertexOffset = faceIndex * NUM_FACE_VERTICES;

            ArrayUtils.swap(vertices, vertexOffset + 0, vertexOffset + 3);
            ArrayUtils.swap(vertices, vertexOffset + 1, vertexOffset + 2);

            ArrayUtils.swap(texCoords, vertexOffset + 0, vertexOffset + 3);
            ArrayUtils.swap(texCoords, vertexOffset + 1, vertexOffset + 2);
        }
    }

    private static void writeVertexList(int[] positions, int faceIndex, int i0, int i1, int i2, int i3) {
        positions[(faceIndex * 4) + 0] = i0;
        positions[(faceIndex * 4) + 1] = i1;
        positions[(faceIndex * 4) + 2] = i2;
        positions[(faceIndex * 4) + 3] = i3;
    }

    private static void writeTexCoords(long[] textures, int faceIndex, float u1, float v1, float u2, float v2) {
        textures[(faceIndex * 4) + 0] = Int2.pack(Float.floatToRawIntBits(u2), Float.floatToRawIntBits(v1));
        textures[(faceIndex * 4) + 1] = Int2.pack(Float.floatToRawIntBits(u1), Float.floatToRawIntBits(v1));
        textures[(faceIndex * 4) + 2] = Int2.pack(Float.floatToRawIntBits(u1), Float.floatToRawIntBits(v2));
        textures[(faceIndex * 4) + 3] = Int2.pack(Float.floatToRawIntBits(u2), Float.floatToRawIntBits(v2));
    }

    public boolean shouldDrawFace(int faceIndex) {
        return (this.cullMask & (1 << faceIndex)) != 0;
    }

    private static int getFaceIndex(@NotNull Direction dir) {
        return switch (dir) {
            case DOWN   -> FACE_NEG_Y;
            case UP     -> FACE_POS_Y;
            case NORTH  -> FACE_NEG_Z;
            case SOUTH  -> FACE_POS_Z;
            case WEST   -> FACE_NEG_X;
            case EAST   -> FACE_POS_X;
        };
    }
}
