package net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data;

import net.caffeinemc.mods.sodium.client.util.NativeBuffer;

public abstract class PresentSorter implements Sorter {
    private NativeBuffer indexBuffer;

    @Override
    public NativeBuffer getIndexBuffer() {
        return this.indexBuffer;
    }

    void initBufferWithQuadLength(int quadCount) {
        this.indexBuffer = new NativeBuffer(TranslucentData.quadCountToIndexBytes(quadCount));
    }

    @Override
    public void destroy() {
        if (this.indexBuffer != null) {
            this.indexBuffer.free();
        }
    }
}
