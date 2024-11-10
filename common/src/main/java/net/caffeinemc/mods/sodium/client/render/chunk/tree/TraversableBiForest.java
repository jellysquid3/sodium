package net.caffeinemc.mods.sodium.client.render.chunk.tree;

import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.SectionTree;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;

public class TraversableBiForest extends BaseBiForest<TraversableTree> implements TraversableForest {
    public TraversableBiForest(int baseOffsetX, int baseOffsetY, int baseOffsetZ, float buildDistance) {
        super(baseOffsetX, baseOffsetY, baseOffsetZ, buildDistance);
    }

    @Override
    public void calculateReduced() {
        this.mainTree.calculateReduced();
        if (this.secondaryTree != null) {
            this.secondaryTree.calculateReduced();
        }
    }

    @Override
    public void traverse(SectionTree.VisibleSectionVisitor visitor, Viewport viewport, float distanceLimit) {
        TraversableForest.super.traverse(visitor, viewport, distanceLimit);
    }

    @Override
    public void traverse(SectionTree.VisibleSectionVisitor visitor, Viewport viewport, float distanceLimit, float buildDistance) {
        // no sorting is necessary because we assume the camera will never be closer to the secondary tree than the main tree
        this.mainTree.traverse(visitor, viewport, distanceLimit, buildDistance);
        if (this.secondaryTree != null) {
            this.secondaryTree.traverse(visitor, viewport, distanceLimit, buildDistance);
        }
    }

    @Override
    protected TraversableTree makeTree(int offsetX, int offsetY, int offsetZ) {
        return new TraversableTree(offsetX, offsetY, offsetZ);
    }
}
