package net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data;

import net.caffeinemc.mods.sodium.client.util.NativeBuffer;

import java.nio.IntBuffer;

public record SharedIndexSorter(int quadCount) implements Sorter {
    @Override
    public NativeBuffer getIndexBuffer() {
        return null;
    }

    @Override
    public IntBuffer getIntBuffer() {
        return null;
    }

    @Override
    public void writeIndexBuffer(CombinedCameraPos cameraPos, boolean initial) {
        // no-op
    }

    @Override
    public void destroy() {
        // no-op
    }
}
