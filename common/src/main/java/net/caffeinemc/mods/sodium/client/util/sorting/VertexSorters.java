package net.caffeinemc.mods.sodium.client.util.sorting;

import com.mojang.blaze3d.vertex.VertexSorting;
import org.joml.Vector3f;

public class VertexSorters {
    public static VertexSorting sortByDistance(Vector3f origin) {
        return new SortByDistance(origin);
    }

    private static class SortByDistance extends AbstractVertexSorter {
        private final Vector3f origin;

        private SortByDistance(Vector3f origin) {
            this.origin = origin;
        }

        @Override
        protected float getKey(Vector3f position) {
            return this.origin.distanceSquared(position);
        }
    }

    /**
     * Sorts the keys given by the subclass by descending value.
     */
    private static abstract class AbstractVertexSorter implements VertexSorting {
        @Override
        public final int[] sort(Vector3f[] positions) {
            final var keys = new int[positions.length];

            for (int index = 0; index < positions.length; index++) {
                keys[index] = ~Float.floatToRawIntBits(this.getKey(positions[index]));
            }

            return MixedSort.mixedSort(keys);
        }

        protected abstract float getKey(Vector3f object);
    }
}
