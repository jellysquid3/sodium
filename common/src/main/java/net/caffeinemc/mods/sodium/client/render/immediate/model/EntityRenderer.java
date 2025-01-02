package net.caffeinemc.mods.sodium.client.render.immediate.model;

import com.mojang.blaze3d.vertex.PoseStack;
import net.caffeinemc.mods.sodium.api.math.MatrixHelper;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.common.EntityVertex;
import net.caffeinemc.mods.sodium.client.util.Int2;
import net.minecraft.core.Direction;
import org.joml.Matrix3f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import static net.caffeinemc.mods.sodium.client.render.immediate.model.ModelCuboid.*;

public class EntityRenderer {
    private static final Matrix3f prevNormalMatrix = new Matrix3f();

    private static final int VERTEX_BUFFER_BYTES = NUM_CUBE_FACES * NUM_FACE_VERTICES * EntityVertex.STRIDE;

    private static final long[] CUBE_VERTEX_XY = new long[NUM_CUBE_VERTICES]; // (pos.x, pos.y)
    private static final long[] CUBE_VERTEX_ZW = new long[NUM_CUBE_VERTICES]; // (pos.z, color)

    private static final int[] CUBE_FACE_NORMAL = new int[NUM_CUBE_FACES];

    public static void renderCuboid(PoseStack.Pose matrices, VertexBufferWriter writer, ModelCuboid cuboid, int light, int overlay, int color) {
        prepareVertices(matrices, cuboid, color);
        prepareNormalsIfChanged(matrices);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            final var vertexBuffer = stack.nmalloc(64, VERTEX_BUFFER_BYTES);
            final var vertexCount = emitQuads(vertexBuffer, cuboid, overlay, light);

            if (vertexCount > 0) {
                writer.push(stack, vertexBuffer, vertexCount, EntityVertex.FORMAT);
            }
        }
    }

    private static int emitQuads(final long buffer, ModelCuboid cuboid, int overlay, int light) {
        // Pack the Overlay and Light coordinates into a 64-bit integer as they are next to each other
        // in the vertex format. This eliminates another 32-bit memory write in the hot path.
        final long packedOverlayLight = Int2.pack(overlay, light);

        long ptr = buffer;

        final int[] normals = cuboid.normals;
        final int[] positions = cuboid.positions;
        final long[] textures = cuboid.textures;

        int vertexCount = 0;

        for (int faceIndex = 0; faceIndex < NUM_CUBE_FACES; faceIndex++) {
            if (!cuboid.shouldDrawFace(faceIndex)) {
                continue;
            }
            
            final int elementOffset = faceIndex * NUM_FACE_VERTICES;
            final int packedNormal = CUBE_FACE_NORMAL[normals[faceIndex]];
            ptr = writeVertex(ptr, positions[elementOffset + 0], textures[elementOffset + 0], packedOverlayLight, packedNormal);
            ptr = writeVertex(ptr, positions[elementOffset + 1], textures[elementOffset + 1], packedOverlayLight, packedNormal);
            ptr = writeVertex(ptr, positions[elementOffset + 2], textures[elementOffset + 2], packedOverlayLight, packedNormal);
            ptr = writeVertex(ptr, positions[elementOffset + 3], textures[elementOffset + 3], packedOverlayLight, packedNormal);

            vertexCount += 4;
        }

        return vertexCount;
    }

    private static long writeVertex(long ptr, int vertexIndex, long packedUv, long packedOverlayLight, int packedNormal) {
        MemoryUtil.memPutLong(ptr + 0L, CUBE_VERTEX_XY[vertexIndex]);
        MemoryUtil.memPutLong(ptr + 8L, CUBE_VERTEX_ZW[vertexIndex]); // overlaps with color attribute
        MemoryUtil.memPutLong(ptr + 16L, packedUv);
        MemoryUtil.memPutLong(ptr + 24L, packedOverlayLight);
        MemoryUtil.memPutInt(ptr + 32L, packedNormal);

        return ptr + EntityVertex.STRIDE;
    }

    private static void prepareVertices(PoseStack.Pose matrices, ModelCuboid cuboid, int color) {
        var pose = matrices.pose();

        float vxx = (pose.m00() * cuboid.sizeX), vxy = (pose.m01() * cuboid.sizeX), vxz = (pose.m02() * cuboid.sizeX);
        float vyx = (pose.m10() * cuboid.sizeY), vyy = (pose.m11() * cuboid.sizeY), vyz = (pose.m12() * cuboid.sizeY);
        float vzx = (pose.m20() * cuboid.sizeZ), vzy = (pose.m21() * cuboid.sizeZ), vzz = (pose.m22() * cuboid.sizeZ);

        // Compute the transformed origin point of the cuboid
        float c000x = MatrixHelper.transformPositionX(pose, cuboid.originX, cuboid.originY, cuboid.originZ);
        float c000y = MatrixHelper.transformPositionY(pose, cuboid.originX, cuboid.originY, cuboid.originZ);
        float c000z = MatrixHelper.transformPositionZ(pose, cuboid.originX, cuboid.originY, cuboid.originZ);
        setVertex(VERTEX_X0_Y0_Z0, c000x, c000y, c000z, color);

        // Add the pre-multiplied vectors to find the other 7 vertices
        // This avoids needing to multiply each vertex position against the pose matrix, which eliminates many
        // floating-point operations (going from 21 flops/vert to 3 flops/vert).
        // Originally suggested by MoePus on GitHub in this pull request:
        //  https://github.com/CaffeineMC/sodium/pull/2960
        float c100x = c000x + vxx;
        float c100y = c000y + vxy;
        float c100z = c000z + vxz;
        setVertex(VERTEX_X1_Y0_Z0, c100x, c100y, c100z, color);

        float c110x = c100x + vyx;
        float c110y = c100y + vyy;
        float c110z = c100z + vyz;
        setVertex(VERTEX_X1_Y1_Z0, c110x, c110y, c110z, color);

        float c010x = c000x + vyx;
        float c010y = c000y + vyy;
        float c010z = c000z + vyz;
        setVertex(VERTEX_X0_Y1_Z0, c010x, c010y, c010z, color);

        float c001x = c000x + vzx;
        float c001y = c000y + vzy;
        float c001z = c000z + vzz;
        setVertex(VERTEX_X0_Y0_Z1, c001x, c001y, c001z, color);

        float c101x = c100x + vzx;
        float c101y = c100y + vzy;
        float c101z = c100z + vzz;
        setVertex(VERTEX_X1_Y0_Z1, c101x, c101y, c101z, color);

        float c111x = c110x + vzx;
        float c111y = c110y + vzy;
        float c111z = c110z + vzz;
        setVertex(VERTEX_X1_Y1_Z1, c111x, c111y, c111z, color);

        float c011x = c010x + vzx;
        float c011y = c010y + vzy;
        float c011z = c010z + vzz;
        setVertex(VERTEX_X0_Y1_Z1, c011x, c011y, c011z, color);
    }

    private static void setVertex(int vertexIndex, float x, float y, float z, int color) {
        // Since we have a spare element, pack the color into it. This makes the code a little obtuse,
        // but it avoids another 32-bit memory write in the hot path, which helps a lot.
        CUBE_VERTEX_XY[vertexIndex] = Int2.pack(Float.floatToRawIntBits(x), Float.floatToRawIntBits(y));
        CUBE_VERTEX_ZW[vertexIndex] = Int2.pack(Float.floatToRawIntBits(z), color);
    }

    private static void prepareNormalsIfChanged(PoseStack.Pose matrices) {
        if (matrices.normal().equals(prevNormalMatrix)) {
            return;
        }

        CUBE_FACE_NORMAL[FACE_NEG_Y] = MatrixHelper.transformNormal(matrices.normal(), matrices.trustedNormals, Direction.DOWN);
        CUBE_FACE_NORMAL[FACE_POS_Y] = MatrixHelper.transformNormal(matrices.normal(), matrices.trustedNormals, Direction.UP);
        CUBE_FACE_NORMAL[FACE_NEG_Z] = MatrixHelper.transformNormal(matrices.normal(), matrices.trustedNormals, Direction.NORTH);
        CUBE_FACE_NORMAL[FACE_POS_Z] = MatrixHelper.transformNormal(matrices.normal(), matrices.trustedNormals, Direction.SOUTH);
        CUBE_FACE_NORMAL[FACE_POS_X] = MatrixHelper.transformNormal(matrices.normal(), matrices.trustedNormals, Direction.WEST);
        CUBE_FACE_NORMAL[FACE_NEG_X] = MatrixHelper.transformNormal(matrices.normal(), matrices.trustedNormals, Direction.EAST);

        prevNormalMatrix.set(matrices.normal());
    }
}
