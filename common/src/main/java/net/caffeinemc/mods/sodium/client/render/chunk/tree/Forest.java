package net.caffeinemc.mods.sodium.client.render.chunk.tree;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;

public interface Forest {
    void add(int x, int y, int z);

    default void add(RenderSection section) {
        add(section.getChunkX(), section.getChunkY(), section.getChunkZ());
    }

    int getPresence(int x, int y, int z);
}
