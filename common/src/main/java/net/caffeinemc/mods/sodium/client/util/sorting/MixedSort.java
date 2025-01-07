/*
 * Copyright (C) 2002-2017 Sebastiano Vigna
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 *
 * For the sorting and binary search code:
 *
 * Copyright (C) 1999 CERN - European Organization for Nuclear Research.
 *
 *   Permission to use, copy, modify, distribute and sell this software and
 *   its documentation for any purpose is hereby granted without fee,
 *   provided that the above copyright notice appear in all copies and that
 *   both that copyright notice and this permission notice appear in
 *   supporting documentation. CERN makes no representations about the
 *   suitability of this software for any purpose. It is provided "as is"
 *   without expressed or implied warranty.
 */
package net.caffeinemc.mods.sodium.client.util.sorting;


import it.unimi.dsi.fastutil.ints.IntArrays;

import java.util.Arrays;

public class MixedSort extends AbstractSort {
    private static final int INDIRECT_SORT_THRESHOLD = 1000;

    public static int[] mixedSort(int[] keys) {
        // indirect sorting is faster for small arrays, packed direct sorting is faster for large arrays
        if (keys.length <= INDIRECT_SORT_THRESHOLD) {
            return indirectRadixSort(keys);
        } else {
            return packedUnstableSort(keys);
        }
    }

    private static int[] indirectRadixSort(int[] keys) {
        var indices = createIndexBuffer(keys.length);
        IntArrays.radixSortIndirect(indices, keys, false);
        return indices;
    }

    private static int[] packedUnstableSort(int[] keys) {
        var items = prepareItems(keys);
        Arrays.sort(items);
        return extractIndices(items);
    }
}
