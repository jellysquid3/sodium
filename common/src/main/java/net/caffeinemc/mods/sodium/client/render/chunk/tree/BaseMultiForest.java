package net.caffeinemc.mods.sodium.client.render.chunk.tree;

public abstract class BaseMultiForest<T extends Tree> extends BaseForest<T> {
    protected final T[] trees;
    protected final int forestDim;

    protected T lastTree;

    public BaseMultiForest(int baseOffsetX, int baseOffsetY, int baseOffsetZ, float buildDistance) {
        super(baseOffsetX, baseOffsetY, baseOffsetZ, buildDistance);

        this.forestDim = forestDimFromBuildDistance(buildDistance);
        this.trees = this.makeTrees(this.forestDim * this.forestDim * this.forestDim);
    }

    public static int forestDimFromBuildDistance(float buildDistance) {
        // / 16 (block to chunk) * 2 (radius to diameter) + 1 (center chunk) / 64 (chunks per tree)
        return (int) Math.ceil((buildDistance / 8.0 + 1) / 64.0);
    }

    protected int getTreeIndex(int localX, int localY, int localZ) {
        var treeX = localX >> 6;
        var treeY = localY >> 6;
        var treeZ = localZ >> 6;

        if (treeX < 0 || treeX >= this.forestDim ||
                treeY < 0 || treeY >= this.forestDim ||
                treeZ < 0 || treeZ >= this.forestDim) {
            return Tree.OUT_OF_BOUNDS;
        }

        return treeX + (treeZ * this.forestDim + treeY) * this.forestDim;
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
        if (treeIndex == Tree.OUT_OF_BOUNDS) {
            return;
        }

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
            if (result != Tree.OUT_OF_BOUNDS) {
                return result;
            }
        }

        var localX = x - this.baseOffsetX;
        var localY = y - this.baseOffsetY;
        var localZ = z - this.baseOffsetZ;

        var treeIndex = this.getTreeIndex(localX, localY, localZ);
        if (treeIndex == Tree.OUT_OF_BOUNDS) {
            return Tree.OUT_OF_BOUNDS;
        }

        var tree = this.trees[treeIndex];
        if (tree != null) {
            this.lastTree = tree;
            return tree.getPresence(x, y, z);
        }
        return Tree.OUT_OF_BOUNDS;
    }

    protected abstract T[] makeTrees(int length);
}
