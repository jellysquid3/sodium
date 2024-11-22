package net.caffeinemc.mods.sodium.client.render.chunk.compile.estimation;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;

public class MeshTaskSizeEstimator extends CategoryFactorEstimator<MeshResultSize.SectionCategory> {
    public static final float NEW_DATA_FACTOR = 0.02f;

    public MeshTaskSizeEstimator() {
        super(NEW_DATA_FACTOR, RenderRegion.SECTION_BUFFER_ESTIMATE);
    }

    public long estimateSize(RenderSection section) {
        var lastResultSize = section.getLastMeshResultSize();
        if (lastResultSize != MeshResultSize.NO_DATA) {
            return lastResultSize;
        }
        return this.estimateAWithB(MeshResultSize.SectionCategory.forSection(section), 1);
    }

    @Override
    public void flushNewData() {
        super.flushNewData();
    }
}
