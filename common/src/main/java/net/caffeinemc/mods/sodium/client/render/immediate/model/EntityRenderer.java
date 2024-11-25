package net.caffeinemc.mods.sodium.client.render.immediate.model;

import com.mojang.blaze3d.vertex.PoseStack;
import net.caffeinemc.mods.sodium.api.math.MatrixHelper;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.common.EntityVertex;
import net.minecraft.core.Direction;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

import static net.caffeinemc.mods.sodium.client.render.immediate.model.ModelCuboid.*;

public class EntityRenderer {
    private static final int NUM_CUBE_VERTICES = 8;
    private static final int NUM_CUBE_FACES = 6;
    private static final int NUM_FACE_VERTICES = 4;

    private static final int
            VERTEX_X1_Y1_Z1 = 0,
            VERTEX_X2_Y1_Z1 = 1,
            VERTEX_X2_Y2_Z1 = 2,
            VERTEX_X1_Y2_Z1 = 3,
            VERTEX_X1_Y1_Z2 = 4,
            VERTEX_X2_Y1_Z2 = 5,
            VERTEX_X2_Y2_Z2 = 6,
            VERTEX_X1_Y2_Z2 = 7;

    private static final Matrix3f lastMatrix = new Matrix3f();

    private static final int VERTEX_BUFFER_BYTES = NUM_CUBE_FACES * NUM_FACE_VERTICES * EntityVertex.STRIDE;

    private static final Vector3f[] CUBE_CORNERS = new Vector3f[NUM_CUBE_VERTICES];
    private static final int[][] CUBE_VERTICES = new int[NUM_CUBE_FACES][];

    private static final Vector3f[][] VERTEX_POSITIONS = new Vector3f[NUM_CUBE_FACES][NUM_FACE_VERTICES];
    private static final Vector3f[][] VERTEX_POSITIONS_MIRRORED = new Vector3f[NUM_CUBE_FACES][NUM_FACE_VERTICES];

    private static final Vector2f[][] VERTEX_TEXTURES = new Vector2f[NUM_CUBE_FACES][NUM_FACE_VERTICES];
    private static final Vector2f[][] VERTEX_TEXTURES_MIRRORED = new Vector2f[NUM_CUBE_FACES][NUM_FACE_VERTICES];

    private static final int[] CUBE_NORMALS = new int[NUM_CUBE_FACES];
    private static final int[] CUBE_NORMALS_MIRRORED = new int[NUM_CUBE_FACES];

    static {
        CUBE_VERTICES[FACE_NEG_Y] = new int[] { VERTEX_X2_Y1_Z2, VERTEX_X1_Y1_Z2, VERTEX_X1_Y1_Z1, VERTEX_X2_Y1_Z1 };
        CUBE_VERTICES[FACE_POS_Y] = new int[] { VERTEX_X2_Y2_Z1, VERTEX_X1_Y2_Z1, VERTEX_X1_Y2_Z2, VERTEX_X2_Y2_Z2 };
        CUBE_VERTICES[FACE_NEG_Z] = new int[] { VERTEX_X2_Y1_Z1, VERTEX_X1_Y1_Z1, VERTEX_X1_Y2_Z1, VERTEX_X2_Y2_Z1 };
        CUBE_VERTICES[FACE_POS_Z] = new int[] { VERTEX_X1_Y1_Z2, VERTEX_X2_Y1_Z2, VERTEX_X2_Y2_Z2, VERTEX_X1_Y2_Z2 };
        CUBE_VERTICES[FACE_NEG_X] = new int[] { VERTEX_X2_Y1_Z2, VERTEX_X2_Y1_Z1, VERTEX_X2_Y2_Z1, VERTEX_X2_Y2_Z2 };
        CUBE_VERTICES[FACE_POS_X] = new int[] { VERTEX_X1_Y1_Z1, VERTEX_X1_Y1_Z2, VERTEX_X1_Y2_Z2, VERTEX_X1_Y2_Z1 };

        for (int cornerIndex = 0; cornerIndex < NUM_CUBE_VERTICES; cornerIndex++) {
            CUBE_CORNERS[cornerIndex] = new Vector3f();
        }

        for (int quadIndex = 0; quadIndex < NUM_CUBE_FACES; quadIndex++) {
            for (int vertexIndex = 0; vertexIndex < NUM_FACE_VERTICES; vertexIndex++) {
                VERTEX_TEXTURES[quadIndex][vertexIndex] = new Vector2f();
                VERTEX_POSITIONS[quadIndex][vertexIndex] = CUBE_CORNERS[CUBE_VERTICES[quadIndex][vertexIndex]];
            }
        }

        for (int quadIndex = 0; quadIndex < NUM_CUBE_FACES; quadIndex++) {
            for (int vertexIndex = 0; vertexIndex < NUM_FACE_VERTICES; vertexIndex++) {
                VERTEX_TEXTURES_MIRRORED[quadIndex][vertexIndex] = VERTEX_TEXTURES[quadIndex][3 - vertexIndex];
                VERTEX_POSITIONS_MIRRORED[quadIndex][vertexIndex] = VERTEX_POSITIONS[quadIndex][3 - vertexIndex];
            }
        }
    }

