package net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data;

abstract class DynamicSorter extends PresentSorter {
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
}
