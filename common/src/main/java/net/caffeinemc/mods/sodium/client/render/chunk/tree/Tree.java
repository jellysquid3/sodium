package net.caffeinemc.mods.sodium.client.render.chunk.tree;

public abstract class Tree {
    public static final int OUT_OF_BOUNDS = 0;
    public static final int NOT_PRESENT = 1;
    public static final int PRESENT = 2;

    protected final long[] tree = new long[64 * 64];
    protected final int offsetX, offsetY, offsetZ;

    public Tree(int offsetX, int offsetY, int offsetZ) {
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
    }

    public static boolean isOutOfBounds(int x, int y, int z) {
        return x > 63 || y > 63 || z > 63 || x < 0 || y < 0 || z < 0;
    }

    protected static int interleave6x3(int x, int y, int z) {
        return Tree.interleave6(x) | Tree.interleave6(y) << 1 | Tree.interleave6(z) << 2;
    }

    private static int interleave6(int n) {
        n &= 0b000000000000111111;
        n = (n | n << 4 | n << 8) & 0b000011000011000011;
        n = (n | n << 2) & 0b001001001001001001;
        return n;
    }

    protected static int deinterleave6(int n) {
        n &= 0b001001001001001001;
        n = (n | n >> 2) & 0b000011000011000011;
        n = (n | n >> 4 | n >> 8) & 0b000000000000111111;
        return n;
    }

    public boolean add(int x, int y, int z) {
        x -= this.offsetX;
        y -= this.offsetY;
        z -= this.offsetZ;
        if (Tree.isOutOfBounds(x, y, z)) {
            return false;
        }

        var bitIndex = Tree.interleave6x3(x, y, z);
        this.tree[bitIndex >> 6] |= 1L << (bitIndex & 0b111111);

        return true;
    }

    public abstract int getPresence(int x, int y, int z);
}
