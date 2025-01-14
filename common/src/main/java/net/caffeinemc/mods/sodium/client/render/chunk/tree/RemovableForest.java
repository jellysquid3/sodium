package net.caffeinemc.mods.sodium.client.render.chunk.tree;

public interface RemovableForest extends TraversableForest {
    void remove(int x, int y, int z);
}
