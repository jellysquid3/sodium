package net.caffeinemc.mods.sodium.client.util.sorting;

import it.unimi.dsi.fastutil.ints.IntArrays;

public class RadixSort {
    private static final int RADIX_SORT_THRESHOLD = 80;

    private static final int DIGIT_BITS = 8;
    private static final int RADIX_KEY_BITS = Integer.BYTES * 8;
    private static final int BUCKET_COUNT = 1 << DIGIT_BITS;
    private static final int DIGIT_COUNT = (RADIX_KEY_BITS + DIGIT_BITS - 1) / DIGIT_BITS;
    private static final int DIGIT_MASK = (1 << DIGIT_BITS) - 1;

    private static void getHistogram(int[][] histogram, int[] keys) {
        for (final int key : keys) {
            for (int digit = 0; digit < DIGIT_COUNT; digit++) {
                histogram[digit][extractDigit(key, digit)] += 1;
            }
        }
    }

    private static void prefixSums(int[][] offsets) {
        for (int digit = 0; digit < DIGIT_COUNT; digit++) {
            final var buckets = offsets[digit];
            var sum = 0;

            for (int bucket_idx = 0; bucket_idx < BUCKET_COUNT; bucket_idx++) {
                final var offset = sum;
                sum += buckets[bucket_idx];
                buckets[bucket_idx] = offset;
            }
        }
    }

    /**
     * <p>Sorts the specified array according to the natural ascending order using an unstable, out-of-place, indirect,
     * 256-way LSD radix sort.</p>
     *
     * <p>This algorithm is well suited for large arrays of integers, especially when they are uniformly distributed
     * over the entire range of the data type.</p>
     *
     * <p>This method implements an <em>indirect</em> sort. The elements of {@param perm} (which must be
     * exactly the numbers in the interval {@code [0..perm.length)}) will be permuted so that
     * {@code x[perm[i]] < x[perm[i + 1]]}.</p>
     *
     * <p>While this radix sort is very fast on larger arrays, there is a certain amount of fixed cost involved in
     * computing the histogram and prefix sums. Because of this, a fallback algorithm (currently quick sort) is used
     * for very small arrays to ensure this method performs well for all inputs of all sizes.</p>
     *
     * @param perm a permutation array indexing {@param keys}.
     * @param keys the array of elements to be sorted.
     */
    public static void sortIndirect(final int[] perm, final int[] keys) {
        if (perm.length <= RADIX_SORT_THRESHOLD) {
            smallSort(perm, keys);
            return;
        }

        int[][] offsets;
        int[] next;

        try {
            offsets = new int[DIGIT_COUNT][BUCKET_COUNT];
            next = new int[perm.length];
        } catch (OutOfMemoryError oom) {
            // Not enough memory to perform an out-of-place sort, so use an in-place alternative.
            fallbackInPlaceSort(perm, keys);
            return;
        }

        sortIndirect(perm, keys, offsets, next);
    }

    private static void sortIndirect(final int[] perm,
                                     final int[] keys,
                                     final int[][] offsets,
                                     int[] next)
    {
        final int length = perm.length;
        getHistogram(offsets, keys);
        prefixSums(offsets);

        int[] cur = perm;

        for (int digit = 0; digit < DIGIT_COUNT; digit++) {
            final var buckets = offsets[digit];

            for (int pos = 0; pos < length; pos++) {
                final var index = cur[pos];
                final var bucket_idx = extractDigit(keys[index], digit);

                next[buckets[bucket_idx]] = index;
                buckets[bucket_idx] += 1;
            }

            {
                // (cur, next) = (next, cur)
                var temp = next;
                next = cur;
                cur = temp;
            }
        }
    }

    private static void smallSort(int[] perm, int[] keys) {
        if (perm.length <= 1) {
            return;
        }

        fallbackInPlaceSort(perm, keys);
    }

    // Fallback sorting method which is guaranteed to be in-place and not require additional memory.
    private static void fallbackInPlaceSort(int[] perm, int[] keys) {
        IntArrays.quickSortIndirect(perm, keys);
    }

    private static int extractDigit(int key, int digit) {
        return ((key >>> (digit * DIGIT_BITS)) & DIGIT_MASK);
    }
}
