package me.jellysquid.mods.sodium.client.model.vertex;

import com.mojang.blaze3d.vertex.VertexConsumer;
import me.jellysquid.mods.sodium.client.model.vertex.type.VertexType;

/**
 * A drain allows the instantiation of {@link VertexSink} and is implemented on outputs which take vertex data.
 */
public interface VertexDrain {
    /**
     * Returns a {@link VertexDrain} implementation on the provided {@link VertexConsumer}. Since the interface
     * is always implemented on a given VertexConsumer, this is simply implemented as a cast internally.
     * @param consumer The {@link VertexConsumer}
     * @return A {@link VertexDrain}
     */
    static VertexDrain of(VertexConsumer consumer) {
        return (VertexDrain) consumer;
    }

    /**
     * Returns a {@link VertexSink} of type {@link T}, created from {@param factory}, which transforms and writes
     * vertices through this vertex drain.
     *
     * @param factory The factory to create a vertex sink using
     * @param <T> The vertex sink's type
     * @return A new {@link VertexSink} of type {@link T}
     */
    <T extends VertexSink> T createSink(VertexType<T> factory);
}
