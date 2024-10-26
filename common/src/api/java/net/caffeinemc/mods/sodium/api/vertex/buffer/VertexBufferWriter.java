package net.caffeinemc.mods.sodium.api.vertex.buffer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.caffeinemc.mods.sodium.api.memory.MemoryIntrinsics;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryStack;

public interface VertexBufferWriter {
    /**
     * Converts a {@link VertexConsumer} into a {@link VertexBufferWriter}.
     *
     * @param consumer The vertex consumer to create a writer for
     * @return An implementation of {@link VertexBufferWriter} which will write vertices into {@param consumer}
     * @throws IllegalArgumentException If the vertex consumer does not implement the necessary interface
     */
    static VertexBufferWriter of(VertexConsumer consumer) {
        if (consumer instanceof VertexBufferWriter writer && writer.canUseIntrinsics()) {
            return writer;
        }

        throw createUnsupportedVertexConsumerThrowable(consumer);
    }

    /**
     * Converts a {@link VertexConsumer} into a {@link VertexBufferWriter} if possible.
     *
     * @param consumer The vertex consumer to create a writer for
     * @return An implementation of {@link VertexBufferWriter} which will write vertices into {@param consumer}, or null
     * if the vertex consumer does not implement the necessary interface
     */
    @Nullable
    static VertexBufferWriter tryOf(VertexConsumer consumer) {
        if (consumer instanceof VertexBufferWriter writer && writer.canUseIntrinsics()) {
            return writer;
        }

        return null;
    }

    private static RuntimeException createUnsupportedVertexConsumerThrowable(VertexConsumer consumer) {
        var clazz = consumer.getClass();
        var name = clazz.getName();

        return new IllegalArgumentException(("The class %s does not implement interface VertexBufferWriter, " +
                "which is required for compatibility with Sodium (see: https://github.com/CaffeineMC/sodium/issues/1620)").formatted(name));
    }

    /**
     * Copy the vertices from the source buffer and pushes them into this vertex buffer. The vertex buffer
     * is advanced by {@param count} vertices. If the vertex format differs from the target format of the
     * vertex buffer, a conversion will be performed.
     * <p>
     * After calling this function, the contents of {@param ptr} are undefined.
     * <p>
     * If {@param stack} is used by the caller, the stack frame will not be pushed/popped. This function should
     * only be called in a try-with-resources block with the provided stack, otherwise it could leak memory.
     *
     * @param stack  The memory stack which can be used as scratch memory
     * @param ptr    The pointer to read vertices from
     * @param count  The number of vertices to copy
     * @param format The format of the vertices
     */
    void push(MemoryStack stack, long ptr, int count, VertexFormat format);

    /**
     * If this {@link VertexBufferWriter} passes through data to nested {@link VertexConsumer} implementations,
     * this method should be implemented to check that the nested implementations also support use of VertexBufferWriter
     * methods.
     * @return true if the inner consumer is also a valid {@link VertexBufferWriter} that can use intrinsics
     */
    default boolean canUseIntrinsics() {
        return true;
    }

    /**
     * Creates a copy of the source data and pushes it into the specified {@param writer}. This is useful for when
     * you need to use to re-use the source data after the call, and do not want the {@param writer} to modify
     * the original data.
     *
     * @param writer   The buffer to push the vertices into
     * @param stack    The memory stack which can be used to create temporary allocations
     * @param ptr      The pointer to read vertices from
     * @param count    The number of vertices to push
     * @param format   The format of the vertices
     */
    static void copyInto(VertexBufferWriter writer,
                         MemoryStack stack, long ptr, int count,
                         VertexFormat format)
    {
        var length = count * format.getVertexSize();
        var copy = stack.nmalloc(length);

        MemoryIntrinsics.copyMemory(ptr, copy, length);

        writer.push(stack, copy, count, format);
    }
}
