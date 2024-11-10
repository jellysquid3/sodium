package net.caffeinemc.mods.sodium.client.render.chunk.tree;

public abstract class BaseForest<T extends Tree> implements Forest {
    protected final int baseOffsetX, baseOffsetY, baseOffsetZ;
    final float buildDistance;

    protected BaseForest(int baseOffsetX, int baseOffsetY, int baseOffsetZ, float buildDistance) {
        this.baseOffsetX = baseOffsetX;
        this.baseOffsetY = baseOffsetY;
        this.baseOffsetZ = baseOffsetZ;
        this.buildDistance = buildDistance;
    }

    @Override
    public float getBuildDistance() {
        return this.buildDistance;
    }

    protected abstract T makeTree(int offsetX, int offsetY, int offsetZ);
}
