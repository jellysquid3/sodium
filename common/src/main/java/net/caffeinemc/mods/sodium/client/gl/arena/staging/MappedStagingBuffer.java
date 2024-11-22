package net.caffeinemc.mods.sodium.client.gl.arena.staging;

import it.unimi.dsi.fastutil.PriorityQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.gl.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.gl.functions.BufferStorageFunctions;
import net.caffeinemc.mods.sodium.client.gl.sync.GlFence;
import net.caffeinemc.mods.sodium.client.gl.util.EnumBitField;
import net.caffeinemc.mods.sodium.client.util.MathUtil;
import net.caffeinemc.mods.sodium.client.gl.buffer.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class MappedStagingBuffer implements StagingBuffer {
    private static final float UPLOAD_LIMIT_MARGIN = 0.8f;

    private static final EnumBitField<GlBufferStorageFlags> STORAGE_FLAGS =
            EnumBitField.of(GlBufferStorageFlags.PERSISTENT, GlBufferStorageFlags.CLIENT_STORAGE, GlBufferStorageFlags.MAP_WRITE);

    private static final EnumBitField<GlBufferMapFlags> MAP_FLAGS =
            EnumBitField.of(GlBufferMapFlags.PERSISTENT, GlBufferMapFlags.INVALIDATE_BUFFER, GlBufferMapFlags.WRITE, GlBufferMapFlags.EXPLICIT_FLUSH);

    private final FallbackStagingBuffer fallbackStagingBuffer;

    private final MappedBuffer mappedBuffer;
    private final PriorityQueue<CopyCommand> pendingCopies = new ObjectArrayFIFOQueue<>();
    private final PriorityQueue<FencedMemoryRegion> fencedRegions = new ObjectArrayFIFOQueue<>();

    private int start = 0;
    private int pos = 0;

    private final int capacity;
    private int remaining;

    public MappedStagingBuffer(CommandList commandList) {
        this(commandList, 1024 * 1024 * 16 /* 16 MB */);
    }

    public MappedStagingBuffer(CommandList commandList, int capacity) {
        GlImmutableBuffer buffer = commandList.createImmutableBuffer(capacity, STORAGE_FLAGS);
        GlBufferMapping map = commandList.mapBuffer(buffer, 0, capacity, MAP_FLAGS);

        this.mappedBuffer = new MappedBuffer(buffer, map);
        this.fallbackStagingBuffer = new FallbackStagingBuffer(commandList);
        this.capacity = capacity;
        this.remaining = this.capacity;
    }

    public static boolean isSupported(RenderDevice instance) {
        return instance.getDeviceFunctions().getBufferStorageFunctions() != BufferStorageFunctions.NONE;
    }

    @Override
    public void enqueueCopy(CommandList commandList, ByteBuffer data, GlBuffer dst, long writeOffset) {
        int length = data.remaining();

        if (length > this.remaining) {
            this.fallbackStagingBuffer.enqueueCopy(commandList, data, dst, writeOffset);

            return;
        }

        int remaining = this.capacity - this.pos;

        // Split the transfer in two if we have enough available memory at the end and start of the buffer
        if (length > remaining) {
            int split = length - remaining;

            this.addTransfer(data.slice(0, remaining), dst, this.pos, writeOffset);
            this.addTransfer(data.slice(remaining, split), dst, 0, writeOffset + remaining);

            this.pos = split;
        } else {
            this.addTransfer(data, dst, this.pos, writeOffset);
            this.pos += length;
        }

        this.remaining -= length;
    }

    private void addTransfer(ByteBuffer data, GlBuffer dst, long readOffset, long writeOffset) {
        this.mappedBuffer.map.write(data, (int) readOffset);
        this.pendingCopies.enqueue(new CopyCommand(dst, readOffset, writeOffset, data.remaining()));
    }

    @Override
    public void flush(CommandList commandList) {
        if (this.pendingCopies.isEmpty()) {
            return;
        }

        if (this.pos < this.start) {
            commandList.flushMappedRange(this.mappedBuffer.map, this.start, this.capacity - this.start);
            commandList.flushMappedRange(this.mappedBuffer.map, 0, this.pos);
        } else {
            commandList.flushMappedRange(this.mappedBuffer.map, this.start, this.pos - this.start);
        }

        int bytes = 0;

        for (CopyCommand command : consolidateCopies(this.pendingCopies)) {
            bytes += command.bytes;

            commandList.copyBufferSubData(this.mappedBuffer.buffer, command.buffer, command.readOffset, command.writeOffset, command.bytes);
        }

        this.fencedRegions.enqueue(new FencedMemoryRegion(commandList.createFence(), bytes));

        this.start = this.pos;
    }

    private static List<CopyCommand> consolidateCopies(PriorityQueue<CopyCommand> queue) {
        List<CopyCommand> merged = new ArrayList<>();
        CopyCommand last = null;

        while (!queue.isEmpty()) {
            CopyCommand command = queue.dequeue();

            if (last != null) {
                if (last.buffer == command.buffer &&
                        last.writeOffset + last.bytes == command.writeOffset &&
                        last.readOffset + last.bytes == command.readOffset) {
                    last.bytes += command.bytes;
                    continue;
                }
            }

            merged.add(last = new CopyCommand(command));
        }

        return merged;
    }

    @Override
    public void delete(CommandList commandList) {
        this.mappedBuffer.delete(commandList);
        this.fallbackStagingBuffer.delete(commandList);
        this.pendingCopies.clear();
    }

    @Override
    public void flip() {
        while (!this.fencedRegions.isEmpty()) {
            var region = this.fencedRegions.first();
            var fence = region.fence();

            if (!fence.isCompleted()) {
                break;
            }

            fence.delete();

            this.fencedRegions.dequeue();
            this.remaining += region.length();
        }
    }

    @Override
    public long getUploadSizeLimit(long frameDuration) {
        return (long) (this.capacity * UPLOAD_LIMIT_MARGIN);
    }

    private static final class CopyCommand {
        private final GlBuffer buffer;
        private final long readOffset;
        private final long writeOffset;

        private long bytes;

        private CopyCommand(GlBuffer buffer, long readOffset, long writeOffset, long bytes) {
            this.buffer = buffer;
            this.readOffset = readOffset;
            this.writeOffset = writeOffset;
            this.bytes = bytes;
        }

        public CopyCommand(CopyCommand command) {
            this.buffer = command.buffer;
            this.writeOffset = command.writeOffset;
            this.readOffset = command.readOffset;
            this.bytes = command.bytes;
        }
    }

    private record MappedBuffer(GlImmutableBuffer buffer,
                                GlBufferMapping map) {
        public void delete(CommandList commandList) {
            commandList.unmap(this.map);
            commandList.deleteBuffer(this.buffer);
        }
    }

    private record FencedMemoryRegion(GlFence fence, int length) {

    }

    @Override
    public String toString() {
        return "Mapped (%s/%s MiB)".formatted(MathUtil.toMib(this.remaining), MathUtil.toMib(this.capacity));
    }
}
