package net.caffeinemc.mods.sodium.client.render.chunk.data;

import net.caffeinemc.mods.sodium.client.gl.arena.GlBufferArena;
import net.caffeinemc.mods.sodium.client.gl.arena.GlBufferSegment;
import net.caffeinemc.mods.sodium.client.gl.arena.PendingUpload;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.render.chunk.SharedQuadIndexBuffer;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.util.UInt32;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * The section render data storage stores the gl buffer segments of uploaded
 * data on the gpu. There's one storage object per region. It stores information
 * about vertex and optionally index buffer data. The array of buffer segment is
 * indexed by the region-local section index. The data about the contents of
 * buffer segments is stored in a natively allocated piece of memory referenced
 * by {@code pMeshDataArray} and accessed through
 * {@link SectionRenderDataUnsafe}.
 * <p>
 * When the backing buffer (from the gl buffer arena) is resized, the storage
 * object is notified and then it updates the changed offsets of the buffer
 * segments. Since the index data's size and alignment directly corresponds to
 * that of the vertex data except for the vertex/index scaling of two thirds,
 * only an offset to the index data within the index data buffer arena is
 * stored.
 * <p>
 * Index and vertex data storage can be managed separately since they may be
 * updated independently of each other (in both directions).
 */
public class SectionRenderDataStorage {
    private final @Nullable GlBufferSegment[] vertexAllocations;
    private final @Nullable GlBufferSegment @Nullable [] elementAllocations;
    private @Nullable GlBufferSegment sharedIndexAllocation;
    private int sharedIndexCapacity = 0;
    private boolean needsSharedIndexUpdate = false;
    private final int[] sharedIndexUsage = new int[RenderRegion.REGION_SIZE];

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

    public void setVertexData(int localSectionIndex, GlBufferSegment allocation, int[] vertexSegments) {
        GlBufferSegment prev = this.vertexAllocations[localSectionIndex];

        if (prev != null) {
            prev.delete();
        }

        this.vertexAllocations[localSectionIndex] = allocation;

        var pMeshData = this.getDataPointer(localSectionIndex);

        int sliceMask = 0;
        long facingList = 0;

        for (int i = 0; i < ModelQuadFacing.COUNT; i++) {
            var segmentIndex = i << 1;

            int facing = vertexSegments[segmentIndex + 1];
            facingList |= (long) facing << (i * 8);

            long vertexCount = UInt32.upcast(vertexSegments[segmentIndex]);
            SectionRenderDataUnsafe.setVertexCount(pMeshData, i, vertexCount);

            if (vertexCount > 0) {
                sliceMask |= 1 << facing;
            }
        }

        SectionRenderDataUnsafe.setBaseVertex(pMeshData, allocation.getOffset());
        SectionRenderDataUnsafe.setSliceMask(pMeshData, sliceMask);
        SectionRenderDataUnsafe.setFacingList(pMeshData, facingList);
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

        SectionRenderDataUnsafe.setLocalBaseElement(pMeshData, allocation.getOffset());
    }

    public void setSharedIndexUsage(int localSectionIndex, int newUsage) {
        var previousUsage = this.sharedIndexUsage[localSectionIndex];
        if (previousUsage == newUsage) {
            return;
        }

        // mark for update if usage is down from max (may need to shrink buffer)
        // or if usage increased beyond the max (need to grow buffer)
        if (newUsage < previousUsage && previousUsage == this.sharedIndexCapacity ||
                newUsage > this.sharedIndexCapacity ||
                newUsage > 0 && this.sharedIndexAllocation == null) {
            this.needsSharedIndexUpdate = true;
        } else {
            // just set the base element since no update is happening
            var sharedBaseElement = this.sharedIndexAllocation.getOffset();
            var pMeshData = this.getDataPointer(localSectionIndex);
            SectionRenderDataUnsafe.setSharedBaseElement(pMeshData, sharedBaseElement);
        }

        this.sharedIndexUsage[localSectionIndex] = newUsage;
    }

    public boolean needsSharedIndexUpdate() {
        return this.needsSharedIndexUpdate;
    }

    /**
     * Updates the shared index data buffer to match the current usage.
     *
     * @param arena The buffer arena to allocate the new buffer from
     * @return true if the arena resized itself
     */
    public boolean updateSharedIndexData(CommandList commandList, GlBufferArena arena) {
        // assumes this.needsSharedIndexUpdate is true when this is called
        this.needsSharedIndexUpdate = false;

        // determine the new required capacity
        int newCapacity = 0;
        for (int i = 0; i < RenderRegion.REGION_SIZE; i++) {
            newCapacity = Math.max(newCapacity, this.sharedIndexUsage[i]);
        }
        if (newCapacity == this.sharedIndexCapacity) {
            return false;
        }

        this.sharedIndexCapacity = newCapacity;

        // remove the existing allocation and exit if we don't need to create a new one
        if (this.sharedIndexAllocation != null) {
            this.sharedIndexAllocation.delete();
            this.sharedIndexAllocation = null;
        }
        if (this.sharedIndexCapacity == 0) {
            return false;
        }

        // add some base-level capacity to avoid resizing the buffer too often
        if (this.sharedIndexCapacity < 128) {
            this.sharedIndexCapacity += 32;
        }

        // create and upload a new shared index buffer
        var buffer = SharedQuadIndexBuffer.createIndexBuffer(SharedQuadIndexBuffer.IndexType.INTEGER, this.sharedIndexCapacity);
        var pendingUpload = new PendingUpload(buffer);
        var bufferChanged = arena.upload(commandList, Stream.of(pendingUpload));
        this.sharedIndexAllocation = pendingUpload.getResult();
        buffer.free();

        // only write the base elements now if we're not going to do so again later because of the buffer resize
        if (!bufferChanged) {
            var sharedBaseElement = this.sharedIndexAllocation.getOffset();
            for (int i = 0; i < RenderRegion.REGION_SIZE; i++) {
                if (this.sharedIndexUsage[i] > 0) {
                    SectionRenderDataUnsafe.setSharedBaseElement(this.getDataPointer(i), sharedBaseElement);
                }
            }
        }

        return bufferChanged;
    }

    public void removeData(int localSectionIndex) {
        this.removeVertexData(localSectionIndex, false);

        if (this.elementAllocations != null) {
            this.removeIndexData(localSectionIndex);
        }

        this.setSharedIndexUsage(localSectionIndex, 0);
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
            SectionRenderDataUnsafe.setLocalBaseElement(pMeshData, baseElement);
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

        var data = this.getDataPointer(sectionIndex);
        long offset = allocation.getOffset();
        SectionRenderDataUnsafe.setBaseVertex(data, offset);
    }

    public void onIndexBufferResized() {
        long sharedBaseElement = 0;
        if (this.sharedIndexAllocation != null) {
            sharedBaseElement = this.sharedIndexAllocation.getOffset();
        }

        for (int i = 0; i < RenderRegion.REGION_SIZE; i++) {
            if (this.sharedIndexUsage[i] > 0) {
                // update index sharing sections to use the new shared index buffer's offset
                SectionRenderDataUnsafe.setSharedBaseElement(this.getDataPointer(i), sharedBaseElement);
            } else if (this.elementAllocations != null) {
                var allocation = this.elementAllocations[i];

                if (allocation != null) {
                    SectionRenderDataUnsafe.setLocalBaseElement(this.getDataPointer(i), allocation.getOffset());
                }
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

        if (this.sharedIndexAllocation != null) {
            this.sharedIndexAllocation.delete();
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
