package net.caffeinemc.mods.sodium.client.render.chunk.tree;

public class TraversableBiForest extends AbstractTraversableBiForest<TraversableTree> {
    public TraversableBiForest(int baseOffsetX, int baseOffsetY, int baseOffsetZ, float buildDistance) {
        super(baseOffsetX, baseOffsetY, baseOffsetZ, buildDistance);
    }

    @Override
    protected TraversableTree makeTree(int offsetX, int offsetY, int offsetZ) {
        return new TraversableTree(offsetX, offsetY, offsetZ);
    }
}
