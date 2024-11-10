package net.caffeinemc.mods.sodium.client.render.chunk.tree;

public abstract class BaseManyForest<T extends Tree> extends BaseForest<T> {
    protected final T[] trees;
    protected final int forestDim;

    protected T lastTree;

    public BaseManyForest(int baseOffsetX, int baseOffsetY, int baseOffsetZ, float buildDistance) {
        super(baseOffsetX, baseOffsetY, baseOffsetZ, buildDistance);

        this.forestDim = (int) Math.ceil(buildDistance / 64.0);
        this.trees = this.makeTrees(this.forestDim * this.forestDim * this.forestDim);
    }

    protected int getTreeIndex(int localX, int localY, int localZ) {
        var treeX = localX >> 6;
        var treeY = localY >> 6;
        var treeZ = localZ >> 6;

        return treeX + (treeZ * this.forestDim + treeY) * this.forestDim;
    }

    protected int getTreeIndexAbsolute(int x, int y, int z) {
        return this.getTreeIndex(x - this.baseOffsetX, y - this.baseOffsetY, z - this.baseOffsetZ);
    }

    @Override
    public void add(int x, int y, int z) {
        if (this.lastTree != null && this.lastTree.add(x, y, z)) {
            return;
        }

        var localX = x - this.baseOffsetX;
        var localY = y - this.baseOffsetY;
        var localZ = z - this.baseOffsetZ;

        var treeIndex = this.getTreeIndex(localX, localY, localZ);
        var tree = this.trees[treeIndex];

        if (tree == null) {
            var treeOffsetX = this.baseOffsetX + (localX & ~0b111111);
            var treeOffsetY = this.baseOffsetY + (localY & ~0b111111);
            var treeOffsetZ = this.baseOffsetZ + (localZ & ~0b111111);
            tree = this.makeTree(treeOffsetX, treeOffsetY, treeOffsetZ);
            this.trees[treeIndex] = tree;
        }

        tree.add(x, y, z);
        this.lastTree = tree;
    }

    @Override
    public int getPresence(int x, int y, int z) {
        if (this.lastTree != null) {
            var result = this.lastTree.getPresence(x, y, z);
            if (result != TraversableTree.OUT_OF_BOUNDS) {
                return result;
            }
        }

        var treeIndex = this.getTreeIndexAbsolute(x, y, z);
        var tree = this.trees[treeIndex];
        if (tree != null) {
            return tree.getPresence(x, y, z);
        }
        return TraversableTree.OUT_OF_BOUNDS;
    }

    protected abstract T[] makeTrees(int length);
}
