package me.jellysquid.mods.sodium.client.util;

import com.mojang.math.Vector3f;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;

/**
 * Provides some utilities for working with packed normal vectors. Each normal component provides 8 bits of
 * precision in the range of [-1.0,1.0].
 *
 * | 32        | 24        | 16        | 8          |
 * | 0000 0000 | 0110 1100 | 0110 1100 | 0110 1100  |
 * | Padding   | X         | Y         | Z          |
 */
public class Norm3b {
    /**
     * The maximum value of a normal's vector component.
     */
    private static final float COMPONENT_RANGE = 127.0f;

    /**
     * Constant value which can be multiplied with a floating-point vector component to get the normalized value. The
     * multiplication is slightly faster than a floating point division, and this code is a hot path which justifies it.
     */
    private static final float NORM = 1.0f / COMPONENT_RANGE;

    static int pack(Vec3i norm) {
        return pack(norm.getX(), norm.getY(), norm.getZ());
    }

    public static int pack(Vector3f dir) {
        return pack(dir.x(), dir.y(), dir.z());
    }

    /**
     * Packs the specified vector components into a 32-bit integer in XYZ ordering with the 8 bits of padding at the
     * end.
     * @param x The x component of the normal's vector
     * @param y The y component of the normal's vector
     * @param z The z component of the normal's vector
     */
    public static int pack(float x, float y, float z) {
        int normX = encode(x);
        int normY = encode(y);
        int normZ = encode(z);

        return (normZ << 16) | (normY << 8) | normX;
    }

    /**
     * Encodes a float in the range of -1.0..1.0 to a normalized unsigned integer in the range of 0..255 which can then
     * be passed to graphics memory.
     */
    private static int encode(float comp) {
        // TODO: is the clamp necessary here? our inputs should always be normalized vector components
        return ((int) (Mth.clamp(comp, -1.0F, 1.0F) * COMPONENT_RANGE) & 255);
    }

    /**
     * Unpacks the x-component of the packed normal, denormalizing it to a float in the range of -1.0..1.0.
     * @param norm The packed normal
     */
    public static float unpackX(int norm) {
        return ((byte) (norm & 0xFF)) * NORM;
    }

    /**
     * Unpacks the y-component of the packed normal, denormalizing it to a float in the range of -1.0..1.0.
     * @param norm The packed normal
     */
    public static float unpackY(int norm) {
        return ((byte) ((norm >> 8) & 0xFF)) * NORM;
    }

    /**
     * Unpacks the z-component of the packed normal, denormalizing it to a float in the range of -1.0..1.0.
     * @param norm The packed normal
     */
    public static float unpackZ(int norm) {
        return ((byte) ((norm >> 16) & 0xFF)) * NORM;
    }
}
