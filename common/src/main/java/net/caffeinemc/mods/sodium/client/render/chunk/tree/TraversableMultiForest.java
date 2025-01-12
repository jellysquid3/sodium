package net.caffeinemc.mods.sodium.client.render.chunk.tree;

public class TraversableMultiForest extends AbstractTraversableMultiForest<TraversableTree> {
    public TraversableMultiForest(int baseOffsetX, int baseOffsetY, int baseOffsetZ, float buildDistance) {
        super(baseOffsetX, baseOffsetY, baseOffsetZ, buildDistance);
    }

    @Override
    protected TraversableTree makeTree(int offsetX, int offsetY, int offsetZ) {
        return new TraversableTree(offsetX, offsetY, offsetZ);
    }

    @Override
    protected TraversableTree[] makeTrees(int length) {
        return new TraversableTree[length];
    }
}
