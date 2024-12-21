package net.caffeinemc.mods.sodium.client.render.chunk.tree;

import it.unimi.dsi.fastutil.ints.IntArrays;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.SectionTree;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;

public abstract class AbstractTraversableMultiForest<T extends TraversableTree> extends BaseMultiForest<T> implements TraversableForest {
    public AbstractTraversableMultiForest(int baseOffsetX, int baseOffsetY, int baseOffsetZ, float buildDistance) {
        super(baseOffsetX, baseOffsetY, baseOffsetZ, buildDistance);
    }

    @Override
    public void prepareForTraversal() {
        for (var tree : this.trees) {
            if (tree != null) {
                tree.prepareForTraversal();
            }
        }
    }

    @Override
    public void traverse(SectionTree.VisibleSectionVisitor visitor, Viewport viewport, float distanceLimit) {
        var cameraPos = viewport.getChunkCoord();
        var cameraSectionX = cameraPos.getX();
        var cameraSectionY = cameraPos.getY();
        var cameraSectionZ = cameraPos.getZ();

        // sort the trees by distance from the camera by sorting a packed index array.
        var items = new int[this.trees.length];
        for (int i = 0; i < this.trees.length; i++) {
            var tree = this.trees[i];
            if (tree != null) {
                var deltaX = Math.abs(tree.offsetX + 32 - cameraSectionX);
                var deltaY = Math.abs(tree.offsetY + 32 - cameraSectionY);
                var deltaZ = Math.abs(tree.offsetZ + 32 - cameraSectionZ);
                items[i] = (deltaX + deltaY + deltaZ + 1) << 16 | i;
            }
        }

        IntArrays.unstableSort(items);

        // traverse in sorted front-to-back order for correct render order
        for (var item : items) {
            if (item == 0) {
                continue;
            }
            var tree = this.trees[item & 0xFFFF];
            if (tree != null) {
                tree.traverse(visitor, viewport, distanceLimit, this.buildDistance);
            }
        }
    }
}
