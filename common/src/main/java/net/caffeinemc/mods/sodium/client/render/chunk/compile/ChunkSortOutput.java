package net.caffeinemc.mods.sodium.client.render.chunk.compile;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.DynamicTopoData;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.Sorter;

public class ChunkSortOutput extends BuilderTaskOutput {
    private Sorter sorter;
    private boolean reuseUploadedIndexData;

    public ChunkSortOutput(RenderSection render, int buildTime) {
        super(render, buildTime);
    }

    public ChunkSortOutput(RenderSection render, int buildTime, Sorter data) {
        this(render, buildTime);
        this.setSorter(data);
    }

    public void setSorter(Sorter sorter) {
        this.sorter = sorter;
        this.reuseUploadedIndexData = false;
    }

    public Sorter getSorter() {
        return this.sorter;
    }

    public void markAsReusingUploadedData() {
        this.reuseUploadedIndexData = true;
    }

    public boolean isReusingUploadedIndexData() {
        return this.reuseUploadedIndexData;
    }

    public DynamicTopoData.DynamicTopoSorter getDynamicSorter() {
        return this.sorter instanceof DynamicTopoData.DynamicTopoSorter dynamicSorter ? dynamicSorter : null;
    }

    public void destroy() {
        super.destroy();
        if (this.sorter != null) {
            this.sorter.destroy();
        }
    }
}
