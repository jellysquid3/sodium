package net.caffeinemc.mods.sodium.client.render.chunk.data;

import net.caffeinemc.mods.sodium.client.gl.arena.GlBufferSegment;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.util.UInt32;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * The section render data storage stores the gl buffer segments of uploaded
 * data on the gpu. There's one storage object per region. It stores information
 * about vertex and optionally index buffer data. The array of buffer segment is
 * indexed by the region-local section index. The data about the contents of
 * buffer segments is stored in a natively allocated piece of memory referenced
 * by {@code pMeshDataArray} and accessed through
 * {@link SectionRenderDataUnsafe}.
 * 
 * When the backing buffer (from the gl buffer arena) is resized, the storage
 * object is notified and then it updates the changed offsets of the buffer
 * segments. Since the index data's size and alignment directly corresponds to
 * that of the vertex data except for the vertex/index scaling of two thirds,
 * only an offset to the index data within the index data buffer arena is
 * stored.
 * 
 * Index and vertex data storage can be managed separately since they may be
 * updated independently of each other (in both directions).
 */
public class SectionRenderDataStorage {
    private final @Nullable GlBufferSegment[] vertexAllocations;
    private final @Nullable GlBufferSegment @Nullable[] elementAllocations;

    private final long pMeshDataArray;

    public SectionRenderDataStorage(boolean storesIndices) {
        this.vertexAllocations = new GlBufferSegment[RenderRegion.REGION_SIZE];

        if (storesIndices) {
            this.elementAllocations = new GlBufferSegment[RenderRegion.REGION_SIZE];
        } else {
            this.elementAllocations = null;
        }

        this.pMeshDataArray = SectionRenderDataUnsafe.allocateHeap(RenderRegion.REGION_SIZE);
    }

    public void setVertexData(int localSectionIndex,
            GlBufferSegment allocation, int[] vertexCounts) {
        GlBufferSegment prev = this.vertexAllocations[localSectionIndex];

        if (prev != null) {
            prev.delete();
        }

        this.vertexAllocations[localSectionIndex] = allocation;

        var pMeshData = this.getDataPointer(localSectionIndex);

        int sliceMask = 0;

        long vertexOffset = allocation.getOffset();

        for (int facingIndex = 0; facingIndex < ModelQuadFacing.COUNT; facingIndex++) {
            long vertexCount = vertexCounts[facingIndex];

            SectionRenderDataUnsafe.setVertexOffset(pMeshData, facingIndex,
                    UInt32.downcast(vertexOffset));
            SectionRenderDataUnsafe.setElementCount(pMeshData, facingIndex,
                    UInt32.downcast((vertexCount >> 2) * 6));

            if (vertexCount > 0) {
                sliceMask |= 1 << facingIndex;
            }

            vertexOffset += vertexCount;
        }

        SectionRenderDataUnsafe.setSliceMask(pMeshData, sliceMask);
    }

    public void setIndexData(int localSectionIndex, GlBufferSegment allocation) {
        if (this.elementAllocations == null) {
            throw new IllegalStateException("Cannot set index data when storesIndices is false");
        }

        GlBufferSegment prev = this.elementAllocations[localSectionIndex];

        if (prev != null) {
            prev.delete();
        }

        this.elementAllocations[localSectionIndex] = allocation;

        var pMeshData = this.getDataPointer(localSectionIndex);

        SectionRenderDataUnsafe.setBaseElement(pMeshData, allocation.getOffset());
    }

    public void removeData(int localSectionIndex) {
        this.removeVertexData(localSectionIndex, false);

        if (this.elementAllocations != null) {
            this.removeIndexData(localSectionIndex);
        }
    }

    public void removeVertexData(int localSectionIndex) {
        this.removeVertexData(localSectionIndex, true);
    }

    private void removeVertexData(int localSectionIndex, boolean retainIndexData) {
        GlBufferSegment prev = this.vertexAllocations[localSectionIndex];

        if (prev == null) {
            return;
        }

        prev.delete();

        this.vertexAllocations[localSectionIndex] = null;

        var pMeshData = this.getDataPointer(localSectionIndex);

        var baseElement = SectionRenderDataUnsafe.getBaseElement(pMeshData);
        SectionRenderDataUnsafe.clear(pMeshData);

        if (retainIndexData) {
            SectionRenderDataUnsafe.setBaseElement(pMeshData, baseElement);
        }
    }

    public void removeIndexData(int localSectionIndex) {
        final GlBufferSegment[] allocations = this.elementAllocations;

        if (allocations == null) {
            throw new IllegalStateException("Cannot remove index data when storesIndices is false");
        }

        GlBufferSegment prev = allocations[localSectionIndex];

        if (prev != null) {
            prev.delete();
        }

        allocations[localSectionIndex] = null;
    }

    public void onBufferResized() {
        for (int sectionIndex = 0; sectionIndex < RenderRegion.REGION_SIZE; sectionIndex++) {
            this.updateMeshes(sectionIndex);
        }
    }

    private void updateMeshes(int sectionIndex) {
        var allocation = this.vertexAllocations[sectionIndex];

        if (allocation == null) {
            return;
        }

        long offset = allocation.getOffset();
        var data = this.getDataPointer(sectionIndex);

        for (int facing = 0; facing < ModelQuadFacing.COUNT; facing++) {
            SectionRenderDataUnsafe.setVertexOffset(data, facing, offset);

            var count = SectionRenderDataUnsafe.getElementCount(data, facing);
            offset += (count / 6) * 4; // convert elements back into vertices
        }
    }

    public void onIndexBufferResized() {
        if (this.elementAllocations == null) {
            return;
        }

        for (int sectionIndex = 0; sectionIndex < RenderRegion.REGION_SIZE; sectionIndex++) {
            var allocation = this.elementAllocations[sectionIndex];

            if (allocation != null) {
                SectionRenderDataUnsafe.setBaseElement(this.getDataPointer(sectionIndex), allocation.getOffset());
            }
        }
    }

    public long getDataPointer(int sectionIndex) {
        return SectionRenderDataUnsafe.heapPointer(this.pMeshDataArray, sectionIndex);
    }

    public void delete() {
        deleteAllocations(this.vertexAllocations);

        if (this.elementAllocations != null) {
            deleteAllocations(this.elementAllocations);
        }

        SectionRenderDataUnsafe.freeHeap(this.pMeshDataArray);
    }

    private static void deleteAllocations(GlBufferSegment @NotNull [] allocations) {
        for (var allocation : allocations) {
            if (allocation != null) {
                allocation.delete();
            }
        }

        Arrays.fill(allocations, null);
    }
}
