package me.jellysquid.mods.sodium.client.model.quad.properties;

import me.jellysquid.mods.sodium.client.model.quad.ModelQuadView;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;

public class ModelQuadFlags {
    /**
     * Indicates that the quad is aligned to the block grid.
     */
    public static final int IS_ALIGNED = 0b01;

    /**
     * Indicates that the quad does not fully cover the given face for the model.
     */
    public static final int IS_PARTIAL = 0b10;

    /**
     * @return True if the bit-flag of {@link ModelQuadFlags} contains the given flag
     */
    public static boolean contains(int flags, int mask) {
        return (flags & mask) != 0;
    }

    /**
     * Calculates the properties of the given quad. This data is used later by the light pipeline in order to make
     * certain optimizations.
     */
    public static int getQuadFlags(BakedQuad bakedQuad) {
        ModelQuadView quad = (ModelQuadView) bakedQuad;
        Direction face = bakedQuad.getDirection();

        float minX = 32.0F;
        float minY = 32.0F;
        float minZ = 32.0F;

        float maxX = -32.0F;
        float maxY = -32.0F;
        float maxZ = -32.0F;

        for (int i = 0; i < 4; ++i) {
            float x = quad.getX(i);
            float y = quad.getY(i);
            float z = quad.getZ(i);

            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        boolean aligned = switch (face) {
            case DOWN -> minY == maxY && minY < 0.0001f;
            case UP -> minY == maxY && maxY > 0.9999F;
            case NORTH -> minZ == maxZ && minZ < 0.0001f;
            case SOUTH -> minZ == maxZ && maxZ > 0.9999F;
            case WEST -> minX == maxX && minX < 0.0001f;
            case EAST -> minX == maxX && maxX > 0.9999F;
        };

        boolean partial = switch (face.getAxis()) {
            case X -> minY >= 0.0001f || minZ >= 0.0001f || maxY <= 0.9999F || maxZ <= 0.9999F;
            case Y -> minX >= 0.0001f || minZ >= 0.0001f || maxX <= 0.9999F || maxZ <= 0.9999F;
            case Z -> minX >= 0.0001f || minY >= 0.0001f || maxX <= 0.9999F || maxY <= 0.9999F;
        };

        int flags = 0;

        if (partial) {
            flags |= IS_PARTIAL;
        }

        if (aligned) {
            flags |= IS_ALIGNED;
        }

        return flags;
    }
}
