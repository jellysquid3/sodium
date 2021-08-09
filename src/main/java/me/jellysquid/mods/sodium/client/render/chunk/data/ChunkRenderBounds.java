package me.jellysquid.mods.sodium.client.render.chunk.data;

import net.minecraft.core.SectionPos;

public class ChunkRenderBounds {
    public static final ChunkRenderBounds ALWAYS_FALSE = new ChunkRenderBounds(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);

    public final float x1, y1, z1;
    public final float x2, y2, z2;

    public ChunkRenderBounds(float x1, float y1, float z1, float x2, float y2, float z2) {
        this.x1 = x1;
        this.y1 = y1;
        this.z1 = z1;

        this.x2 = x2;
        this.y2 = y2;
        this.z2 = z2;
    }

    public ChunkRenderBounds(SectionPos origin) {
        this.x1 = origin.minBlockX();
        this.y1 = origin.minBlockY();
        this.z1 = origin.minBlockZ();

        this.x2 = origin.maxBlockX() + 1;
        this.y2 = origin.maxBlockY() + 1;
        this.z2 = origin.maxBlockZ() + 1;
    }

    public static class Builder {
        // Bit-mask of the blocks set on each axis
        private int x = 0, y = 0, z = 0;

        public void addBlock(int x, int y, int z) {
            // Accumulate bits on each axis for the given block position. This avoids needing to
            // branch multiple times trying to determine if the bounds need to grow. The min/max
            // value of each axis can later be extracted by counting the trailing/leading zeroes.
            this.x |= 1 << x;
            this.y |= 1 << y;
            this.z |= 1 << z;
        }

        public ChunkRenderBounds build(SectionPos origin) {
            // If no bits were set on any axis, return the default bounds
            if ((this.x | this.y | this.z) == 0) {
                return new ChunkRenderBounds(origin);
            }

            int x1 = origin.minBlockX() + leftBound(this.x);
            int x2 = origin.minBlockX() + rightBound(this.x);

            int y1 = origin.minBlockY() + leftBound(this.y);
            int y2 = origin.minBlockY() + rightBound(this.y);

            int z1 = origin.minBlockZ() + leftBound(this.z);
            int z2 = origin.minBlockZ() + rightBound(this.z);

            return new ChunkRenderBounds(
                    Math.max(x1, origin.minBlockX()) - 0.5f,
                    Math.max(y1, origin.minBlockY()) - 0.5f,
                    Math.max(z1, origin.minBlockZ()) - 0.5f,

                    Math.min(x2, origin.maxBlockX()) + 0.5f,
                    Math.min(y2, origin.maxBlockY()) + 0.5f,
                    Math.min(z2, origin.maxBlockZ()) + 0.5f
            );
        }

        // Return the left-bound of the bit-masked axis
        private static int leftBound(int i) {
            return Integer.numberOfTrailingZeros(i);
        }

        // Return the right-bound of the bit-masked axis
        private static int rightBound(int i) {
            return 32 - Integer.numberOfLeadingZeros(i);
        }
    }
}
