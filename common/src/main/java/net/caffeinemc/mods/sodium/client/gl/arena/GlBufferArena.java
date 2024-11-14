package net.caffeinemc.mods.sodium.client.gl.arena;

import net.caffeinemc.mods.sodium.client.gl.arena.staging.StagingBuffer;
import net.caffeinemc.mods.sodium.client.gl.buffer.GlBuffer;
import net.caffeinemc.mods.sodium.client.gl.buffer.GlBufferUsage;
import net.caffeinemc.mods.sodium.client.gl.buffer.GlMutableBuffer;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GlBufferArena {
    static final boolean CHECK_ASSERTIONS = false;

    private static final GlBufferUsage BUFFER_USAGE = GlBufferUsage.STATIC_DRAW;

    private final int resizeIncrement;

    private final StagingBuffer stagingBuffer;
    private GlMutableBuffer arenaBuffer;

    private GlBufferSegment head;

    private long capacity;
    private long used;

    private final int stride;

    public GlBufferArena(CommandList commands, int initialCapacity, int stride, StagingBuffer stagingBuffer) {
        this.capacity = initialCapacity;
        this.resizeIncrement = initialCapacity / 16;

        this.stride = stride;

        this.head = new GlBufferSegment(this, 0, initialCapacity);
        this.head.setFree(true);

        this.arenaBuffer = commands.createMutableBuffer();
        commands.allocateStorage(this.arenaBuffer, this.capacity * stride, BUFFER_USAGE);

        this.stagingBuffer = stagingBuffer;
    }

    private void resize(CommandList commandList, long newCapacity) {
        if (this.used > newCapacity) {
            throw new UnsupportedOperationException("New capacity must be larger than used size");
        }

        this.checkAssertions();

        long tail = newCapacity - this.used;

        List<GlBufferSegment> usedSegments = this.getUsedSegments();
        List<PendingBufferCopyCommand> pendingCopies = this.buildTransferList(usedSegments, tail);

        this.transferSegments(commandList, pendingCopies, newCapacity);

        this.head = new GlBufferSegment(this, 0, tail);
        this.head.setFree(true);

        if (usedSegments.isEmpty()) {
            this.head.setNext(null);
        } else {
            this.head.setNext(usedSegments.getFirst());
            this.head.getNext()
                    .setPrev(this.head);
        }

        this.checkAssertions();
    }

    private List<PendingBufferCopyCommand> buildTransferList(List<GlBufferSegment> usedSegments, long base) {
        List<PendingBufferCopyCommand> pendingCopies = new ArrayList<>();
        PendingBufferCopyCommand currentCopyCommand = null;

        long writeOffset = base;

        for (int i = 0; i < usedSegments.size(); i++) {
            GlBufferSegment s = usedSegments.get(i);

            if (currentCopyCommand == null || currentCopyCommand.getReadOffset() + currentCopyCommand.getLength() != s.getOffset()) {
                if (currentCopyCommand != null) {
                    pendingCopies.add(currentCopyCommand);
                }

                currentCopyCommand = new PendingBufferCopyCommand(s.getOffset(), writeOffset, s.getLength());
            } else {
                currentCopyCommand.setLength(currentCopyCommand.getLength() + s.getLength());
            }

            s.setOffset(writeOffset);

            if (i + 1 < usedSegments.size()) {
                s.setNext(usedSegments.get(i + 1));
            } else {
                s.setNext(null);
            }

            if (i - 1 < 0) {
                s.setPrev(null);
            } else {
                s.setPrev(usedSegments.get(i - 1));
            }

            writeOffset += s.getLength();
        }

        if (currentCopyCommand != null) {
            pendingCopies.add(currentCopyCommand);
        }

        return pendingCopies;
    }

    private void transferSegments(CommandList commandList, Collection<PendingBufferCopyCommand> list, long capacity) {
        if (capacity >= (1L << 32)) {
            throw new IllegalArgumentException("Maximum arena buffer size is 4 GiB");
        }

        GlMutableBuffer srcBufferObj = this.arenaBuffer;
        GlMutableBuffer dstBufferObj = commandList.createMutableBuffer();

        commandList.allocateStorage(dstBufferObj, capacity * this.stride, BUFFER_USAGE);

        for (PendingBufferCopyCommand cmd : list) {
            commandList.copyBufferSubData(srcBufferObj, dstBufferObj,
                    cmd.getReadOffset() * this.stride,
                    cmd.getWriteOffset() * this.stride,
                    cmd.getLength() * this.stride);
        }

        commandList.deleteBuffer(srcBufferObj);

        this.arenaBuffer = dstBufferObj;
        this.capacity = capacity;
    }

    private ArrayList<GlBufferSegment> getUsedSegments() {
        ArrayList<GlBufferSegment> used = new ArrayList<>();
        GlBufferSegment seg = this.head;

        while (seg != null) {
            GlBufferSegment next = seg.getNext();

            if (!seg.isFree()) {
                used.add(seg);
            }

            seg = next;
        }

        return used;
    }

    public long getDeviceUsedMemory() {
        return this.used * this.stride;
    }

    public long getDeviceAllocatedMemory() {
        return this.capacity * this.stride;
    }

    private GlBufferSegment alloc(int size) {
        GlBufferSegment a = this.findFree(size);

        if (a == null) {
            return null;
        }

        GlBufferSegment result;

        if (a.getLength() == size) {
            a.setFree(false);

            result = a;
        } else {
            GlBufferSegment b = new GlBufferSegment(this, a.getEnd() - size, size);
            b.setNext(a.getNext());
            b.setPrev(a);

            if (b.getNext() != null) {
                b.getNext()
                        .setPrev(b);
            }

            a.setLength(a.getLength() - size);
            a.setNext(b);

            result = b;
        }

        this.used += result.getLength();
        this.checkAssertions();

        return result;
    }

    private GlBufferSegment findFree(int size) {
        GlBufferSegment entry = this.head;
        GlBufferSegment best = null;

        while (entry != null) {
            if (entry.isFree()) {
                if (entry.getLength() == size) {
                    return entry;
                } else if (entry.getLength() >= size) {
                    if (best == null || best.getLength() > entry.getLength()) {
                        best = entry;
                    }
                }
            }

            entry = entry.getNext();
        }

        return best;
    }

    public void free(GlBufferSegment entry) {
        if (entry.isFree()) {
            throw new IllegalStateException("Already freed");
        }

        entry.setFree(true);

        this.used -= entry.getLength();

        GlBufferSegment next = entry.getNext();

        if (next != null && next.isFree()) {
            entry.mergeInto(next);
        }

        GlBufferSegment prev = entry.getPrev();

        if (prev != null && prev.isFree()) {
            prev.mergeInto(entry);
        }

        this.checkAssertions();
    }

    public void delete(CommandList commands) {
        commands.deleteBuffer(this.arenaBuffer);
    }

    public boolean isEmpty() {
        return this.used <= 0;
    }

    public GlBuffer getBufferObject() {
        return this.arenaBuffer;
    }

    public boolean upload(CommandList commandList, Stream<PendingUpload> stream) {
        // Record the buffer object before we start any work
        // If the arena needs to re-allocate a buffer, this will allow us to check and return an appropriate flag
        GlBuffer buffer = this.arenaBuffer;

        // A linked list is used as we'll be randomly removing elements and want O(1) performance
        List<PendingUpload> queue = stream.collect(Collectors.toCollection(LinkedList::new));

        // Try to upload all of the data into free segments first
        this.tryUploads(commandList, queue);

        // If we weren't able to upload some buffers, they will have been left behind in the queue
        if (!queue.isEmpty()) {
            // Calculate the amount of memory needed for the remaining uploads
            int remainingElements = queue.stream()
                    .mapToInt(upload -> upload.getDataBuffer().getLength())
                    .sum();

            // Ask the arena to grow to accommodate the remaining uploads
            // This will force a re-allocation and compaction, which will leave us a continuous free segment
            // for the remaining uploads
            this.ensureCapacity(commandList, remainingElements);

            // Try again to upload any buffers that failed last time
            this.tryUploads(commandList, queue);

            // If we still had failures, something has gone wrong
            if (!queue.isEmpty()) {
                throw new RuntimeException("Failed to upload all buffers");
            }
        }

        return this.arenaBuffer != buffer;
    }

    private void tryUploads(CommandList commandList, List<PendingUpload> queue) {
        queue.removeIf(upload -> this.tryUpload(commandList, upload));
        this.stagingBuffer.flush(commandList);
    }

    private boolean tryUpload(CommandList commandList, PendingUpload upload) {
        ByteBuffer data = upload.getDataBuffer()
                .getDirectBuffer();

        int elementCount = data.remaining() / this.stride;

        GlBufferSegment dst = this.alloc(elementCount);

        if (dst == null) {
            return false;
        }

        // Copy the data into our staging buffer, then copy it into the arena's buffer
        this.stagingBuffer.enqueueCopy(commandList, data, this.arenaBuffer, dst.getOffset() * this.stride);

        upload.setResult(dst);

        return true;
    }

    public void ensureCapacity(CommandList commandList, long elementCount) {
        // Re-sizing the arena results in a compaction, so any free space in the arena will be
        // made into one contiguous segment, joined with the new segment of free space we're asking for
        // We calculate the number of free elements in our arena and then subtract that from the total requested
        long elementsNeeded = elementCount - (this.capacity - this.used);

        // Try to allocate some extra buffer space unless this is an unusually large allocation
        this.resize(commandList, Math.max(this.capacity + this.resizeIncrement, this.capacity + elementsNeeded));
    }

    private void checkAssertions() {
        if (CHECK_ASSERTIONS) {
            this.checkAssertions0();
        }
    }

    private void checkAssertions0() {
        GlBufferSegment seg = this.head;
        long used = 0;

        while (seg != null) {
            if (seg.getOffset() < 0) {
                throw new IllegalStateException("segment.start < 0: out of bounds");
            } else if (seg.getEnd() > this.capacity) {
                throw new IllegalStateException("segment.end > arena.capacity: out of bounds");
            }

            if (!seg.isFree()) {
                used += seg.getLength();
            }

            GlBufferSegment next = seg.getNext();

            if (next != null) {
                if (next.getOffset() < seg.getEnd()) {
                    throw new IllegalStateException("segment.next.start < segment.end: overlapping segments (corrupted)");
                } else if (next.getOffset() > seg.getEnd()) {
                    throw new IllegalStateException("segment.next.start > segment.end: not truly connected (sparsity error)");
                }

                if (next.isFree() && next.getNext() != null) {
                    if (next.getNext().isFree()) {
                        throw new IllegalStateException("segment.free && segment.next.free: not merged consecutive segments");
                    }
                }
            }

            GlBufferSegment prev = seg.getPrev();

            if (prev != null) {
                if (prev.getEnd() > seg.getOffset()) {
                    throw new IllegalStateException("segment.prev.end > segment.start: overlapping segments (corrupted)");
                } else if (prev.getEnd() < seg.getOffset()) {
                    throw new IllegalStateException("segment.prev.end < segment.start: not truly connected (sparsity error)");
                }

                if (prev.isFree() && prev.getPrev() != null) {
                    if (prev.getPrev().isFree()) {
                        throw new IllegalStateException("segment.free && segment.prev.free: not merged consecutive segments");
                    }
                }
            }

            seg = next;
        }

        if (this.used < 0) {
            throw new IllegalStateException("arena.used < 0: failure to track");
        } else if (this.used > this.capacity) {
            throw new IllegalStateException("arena.used > arena.capacity: failure to track");
        }

        if (this.used != used) {
            throw new IllegalStateException("arena.used is invalid");
        }
    }

}
