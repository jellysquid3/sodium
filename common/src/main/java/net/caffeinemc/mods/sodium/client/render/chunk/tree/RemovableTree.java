package net.caffeinemc.mods.sodium.client.render.chunk.tree;

import net.minecraft.core.SectionPos;

public class RemovableTree extends TraversableTree {
    private boolean reducedIsValid = true;
    private int sortKey;

    public RemovableTree(int offsetX, int offsetY, int offsetZ) {
        super(offsetX, offsetY, offsetZ);
    }

    public boolean remove(int x, int y, int z) {
        x -= this.offsetX;
        y -= this.offsetY;
        z -= this.offsetZ;
        if (Tree.isOutOfBounds(x, y, z)) {
            return false;
        }

        var bitIndex = Tree.interleave6x3(x, y, z);
        this.tree[bitIndex >> 6] &= ~(1L << (bitIndex & 0b111111));

        this.reducedIsValid = false;

        return true;
    }

    @Override
    public void prepareForTraversal() {
        if (!this.reducedIsValid) {
            super.prepareForTraversal();
            this.reducedIsValid = true;
        }
    }

    @Override
    public boolean add(int x, int y, int z) {
        var result = super.add(x, y, z);
        if (result) {
            this.reducedIsValid = false;
        }
        return result;
    }

    public boolean isEmpty() {
        return this.treeDoubleReduced == 0;
    }

    public long getTreeKey() {
        return SectionPos.asLong(this.offsetX, this.offsetY, this.offsetZ);
    }

    public void updateSortKeyFor(int cameraSectionX, int cameraSectionY, int cameraSectionZ) {
        var deltaX = Math.abs(this.offsetX + 32 - cameraSectionX);
        var deltaY = Math.abs(this.offsetY + 32 - cameraSectionY);
        var deltaZ = Math.abs(this.offsetZ + 32 - cameraSectionZ);
        this.sortKey = deltaX + deltaY + deltaZ + 1;
    }

    public int getSortKey() {
        return this.sortKey;
    }

    @Override
    public int getPresence(int i, int i1, int i2) {
        throw new UnsupportedOperationException("Not implemented");
    }
}