package me.jellysquid.mods.sodium.client.model.light.smooth;

import net.minecraft.core.Direction;

/**
 * The neighbor information for each face of a block, used when performing smooth lighting in order to calculate
 * the occlusion of each corner.
 */
@SuppressWarnings("UnnecessaryLocalVariable")
enum AoNeighborInfo {
    DOWN(new Direction[] { Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH }, 0.5F) {
        @Override
        public void calculateCornerWeights(float x, float y, float z, float[] out) {
            final float u = z;
            final float v = 1.0f - x;

            out[0] = v * u;
            out[1] = v * (1.0f - u);
            out[2] = (1.0f - v) * (1.0f - u);
            out[3] = (1.0f - v) * u;
        }

        @Override
        public void mapCorners(int[] lm0, float[] ao0, int[] lm1, float[] ao1) {
            lm1[0] = lm0[0];
            lm1[1] = lm0[1];
            lm1[2] = lm0[2];
            lm1[3] = lm0[3];

            ao1[0] = ao0[0];
            ao1[1] = ao0[1];
            ao1[2] = ao0[2];
            ao1[3] = ao0[3];
        }

        @Override
        public float getDepth(float x, float y, float z) {
            return y;
        }
    },
    UP(new Direction[] { Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH }, 1.0F) {
        @Override
        public void calculateCornerWeights(float x, float y, float z, float[] out) {
            final float u = z;
            final float v = x;

            out[0] = v * u;
            out[1] = v * (1.0f - u);
            out[2] = (1.0f - v) * (1.0f - u);
            out[3] = (1.0f - v) * u;
        }

        @Override
        public void mapCorners(int[] lm0, float[] ao0, int[] lm1, float[] ao1) {
            lm1[2] = lm0[0];
            lm1[3] = lm0[1];
            lm1[0] = lm0[2];
            lm1[1] = lm0[3];

            ao1[2] = ao0[0];
            ao1[3] = ao0[1];
            ao1[0] = ao0[2];
            ao1[1] = ao0[3];
        }

        @Override
        public float getDepth(float x, float y, float z) {
            return 1.0f - y;
        }
    },
    NORTH(new Direction[] { Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST }, 0.8F) {
        @Override
        public void calculateCornerWeights(float x, float y, float z, float[] out) {
            final float u = 1.0f - x;
            final float v = y;

            out[0] = v * u;
            out[1] = v * (1.0f - u);
            out[2] = (1.0f - v) * (1.0f - u);
            out[3] = (1.0f - v) * u;
        }

        @Override
        public void mapCorners(int[] lm0, float[] ao0, int[] lm1, float[] ao1) {
            lm1[3] = lm0[0];
            lm1[0] = lm0[1];
            lm1[1] = lm0[2];
            lm1[2] = lm0[3];

            ao1[3] = ao0[0];
            ao1[0] = ao0[1];
            ao1[1] = ao0[2];
            ao1[2] = ao0[3];
        }

        @Override
        public float getDepth(float x, float y, float z) {
            return z;
        }
    },
    SOUTH(new Direction[] { Direction.WEST, Direction.EAST, Direction.DOWN, Direction.UP }, 0.8F) {
        @Override
        public void calculateCornerWeights(float x, float y, float z, float[] out) {
            final float u = y;
            final float v = 1.0f - x;

            out[0] = u * v;
            out[1] = (1.0f - u) * v;
            out[2] = (1.0f - u) * (1.0f - v);
            out[3] = u * (1.0f - v);
        }

        @Override
        public void mapCorners(int[] lm0, float[] ao0, int[] lm1, float[] ao1) {
            lm1[0] = lm0[0];
            lm1[1] = lm0[1];
            lm1[2] = lm0[2];
            lm1[3] = lm0[3];

            ao1[0] = ao0[0];
            ao1[1] = ao0[1];
            ao1[2] = ao0[2];
            ao1[3] = ao0[3];
        }

        @Override
        public float getDepth(float x, float y, float z) {
            return 1.0f - z;
        }
    },
    WEST(new Direction[] { Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH }, 0.6F) {
        @Override
        public void calculateCornerWeights(float x, float y, float z, float[] out) {
            final float u = z;
            final float v = y;

            out[0] = v * u;
            out[1] = v * (1.0f - u);
            out[2] = (1.0f - v) * (1.0f - u);
            out[3] = (1.0f - v) * u;
        }

        @Override
        public void mapCorners(int[] lm0, float[] ao0, int[] lm1, float[] ao1) {
            lm1[3] = lm0[0];
            lm1[0] = lm0[1];
            lm1[1] = lm0[2];
            lm1[2] = lm0[3];

            ao1[3] = ao0[0];
            ao1[0] = ao0[1];
            ao1[1] = ao0[2];
            ao1[2] = ao0[3];
        }

        @Override
        public float getDepth(float x, float y, float z) {
            return x;
        }
    },
    EAST(new Direction[] { Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH }, 0.6F) {
        @Override
        public void calculateCornerWeights(float x, float y, float z, float[] out) {
            final float u = z;
            final float v = 1.0f - y;

            out[0] = v * u;
            out[1] = v * (1.0f - u);
            out[2] = (1.0f - v) * (1.0f - u);
            out[3] = (1.0f - v) * u;
        }

        @Override
        public void mapCorners(int[] lm0, float[] ao0, int[] lm1, float[] ao1) {
            lm1[1] = lm0[0];
            lm1[2] = lm0[1];
            lm1[3] = lm0[2];
            lm1[0] = lm0[3];

            ao1[1] = ao0[0];
            ao1[2] = ao0[1];
            ao1[3] = ao0[2];
            ao1[0] = ao0[3];
        }

        @Override
        public float getDepth(float x, float y, float z) {
            return 1.0f - x;
        }
    };

