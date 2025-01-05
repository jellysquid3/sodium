package net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data;

public interface Sorter extends PresentSortData {
    void writeIndexBuffer(CombinedCameraPos cameraPos, boolean initial);

    void destroy();
}
