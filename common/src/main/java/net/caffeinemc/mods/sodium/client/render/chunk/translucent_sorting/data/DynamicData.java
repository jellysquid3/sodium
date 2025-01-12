package net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data;

import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.SortType;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.trigger.GeometryPlanes;
import net.minecraft.core.SectionPos;
import org.joml.Vector3dc;

public abstract class DynamicData extends MixedDirectionData {
    private GeometryPlanes geometryPlanes;
    private final Vector3dc initialCameraPos;

    DynamicData(SectionPos sectionPos, int vertexCount, int quadCount, GeometryPlanes geometryPlanes, Vector3dc initialCameraPos) {
        super(sectionPos, vertexCount, quadCount);
        this.geometryPlanes = geometryPlanes;
        this.initialCameraPos = initialCameraPos;
    }

    @Override
    public SortType getSortType() {
        return SortType.DYNAMIC;
    }

    public abstract DynamicSorter getSorter();

    public GeometryPlanes getGeometryPlanes() {
        return this.geometryPlanes;
    }

    public void discardGeometryPlanes() {
        this.geometryPlanes = null;
    }

    public Vector3dc getInitialCameraPos() {
        return this.initialCameraPos;
    }
}
