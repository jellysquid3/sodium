package net.caffeinemc.mods.sodium.client.util.sorting;

import com.mojang.blaze3d.vertex.VertexSorting;
import net.caffeinemc.mods.sodium.client.util.MathUtil;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public class VertexSorters {
    public static VertexSortingExtended distance(float x, float y, float z) {
        if (x == 0.0f && y == 0.0f && z == 0.0f) {
            return SortByDistanceToOrigin.INSTANCE;
        }

        return new SortByDistanceToPoint(x, y, z);
    }

    public static VertexSortingExtended orthographicZ() {
        return SortByOrthographicZ.INSTANCE;
    }

    // Slow, should only be used when none of the other classes apply.
    public static VertexSortingExtended fallback(VertexSorting.DistanceFunction metric) {
        return new SortByFallback(metric);
    }

    private abstract static class AbstractSorter implements VertexSortingExtended {
        @Override
        public final int @NotNull [] sort(Vector3f[] centroids) {
            final int length = centroids.length;
            final var keys = new int[length];
            final var perm = new int[length];

            for (int index = 0; index < length; index++) {
                keys[index] = ~MathUtil.floatToComparableInt(this.applyMetric(centroids[index]));
                perm[index] = index;
            }

            RadixSort.sortIndirect(perm, keys);

            return perm;
        }
    }

    private static class SortByDistanceToPoint extends AbstractSorter {
        private final float x, y, z;

        private SortByDistanceToPoint(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public float applyMetric(float x, float y, float z) {
            float dx = this.x - x;
            float dy = this.y - y;
            float dz = this.z - z;

            return (dx * dx) + (dy * dy) + (dz * dz);
        }
    }

    private static class SortByDistanceToOrigin extends AbstractSorter {
        private static final SortByDistanceToOrigin INSTANCE = new SortByDistanceToOrigin();

        @Override
        public float applyMetric(float x, float y, float z) {
            return (x * x) + (y * y) + (z * z);
        }
    }

    private static class SortByOrthographicZ extends AbstractSorter {
        private static final SortByOrthographicZ INSTANCE = new SortByOrthographicZ();

        @Override
        public float applyMetric(float x, float y, float z) {
            return -z;
        }
    }

    private static class SortByFallback extends AbstractSorter {
        private final DistanceFunction function;
        private final Vector3f scratch = new Vector3f();

        private SortByFallback(DistanceFunction function) {
            this.function = function;
        }

        @Override
        public float applyMetric(float x, float y, float z) {
            return this.function.apply(this.scratch.set(x, y, z));
        }
    }

    public static int[] sort(ByteBuffer buffer, int vertexCount, int vertexStride, VertexSortingExtended sorting) {
        Validate.isTrue(buffer.remaining() >= vertexStride * vertexCount,
                "Vertex buffer is not large enough to contain all vertices");

        long pVertex0 = MemoryUtil.memAddress(buffer);
        long pVertex2 = MemoryUtil.memAddress(buffer, vertexStride * 2);

        int primitiveCount = vertexCount / 4;
        int primitiveStride = vertexStride * 4;

        final int[] keys = new int[primitiveCount];
        final int[] perm = new int[primitiveCount];

        for (int primitiveId = 0; primitiveId < primitiveCount; primitiveId++) {
            // Position of vertex[0]
            float v0x = MemoryUtil.memGetFloat(pVertex0 + 0L);
            float v0y = MemoryUtil.memGetFloat(pVertex0 + 4L);
            float v0z = MemoryUtil.memGetFloat(pVertex0 + 8L);

            // Position of vertex[2]
            float v2x = MemoryUtil.memGetFloat(pVertex2 + 0L);
            float v2y = MemoryUtil.memGetFloat(pVertex2 + 4L);
            float v2z = MemoryUtil.memGetFloat(pVertex2 + 8L);

            // The centroid of the quad is calculated using the mid-point of the diagonal edge. This will not work
            // for degenerate quads, but those are not sortable anyway.
            float cx = (v0x + v2x) * 0.5F;
            float cy = (v0y + v2y) * 0.5F;
            float cz = (v0z + v2z) * 0.5F;

            // The sign bit of the metric is negated as we need back-to-front (descending) ordering.
            keys[primitiveId] = MathUtil.floatToComparableInt(-sorting.applyMetric(cx, cy, cz));
            perm[primitiveId] = primitiveId;

            pVertex0 += primitiveStride;
            pVertex2 += primitiveStride;
        }

        RadixSort.sortIndirect(perm, keys);

        return perm;
    }
}
