package net.caffeinemc.mods.sodium.client.render.chunk.tree;

import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.SectionTree;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;

public abstract class AbstractTraversableBiForest<T extends TraversableTree> extends BaseBiForest<T> implements TraversableForest {
    public AbstractTraversableBiForest(int baseOffsetX, int baseOffsetY, int baseOffsetZ, float buildDistance) {
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
        // no sorting is necessary because we assume the camera will never be closer to the secondary tree than the main tree
        this.mainTree.traverse(visitor, viewport, distanceLimit, this.buildDistance);
        if (this.secondaryTree != null) {
            this.secondaryTree.traverse(visitor, viewport, distanceLimit, this.buildDistance);
        }
    }
}
