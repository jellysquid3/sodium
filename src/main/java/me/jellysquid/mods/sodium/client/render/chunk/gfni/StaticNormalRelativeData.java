package me.jellysquid.mods.sodium.client.render.chunk.gfni;

import java.nio.IntBuffer;

import org.joml.Vector3f;

import com.mojang.blaze3d.systems.VertexSorter;

import me.jellysquid.mods.sodium.client.gl.util.VertexRange;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.render.chunk.data.BuiltSectionMeshParts;
import me.jellysquid.mods.sodium.client.render.chunk.gfni.GroupBuilder.Quad;
import me.jellysquid.mods.sodium.client.util.NativeBuffer;
import me.jellysquid.mods.sodium.client.util.sorting.MergeSort;
import me.jellysquid.mods.sodium.client.util.sorting.VertexSorters;
import net.minecraft.util.math.ChunkSectionPos;

public class StaticNormalRelativeData extends PresentTranslucentData {
    public final VertexRange[] ranges;

    public StaticNormalRelativeData(ChunkSectionPos sectionPos, NativeBuffer buffer, VertexRange[] ranges) {
        super(sectionPos, buffer);
        this.ranges = ranges;
    }

    @Override
    public SortType getSortType() {
        return SortType.STATIC_NORMAL_RELATIVE;
    }

    /**
     * The vertex sorter for each direction.
     * TODO: is there a better place to put this
     */
    private static final VertexSorter[] SORTERS = new VertexSorter[ModelQuadFacing.DIRECTIONS];

    static {
        for (int i = 0; i < ModelQuadFacing.DIRECTIONS; i++) {
            SORTERS[i] = VertexSorters.sortByAxis(ModelQuadFacing.VALUES[i]);
        }
    }

    private static StaticNormalRelativeData fromDoubleUnaligned(BuiltSectionMeshParts translucentMesh,
            Quad[] quads, ChunkSectionPos sectionPos, GroupBuilder groupBuilder) {
        VertexRange[] ranges = translucentMesh.getVertexRanges();
        float[] keys = new float[quads.length];

        for (int i = 0; i < quads.length; i++) {
            Quad quad = quads[i];
            keys[i] = quad.center().dot(quad.normal());
        }

        var buffer = new NativeBuffer(TranslucentData.vertexCountToIndexBytes(
                ranges[ModelQuadFacing.UNASSIGNED.ordinal()].vertexCount()));
        IntBuffer bufferBuilder = buffer.getDirectBuffer().asIntBuffer();

        TranslucentData.writeVertexIndexes(bufferBuilder, MergeSort.mergeSort(keys));

        return new StaticNormalRelativeData(sectionPos, buffer, ranges);
    }

    private static StaticNormalRelativeData fromAligned(BuiltSectionMeshParts translucentMesh,
            Quad[] quads, ChunkSectionPos sectionPos) {
        VertexRange[] ranges = translucentMesh.getVertexRanges();
        Vector3f[][] centers = new Vector3f[ModelQuadFacing.COUNT][];

        for (int i = 0; i < ModelQuadFacing.COUNT; i++) {
            VertexRange range = ranges[i];
            if (range != null) {
                centers[i] = new Vector3f[range.vertexCount() / TranslucentData.VERTICES_PER_QUAD];
            }
        }

        int centerCounter = 0;
        for (Quad quad : quads) {
            var directionCenters = centers[quad.facing().ordinal()];
            directionCenters[centerCounter++] = quad.center();

            if (centerCounter == directionCenters.length) {
                centerCounter = 0;
            }
        }

        var buffer = new NativeBuffer(
                TranslucentData.vertexCountToIndexBytes(quads.length * TranslucentData.VERTICES_PER_QUAD));
        IntBuffer bufferBuilder = buffer.getDirectBuffer().asIntBuffer();

        // in this case there can only be up to one unaligned normal.
        // since the quads are sorted by facing, it will be at the end if it exists
        Vector3f unalignedNormal = new Vector3f(quads[quads.length - 1].normal());

        for (int i = 0; i < ModelQuadFacing.COUNT; i++) {
            if (ranges[i] != null) {
                VertexSorter sorter;
                if (i == ModelQuadFacing.UNASSIGNED.ordinal()) {
                    sorter = VertexSorters.sortByNormalRelative(unalignedNormal);
                } else {
                    sorter = SORTERS[i];
                }
                TranslucentData.writeVertexIndexes(bufferBuilder, sorter.sort(centers[i]));
            }
        }

        return new StaticNormalRelativeData(sectionPos, buffer, ranges);
    }

    static StaticNormalRelativeData fromMesh(BuiltSectionMeshParts translucentMesh,
            Quad[] quads, ChunkSectionPos sectionPos, GroupBuilder groupBuilder) {
        if (groupBuilder.alignedNormalBitmap == 0) {
            return fromDoubleUnaligned(translucentMesh, quads, sectionPos, groupBuilder);
        } else {
            return fromAligned(translucentMesh, quads, sectionPos);
        }
    }
}
