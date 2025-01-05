package net.caffeinemc.mods.sodium.client.render.chunk.data;

import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.util.NativeBuffer;

public class BuiltSectionMeshParts {
    private final int[] vertexSegments;
    private final NativeBuffer buffer;

    public BuiltSectionMeshParts(NativeBuffer buffer, int[] vertexCounts) {
        this.vertexSegments = vertexCounts;
        this.buffer = buffer;
    }

    public NativeBuffer getVertexData() {
        return this.buffer;
    }

    public int[] getVertexSegments() {
        return this.vertexSegments;
    }

    public int[] computeVertexCounts() {
        var vertexCounts = new int[ModelQuadFacing.COUNT];

        for (int i = 0; i < this.vertexSegments.length; i += 2) {
            vertexCounts[this.vertexSegments[i + 1]] = this.vertexSegments[i];
        }

        return vertexCounts;
    }
}
