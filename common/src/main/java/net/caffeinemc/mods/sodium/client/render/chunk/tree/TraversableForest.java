package net.caffeinemc.mods.sodium.client.render.chunk.tree;

import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.SectionTree;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.world.level.Level;

public interface TraversableForest extends Forest {
    void prepareForTraversal();

    void traverse(SectionTree.VisibleSectionVisitor visitor, Viewport viewport, float distanceLimit);

    static TraversableForest createTraversableForest(int baseOffsetX, int baseOffsetY, int baseOffsetZ, float buildDistance, Level level) {
        if (BaseBiForest.checkApplicable(buildDistance, level)) {
            return new TraversableBiForest(baseOffsetX, baseOffsetY, baseOffsetZ, buildDistance);
        }

        return new TraversableMultiForest(baseOffsetX, baseOffsetY, baseOffsetZ, buildDistance);
    }
}
