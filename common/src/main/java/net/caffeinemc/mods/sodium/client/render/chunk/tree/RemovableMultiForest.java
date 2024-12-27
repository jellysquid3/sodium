package net.caffeinemc.mods.sodium.client.render.chunk.tree;

import it.unimi.dsi.fastutil.longs.Long2ReferenceLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.SectionTree;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.core.SectionPos;

import java.util.Comparator;

public class RemovableMultiForest implements RemovableForest {
    private final Long2ReferenceLinkedOpenHashMap<RemovableTree> trees;
    private final ReferenceArrayList<RemovableTree> treeSortList = new ReferenceArrayList<>();
    private RemovableTree lastTree;

    // the removable tree separately tracks if it needs to prepared for traversal because it's not just built once, prepared, and then traversed. Since it can receive updates, it needs to be prepared for traversal again and to avoid unnecessary preparation, it tracks whether it's ready.
    private boolean treesAreReady = true;

    public RemovableMultiForest(float buildDistance) {
        this.trees = new Long2ReferenceLinkedOpenHashMap<>(getCapacity(buildDistance));
    }

    private static int getCapacity(float buildDistance) {
        var forestDim = BaseMultiForest.forestDimFromBuildDistance(buildDistance) + 1;
        return forestDim * forestDim * forestDim;
    }

    public void ensureCapacity(float buildDistance) {
        this.trees.ensureCapacity(getCapacity(buildDistance));
    }

    @Override
    public void prepareForTraversal() {
        if (this.treesAreReady) {
            return;
        }

        for (var tree : this.trees.values()) {
            tree.prepareForTraversal();
            if (tree.isEmpty()) {
                this.trees.remove(tree.getTreeKey());
                if (this.lastTree == tree) {
                    this.lastTree = null;
                }
            }
        }

        this.treesAreReady = true;
    }

    @Override
    public void traverse(SectionTree.VisibleSectionVisitor visitor, Viewport viewport, float distanceLimit) {
        var transform = viewport.getTransform();
        var cameraSectionX = transform.intX >> 4;
        var cameraSectionY = transform.intY >> 4;
        var cameraSectionZ = transform.intZ >> 4;

        // sort the trees by distance from the camera by sorting a packed index array.
        this.treeSortList.clear();
        this.treeSortList.ensureCapacity(this.trees.size());
        this.treeSortList.addAll(this.trees.values());
        for (var tree : this.treeSortList) {
            tree.updateSortKeyFor(cameraSectionX, cameraSectionY, cameraSectionZ);
        }

        this.treeSortList.unstableSort(Comparator.comparingInt(RemovableTree::getSortKey));

        // traverse in sorted front-to-back order for correct render order
        for (var tree : this.treeSortList) {
            // disable distance test in traversal because we don't use it here
            tree.traverse(visitor, viewport, 0, 0);
        }
    }

    @Override
    public void add(int x, int y, int z) {
        this.treesAreReady = false;

        if (this.lastTree != null && this.lastTree.add(x, y, z)) {
            return;
        }

        // get the tree coordinate by dividing by 64
        var treeX = x >> 6;
        var treeY = y >> 6;
        var treeZ = z >> 6;

        var treeKey = SectionPos.asLong(treeX, treeY, treeZ);
        var tree = this.trees.get(treeKey);

        if (tree == null) {
            var treeOffsetX = treeX << 6;
            var treeOffsetY = treeY << 6;
            var treeOffsetZ = treeZ << 6;
            tree = new RemovableTree(treeOffsetX, treeOffsetY, treeOffsetZ);
            this.trees.put(treeKey, tree);
        }

        tree.add(x, y, z);
        this.lastTree = tree;
    }

    public void remove(int x, int y, int z) {
        this.treesAreReady = false;

        if (this.lastTree != null && this.lastTree.remove(x, y, z)) {
            return;
        }

        // get the tree coordinate by dividing by 64
        var treeX = x >> 6;
        var treeY = y >> 6;
        var treeZ = z >> 6;

        var treeKey = SectionPos.asLong(treeX, treeY, treeZ);
        var tree = this.trees.get(treeKey);

        if (tree == null) {
            return;
        }

        tree.remove(x, y, z);

        this.lastTree = tree;
    }

    public void remove(RenderSection section) {
        this.remove(section.getChunkX(), section.getChunkY(), section.getChunkZ());
    }

    @Override
    public int getPresence(int x, int y, int z) {
        // unused operation on removable trees
        throw new UnsupportedOperationException("Not implemented");
    }
}