package net.caffeinemc.mods.sodium.client.util.sorting;


public class AbstractSort {
    protected static int[] createIndexBuffer(int length) {
        var indices = new int[length];

        for (int i = 0; i < length; i++) {
            indices[i] = i;
        }

        return indices;
    }

    protected static int[] extractIndices(long[] items) {
        int[] indices = new int[items.length];
        for (int i = 0; i < items.length; i++) {
            indices[i] = (int) items[i];
        }
        return indices;
    }

    protected static long[] prepareItems(int[] keys) {
        long[] items = new long[keys.length];
        for (int i = 0; i < keys.length; i++) {
            items[i] = (long) keys[i] << 32 | i;
        }
        return items;
    }
}
