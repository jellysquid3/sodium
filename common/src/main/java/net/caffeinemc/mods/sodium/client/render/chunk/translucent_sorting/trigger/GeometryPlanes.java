package net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.trigger;

import java.util.Collection;

import org.joml.Vector3fc;

import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TQuad;
import net.minecraft.core.SectionPos;

/**
 * GeometryPlanes stores the NormalPlanes for different normals, both aligned
 * and unaligned.
 */
public class GeometryPlanes {
    private NormalPlanes[] alignedPlanes;
    private Object2ReferenceOpenHashMap<Vector3fc, NormalPlanes> unalignedPlanes;

    public NormalPlanes[] getAligned() {
        return this.alignedPlanes;
    }

    public NormalPlanes[] getAlignedOrCreate() {
        if (this.alignedPlanes == null) {
            this.alignedPlanes = new NormalPlanes[ModelQuadFacing.DIRECTIONS];
        }
        return this.alignedPlanes;
    }

    public Collection<NormalPlanes> getUnaligned() {
        if (this.unalignedPlanes == null) {
            return null;
        }
        return this.unalignedPlanes.values();
    }

    public Object2ReferenceOpenHashMap<Vector3fc, NormalPlanes> getUnalignedOrCreate() {
        if (this.unalignedPlanes == null) {
            this.unalignedPlanes = new Object2ReferenceOpenHashMap<>();
        }
        return this.unalignedPlanes;
    }

    public Collection<Vector3fc> getUnalignedNormals() {
        if (this.unalignedPlanes == null) {
            return null;
        }
        return this.unalignedPlanes.keySet();
    }

    NormalPlanes getPlanesForNormal(NormalList normalList) {
        var normal = normalList.getNormal();
        if (normal.isAligned()) {
            if (this.alignedPlanes == null) {
                return null;
            }
            return this.alignedPlanes[normal.getAlignedDirection()];
        } else {
            if (this.unalignedPlanes == null) {
                return null;
            }
            return this.unalignedPlanes.get(normal);
        }
    }

    public void addAlignedPlane(SectionPos sectionPos, int direction, float distance) {
        var alignedDistances = this.getAlignedOrCreate();
        var normalPlanes = alignedDistances[direction];
        if (normalPlanes == null) {
            normalPlanes = new NormalPlanes(sectionPos, direction);
            alignedDistances[direction] = normalPlanes;
        }
        normalPlanes.addPlaneMember(distance);
    }

    public void addDoubleSidedPlane(SectionPos sectionPos, int axis, float distance) {
        this.addAlignedPlane(sectionPos, axis, distance);
        this.addAlignedPlane(sectionPos, axis + 3, -distance);
    }

    public void addUnalignedPlane(SectionPos sectionPos, Vector3fc normal, float distance) {
        var unalignedDistances = this.getUnalignedOrCreate();
        var normalPlanes = unalignedDistances.get(normal);
        if (normalPlanes == null) {
            normalPlanes = new NormalPlanes(sectionPos, normal);
            unalignedDistances.put(normal, normalPlanes);
        }
        normalPlanes.addPlaneMember(distance);
    }

    public void addQuadPlane(SectionPos sectionPos, TQuad quad) {
        var facing = quad.useQuantizedFacing();
        if (facing.isAligned()) {
            this.addAlignedPlane(sectionPos, facing.ordinal(), quad.getQuantizedDotProduct());
        } else {
            this.addUnalignedPlane(sectionPos, quad.getQuantizedNormal(), quad.getQuantizedDotProduct());
        }
    }

    private void prepareAndInsert(Object2ReferenceOpenHashMap<Vector3fc, float[]> distancesByNormal) {
        if (this.alignedPlanes != null) {
            for (var normalPlanes : this.alignedPlanes) {
                if (normalPlanes != null) {
                    normalPlanes.prepareAndInsert(distancesByNormal);
                }
            }
        }
        if (this.unalignedPlanes != null) {
            for (var normalPlanes : this.unalignedPlanes.values()) {
                normalPlanes.prepareAndInsert(distancesByNormal);
            }
        }
    }

    public void prepareIntegration() {
        this.prepareAndInsert(null);
    }

    public Object2ReferenceOpenHashMap<Vector3fc, float[]> prepareAndGetDistances() {
        var distancesByNormal = new Object2ReferenceOpenHashMap<Vector3fc, float[]>(10);
        this.prepareAndInsert(distancesByNormal);
        return distancesByNormal;
    }

    public static GeometryPlanes fromQuadLists(SectionPos sectionPos, TQuad[] quads) {
        var geometryPlanes = new GeometryPlanes();
        for (var quad : quads) {
            geometryPlanes.addQuadPlane(sectionPos, quad);
        }
        return geometryPlanes;
    }
}