    /**
     * The direction of each corner block from this face, which can be retrieved by offsetting the position of the origin
     * block by the direction vector.
     */
    public final Direction[] faces;

    /**
     * The constant brightness modifier for this face. This data exists to emulate the results of the OpenGL lighting
     * model which gives a faux directional light appearance to blocks in the game. Not currently used.
     */
    public final float strength;

    AoNeighborInfo(Direction[] directions, float strength) {
        this.faces = directions;
        this.strength = strength;
    }

    /**
     * Calculates how much each corner contributes to the final "darkening" of the vertex at the specified position. The
     * weight is a function of the distance from the vertex's position to the corner block's position.
     *
     * @param x The x-position of the vertex
     * @param y The y-position of the vertex
     * @param z The z-position of the vertex
     * @param out The weight values for each corner
     */
    public abstract void calculateCornerWeights(float x, float y, float z, float[] out);

    /**
     * Maps the light map and occlusion value arrays {@param lm0} and {@param ao0} from {@link AoFaceData} to the
     * correct corners for this facing.
     *
     * @param lm0 The input light map texture coordinates array
     * @param ao0 The input ambient occlusion color array
     * @param lm1 The re-orientated output light map texture coordinates array
     * @param ao1 The re-orientated output ambient occlusion color array
     */
    public abstract void mapCorners(int[] lm0, float[] ao0, int[] lm1, float[] ao1);

    /**
     * Calculates the depth (or inset) of the vertex into this facing of the block. Used to determine
     * how much shadow is contributed by the direct neighbors of a block.
     *
     * @param x The x-position of the vertex
     * @param y The y-position of the vertex
     * @param z The z-position of the vertex
     * @return The depth of the vertex into this face
     */
    public abstract float getDepth(float x, float y, float z);

    private static final AoNeighborInfo[] VALUES = AoNeighborInfo.values();

    /**
     * @return Returns the {@link AoNeighborInfo} which corresponds with the specified direction
     */
    public static AoNeighborInfo get(Direction direction) {
        return VALUES[direction.get3DDataValue()];
    }
}
