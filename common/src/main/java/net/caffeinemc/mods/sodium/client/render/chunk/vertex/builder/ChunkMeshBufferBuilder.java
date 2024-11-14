package net.caffeinemc.mods.sodium.client.render.chunk.vertex.builder;

import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import org.apache.commons.lang3.Validate;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;

public class ChunkMeshBufferBuilder {
    private final ChunkVertexEncoder encoder;
    private final int stride;

    private final int initialCapacity;

    private ByteBuffer buffer;
    private int vertexCount;
    private int vertexCapacity;

    private int sectionIndex;

    public ChunkMeshBufferBuilder(ChunkVertexType vertexType, int initialCapacity) {
        this.encoder = vertexType.getEncoder();
        this.stride = vertexType.getVertexFormat().getStride();

        this.buffer = null;

        this.vertexCapacity = initialCapacity;
        this.initialCapacity = initialCapacity;
    }

    public void push(ChunkVertexEncoder.Vertex[] vertices, Material material) {
        this.push(vertices, material.bits());
    }

    public void push(ChunkVertexEncoder.Vertex[] vertices, int materialBits) {
        if (vertices.length != 4) {
            throw new IllegalArgumentException("Only quad primitives (with 4 vertices) can be pushed");
        }

        this.ensureCapacity(4);

        this.encoder.write(MemoryUtil.memAddress(this.buffer, this.vertexCount * this.stride),
                materialBits, vertices, this.sectionIndex);
        this.vertexCount += 4;
    }

    private void ensureCapacity(int vertexCount) {
        if (this.vertexCount + vertexCount >= this.vertexCapacity) {
            this.grow(vertexCount);
        }
    }

    private void grow(int vertexCount) {
        this.reallocate(
                // The new capacity will at least twice as large
                Math.max(this.vertexCapacity * 2, this.vertexCapacity + vertexCount)
        );
    }

    private void reallocate(int vertexCount) {
        this.buffer = MemoryUtil.memRealloc(this.buffer, vertexCount * this.stride);
        this.vertexCapacity = vertexCount;
    }

    public void start(int sectionIndex) {
        this.vertexCount = 0;
        this.sectionIndex = sectionIndex;

        this.reallocate(this.initialCapacity);
    }

    public void destroy() {
        if (this.buffer != null) {
            MemoryUtil.memFree(this.buffer);
        }

        this.buffer = null;
    }

    public boolean isEmpty() {
        return this.vertexCount == 0;
    }

    public ByteBuffer slice() {
        if (this.isEmpty()) {
            throw new IllegalStateException("No vertex data in buffer");
        }

        return MemoryUtil.memSlice(this.buffer, 0, this.stride * this.vertexCount);
    }

    public int count() {
        return this.vertexCount;
    }
}