    public static void renderCuboid(PoseStack.Pose matrices, VertexBufferWriter writer, ModelCuboid cuboid, int light, int overlay, int color) {
        prepareNormalsIfChanged(matrices);
        prepareVertices(matrices, cuboid);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            final var vertexBuffer = stack.nmalloc(16, VERTEX_BUFFER_BYTES);
            final var vertexCount = emitQuads(vertexBuffer, cuboid, color, overlay, light);

            if (vertexCount > 0) {
                writer.push(stack, vertexBuffer, vertexCount, EntityVertex.FORMAT);
            }
        }
    }

    private static int emitQuads(final long buffer, ModelCuboid cuboid, int color, int overlay, int light) {
        final var positions = cuboid.mirror ? VERTEX_POSITIONS_MIRRORED : VERTEX_POSITIONS;
        final var textures = cuboid.mirror ? VERTEX_TEXTURES_MIRRORED : VERTEX_TEXTURES;
        final var normals = cuboid.mirror ? CUBE_NORMALS_MIRRORED :  CUBE_NORMALS;

        var vertexCount = 0;

        long ptr = buffer;

        for (int quadIndex = 0; quadIndex < NUM_CUBE_FACES; quadIndex++) {
            if (!cuboid.shouldDrawFace(quadIndex)) {
                continue;
            }

            emitVertex(ptr, positions[quadIndex][0], color, textures[quadIndex][0], overlay, light, normals[quadIndex]);
            ptr += EntityVertex.STRIDE;

            emitVertex(ptr, positions[quadIndex][1], color, textures[quadIndex][1], overlay, light, normals[quadIndex]);
            ptr += EntityVertex.STRIDE;

            emitVertex(ptr, positions[quadIndex][2], color, textures[quadIndex][2], overlay, light, normals[quadIndex]);
            ptr += EntityVertex.STRIDE;

            emitVertex(ptr, positions[quadIndex][3], color, textures[quadIndex][3], overlay, light, normals[quadIndex]);
            ptr += EntityVertex.STRIDE;

            vertexCount += 4;
        }

        return vertexCount;
    }

    private static void emitVertex(long ptr, Vector3f pos, int color, Vector2f tex, int overlay, int light, int normal) {
        EntityVertex.write(ptr, pos.x, pos.y, pos.z, color, tex.x, tex.y, overlay, light, normal);
    }

    private static void prepareVertices(PoseStack.Pose matrices, ModelCuboid cuboid) {
        buildVertexPosition(CUBE_CORNERS[VERTEX_X1_Y1_Z1], cuboid.x1, cuboid.y1, cuboid.z1, matrices.pose());
        buildVertexPosition(CUBE_CORNERS[VERTEX_X2_Y1_Z1], cuboid.x2, cuboid.y1, cuboid.z1, matrices.pose());
        buildVertexPosition(CUBE_CORNERS[VERTEX_X2_Y2_Z1], cuboid.x2, cuboid.y2, cuboid.z1, matrices.pose());
        buildVertexPosition(CUBE_CORNERS[VERTEX_X1_Y2_Z1], cuboid.x1, cuboid.y2, cuboid.z1, matrices.pose());
        buildVertexPosition(CUBE_CORNERS[VERTEX_X1_Y1_Z2], cuboid.x1, cuboid.y1, cuboid.z2, matrices.pose());
        buildVertexPosition(CUBE_CORNERS[VERTEX_X2_Y1_Z2], cuboid.x2, cuboid.y1, cuboid.z2, matrices.pose());
        buildVertexPosition(CUBE_CORNERS[VERTEX_X2_Y2_Z2], cuboid.x2, cuboid.y2, cuboid.z2, matrices.pose());
        buildVertexPosition(CUBE_CORNERS[VERTEX_X1_Y2_Z2], cuboid.x1, cuboid.y2, cuboid.z2, matrices.pose());

        buildVertexTexCoord(VERTEX_TEXTURES[FACE_NEG_Y], cuboid.u1, cuboid.v0, cuboid.u2, cuboid.v1);
        buildVertexTexCoord(VERTEX_TEXTURES[FACE_POS_Y], cuboid.u2, cuboid.v1, cuboid.u3, cuboid.v0);
        buildVertexTexCoord(VERTEX_TEXTURES[FACE_NEG_Z], cuboid.u1, cuboid.v1, cuboid.u2, cuboid.v2);
        buildVertexTexCoord(VERTEX_TEXTURES[FACE_POS_Z], cuboid.u4, cuboid.v1, cuboid.u5, cuboid.v2);
        buildVertexTexCoord(VERTEX_TEXTURES[FACE_NEG_X], cuboid.u2, cuboid.v1, cuboid.u4, cuboid.v2);
        buildVertexTexCoord(VERTEX_TEXTURES[FACE_POS_X], cuboid.u0, cuboid.v1, cuboid.u1, cuboid.v2);
    }

    public static void prepareNormalsIfChanged(PoseStack.Pose matrices) {
        if (!matrices.normal().equals(lastMatrix)) {
            lastMatrix.set(matrices.normal());

            CUBE_NORMALS[FACE_NEG_Y] = MatrixHelper.transformNormal(matrices.normal(), matrices.trustedNormals, Direction.DOWN);
            CUBE_NORMALS[FACE_POS_Y] = MatrixHelper.transformNormal(matrices.normal(), matrices.trustedNormals, Direction.UP);
            CUBE_NORMALS[FACE_NEG_Z] = MatrixHelper.transformNormal(matrices.normal(), matrices.trustedNormals, Direction.NORTH);
            CUBE_NORMALS[FACE_POS_Z] = MatrixHelper.transformNormal(matrices.normal(), matrices.trustedNormals, Direction.SOUTH);
            CUBE_NORMALS[FACE_POS_X] = MatrixHelper.transformNormal(matrices.normal(), matrices.trustedNormals, Direction.WEST);
            CUBE_NORMALS[FACE_NEG_X] = MatrixHelper.transformNormal(matrices.normal(), matrices.trustedNormals, Direction.EAST);

            // When mirroring is used, the normals for EAST and WEST are swapped.
            CUBE_NORMALS_MIRRORED[FACE_NEG_Y] = CUBE_NORMALS[FACE_NEG_Y];
            CUBE_NORMALS_MIRRORED[FACE_POS_Y] = CUBE_NORMALS[FACE_POS_Y];
            CUBE_NORMALS_MIRRORED[FACE_NEG_Z] = CUBE_NORMALS[FACE_NEG_Z];
            CUBE_NORMALS_MIRRORED[FACE_POS_Z] = CUBE_NORMALS[FACE_POS_Z];
            CUBE_NORMALS_MIRRORED[FACE_POS_X] = CUBE_NORMALS[FACE_NEG_X]; // mirrored
            CUBE_NORMALS_MIRRORED[FACE_NEG_X] = CUBE_NORMALS[FACE_POS_X]; // mirrored
        }
    }

    private static void buildVertexPosition(Vector3f vector, float x, float y, float z, Matrix4f matrix) {
        vector.x = MatrixHelper.transformPositionX(matrix, x, y, z);
        vector.y = MatrixHelper.transformPositionY(matrix, x, y, z);
        vector.z = MatrixHelper.transformPositionZ(matrix, x, y, z);
    }

    private static void buildVertexTexCoord(Vector2f[] uvs, float u1, float v1, float u2, float v2) {
        uvs[0].set(u2, v1);
        uvs[1].set(u1, v1);
        uvs[2].set(u1, v2);
        uvs[3].set(u2, v2);
    }
}
