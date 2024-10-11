package net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data;

public abstract class DynamicSorter extends Sorter {
    private final int quadCount;

    DynamicSorter(int quadCount) {
        this.quadCount = quadCount;
    }

    abstract void writeSort(CombinedCameraPos cameraPos, boolean initial);

    @Override
    public void writeIndexBuffer(CombinedCameraPos cameraPos, boolean initial) {
        this.initBufferWithQuadLength(this.quadCount);
        this.writeSort(cameraPos, initial);
    }

    public int getQuadCount() {
        return this.quadCount;
    }
}
