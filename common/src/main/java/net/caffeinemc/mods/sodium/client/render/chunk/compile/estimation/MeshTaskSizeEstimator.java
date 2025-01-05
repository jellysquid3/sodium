package net.caffeinemc.mods.sodium.client.render.chunk.compile.estimation;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;

import java.util.EnumMap;
import java.util.Map;

public class MeshTaskSizeEstimator extends Average1DEstimator<MeshResultSize.SectionCategory> {
    public static final float NEW_DATA_RATIO = 0.02f;

    public MeshTaskSizeEstimator() {
        super(NEW_DATA_RATIO, RenderRegion.SECTION_BUFFER_ESTIMATE);
    }

    public long estimateSize(RenderSection section) {
        var lastResultSize = section.getLastMeshResultSize();
        if (lastResultSize != MeshResultSize.NO_DATA) {
            return lastResultSize;
        }
        return this.predict(MeshResultSize.SectionCategory.forSection(section));
    }

    @Override
    protected <T> Map<MeshResultSize.SectionCategory, T> createMap() {
        return new EnumMap<>(MeshResultSize.SectionCategory.class);
    }
}
